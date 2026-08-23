package com.example.espoints.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointsPresetLoaderTest {

    @Test
    void selectsOnlyMatchingModeAndIsSeedStable(@TempDir Path mapRoot) throws Exception {
        Path esConfig = mapRoot.resolve("EsConfig");
        Path points = mapRoot.resolve("Points");
        Files.createDirectories(esConfig);
        Files.createDirectories(points);
        Files.writeString(esConfig.resolve("game.json"), """
            {"game":{"objectiveMode":"RAAS"}}
            """, StandardCharsets.UTF_8);
        Files.writeString(points.resolve("only_aas.json"), """
            {"modes":["AAS"],"plannedPoints":[{"name":"A","batch":1,"pos1":[0,0,0],"pos2":[1,1,1]}]}
            """, StandardCharsets.UTF_8);
        Files.writeString(points.resolve("raas_a.json"), """
            {"modes":["RAAS"],"ticketBleedPerSecond":3,"raas":{"points":[],"lanes":[]}}
            """, StandardCharsets.UTF_8);
        Files.writeString(points.resolve("raas_b.json"), """
            {"modes":["RAAS","AAS"],"ticketBleedPerSecond":7,"raas":{"points":[],"lanes":[]}}
            """, StandardCharsets.UTF_8);

        assertEquals("RAAS", PointsPresetLoader.readConfiguredMode(esConfig));

        PointsPresetLoader.Selection first =
            PointsPresetLoader.select(esConfig, "RAAS", 42L);
        PointsPresetLoader.Selection again =
            PointsPresetLoader.select(esConfig, "RAAS", 42L);
        assertEquals(first.sourceName(), again.sourceName());
        assertTrue(first.sourceName().startsWith("raas_"));
        assertTrue(first.json().contains("\"objectiveMode\":\"RAAS\""));
    }

    @Test
    void fallsBackToLegacyCapturePoints(@TempDir Path mapRoot) throws Exception {
        Path esConfig = mapRoot.resolve("EsConfig");
        Files.createDirectories(esConfig);
        Files.writeString(esConfig.resolve("CapturePoints.json"), """
            {"objectiveMode":"AAS","plannedPoints":[{"name":"A","batch":1,"pos1":[0,0,0],"pos2":[1,1,1]}]}
            """, StandardCharsets.UTF_8);

        PointsPresetLoader.Selection selection =
            PointsPresetLoader.select(esConfig, "AAS", 1L);
        assertEquals("CapturePoints.json", selection.sourceName());
    }
}
