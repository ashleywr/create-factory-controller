package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.component.SyncCodecs;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Freeze and encode a shared snapshot/delta once on the server thread, before fan-out. */
public record EncodedPanelUpdatePacket(boolean full, byte[] body) implements CustomPacketPayload {
    public static final Type<EncodedPanelUpdatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "panel_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EncodedPanelUpdatePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public EncodedPanelUpdatePacket decode(RegistryFriendlyByteBuf buf) {
            return new EncodedPanelUpdatePacket(buf.readBoolean(), buf.readByteArray());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, EncodedPanelUpdatePacket p) { buf.writeBoolean(p.full); buf.writeByteArray(p.body); }
    };
    public static EncodedPanelUpdatePacket freeze(CustomPacketPayload packet, RegistryAccess registries) {
        if (packet instanceof SyncPanelStatePacket full) return new EncodedPanelUpdatePacket(true,
                SyncCodecs.capture(registries, buf -> SyncPanelStatePacket.STREAM_CODEC.encode(buf, full)));
        if (packet instanceof SyncPanelDeltaPacket delta) return new EncodedPanelUpdatePacket(false,
                SyncCodecs.capture(registries, buf -> SyncPanelDeltaPacket.STREAM_CODEC.encode(buf, delta)));
        throw new IllegalArgumentException("Not a panel update");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(EncodedPanelUpdatePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            RegistryFriendlyByteBuf body = SyncCodecs.wrap(p.body, ctx.player().registryAccess());
            try {
                if (p.full) SyncPanelStatePacket.handle(SyncPanelStatePacket.STREAM_CODEC.decode(body), ctx);
                else SyncPanelDeltaPacket.handle(SyncPanelDeltaPacket.STREAM_CODEC.decode(body), ctx);
            } finally { body.release(); }
        });
    }
}
