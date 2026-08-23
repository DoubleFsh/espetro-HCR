package com.example.espoints.objective;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectiveLayoutTest {

    @Test
    void legacyCapturePointsRemainAasCompatible() {
        ObjectiveLayout.Selection selection = ObjectiveLayout.parse(aasRoot()).select(42L);

        assertEquals("AAS", selection.mode());
        JsonObject result = JsonParser.parseString(selection.capturePointsJson()).getAsJsonObject();
        assertEquals(1, result.get("totalBatches").getAsInt());
        assertEquals("A", result.getAsJsonArray("plannedPoints")
            .get(0).getAsJsonObject().get("name").getAsString());
        assertFalse(result.has("objectiveMode"));
    }

    @Test
    void raasSelectionAssignsStageBatchesAndIncludesAllStagePoints() {
        ObjectiveLayout layout = ObjectiveLayout.parse(raasRoot("RAAS"));
        ObjectiveLayout.Selection first = layout.select(123456L);
        ObjectiveLayout.Selection again = layout.select(123456L);

        assertEquals(first, again);
        assertEquals("RAAS", first.mode());
        assertEquals("east", first.laneId());

        JsonObject result = JsonParser.parseString(first.capturePointsJson()).getAsJsonObject();
        assertEquals(3, result.get("totalBatches").getAsInt());
        assertTrue(result.get("raasFrontline").getAsBoolean());
        assertFalse(result.has("raasSymmetric"));
        // stages: [main], [crossing_north, crossing_south], [terminal]
        // → 4 points with batches 1, 2, 2, 3
        assertEquals(4, result.getAsJsonArray("plannedPoints").size());
        assertEquals("A", result.getAsJsonArray("plannedPoints")
            .get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(1, result.getAsJsonArray("plannedPoints")
            .get(0).getAsJsonObject().get("batch").getAsInt());
        assertEquals(2, result.getAsJsonArray("plannedPoints")
            .get(1).getAsJsonObject().get("batch").getAsInt());
        assertEquals(2, result.getAsJsonArray("plannedPoints")
            .get(2).getAsJsonObject().get("batch").getAsInt());
        assertEquals(3, result.getAsJsonArray("plannedPoints")
            .get(3).getAsJsonObject().get("batch").getAsInt());
        assertFalse(result.has("raas"));
        assertFalse(result.has("objectiveMode"));
    }

    @Test
    void rejectsRandomMode() {
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class, () -> ObjectiveLayout.parse(raasRoot("RANDOM")));
        assertTrue(error.getMessage().contains("AAS") || error.getMessage().contains("RAAS"));
    }

    @Test
    void rejectsUnknownAndRepeatedLaneReferences() {
        JsonObject unknown = raasRoot("RAAS");
        unknown.getAsJsonObject("raas").getAsJsonArray("lanes")
            .get(0).getAsJsonObject().getAsJsonArray("stages")
            .get(1).getAsJsonArray().set(0, JsonParser.parseString("\"missing\""));
        assertThrows(IllegalArgumentException.class, () -> ObjectiveLayout.parse(unknown));

        JsonObject repeated = raasRoot("RAAS");
        repeated.getAsJsonObject("raas").getAsJsonArray("lanes")
            .get(0).getAsJsonObject().getAsJsonArray("stages")
            .get(2).getAsJsonArray().set(0, JsonParser.parseString("\"crossing_north\""));
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class, () -> ObjectiveLayout.parse(repeated));
        assertTrue(error.getMessage().contains("重复引用"));
    }

    @Test
    void rejectsInvalidNamesAndLaneIds() {
        JsonObject invalidName = aasRoot();
        invalidName.getAsJsonArray("plannedPoints").get(0).getAsJsonObject()
            .addProperty("name", 1);
        assertThrows(IllegalArgumentException.class,
            () -> ObjectiveLayout.parse(invalidName));

        JsonObject invalidLane = raasRoot("RAAS");
        invalidLane.getAsJsonObject("raas").getAsJsonArray("lanes")
            .get(0).getAsJsonObject().addProperty("id", "East Route");
        assertThrows(IllegalArgumentException.class,
            () -> ObjectiveLayout.parse(invalidLane));
    }

    private static JsonObject aasRoot() {
        return JsonParser.parseString("""
            {
              "totalBatches": 1,
              "endBehavior": "terminate",
              "teamReinforcements": {"ATTACK": 280, "DEFEND": 1200},
              "plannedPoints": [
                {"name":"A", "batch":1,
                 "pos1":{"x":0,"y":60,"z":0},
                 "pos2":{"x":16,"y":80,"z":16}}
              ]
            }
            """).getAsJsonObject();
    }

    private static JsonObject raasRoot(String mode) {
        JsonObject root = aasRoot();
        root.addProperty("objectiveMode", mode);
        root.add("raas", JsonParser.parseString("""
            {
              "points": [
                {"id":"main", "pos1":{"x":0,"y":60,"z":0}, "pos2":{"x":16,"y":80,"z":16}},
                {"id":"crossing_north", "pos1":{"x":100,"y":60,"z":0}, "pos2":{"x":116,"y":80,"z":16}},
                {"id":"crossing_south", "pos1":{"x":100,"y":60,"z":100}, "pos2":{"x":116,"y":80,"z":116}},
                {"id":"terminal", "pos1":{"x":200,"y":60,"z":0}, "pos2":{"x":216,"y":80,"z":16}}
              ],
              "lanes": [
                {"id":"east", "stages":[["main"], ["crossing_north","crossing_south"], ["terminal"]]}
              ]
            }
            """).getAsJsonObject());
        return root;
    }
}
