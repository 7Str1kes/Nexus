package org.glstudio.nexus.modules.menu.extra;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.glstudio.nexus.modules.menu.Menu;
import org.glstudio.nexus.modules.menu.MenuManager;
import org.glstudio.nexus.modules.menu.button.Button;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A generic yes/no confirmation dialog: a display item, a confirm button and a cancel button.
 * Guarded against double-firing (a fast double click only ever triggers one outcome), and treats
 * closing the menu without deciding as an implicit cancel.
 *
 * <p>Build one with {@link #builder(MenuManager, Player)} rather than the constructor directly.
 */
public final class ConfirmMenu extends Menu {

    private final int displaySlot;
    private final ItemStack displayItem;
    private final int confirmSlot;
    private final ItemStack confirmItem;
    private final int cancelSlot;
    private final ItemStack cancelItem;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    private final AtomicBoolean decided = new AtomicBoolean(false);

    private ConfirmMenu(MenuManager manager, Player viewer, String title, int size,
                         int displaySlot, ItemStack displayItem,
                         int confirmSlot, ItemStack confirmItem,
                         int cancelSlot, ItemStack cancelItem,
                         Runnable onConfirm, Runnable onCancel) {
        super(manager, viewer, title, size);
        this.displaySlot = displaySlot;
        this.displayItem = displayItem;
        this.confirmSlot = confirmSlot;
        this.confirmItem = confirmItem;
        this.cancelSlot = cancelSlot;
        this.cancelItem = cancelItem;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    private void decide(boolean confirmed) {
        if (!decided.compareAndSet(false, true)) return;
        close();
        if (confirmed) {
            if (onConfirm != null) onConfirm.run();
        } else {
            if (onCancel != null) onCancel.run();
        }
    }

    @Override
    protected void render() {
        clear();
        if (displayItem != null && displaySlot >= 0) place(displaySlot, Button.of(displayItem));
        place(confirmSlot, Button.of(confirmItem, () -> decide(true)));
        place(cancelSlot, Button.of(cancelItem, () -> decide(false)));
    }

    @Override
    protected void onClose() {
        // Closed without a decision (escape, another menu, disconnect via close-on-quit) — treat
        // it as a cancel exactly once, same as clicking the cancel button.
        if (decided.compareAndSet(false, true) && onCancel != null) onCancel.run();
    }

    public static Builder builder(MenuManager manager, Player viewer) {
        return new Builder(manager, viewer);
    }

    public static final class Builder {
        private final MenuManager manager;
        private final Player viewer;
        private String title = "Confirm";
        private int size = 27;
        private int displaySlot = -1;
        private ItemStack displayItem;
        private int confirmSlot = 11;
        private ItemStack confirmItem;
        private int cancelSlot = 15;
        private ItemStack cancelItem;
        private Runnable onConfirm = () -> {
        };
        private Runnable onCancel = () -> {
        };

        private Builder(MenuManager manager, Player viewer) {
            this.manager = manager;
            this.viewer = viewer;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public Builder display(int slot, ItemStack item) {
            this.displaySlot = slot;
            this.displayItem = item;
            return this;
        }

        public Builder confirm(int slot, ItemStack item, Runnable action) {
            this.confirmSlot = slot;
            this.confirmItem = item;
            this.onConfirm = action;
            return this;
        }

        public Builder cancel(int slot, ItemStack item, Runnable action) {
            this.cancelSlot = slot;
            this.cancelItem = item;
            this.onCancel = action;
            return this;
        }

        public ConfirmMenu build() {
            return new ConfirmMenu(manager, viewer, title, size, displaySlot, displayItem,
                    confirmSlot, confirmItem, cancelSlot, cancelItem, onConfirm, onCancel);
        }

        public void open() {
            build().open();
        }

        public void openLater() {
            build().openLater();
        }
    }
}
