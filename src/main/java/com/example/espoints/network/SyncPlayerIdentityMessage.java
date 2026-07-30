package com.example.espoints.network;

import com.example.espoints.client.ClientPlayerIdentityState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Static/identity table sent only on subscription, join/leave or role changes. */
public final class SyncPlayerIdentityMessage {
    private final long session;
    private final List<Identity> identities;

    public SyncPlayerIdentityMessage(long session, List<Identity> identities) {
        if (session <= 0L || identities == null
            || identities.size() > SyncPlayerPositionsMessage.MAX_PLAYERS) {
            throw new IllegalArgumentException("Invalid tactical identity table");
        }
        this.session = session;
        this.identities = List.copyOf(identities);
    }

    public long session() {
        return session;
    }

    public List<Identity> identities() {
        return identities;
    }

    public record Identity(int shortId, UUID uuid, String name, String team,
                           int squadId, boolean squadLeader, boolean commander) {
        public Identity {
            name = name == null ? "" : name;
            team = team == null ? "" : team;
        }
    }

    public static void encode(SyncPlayerIdentityMessage message, FriendlyByteBuf buf) {
        buf.writeVarLong(message.session);
        buf.writeVarInt(message.identities.size());
        Set<Integer> shortIds = new HashSet<>();
        Set<UUID> uuids = new HashSet<>();
        for (Identity identity : message.identities) {
            if (identity.shortId <= 0 || identity.shortId > 0xffff
                || identity.uuid == null
                || identity.name.length() > 64 || identity.team.length() > 32
                || identity.squadId < -1 || identity.squadId > 65_535
                || !shortIds.add(identity.shortId) || !uuids.add(identity.uuid)) {
                throw new IllegalArgumentException("Invalid tactical identity");
            }
            buf.writeShort(identity.shortId);
            buf.writeUUID(identity.uuid);
            buf.writeUtf(identity.name, 64);
            buf.writeUtf(identity.team, 32);
            buf.writeVarInt(identity.squadId);
            int flags = (identity.squadLeader ? 1 : 0) | (identity.commander ? 2 : 0);
            buf.writeByte(flags);
        }
    }

    public static SyncPlayerIdentityMessage decode(FriendlyByteBuf buf) {
        long session = buf.readVarLong();
        if (session <= 0L) {
            throw new IllegalArgumentException("Invalid tactical identity session");
        }
        int size = PacketValidation.checkedCount(
            buf.readVarInt(), SyncPlayerPositionsMessage.MAX_PLAYERS, "player identity");
        List<Identity> identities = new ArrayList<>(size);
        Set<Integer> shortIds = new HashSet<>();
        Set<UUID> uuids = new HashSet<>();
        for (int index = 0; index < size; index++) {
            int shortId = buf.readUnsignedShort();
            UUID uuid = buf.readUUID();
            String name = buf.readUtf(64);
            String team = buf.readUtf(32);
            int squadId = buf.readVarInt();
            int flags = buf.readUnsignedByte();
            if (shortId == 0 || squadId < -1 || squadId > 65_535
                || (flags & ~3) != 0
                || !shortIds.add(shortId) || !uuids.add(uuid)) {
                throw new IllegalArgumentException("Invalid tactical identity");
            }
            identities.add(new Identity(
                shortId, uuid, name, team, squadId, (flags & 1) != 0, (flags & 2) != 0));
        }
        return new SyncPlayerIdentityMessage(session, identities);
    }

    public static void handle(SyncPlayerIdentityMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                ClientPlayerIdentityState.get().replace(message.session, message.identities);
            }
        });
        context.setPacketHandled(true);
    }

    public static void sendToPlayers(
            java.util.Collection<? extends ServerPlayer> players,
            long session,
            List<Identity> identities) {
        if (players == null || players.isEmpty()) {
            return;
        }
        SyncPlayerIdentityMessage message =
            new SyncPlayerIdentityMessage(session, identities);
        for (ServerPlayer player : players) {
            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player), message);
        }
    }

    public static void sendToPlayer(ServerPlayer player, long session, List<Identity> identities) {
        NetworkHandler.INSTANCE.send(
            PacketDistributor.PLAYER.with(() -> player),
            new SyncPlayerIdentityMessage(session, identities));
    }
}
