package com.example.espoints.client;

import com.example.espoints.network.SyncPlayerIdentityMessage;
import com.example.espoints.network.SyncPlayerPositionsMessage;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Rejects stale frames and resolves compact session IDs to immutable identity data. */
public final class ClientPlayerIdentityState {
    private static final ClientPlayerIdentityState INSTANCE =
        new ClientPlayerIdentityState();

    private long session = Long.MIN_VALUE;
    private long revision;
    private Map<Integer, SyncPlayerIdentityMessage.Identity> identities = Map.of();

    private ClientPlayerIdentityState() {
    }

    public static ClientPlayerIdentityState get() {
        return INSTANCE;
    }

    public synchronized boolean replace(
            long incomingSession, List<SyncPlayerIdentityMessage.Identity> incoming) {
        if (incomingSession < session) {
            return false;
        }
        Map<Integer, SyncPlayerIdentityMessage.Identity> next = new HashMap<>();
        for (SyncPlayerIdentityMessage.Identity identity : incoming) {
            if (next.put(identity.shortId(), identity) != null) {
                return false;
            }
        }
        session = incomingSession;
        identities = Map.copyOf(next);
        revision++;
        return true;
    }

    public synchronized Map<UUID, SyncPlayerPositionsMessage.PlayerPosition> resolve(
            long frameSession,
            Map<Integer, SyncPlayerPositionsMessage.PlayerPosition> frame) {
        if (frameSession != session) {
            return null;
        }
        Map<UUID, SyncPlayerPositionsMessage.PlayerPosition> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, SyncPlayerPositionsMessage.PlayerPosition> entry
                : frame.entrySet()) {
            SyncPlayerIdentityMessage.Identity identity = identities.get(entry.getKey());
            if (identity == null) {
                continue;
            }
            SyncPlayerPositionsMessage.PlayerPosition sample = entry.getValue();
            result.put(identity.uuid(), new SyncPlayerPositionsMessage.PlayerPosition(
                sample.getX(), sample.getY(), sample.getZ(),
                identity.name(), identity.team(), sample.getYaw(),
                identity.squadId(), identity.squadLeader(), identity.commander()));
        }
        return result;
    }

    public synchronized void clear() {
        session = Long.MIN_VALUE;
        identities = Map.of();
        revision++;
    }

    public synchronized long revision() {
        return revision;
    }
}
