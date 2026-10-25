package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.client.panel.FactoryControllerClientBoards;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;

/** Explicitly distinguish an unloaded core from an empty board or a detached member. */
public record DashboardUnavailablePacket(ResourceLocation dimension, BlockPos core, UUID identity) implements CustomPacketPayload {
    public static final Type<DashboardUnavailablePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "dashboard_unavailable"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DashboardUnavailablePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public DashboardUnavailablePacket decode(RegistryFriendlyByteBuf buf) {
            return new DashboardUnavailablePacket(buf.readResourceLocation(), buf.readBlockPos(), buf.readUUID());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, DashboardUnavailablePacket p) {
            buf.writeResourceLocation(p.dimension); buf.writeBlockPos(p.core); buf.writeUUID(p.identity);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(DashboardUnavailablePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = Minecraft.getInstance().level;
            if (level != null && level.dimension().location().equals(p.dimension)) {
                io.github.nbcss.createfactorycontroller.content.client.panel.DashboardRenderDiagnostics.unavailable(p.core, p.identity);
                FactoryControllerClientBoards.get(p.core, p.identity).available = false;
            }
        });
    }
}
