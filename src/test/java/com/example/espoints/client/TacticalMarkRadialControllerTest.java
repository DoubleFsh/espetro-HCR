package com.example.espoints.client;

import cc.sighs.auratip.data.RadialMenuData;
import com.example.espoints.tactical.TacticalMarkerType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Test
    void builtMenuHasExactlyOneSlotPerSelectableTypeWithoutArtillery() {
        RadialMenuData menu = TacticalMarkRadialController.buildMenuData();
        Map<String, RadialMenuData.Slot> byName = menu.slots().stream()
            .collect(Collectors.toMap(RadialMenuData.Slot::name, slot -> slot));
        for (TacticalMarkerType type : TacticalMarkerType.selectableValues()) {
            assertTrue(byName.containsKey("espoints.mark." + type.name()),
                "缺失标点槽位: " + type);
        }
        assertEquals(TacticalMarkerType.selectableValues().length, byName.size());
        assertFalse(byName.containsKey("espoints.mark.ARTILLERY_TARGET"));
    }

    @Test
    void everyMarkSlotClosesAfterAction() {
        RadialMenuData menu = TacticalMarkRadialController.buildMenuData();
        assertFalse(menu.slots().isEmpty());
        for (RadialMenuData.Slot slot : menu.slots()) {
            assertTrue(slot.closeAfterAction(),
                "标点槽位必须选择后关闭: " + slot.name());
        }
    }
}