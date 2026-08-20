package io.github.xingguanglang.casinotables.listener;

import io.github.xingguanglang.casinotables.arena.ArenaTags;
import io.github.xingguanglang.casinotables.CasinoTablesPlugin;
import io.github.xingguanglang.casinotables.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;


public final class GameListener implements Listener {
    private final CasinoTablesPlugin plugin;

    public GameListener(CasinoTablesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        String input = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (plugin.menus().handleChat(event.getPlayer(), input)) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.menus().handleInventoryClick(event)) return;
        if (plugin.blackjack().protectedPlayer(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.menus().handleInventoryDrag(event)) return;
        if (plugin.blackjack().protectedPlayer(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTeleport(PlayerTeleportEvent event) {
        if (plugin.handleTableTeleport(event.getPlayer(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null) return;
        boolean blocked = plugin.handleTableMove(event.getPlayer(), event.getTo());
        if (!blocked) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(),
                to.getYaw(), to.getPitch()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.poker().handleJoin(event.getPlayer());
        plugin.blackjack().handleJoin(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.protectedPlayer(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.poker().placeChip(event.getPlayer(), event.getBlockPlaced().getType(),
                event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (plugin.blackjack().placeChip(event.getPlayer(), event.getBlockPlaced().getType(),
                event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (plugin.protectedPlayer(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() != null
                && plugin.flights().interactButton(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedBlock() != null
                && plugin.poker().interactExitButton(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedBlock() != null
                && plugin.poker().interactControlButton(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedBlock() != null
                && plugin.poker().interactAtm(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedBlock() != null
                && plugin.blackjack().interact(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
            return;
        }
        org.bukkit.Material held = event.getItem() == null ? org.bukkit.Material.AIR : event.getItem().getType();
        if (plugin.flights().interactControl(event.getPlayer(), held)) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_AIR
                && plugin.poker().chipAction(event.getPlayer(), held, event.getPlayer().isSneaking())) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_AIR
                && plugin.blackjack().chipAction(event.getPlayer(), held, event.getPlayer().isSneaking())) {
            event.setCancelled(true);
            return;
        }
        if (plugin.poker().control(event.getPlayer(), held)
                || plugin.blackjack().control(event.getPlayer(), held)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        // 用各竞技场自己的常量，别再抄字面量：抄错一个字母监听器就静默失灵。
        if (event.getEntity().getScoreboardTags().contains(ArenaTags.FLIGHT_PIECE)
                || event.getEntity().getScoreboardTags().contains(ArenaTags.POKER_DISPLAY)
                || event.getEntity().getScoreboardTags().contains(ArenaTags.BLACKJACK_ENTITY)) {
            event.setCancelled(true);
        } else if (event.getEntity() instanceof Player player && plugin.protectedPlayer(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.protectedPlayer(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.protectedPlayer(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.protectedPlayer(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (plugin.protectedPlayer(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
    }

    @EventHandler(ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        if (event.getEntity().getScoreboardTags().contains("casinotables_flight_piece")) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getScoreboardTags().contains("casinotables_flight_piece")) {
            if (event.getHand() == EquipmentSlot.HAND) {
                plugin.flights().interactPiece(event.getPlayer(), event.getRightClicked());
            }
            event.setCancelled(true);
            return;
        }
        if (event.getHand() == EquipmentSlot.HAND
                && plugin.flights().interactControl(event.getPlayer(),
                event.getPlayer().getInventory().getItemInMainHand().getType())) {
            event.setCancelled(true);
            return;
        }
        if (event.getRightClicked().getScoreboardTags().contains(ArenaTags.POKER_DISPLAY)
                || event.getRightClicked().getScoreboardTags().contains(ArenaTags.BLACKJACK_ENTITY)) {
            event.setCancelled(true);
        }
    }
}
