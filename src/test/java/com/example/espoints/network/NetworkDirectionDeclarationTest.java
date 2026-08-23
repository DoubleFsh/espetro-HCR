package com.example.espoints.network;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkDirectionDeclarationTest {
    @Test
    void protocolFifteenDeclaresDirectionForEveryRegisteredPacket() throws Exception {
        assertEquals("15", NetworkHandler.PROTOCOL_VERSION);
        Path source = Path.of(System.getProperty("user.dir"),
            "src/main/java/com/example/espoints/network/NetworkHandler.java");
        List<String> registrations = Files.readAllLines(source).stream()
            .filter(line -> line.contains("INSTANCE.messageBuilder("))
            .toList();

        assertEquals(23, registrations.size());
        assertTrue(registrations.stream().allMatch(line ->
            line.contains("NetworkDirection.PLAY_TO_CLIENT")
                || line.contains("NetworkDirection.PLAY_TO_SERVER")));
        assertEquals(16, registrations.stream()
            .filter(line -> line.contains("PLAY_TO_CLIENT")).count());
        assertEquals(7, registrations.stream()
            .filter(line -> line.contains("PLAY_TO_SERVER")).count());
    }
}
