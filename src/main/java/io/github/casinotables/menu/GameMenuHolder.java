package io.github.casinotables.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class GameMenuHolder implements InventoryHolder {
    enum Page { MAIN, LOBBY_INVITES, OPEN_ROOMS }

    private final UUID viewer;
    private final Page page;
    private final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    GameMenuHolder(UUID viewer, Page page) {
        this.viewer = viewer;
        this.page = page;
    }

    UUID viewer() { return viewer; }
    Page page() { return page; }
    void inventory(Inventory inventory) { this.inventory = inventory; }
    void action(int slot, String action) { actions.put(slot, action); }
    String action(int slot) { return actions.get(slot); }

    @Override
    public Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("Menu inventory has not been created yet");
        return inventory;
    }
}
