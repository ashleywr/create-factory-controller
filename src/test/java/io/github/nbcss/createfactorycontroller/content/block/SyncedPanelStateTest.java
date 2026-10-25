package io.github.nbcss.createfactorycontroller.content.block;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SyncedPanelStateTest {
    @Test void snapshotResetsTokensAndEndsRecovery() {
        SyncedPanelState state = new SyncedPanelState(BlockPos.ZERO, UUID.randomUUID());
        state.resyncPending = true;
        state.applyFullSync(21, 7, List.of(), List.of(), List.of(), List.of(), "Assembly A", true);
        assertEquals(21, state.syncEpoch); assertEquals(7, state.syncRevision);
        assertFalse(state.resyncPending); assertEquals("Assembly A", state.controllerName); assertTrue(state.controllerPowered);
    }
    @Test void deltaMustMatchEpochAndBaseExactly() {
        SyncedPanelState state = new SyncedPanelState(BlockPos.ZERO, UUID.randomUUID());
        state.syncEpoch = 10; state.syncRevision = 40;
        assertTrue(state.acceptsDelta(10, 40));
        assertFalse(state.acceptsDelta(11, 40)); assertFalse(state.acceptsDelta(10, 39)); assertFalse(state.acceptsDelta(10, 41));
    }
    @Test void stateOnlyTrafficDoesNotChangeGeometryRevision() {
        SyncedPanelState state = new SyncedPanelState(BlockPos.ZERO, UUID.randomUUID());
        long revision = state.structureRevision;
        state.controllerPowered = true; state.syncRevision++;
        assertEquals(revision, state.structureRevision);
    }
    @Test void identitiesSeparateReplacedCoresAtTheSamePosition() {
        SyncedPanelState first = new SyncedPanelState(BlockPos.ZERO, UUID.randomUUID());
        SyncedPanelState replacement = new SyncedPanelState(BlockPos.ZERO, UUID.randomUUID());
        assertEquals(first.controllerPos, replacement.controllerPos); assertNotEquals(first.identity, replacement.identity);
    }
}
