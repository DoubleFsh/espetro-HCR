package com.example.espoints.network;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class TacticalMapDedicatedSafetyTest {
    @Test
    void commonTilePacketsDoNotImportClientOnlyCacheTypes() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"),
            "src/main/java/com/example/espoints/network");
        for (String file : new String[] {
                "SyncTacticalMapBackgroundMessage.java", "SyncTacticalMapTileMessage.java"}) {
            String source = Files.readString(root.resolve(file));
            assertFalse(source.contains(
                "import com.example.espoints.client.ClientTacticalMapTileCache"));
        }
    }
}
