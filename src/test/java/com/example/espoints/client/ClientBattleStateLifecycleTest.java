package com.example.espoints.client;

import com.example.espoints.capturepoint.CapturePoint;
import com.example.espoints.capturepoint.CaptureState;
import com.example.espoints.capturepoint.DisplayState;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientBattleStateLifecycleTest {
    @AfterEach
    void clear() {
        ClientBattleState.get().clear();
    }

    @Test
    void reusesStablePointObjectsWithoutConsultingIntegratedServerState() {
        ClientBattleState state = ClientBattleState.get();
        CapturePoint.SerializableCapturePoint neutral = point(CaptureState.NEUTRAL, 0);
        CapturePoint first = state.replaceCapturePoints(List.of(neutral)).get(0);
        CapturePoint.SerializableCapturePoint captured = point(CaptureState.CAPTURED, 100);
        CapturePoint second = state.replaceCapturePoints(List.of(captured)).get(0);

        assertSame(first, second);
        assertEquals(CaptureState.CAPTURED, second.getState());
        assertEquals(100, second.getProgress());
    }

    @Test
    void fiftyRoundActivateAndDoubleClearLeavesNoClientState() {
        ClientBattleState state = ClientBattleState.get();
        for (int round = 0; round < 50; round++) {
            state.replaceCapturePoints(List.of(point(CaptureState.NEUTRAL, 0)));
            state.clear();
            state.clear();
            assertTrue(state.points().isEmpty());
        }
    }

    private static CapturePoint.SerializableCapturePoint point(
            CaptureState captureState, int progress) {
        return new CapturePoint.SerializableCapturePoint(
            "A", new BlockPos(0, 50, 0), new BlockPos(10, 80, 10), 1,
            captureState,
            captureState == CaptureState.CAPTURED
                ? DisplayState.CAPTURED : DisplayState.NEUTRAL,
            captureState == CaptureState.CAPTURED ? "ATTACK" : "",
            progress);
    }
}
