package io.github.nbcss.createfactorycontroller.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** All board mutations require the same live, nearby, identity-matched menu session. */
public final class ControllerAccess {
    private ControllerAccess() {}
    public static FactoryControllerBlockEntity get(ServerPlayer player, BlockPos pos) {
        if (!(player.containerMenu instanceof FactoryControllerMenu menu) || !menu.controllerPos.equals(pos)
                || !menu.stillValid(player)) return null;
        FactoryControllerBlockEntity core = DashboardAssemblyManager.coreAt(player.level(), pos);
        return core != null && core.dashboard().identity.equals(menu.controllerIdentity) ? core : null;
    }
}
