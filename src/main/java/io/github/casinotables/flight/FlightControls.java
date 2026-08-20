package io.github.casinotables.flight;

import org.bukkit.Material;

import java.util.List;

public final class FlightControls {
    public static final int NONE = -2;
    public static final int ROLL = -1;
    public static final Material ROLL_MATERIAL = Material.STONE_BUTTON;
    private static final List<Material> PIECES = List.of(
            Material.OAK_BUTTON, Material.SPRUCE_BUTTON,
            Material.BIRCH_BUTTON, Material.JUNGLE_BUTTON);

    private FlightControls() { }

    public static Material pieceMaterial(int piece) {
        if (piece < 0 || piece >= PIECES.size()) throw new IllegalArgumentException("piece must be 0..3");
        return PIECES.get(piece);
    }

    public static int action(Material material) {
        if (material == ROLL_MATERIAL) return ROLL;
        int piece = PIECES.indexOf(material);
        return piece >= 0 ? piece : NONE;
    }
}
