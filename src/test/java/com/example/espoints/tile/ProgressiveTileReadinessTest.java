package com.example.espoints.tile;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressiveTileReadinessTest {
    @Test
    void oneKeyHasExactlyOneOwnerAndOldGenerationCannotPublish() throws Exception {
        ProgressiveTileReadiness<String, byte[]> readiness =
            new ProgressiveTileReadiness<>(9L);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<CompletableFuture<Boolean>> claims = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                claims.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                    return readiness.claim(9L, "preview");
                }, executor));
            }
            start.countDown();
            long owners = claims.stream().map(CompletableFuture::join)
                .filter(Boolean::booleanValue).count();
            assertEquals(1L, owners);
        } finally {
            executor.shutdownNow();
        }
        assertFalse(readiness.publish(8L, "preview", new byte[] {1}));
        assertTrue(readiness.publish(9L, "preview", new byte[] {2}));
        assertEquals(2, readiness.future("preview").join()[0]);
    }

    @Test
    void previewCanPublishWhileFullBuildIsStillBlocked() {
        ProgressiveTileReadiness<String, String> readiness =
            new ProgressiveTileReadiness<>(3L);
        CompletableFuture<Void> fullBuild = new CompletableFuture<>();
        assertTrue(readiness.claim(3L, "preview"));
        assertTrue(readiness.publish(3L, "preview", "ready"));
        assertEquals("ready", readiness.future("preview").join());
        assertFalse(fullBuild.isDone());
        assertFalse(readiness.future("detail").isDone());
    }
}
