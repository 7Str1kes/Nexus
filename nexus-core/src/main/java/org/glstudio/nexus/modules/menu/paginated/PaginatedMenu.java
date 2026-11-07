package org.glstudio.nexus.modules.menu.paginated;

import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.menu.Menu;
import org.glstudio.nexus.modules.menu.MenuManager;
import org.glstudio.nexus.modules.menu.button.Button;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link Menu} that lays a list of {@code T} out across a fixed pool of content slots, one page at
 * a time, from data already in memory.
 *
 * <p>{@link #getItems(Player)} is called on every render and sliced in memory. For data that must be
 * fetched page by page, use {@link AsyncPaginatedMenu}.
 *
 * @param <T> the listed type
 */
public abstract class PaginatedMenu<T> extends Menu {

    private List<Integer> contentSlots;

    /**
     * Current page, from 0. Protected so a subclass that overrides {@link #render()} entirely can
     * clamp it like its own field.
     */
    protected int page = 0;

    /**
     * Creates the menu.
     *
     * @param manager the plugin's menu manager
     * @param viewer  the player who will see it
     * @param title   the title, legacy colour codes allowed
     * @param size    the size in slots
     */
    protected PaginatedMenu(MenuManager manager, Player viewer, String title, int size) {
        super(manager, viewer, title, size);
    }

    /**
     * Returns the slots the paged content is drawn into.
     *
     * @return the slots set with {@link #setContentSlots(List)}, or the center slots
     */
    protected List<Integer> getContentSlots() {
        if (contentSlots == null || contentSlots.isEmpty()) return getCenterSlots();
        return contentSlots;
    }

    /**
     * Sets the slots the paged content is drawn into.
     *
     * @param slots the slots, in drawing order
     */
    protected final void setContentSlots(List<Integer> slots) {
        this.contentSlots = slots;
    }

    /**
     * Returns the full, unpaged list. Called on every render: keep it cheap or cache upstream.
     *
     * @param viewer the viewer
     * @return the items
     */
    protected abstract List<T> getItems(Player viewer);

    /**
     * Turns one item into its button.
     *
     * @param item the item
     * @return the button
     */
    protected abstract Button toButton(T item);

    /**
     * Returns buttons drawn over the content on every render: navigation, page info...
     *
     * @param viewer  the viewer
     * @param page    the current page, from 0
     * @param maxPage the last page, from 0
     * @return buttons by slot
     */
    protected Map<Integer, Button> getStaticButtons(Player viewer, int page, int maxPage) {
        return Map.of();
    }

    /**
     * Returns the button shown when the list is empty.
     *
     * @param viewer the viewer
     * @return the button, or {@code null} to draw nothing
     */
    protected Button getEmptyButton(Player viewer) {
        return null;
    }

    /**
     * Returns the slot of the empty-state button.
     *
     * @param viewer the viewer
     * @return the slot, or -1 for the first content slot
     */
    protected int getEmptySlot(Player viewer) {
        return -1;
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
     * Returns the last page.
     *
     * @return the last page, from 0
     */
    public final int getMaxPage() {
        int perPage = Math.max(1, getContentSlots().size());
        int itemCount = getItems(viewer).size();
        return itemCount == 0 ? 0 : (itemCount - 1) / perPage;
    }

    /**
     * Shows {@code targetPage}, clamped to the existing pages.
     *
     * @param targetPage the page, from 0
     */
    public final void goToPage(int targetPage) {
        this.page = Math.max(0, Math.min(targetPage, getMaxPage()));
        update();
    }

    /**
     * Shows the next page, if any.
     */
    public final void nextPage() {
        goToPage(page + 1);
    }

    /**
     * Shows the previous page, if any.
     */
    public final void previousPage() {
        goToPage(page - 1);
    }

    /**
     * Shows the first page.
     */
    public final void firstPage() {
        goToPage(0);
    }

    /**
     * Shows the last page.
     */
    public final void lastPage() {
        goToPage(getMaxPage());
    }

    /**
     * Returns {@code page}, {@code max_page}, {@code next_page} and {@code previous_page} (1-based)
     * for navigation button text.
     *
     * @return the tokens
     */
    protected final Map<String, String> pageTokens() {
        int maxPage = getMaxPage();
        Map<String, String> tokens = new HashMap<>();
        tokens.put("page", String.valueOf(page + 1));
        tokens.put("max_page", String.valueOf(maxPage + 1));
        tokens.put("next_page", String.valueOf(Math.min(page + 2, maxPage + 1)));
        tokens.put("previous_page", String.valueOf(Math.max(page, 1)));
        return tokens;
    }

    @Override
    protected void render() {
        clear();

        List<T> items = getItems(viewer);
        List<Integer> slots = getContentSlots();
        int perPage = Math.max(1, slots.size());
        int maxPage = items.isEmpty() ? 0 : (items.size() - 1) / perPage;
        if (page > maxPage) page = maxPage;

        if (items.isEmpty()) {
            Button empty = getEmptyButton(viewer);
            if (empty != null && !slots.isEmpty()) {
                int emptySlot = getEmptySlot(viewer);
                place(emptySlot >= 0 ? emptySlot : slots.get(0), empty);
            }
        } else {
            int start = page * perPage;
            int end = Math.min(items.size(), start + perPage);
            for (int i = start; i < end; i++) {
                place(slots.get(i - start), toButton(items.get(i)));
            }
        }

        place(getStaticButtons(viewer, page, maxPage));
    }
}
