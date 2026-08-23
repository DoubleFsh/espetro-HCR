package com.example.espoints.client;

import com.example.espoints.tactical.TacticalMarkerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalMarkRadialControllerTest {

    @Test
    void menuListsEverySelectableTypeOnceAndSkipsArtillery() {
        List<String> ids = TacticalMarkRadialController.menuSlotIds();
        assertEquals(TacticalMarkerType.selectableValues().length, ids.size());
        assertTrue(ids.contains("espoints.mark.ENEMY_INFANTRY"));
        assertTrue(ids.contains("espoints.mark.ATTACK_HERE"));
        assertTrue(ids.contains("espoints.mark.DEFEND_HERE"));
        assertFalse(ids.contains("espoints.mark.ARTILLERY_TARGET"));
        assertEquals(ids.size(), ids.stream().distinct().count());
    }
}
