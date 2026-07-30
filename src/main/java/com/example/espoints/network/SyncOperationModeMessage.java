package com.example.espoints.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import com.example.espoints.client.ClientBattleState;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 同步行动模式状态的网络消息
 */
public class SyncOperationModeMessage {
    private final boolean operationModeRunning;
    private final int currentBatch;
    private final int totalBatches;
    private final String endBehavior;
    private final Map<String, String> teamRoles;
    private final Map<String, Integer> teamReinforcements;
    private final Map<String, Integer> teamInitialReinforcements;

    public SyncOperationModeMessage(boolean operationModeRunning, int currentBatch, int totalBatches,
                                  String endBehavior, Map<String, String> teamRoles,
                                  Map<String, Integer> teamReinforcements,
                                  Map<String, Integer> teamInitialReinforcements) {
        this.operationModeRunning = operationModeRunning;
        this.currentBatch = currentBatch;
        this.totalBatches = totalBatches;
        this.endBehavior = endBehavior;
        this.teamRoles = new HashMap<>(teamRoles);
        this.teamReinforcements = new HashMap<>(teamReinforcements);
        this.teamInitialReinforcements = new HashMap<>(teamInitialReinforcements);
    }

    /**
     * 编码消息
     */
    public static void encode(SyncOperationModeMessage msg, FriendlyByteBuf buf) {
        validateHeader(msg.currentBatch, msg.totalBatches, msg.endBehavior);
        checkedSize(msg.teamRoles.size(), "team roles");
        checkedSize(msg.teamReinforcements.size(), "reinforcements");
        checkedSize(msg.teamInitialReinforcements.size(), "initial reinforcements");
        buf.writeBoolean(msg.operationModeRunning);
        buf.writeInt(msg.currentBatch);
        buf.writeInt(msg.totalBatches);
        buf.writeUtf(msg.endBehavior, 16);
        
        // 编码队伍角色映射
        buf.writeInt(msg.teamRoles.size());
        for (Map.Entry<String, String> entry : msg.teamRoles.entrySet()) {
            buf.writeUtf(entry.getKey(), 32);
            buf.writeUtf(entry.getValue(), 16);
        }
        
        // 编码队伍兵力映射
        buf.writeInt(msg.teamReinforcements.size());
        for (Map.Entry<String, Integer> entry : msg.teamReinforcements.entrySet()) {
            buf.writeUtf(entry.getKey(), 32);
            checkedReinforcements(entry.getValue());
            buf.writeInt(entry.getValue());
        }
        
        // 编码队伍初始兵力映射
        buf.writeInt(msg.teamInitialReinforcements.size());
        for (Map.Entry<String, Integer> entry : msg.teamInitialReinforcements.entrySet()) {
            buf.writeUtf(entry.getKey(), 32);
            checkedReinforcements(entry.getValue());
            buf.writeInt(entry.getValue());
        }
    }

    /**
     * 解码消息
     */
    public static SyncOperationModeMessage decode(FriendlyByteBuf buf) {
        boolean operationModeRunning = buf.readBoolean();
        int currentBatch = buf.readInt();
        int totalBatches = buf.readInt();
        String endBehavior = buf.readUtf(16);
        validateHeader(currentBatch, totalBatches, endBehavior);
        
        // 解码队伍角色映射
        int teamRolesSize = checkedSize(buf.readInt(), "team roles");
        Map<String, String> teamRoles = new HashMap<>();
        for (int i = 0; i < teamRolesSize; i++) {
            String team = buf.readUtf(32);
            String role = buf.readUtf(16);
            teamRoles.put(team, role);
        }
        
        // 解码队伍兵力映射
        int teamReinforcementsSize = checkedSize(buf.readInt(), "reinforcements");
        Map<String, Integer> teamReinforcements = new HashMap<>();
        for (int i = 0; i < teamReinforcementsSize; i++) {
            String team = buf.readUtf(32);
            int reinforcements = buf.readInt();
            checkedReinforcements(reinforcements);
            teamReinforcements.put(team, reinforcements);
        }
        
        // 解码队伍初始兵力映射
        int teamInitialReinforcementsSize = checkedSize(buf.readInt(), "initial reinforcements");
        Map<String, Integer> teamInitialReinforcements = new HashMap<>();
        for (int i = 0; i < teamInitialReinforcementsSize; i++) {
            String team = buf.readUtf(32);
            int initialReinforcements = buf.readInt();
            checkedReinforcements(initialReinforcements);
            teamInitialReinforcements.put(team, initialReinforcements);
        }
        
        return new SyncOperationModeMessage(operationModeRunning, currentBatch, totalBatches,
                                           endBehavior, teamRoles, teamReinforcements,
                                           teamInitialReinforcements);
    }

    /**
     * 处理消息
     */
    public static void handle(SyncOperationModeMessage msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 只在客户端处理
            if (context.getDirection().getReceptionSide().isClient()) {
                // 更新客户端的行动模式状态
                ClientBattleState.get().applyOperation(
                    msg.operationModeRunning,
                    msg.currentBatch,
                    msg.totalBatches,
                    msg.endBehavior,
                    msg.teamRoles,
                    msg.teamReinforcements,
                    msg.teamInitialReinforcements
                );
            }
        });
        context.setPacketHandled(true);
    }

    private static int checkedSize(int size, String field) {
        if (size < 0 || size > 8) {
            throw new IllegalArgumentException("Invalid " + field + " count: " + size);
        }
        return size;
    }

    private static void validateHeader(int currentBatch, int totalBatches, String endBehavior) {
        if (currentBatch < 1 || currentBatch > 64
            || totalBatches < 0 || totalBatches > 64
            || (!"terminate".equalsIgnoreCase(endBehavior)
                && !"loop".equalsIgnoreCase(endBehavior))) {
            throw new IllegalArgumentException("Invalid operation mode header");
        }
    }

    private static void checkedReinforcements(Integer value) {
        if (value == null || value < 0 || value > 10_000_000) {
            throw new IllegalArgumentException("Invalid reinforcement value");
        }
    }

    /**
     * 广播消息给所有玩家
     */
    public static void broadcastToAll(boolean operationModeRunning, int currentBatch, int totalBatches,
                                     String endBehavior, Map<String, String> teamRoles,
                                     Map<String, Integer> teamReinforcements,
                                     Map<String, Integer> teamInitialReinforcements) {
        NetworkHandler.INSTANCE.send(
            PacketDistributor.ALL.noArg(),
            new SyncOperationModeMessage(operationModeRunning, currentBatch, totalBatches,
                                        endBehavior, teamRoles, teamReinforcements,
                                        teamInitialReinforcements)
        );
    }
    
    /**
     * 发送消息给指定玩家
     */
    public static void sendToPlayer(net.minecraft.server.level.ServerPlayer player, 
                                   boolean operationModeRunning, int currentBatch, int totalBatches,
                                   String endBehavior, Map<String, String> teamRoles,
                                   Map<String, Integer> teamReinforcements,
                                   Map<String, Integer> teamInitialReinforcements) {
        NetworkHandler.INSTANCE.send(
            PacketDistributor.PLAYER.with(() -> player),
            new SyncOperationModeMessage(operationModeRunning, currentBatch, totalBatches,
                                        endBehavior, teamRoles, teamReinforcements,
                                        teamInitialReinforcements)
        );
    }
}
