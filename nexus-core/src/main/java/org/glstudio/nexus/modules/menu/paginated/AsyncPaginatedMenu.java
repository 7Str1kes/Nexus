package org.glstudio.nexus.modules.menu.paginated;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.menu.Menu;
import org.glstudio.nexus.modules.menu.MenuManager;
import org.glstudio.nexus.modules.menu.button.Button;
import org.glstudio.nexus.utils.Diagnostics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link Menu} whose content is fetched one page at a time, typically from a database.
 *
 * <p>Turning a page runs a new query: only the requested page is ever loaded. While a page loads the
 * menu shows {@link #loadingButton()}; a result that arrives after the viewer moved on (another page,
 * a closed menu) is discarded.
 *
 * @param <T> the listed type
 */
public abstract class AsyncPaginatedMenu<T> extends Menu {

    private List<Integer> contentSlots;
    private int page = 0;
    private int totalPages = 1;
    private List<T> current = List.of();
    private boolean loading;
    private int generation;

    /**
     * Creates the menu.
     *
     * @param manager the plugin's menu manager
     * @param viewer  the player who will see it
     * @param title   the title, legacy colour codes allowed
     * @param size    the size in slots
     */
    protected AsyncPaginatedMenu(MenuManager manager, Player viewer, String title, int size) {
        super(manager, viewer, title, size);
    }

    /**
     * One page of results.
     *
     * @param items      the items of the page
     * @param totalItems how many items exist in total
     * @param <T>        the listed type
     */
    public record Page<T>(List<T> items, int totalItems) {

        /**
         * Creates a page with an immutable copy of the items.
         *
         * @param items      the items of the page
         * @param totalItems how many items exist in total
         */
        public Page {
            items = List.copyOf(items);
        }
    }

    /**
     * Loads one page. Called off no particular thread; the result is applied on the main thread.
     *
     * @param page     the page, from 0
     * @param pageSize how many items fit
     * @return the page
     */
    protected abstract CompletableFuture<Page<T>> loadPage(int page, int pageSize);

    /**
     * Turns one item into its button.
     *
     * @param item the item
     * @return the button
     */
    protected abstract Button toButton(T item);

    /**
     * Returns buttons drawn over the content on every render.
     *
     * @param page       the current page, from 0
     * @param totalPages the number of pages, at least 1
     * @return buttons by slot
     */
    protected Map<Integer, Button> getStaticButtons(int page, int totalPages) {
        return Map.of();
    }

    /**
     * Returns the button drawn in the first content slot while a page loads.
     *
     * @return the button, or {@code null} for none
     */
    protected Button loadingButton() {
        return null;
    }

    /**
     * Returns the button drawn in the first content slot when the page is empty.
     *
     * @return the button, or {@code null} for none
     */
    protected Button emptyButton() {
        return null;
    }

    /**
     * Called on the main thread when a page fails to load. Logs by default.
     *
     * @param error the failure
     */
    protected void onLoadFailed(Throwable error) {
        Diagnostics.logFailure(getClass().getSimpleName() + "#loadPage", error);
    }

    /**
     * Returns the content slots.
     *
     * @return the slots set with {@link #setContentSlots(List)}, or the center slots
     */
    protected List<Integer> getContentSlots() {
        return contentSlots == null || contentSlots.isEmpty() ? getCenterSlots() : contentSlots;
    }

    /**
     * Sets the content slots.
     *
     * @param slots the slots, in drawing order
     */
    protected final void setContentSlots(List<Integer> slots) {
        this.contentSlots = slots;
    }

    /**
     * Opens the menu showing the loading state and starts loading the current page.
     */
    @Override
    public void open() {
        loading = true;
        super.open();
        reload();
    }

    /**
     * Loads the current page again.
     */
    public final void reload() {
        int requested = ++generation;
        loading = true;
        if (!isDestroyed() && viewer.getOpenInventory().getTopInventory().equals(getInventory())) update();

        int pageSize = Math.max(1, getContentSlots().size());
        loadPage(page, pageSize).whenComplete((result, error) -> {
            if (!plugin.isEnabled()) return;
            // The query completes on a worker thread; rendering touches the inventory.
            Bukkit.getScheduler().runTask(plugin, () -> apply(requested, pageSize, result, error));
        });
    }

    private void apply(int requested, int pageSize, Page<T> result, Throwable error) {
        if (requested != generation || isDestroyed()) return;
        loading = false;
        if (error != null) {
            current = List.of();
            onLoadFailed(error);
        } else {
            current = result.items();
            totalPages = Math.max(1, (result.totalItems() + pageSize - 1) / pageSize);
        }
        update();
    }

    /**
     * Loads {@code target}, clamped to the known pages.
     *
     * @param target the page, from 0
     */
    public final void goToPage(int target) {
        int clamped = Math.max(0, Math.min(target, totalPages - 1));
        if (clamped == page) return;
        page = clamped;
        reload();
    }

    /**
     * Loads the next page, if any.
     */
    public final void nextPage() {
        goToPage(page + 1);
    }

    /**
     * Loads the previous page, if any.
     */
    public final void previousPage() {
        goToPage(page - 1);
    }

    /**
     * Returns the current page.
     *
     * @return the page, from 0
     */
    public final int getPage() {
        return page;
    }

    /**
     * Returns the number of pages known from the last load.
     *
     * @return at least 1
     */
    public final int getTotalPages() {
        return totalPages;
    }

    /**
     * Returns whether a page is loading.
     *
     * @return {@code true} while loading
     */
    public final boolean isLoading() {
        return loading;
    }

    @Override
    protected void render() {
        clear();
        List<Integer> slots = getContentSlots();
        if (loading) {
            Button button = loadingButton();
            if (button != null && !slots.isEmpty()) place(slots.getFirst(), button);
        } else if (current.isEmpty()) {
            Button button = emptyButton();
            if (button != null && !slots.isEmpty()) place(slots.getFirst(), button);
        } else {
            for (int i = 0; i < current.size() && i < slots.size(); i++) {
                place(slots.get(i), toButton(current.get(i)));
            }
        }
        place(getStaticButtons(page, totalPages));
    }
}
