package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.ServerConfig;
import io.github.nbcss.createfactorycontroller.content.block.DashboardAssemblyManager;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Read-only cache request via an actual loaded tile, including when the client cannot see the core. */
public record RequestDashboardStatePacket(BlockPos member, UUID identity) implements CustomPacketPayload {
    public static final Type<RequestDashboardStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "request_dashboard_state"));
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestDashboardStatePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public RequestDashboardStatePacket decode(RegistryFriendlyByteBuf buf) {
            return new RequestDashboardStatePacket(buf.readBlockPos(), buf.readUUID());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, RequestDashboardStatePacket p) {
            buf.writeBlockPos(p.member); buf.writeUUID(p.identity);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(RequestDashboardStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            long now = player.level().getGameTime();
            long last = LAST_REQUEST.getOrDefault(player, Long.MIN_VALUE / 2);
            if (now - last < 4) return;
            LAST_REQUEST.put(player, now);
            if (player.distanceToSqr(packet.member.getX() + .5, packet.member.getY() + .5, packet.member.getZ() + .5)
                    > Math.pow(ServerConfig.DASHBOARD_SYNC_RADIUS.get(), 2)) return;
            FactoryControllerBlockEntity core = DashboardAssemblyManager.coreAt(player.level(), packet.member);
            if (core == null) {
                var tile = DashboardAssemblyManager.tileAt(player.level(), packet.member);
                if (tile != null && tile.dashboard().core != null && tile.dashboard().identity.equals(packet.identity))
                    PacketDistributor.sendToPlayer(player, new DashboardUnavailablePacket(player.level().dimension().location(),
                            tile.dashboard().core, packet.identity));
                return;
            }
            if (!core.dashboard().identity.equals(packet.identity)) return;
            PacketDistributor.sendToPlayer(player, EncodedPanelUpdatePacket.freeze(core.buildFullSnapshot(), player.registryAccess()));
        });
    }
}
