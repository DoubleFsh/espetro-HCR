package com.example.espoints.client;

import com.example.espoints.network.SyncPlayerIdentityMessage;
import com.example.espoints.network.SyncPlayerPositionsMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPlayerIdentityStateTest {
    @AfterEach
    void clear() {
        ClientPlayerIdentityState.get().clear();
    }

    @Test
    void rejectsStaleTablesAndFramesAndResolvesCurrentIdentity() {
        UUID player = UUID.randomUUID();
        SyncPlayerIdentityMessage.Identity identity =
            new SyncPlayerIdentityMessage.Identity(
                1, player, "Alpha", "ATTACK", 2, true, false);
        ClientPlayerIdentityState state = ClientPlayerIdentityState.get();

        assertTrue(state.replace(10, List.of(identity)));
        assertFalse(state.replace(9, List.of(identity)));
        assertNull(state.resolve(9, Map.of()));
        var resolved = state.resolve(10, Map.of(
            1, SyncPlayerPositionsMessage.PlayerPosition.positionOnly(
                12.5, 0, 42.25, 90)));
        assertEquals("Alpha", resolved.get(player).getName());
        assertEquals(2, resolved.get(player).getSquadId());
    }
}
