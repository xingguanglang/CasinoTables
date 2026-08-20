package io.github.casinotables.arena;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class PlayerSnapshot {
    private final Location location;
    private final ItemStack[] storage;
    private final ItemStack[] armor;
    private final ItemStack[] extra;
    private final GameMode gameMode;
    private final int heldItemSlot;
    private final boolean allowFlight;
    private final boolean flying;
    private final boolean invulnerable;
    private final boolean glowing;
    private final double health;
    private final int food;
    private final float saturation;
    private final int level;
    private final float exp;
    private final Collection<PotionEffect> effects;

    private PlayerSnapshot(Location location, ItemStack[] storage, ItemStack[] armor, ItemStack[] extra,
                           GameMode gameMode, int heldItemSlot, boolean allowFlight, boolean flying,
                           boolean invulnerable, boolean glowing,
                           double health, int food, float saturation, int level, float exp,
                           Collection<PotionEffect> effects) {
        this.location = location;
        this.storage = cloneItems(storage);
        this.armor = cloneItems(armor);
        this.extra = cloneItems(extra);
        this.gameMode = gameMode;
        this.heldItemSlot = heldItemSlot;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.invulnerable = invulnerable;
        this.glowing = glowing;
        this.health = health;
        this.food = food;
        this.saturation = saturation;
        this.level = level;
        this.exp = exp;
        this.effects = List.copyOf(effects);
    }

    public static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(player.getLocation().clone(), player.getInventory().getStorageContents(),
                player.getInventory().getArmorContents(), player.getInventory().getExtraContents(),
                player.getGameMode(), player.getInventory().getHeldItemSlot(), player.getAllowFlight(),
                player.isFlying(), player.isInvulnerable(), player.isGlowing(),
                player.getHealth(), player.getFoodLevel(), player.getSaturation(), player.getLevel(),
                player.getExp(), player.getActivePotionEffects());
    }

    public void prepare(Player player) {
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.setGameMode(GameMode.ADVENTURE);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setInvulnerable(true);
        player.setGlowing(false);
        player.setFireTicks(0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) player.removePotionEffect(effect.getType());
    }

    public void restore(Player player) {
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setStorageContents(cloneItems(storage));
        player.getInventory().setArmorContents(cloneItems(armor));
        player.getInventory().setExtraContents(cloneItems(extra));
        player.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, heldItemSlot)));
        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && flying);
        player.setInvulnerable(invulnerable);
        player.setGlowing(glowing);
        double maxHealth = maxHealthOf(player);
        player.setHealth(Math.max(0.1, Math.min(health, maxHealth)));
        player.setFoodLevel(food);
        player.setSaturation(saturation);
        player.setLevel(level);
        player.setExp(exp);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) player.removePotionEffect(effect.getType());
        player.addPotionEffects(effects);
    }

    public Location location() { return location.clone(); }

    public void save(ConfigurationSection section) {
        section.set("location", location);
        section.set("storage", Arrays.asList(storage));
        section.set("armor", Arrays.asList(armor));
        section.set("extra", Arrays.asList(extra));
        section.set("game-mode", gameMode.name());
        section.set("held-item-slot", heldItemSlot);
        section.set("allow-flight", allowFlight);
        section.set("flying", flying);
        section.set("invulnerable", invulnerable);
        section.set("glowing", glowing);
        section.set("health", health);
        section.set("food", food);
        section.set("saturation", saturation);
        section.set("level", level);
        section.set("exp", exp);
        section.set("effects", new ArrayList<>(effects));
    }

    public static PlayerSnapshot load(ConfigurationSection section) {
        Location location = section.getLocation("location");
        if (location == null) return null;
        return new PlayerSnapshot(location, items(section.getList("storage")), items(section.getList("armor")),
                items(section.getList("extra")), GameMode.valueOf(section.getString("game-mode", "SURVIVAL")),
                section.getInt("held-item-slot", 0), section.getBoolean("allow-flight"), section.getBoolean("flying"),
                section.getBoolean("invulnerable"), section.getBoolean("glowing"),
                section.getDouble("health", 20.0),
                section.getInt("food", 20), (float) section.getDouble("saturation", 5.0),
                section.getInt("level"), (float) section.getDouble("exp"),
                effects(section.getList("effects")));
    }

    private static ItemStack[] items(List<?> list) {
        if (list == null) return new ItemStack[0];
        ItemStack[] result = new ItemStack[list.size()];
        for (int i = 0; i < result.length; i++) if (list.get(i) instanceof ItemStack item) result[i] = item;
        return result;
    }

    private static Collection<PotionEffect> effects(List<?> list) {
        List<PotionEffect> result = new ArrayList<>();
        if (list != null) for (Object value : list) if (value instanceof PotionEffect effect) result.add(effect);
        return result;
    }

    private static ItemStack[] cloneItems(ItemStack[] source) {
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[i] == null ? null : source[i].clone();
        return result;
    }

    /**
     * The max-health attribute constant was renamed between Minecraft releases
     * (GENERIC_MAX_HEALTH before 1.21.3, MAX_HEALTH after), so resolving it by
     * registry key instead of by constant keeps one jar working across the range.
     */
    private static double maxHealthOf(Player player) {
        Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"));
        if (attribute == null) return 20.0;
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? 20.0 : instance.getValue();
    }
}
