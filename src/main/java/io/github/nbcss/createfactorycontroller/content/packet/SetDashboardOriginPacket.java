package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.ControllerAccess;
import io.github.nbcss.createfactorycontroller.content.block.DashboardAssemblyManager;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetDashboardOriginPacket(BlockPos pos, int x, int y) implements CustomPacketPayload {
    public static final Type<SetDashboardOriginPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "set_dashboard_origin"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetDashboardOriginPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetDashboardOriginPacket::pos, ByteBufCodecs.INT, SetDashboardOriginPacket::x,
            ByteBufCodecs.INT, SetDashboardOriginPacket::y, SetDashboardOriginPacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(SetDashboardOriginPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            FactoryControllerBlockEntity core = ControllerAccess.get(player, packet.pos);
            if (core == null || Math.abs((long) packet.x) > FactoryControllerBlockEntity.BOARD_LIMIT
                    || Math.abs((long) packet.y) > FactoryControllerBlockEntity.BOARD_LIMIT) return;
            if (core.dashboard().originX == packet.x && core.dashboard().originY == packet.y) return;
            core.dashboard().originX = packet.x; core.dashboard().originY = packet.y; core.dashboard().generation++;
            core.setChanged(); core.sendData();
            DashboardAssemblyManager.schedule(core.getLevel(), core.getBlockPos());
        });
    }
}
