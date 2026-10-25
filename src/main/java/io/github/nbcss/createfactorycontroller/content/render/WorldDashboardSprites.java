package io.github.nbcss.createfactorycontroller.content.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Reuses power-of-two GUI artwork on the block atlas without altering the original menu sprites. */
public final class WorldDashboardSprites {
    // Do not add 15x15 operator icons or 16x8 strips: they would reduce the entire block atlas mip level.
    public static final Set<String> PATHS = Set.of(
            "factory_controller/arithmetic_tube/back",
            "factory_controller/arithmetic_tube/front",
            "factory_controller/factory_gauge/back",
            "factory_controller/factory_gauge/front",
            "factory_controller/factory_gauge/indicator",
            "factory_controller/fluid_gauge/front",
            "factory_controller/logical_tube/and",
            "factory_controller/logical_tube/back",
            "factory_controller/logical_tube/front_off",
            "factory_controller/logical_tube/front_on",
            "factory_controller/logical_tube/nand",
            "factory_controller/logical_tube/nor",
            "factory_controller/logical_tube/or",
            "factory_controller/redstone_link/back_power",
            "factory_controller/redstone_link/back",
            "factory_controller/redstone_link/front_power",
            "factory_controller/redstone_link/front",
            "factory_controller/redstone_link/receiver_off",
            "factory_controller/redstone_link/receiver_on",
            "factory_controller/redstone_link/transmitter_off",
            "factory_controller/redstone_link/transmitter_on",
            "factory_controller/target/half_1",
            "factory_controller/target/half_2",
            "factory_controller/target/regular");
    private WorldDashboardSprites() {}

    public static TextureAtlasSprite sprite(ResourceLocation id) {
        var mc = Minecraft.getInstance();
        if (id.getNamespace().equals("createfactorycontroller") && PATHS.contains(id.getPath())) {
            var world = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id.withPrefix("world_dashboard/"));
            if (!world.contents().name().equals(MissingTextureAtlasSprite.getLocation())) return world;
        }
        // Compat/custom widgets and near-only tiny icons keep their existing GUI artwork.
        return mc.getGuiSprites().getSprite(id);
    }
}
