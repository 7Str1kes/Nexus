package org.glstudio.nexus.modules.menu.paginated;

import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.menu.Menu;
import org.glstudio.nexus.modules.menu.MenuManager;
import org.glstudio.nexus.modules.menu.button.Button;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link Menu} that lays a list of {@code T} out across a fixed pool of content slots, one page
 * at a time.
 *
 * <p>The default {@link #render()} is synchronous: {@link #getItems(Player)} is called on every
 * render and sliced in memory. For data that must be fetched (a DB-backed list), override
 * {@link #render()} instead — render a "loading" state immediately, kick off the fetch, and call
 * {@link #update()} from the main thread once it completes. {@link #goToPage(int)} and friends
 * work the same way either way; they just call {@link #update()}.
 */
public abstract class PaginatedMenu<T> extends Menu {

    private List<Integer> contentSlots;

    /**
     * Protected (not private) so a subclass that overrides {@link #render()} entirely — to
     * reproduce its own pre-existing config-driven pagination convention — can clamp it directly,
     * the same way it would clamp its own field.
     */
    protected int page = 0;

    protected PaginatedMenu(MenuManager manager, Player viewer, String title, int size) {
        super(manager, viewer, title, size);
    }

    /** The slots the paged content is drawn into. Defaults to {@link #getCenterSlots()}. */
    protected List<Integer> getContentSlots() {
        if (contentSlots == null || contentSlots.isEmpty()) return getCenterSlots();
        return contentSlots;
    }

    protected final void setContentSlots(List<Integer> slots) {
        this.contentSlots = slots;
    }

    /** The full, unpaged item list. Called once per render — keep it cheap or cache upstream. */
    protected abstract List<T> getItems(Player viewer);

    protected abstract Button toButton(T item);

    /** Buttons overlaid on top of the content slots every render — nav buttons, page info, etc. */
    protected Map<Integer, Button> getStaticButtons(Player viewer, int page, int maxPage) {
        return Map.of();
    }

    /** Shown across every content slot when {@link #getItems} is empty. Null draws nothing. */
    protected Button getEmptyButton(Player viewer) {
        return null;
    }

    public final int getPage() {
        return page;
    }

    public final int getMaxPage() {
        int perPage = Math.max(1, getContentSlots().size());
        int itemCount = getItems(viewer).size();
        return itemCount == 0 ? 0 : (itemCount - 1) / perPage;
    }

    public final void goToPage(int targetPage) {
        this.page = Math.max(0, Math.min(targetPage, getMaxPage()));
        update();
    }

    public final void nextPage() {
        goToPage(page + 1);
    }

    public final void previousPage() {
        goToPage(page - 1);
    }

    public final void firstPage() {
        goToPage(0);
    }

    public final void lastPage() {
        goToPage(getMaxPage());
    }

    /** {@code %page%/%max_page%/%next_page%/%previous_page%} (1-based) for use in nav button text. */
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
            if (empty != null) place(slots, empty);
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
