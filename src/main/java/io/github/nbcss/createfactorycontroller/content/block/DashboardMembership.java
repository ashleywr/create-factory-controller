package io.github.nbcss.createfactorycontroller.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Small, client-synced metadata. Never contains components or copies of the controller setup. */
public final class DashboardMembership {
    public UUID identity = UUID.randomUUID();
    public BlockPos core;
    public int tileX, tileY, generation, edges, originX, originY;
    public final Set<BlockPos> tiles = new LinkedHashSet<>();

    public void write(CompoundTag root) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Identity", identity);
        if (core != null) tag.putLong("Core", core.asLong());
        tag.putInt("X", tileX); tag.putInt("Y", tileY);
        tag.putInt("Generation", generation); tag.putInt("Edges", edges);
        tag.putInt("OriginX", originX); tag.putInt("OriginY", originY);
        ListTag list = new ListTag();
        for (BlockPos tile : tiles) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", tile.getX()); entry.putInt("Y", tile.getY());
            list.add(entry);
        }
        tag.put("Tiles", list);
        root.put("Dashboard", tag);
    }

    public void read(CompoundTag root) {
        if (!root.contains("Dashboard", Tag.TAG_COMPOUND)) return; // legacy core: stable board, fresh identity
        CompoundTag tag = root.getCompound("Dashboard");
        if (tag.hasUUID("Identity")) identity = tag.getUUID("Identity");
        core = tag.contains("Core") ? BlockPos.of(tag.getLong("Core")) : null;
        tileX = tag.getInt("X"); tileY = tag.getInt("Y"); generation = tag.getInt("Generation");
        edges = tag.getInt("Edges"); originX = tag.getInt("OriginX"); originY = tag.getInt("OriginY");
        tiles.clear();
        ListTag list = tag.getList("Tiles", Tag.TAG_COMPOUND);
        // Defensive bound for malformed saves; placement uses the configurable, tighter limits.
        for (int i = 0; i < Math.min(list.size(), 256); i++) {
            CompoundTag entry = list.getCompound(i);
            tiles.add(new BlockPos(entry.getInt("X"), entry.getInt("Y"), 0));
        }
    }
}
