package com.example.espoints.capturepoint;

import com.example.espoints.ESPointsMod;
import com.example.espoints.config.ModConfig;
import com.example.espoints.network.SyncBastionsMessage;
import com.example.espoints.network.SyncCapturePointsMessage;
import com.example.espoints.util.EspetroTeamBridge;
import com.example.espoints.util.ModLogger;
import com.example.espoints.capturepoint.CaptureState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 据点管理器 - 管理所有的据点实例
 * 负责据点的创建、删除、状态更新和数据同步
 */
public class CapturePointManager {
    // 单例实例
    private static CapturePointManager INSTANCE;
    
    // 存储所有据点的Map，键为据点名称，值为据点实例
    private final Map<String, CapturePoint> capturePoints;
    
    // 存储玩家名称的映射，键为UUID，值为玩家名称
    private final Map<UUID, String> playerNameMap;
    
    // 存储玩家上一次同步时的队伍名称，用于检测原版team命令带来的队伍变化
    private final Map<UUID, String> playerTeamNameMap;

    // 高频位置包仅在首次出现或身份变化时携带姓名/阵营。
    private final Map<UUID, PlayerMetadata> lastBroadcastPlayerMetadata = new HashMap<>();

    // 兵站与基地通常不变，只在玩家可见数据实际变化时重发。
    private final Map<UUID, BastionSyncState> lastBastionSyncByPlayer = new HashMap<>();
    private List<SyncBastionsMessage.VehicleSupplyStationInfo> cachedEspetroVehicleSupplyStations = List.of();
    private long cachedEspetroVehicleSupplyStationTick = Long.MIN_VALUE;
    
    // 存储玩家进入据点的时间，键为玩家UUID，值为进入时间（毫秒）
    private final Map<UUID, Long> playerEnterTime = new ConcurrentHashMap<>();
    
    // 存储据点占领信息，键为据点名称，值为占领信息对象
    private final Map<String, CapturedInfo> capturedInfoMap = new ConcurrentHashMap<>();
    
    // 存储据点失去占领状态的时间，用于防止刷分，键为据点名称，值为失去占领状态的时间（毫秒）
    private final Map<String, Long> lastLostCaptureTime = new ConcurrentHashMap<>();
    
    // 检查间隔计数器（每40tick检查一次，即每2秒，占领一个点需要40秒）
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 40;
    
    // 行动攻防机制相关字段
    private int currentBatch = 1; // 当前批次
    private int totalBatches = 0; // 总批数
    private String endBehavior = "terminate"; // 结束行为：terminate(终止)或loop(循环)
    private boolean operationModeRunning = false; // 行动模式是否正在运行
    private final Map<String, String> teamRoles = new ConcurrentHashMap<>(); // 队伍角色映射：team -> role(attacker/defender)
    private final Map<String, Integer> teamReinforcements = new ConcurrentHashMap<>(); // 队伍当前兵力映射：team -> reinforcements
    private final Map<String, Integer> teamInitialReinforcements = new ConcurrentHashMap<>(); // 队伍初始兵力映射：team -> initial_reinforcements
    private final Map<CapturePoint, Long> progressRecoveryTimers = new ConcurrentHashMap<>(); // 进度恢复计时器
    private final Map<String, CapturePoint.SerializableCapturePoint> operationPointSnapshots = new ConcurrentHashMap<>(); // 行动模式非当前批次据点的最终显示状态
    
    // 玩家位置同步计时器
    private int playerPositionSyncTimer = 0;
    private static final int PLAYER_POSITION_SYNC_INTERVAL = 20; // 每20tick同步一次（1秒）    
    private static final int BATCH_COMPLETION_ATTACK_REINFORCEMENT = 200;
    private static final String ESPETRO_MOD_ID = "espetro";
    private static final String ESPETRO_TROOP_COUNT_MANAGER_CLASS = "org.espetro.team.TroopCountManager";
    private static final String ESPETRO_BASTION_MANAGER_CLASS = "org.espetro.bastion.BastionManager";
    private static final String ESPETRO_GAME_STATE_MANAGER_CLASS = "org.espetro.team.GameStateManager";
    private static final String ESPETRO_SPAWN_POINT_CONFIG_CLASS = "org.espetro.team.SpawnPointConfig";
    private static final String ESPETRO_VEHICLE_SUPPLY_STATION_TAG = "espetro_vehicle_supply_station";
    private static final String ESPETRO_VEHICLE_SUPPLY_STATION_TEAM_KEY = "espetro_vehicle_supply_station_team";
    private static final String ESPETRO_VEHICLE_SUPPLY_STATION_ID_KEY = "espetro_vehicle_supply_station_id";
    private static final String ESPETRO_VEHICLE_SUPPLY_STATION_X_KEY = "espetro_vehicle_supply_station_x";
    private static final String ESPETRO_VEHICLE_SUPPLY_STATION_Y_KEY = "espetro_vehicle_supply_station_y";
    private static final String ESPETRO_VEHICLE_SUPPLY_STATION_Z_KEY = "espetro_vehicle_supply_station_z";
    private boolean espetroDeployingCapturePointsActivated = false;
    private boolean tacticalMarkersClearedForWaiting = false;

    private record PlayerMetadata(String name, String teamName) {
    }

    private record BastionSyncState(List<SyncBastionsMessage.BastionInfo> bastions,
                                    List<SyncBastionsMessage.BaseInfo> bases,
                                    List<SyncBastionsMessage.VehicleSupplyStationInfo> vehicleSupplyStations) {
    }

    
    /**
     * 据点占领信息类，存储据点被占领的时间和占领者
     */
    private static class CapturedInfo {
        private final String captorName;
        private final long captureTime;
        private long lastRewardTime;
        
        public CapturedInfo(String captorName, long captureTime) {
            this.captorName = captorName;
            this.captureTime = captureTime;
            this.lastRewardTime = captureTime;
        }
        
        public String getCaptorName() {
            return captorName;
        }
        
        public long getCaptureTime() {
            return captureTime;
        }
        
        public long getLastRewardTime() {
            return lastRewardTime;
        }
        
        public void setLastRewardTime(long lastRewardTime) {
            this.lastRewardTime = lastRewardTime;
        }
    }
    
    /**
     * 验证据点名称是否有效（必须为单个大写字母A-Z）
     * @param name 据点名称
     * @return 是否有效
     */
    public boolean isValidPointName(String name) {
        return name != null && name.length() == 1 && name.charAt(0) >= 'A' && name.charAt(0) <= 'Z';
    }
    
    /**
     * 验证坐标是否有效（构成有效的长方体区域）
     * @param pos1 第一个坐标点
     * @param pos2 第二个坐标点
     * @return 是否有效
     */
    public boolean isValidCoordinates(BlockPos pos1, BlockPos pos2) {
        return pos1 != null && pos2 != null && !pos1.equals(pos2);
    }
    
    /**
     * 私有构造函数，防止外部实例化
     */
    private CapturePointManager() {
        this.capturePoints = new ConcurrentHashMap<>();
        this.playerNameMap = new ConcurrentHashMap<>();
        this.playerTeamNameMap = new ConcurrentHashMap<>();
        this.plannedPointsMap = new ConcurrentHashMap<>();
        bindEspetroTeams();
    }
    
    /**
     * 行动攻防机制：计划据点类，用于存储行动模式下的据点计划
     */
    private static class PlannedCapturePoint {
        private final String name;
        private final BlockPos pos1;
        private final BlockPos pos2;
        private final int batch;
        
        public PlannedCapturePoint(String name, BlockPos pos1, BlockPos pos2, int batch) {
            this.name = name;
            this.pos1 = pos1;
            this.pos2 = pos2;
            this.batch = batch;
        }
        
        public String getName() { return name; }
        public BlockPos getPos1() { return pos1; }
        public BlockPos getPos2() { return pos2; }
        public int getBatch() { return batch; }
    }
    
    // 存储计划据点的映射，键为据点名称，值为计划据点对象
    private final Map<String, PlannedCapturePoint> plannedPointsMap;
    
    /**
     * 添加计划据点
     * @param name 据点名称
     * @param pos1 第一个坐标点
     * @param pos2 第二个坐标点
     * @param batch 批次号
     * @return 是否添加成功
     */
    public boolean addPlannedCapturePoint(String name, BlockPos pos1, BlockPos pos2, int batch) {
        try {
            // 检查据点名称是否已存在于计划中
            if (plannedPointsMap.containsKey(name)) {
                ModLogger.warn("计划据点【" + name + "】已存在，添加失败");
                return false;
            }
            
            // 创建计划据点
            PlannedCapturePoint plannedPoint = new PlannedCapturePoint(name, pos1, pos2, batch);
            
            // 添加到计划映射
            plannedPointsMap.put(name, plannedPoint);
            
            ModLogger.info("计划据点【" + name + "】（批次 " + batch + "）添加成功");
            return true;
        } catch (Exception e) {
            ModLogger.error("添加计划据点时发生异常: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 从计划中移除据点
     * @param name 据点名称
     * @return 是否移除成功
     */
    public boolean removePlannedCapturePoint(String name) {
        try {
            PlannedCapturePoint plannedPoint = plannedPointsMap.remove(name);
            if (plannedPoint == null) {
                ModLogger.warn("未找到计划据点【" + name + "】，移除失败");
                return false;
            }
            operationPointSnapshots.remove(name);
            
            ModLogger.info("计划据点【" + name + "】移除成功");
            return true;
        } catch (Exception e) {
            ModLogger.error("移除计划据点时发生异常: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 清空所有计划据点
     */
    public void clearPlannedCapturePoints() {
        plannedPointsMap.clear();
        operationPointSnapshots.clear();
        ModLogger.info("所有计划据点已清空");
    }
    

    
    /**
     * 获取单例实例
     * @return CapturePointManager实例
     */
    public static CapturePointManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CapturePointManager();
        }
        return INSTANCE;
    }

    public void resetTransientSyncCaches() {
        lastBroadcastPlayerMetadata.clear();
        lastBastionSyncByPlayer.clear();
        cachedEspetroVehicleSupplyStations = List.of();
        cachedEspetroVehicleSupplyStationTick = Long.MIN_VALUE;
    }
    
    /**
     * 创建一个新的据点
     * @param name 据点名称
     * @param pos1 第一个坐标点
     * @param pos2 第二个坐标点
     * @return 创建的据点实例，如果创建失败则返回null
     */
    public CapturePoint createCapturePoint(String name, BlockPos pos1, BlockPos pos2) {
        return createCapturePoint(name, pos1, pos2, 1); // 默认批次为1
    }
    
    /**
     * 创建一个新的据点，带批次信息
     * @param name 据点名称
     * @param pos1 第一个坐标点
     * @param pos2 第二个坐标点
     * @param batch 据点所属批次
     * @return 创建的据点实例，如果创建失败则返回null
     */
    public CapturePoint createCapturePoint(String name, BlockPos pos1, BlockPos pos2, int batch) {
        ModLogger.warn("普通据点模式已移除，请使用 addPlannedCapturePoint 创建行动模式计划据点");
        return null;
    }
    
    /**
     * 删除指定名称的据点
     * @param name 据点名称
     * @return 是否删除成功
     */
    public boolean removeCapturePoint(String name) {
        try {
            CapturePoint removed = capturePoints.remove(name);
            if (removed != null) {
                ModLogger.info("据点 " + name + " 已删除");
                
                // 同步到所有客户端
                syncToAllClients();
                return true;
            } else {
                ModLogger.warn("删除据点失败：未找到名称为 " + name + " 的据点");
                return false;
            }
        } catch (Exception e) {
            ModLogger.error("删除据点时发生异常: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 清空所有据点
     */
    public void clearAllCapturePoints() {
        try {
            capturePoints.clear();
            ModLogger.info("所有据点已清空");
            
            // 同步到所有客户端
            syncToAllClients();
        } catch (Exception e) {
            ModLogger.error("清空据点时发生异常: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有据点
     * @return 所有据点的集合
     */
    public Collection<CapturePoint> getAllCapturePoints() {
        return new ArrayList<>(capturePoints.values());
    }
    
    /**
     * 根据名称获取据点
     * @param name 据点名称
     * @return 据点实例，如果不存在则返回null
     */
    public CapturePoint getCapturePoint(String name) {
        return capturePoints.get(name);
    }
    
    /**
     * 检查指定玩家是否在某个据点内
     * @param player 玩家
     * @return 如果在据点内则返回据点实例，否则返回null
     */
    public CapturePoint checkPlayerInCapturePoint(Player player) {
        BlockPos playerPos = player.blockPosition();
        for (CapturePoint point : capturePoints.values()) {
            // 行动模式下只检查当前批次的据点
            if (operationModeRunning && point.getBatch() != currentBatch) {
                continue;
            }
            if (point.isPositionInside(playerPos)) {
                return point;
            }
        }
        return null;
    }
    
    /**
     * 设置队伍角色（进攻方/防守方）
     * @param team 队伍名称
     * @param role 角色类型（attacker/defender）
     * @return 是否设置成功
     */
    public boolean setTeamRole(String team, String role) {
        return setTeamRole(team, role, 50); // 默认兵力为50
    }
    
    /**
     * 设置队伍角色（进攻方/防守方）和兵力
     * @param team 队伍名称
     * @param role 角色类型（attacker/defender）
     * @param reinforcements 队伍兵力，必须大于0
     * @return 是否设置成功
     */
    public boolean setTeamRole(String team, String role, int reinforcements) {
        String normalizedRole = role.toLowerCase();

        // 检查角色类型是否有效
        if (!normalizedRole.equals("attacker") && !normalizedRole.equals("defender")) {
            ModLogger.warn("无效的角色类型：" + role + "，只能是 attacker 或 defender");
            return false;
        }
        
        // 检查兵力是否有效
        if (reinforcements <= 0) {
            ModLogger.warn("无效的兵力值：" + reinforcements + "，兵力必须大于0");
            return false;
        }

        String canonicalTeam = "attacker".equals(normalizedRole)
                ? EspetroTeamBridge.ATTACK
                : EspetroTeamBridge.DEFEND;

        teamRoles.put(canonicalTeam, normalizedRole);
        teamReinforcements.put(canonicalTeam, reinforcements);
        teamInitialReinforcements.put(canonicalTeam, reinforcements); // 保存初始兵力值
        ModLogger.info("Espetro 阵营 " + canonicalTeam + " 已绑定为 " + normalizedRole + " 角色，兵力：" + reinforcements);
        
        // 同步到客户端
        syncOperationModeToClients();
        return true;
    }

    /**
     * 将 HCRpoints 队伍关系固定绑定到 Espetro 的 ATTACK/DEFEND 阵营。
     */
    public void bindEspetroTeams() {
        teamRoles.put(EspetroTeamBridge.ATTACK, "attacker");
        teamRoles.put(EspetroTeamBridge.DEFEND, "defender");
        teamReinforcements.putIfAbsent(EspetroTeamBridge.ATTACK, 50);
        teamReinforcements.putIfAbsent(EspetroTeamBridge.DEFEND, 50);
        teamInitialReinforcements.putIfAbsent(EspetroTeamBridge.ATTACK, 50);
        teamInitialReinforcements.putIfAbsent(EspetroTeamBridge.DEFEND, 50);
    }
    
    /**
     * 检查是否已经设置了攻守两方队伍
     * @return 如果同时存在进攻方和防守方则返回true，否则返回false
     */
    public boolean hasBothRolesSet() {
        bindEspetroTeams();
        return true;
    }
    
    /**
     * 获取进攻方队伍名称
     * @return 进攻方队伍名称，如果没有则返回null
     */
    public String getAttackerTeam() {
        return EspetroTeamBridge.ATTACK;
    }
    
    /**
     * 获取防守方队伍名称
     * @return 防守方队伍名称，如果没有则返回null
     */
    public String getDefenderTeam() {
        return EspetroTeamBridge.DEFEND;
    }
    
    /**
     * 获取队伍兵力
     * @param team 队伍名称
     * @return 队伍兵力，如果队伍不存在则返回0
     */
    public int getTeamReinforcements(String team) {
        bindEspetroTeams();
        String canonicalTeam = EspetroTeamBridge.canonicalizeTeamName(team);
        return canonicalTeam != null ? teamReinforcements.getOrDefault(canonicalTeam, 0) : 0;
    }
    
    /**
     * 获取队伍初始兵力
     * @param team 队伍名称
     * @return 队伍初始兵力，如果队伍不存在则返回0
     */
    public int getTeamInitialReinforcements(String team) {
        bindEspetroTeams();
        String canonicalTeam = EspetroTeamBridge.canonicalizeTeamName(team);
        return canonicalTeam != null ? teamInitialReinforcements.getOrDefault(canonicalTeam, 0) : 0;
    }
    
    /**
     * 扣除队伍兵力
     * @param team 队伍名称
     * @param amount 扣除数量
     * @return 扣除后的兵力，返回-1表示队伍不存在
     */
    public int deductTeamReinforcements(String team, int amount) {
        String canonicalTeam = EspetroTeamBridge.canonicalizeTeamName(team);
        if (canonicalTeam == null || !teamReinforcements.containsKey(canonicalTeam)) {
            return -1;
        }
        
        int currentReinforcements = teamReinforcements.get(canonicalTeam);
        int newReinforcements = Math.max(0, currentReinforcements - amount);
        teamReinforcements.put(canonicalTeam, newReinforcements);
        
        ModLogger.info("队伍 " + canonicalTeam + " 兵力减少 " + amount + "，剩余兵力：" + newReinforcements);
        
        // 检查胜负条件
        checkWinLossCondition();
        
        // 检查兵力阈值，可能需要播放背水一战音频
        checkLowReinforcementThreshold();
        
        // 同步到客户端
        syncOperationModeToClients();
        
        return newReinforcements;
    }
    
    /**
     * 清除队伍兵力
     * @param team 队伍名称
     */
    public void clearTeamReinforcements(String team) {
        String canonicalTeam = EspetroTeamBridge.canonicalizeTeamName(team);
        if (canonicalTeam == null) {
            return;
        }
        teamReinforcements.put(canonicalTeam, 0);
        ModLogger.info("队伍 " + canonicalTeam + " 兵力已清空");
        
        // 检查胜负条件
        checkWinLossCondition();
        
        // 检查兵力阈值，可能需要播放背水一战音频
        checkLowReinforcementThreshold();
        
        // 同步到客户端
        syncOperationModeToClients();
    }
    
    /**
     * 检查胜负条件
     */
    private void checkWinLossCondition() {
        String attackerTeam = getAttackerTeam();
        String defenderTeam = getDefenderTeam();
        
        if (attackerTeam != null && defenderTeam != null) {
            int attackerReinforcements = getTeamReinforcements(attackerTeam);
            int defenderReinforcements = getTeamReinforcements(defenderTeam);
            
            // 检查进攻方是否耗尽兵力
            if (attackerReinforcements <= 0) {
                endOperationModeWithResult(defenderTeam, attackerTeam);
            }
            // 检查防守方是否耗尽兵力
            else if (defenderReinforcements <= 0) {
                endOperationModeWithResult(attackerTeam, defenderTeam);
            }
        }
    }
    
    /**
     * 检查兵力阈值，当一方兵力低于阈值时播放背水一战音频
     */
    private void checkLowReinforcementThreshold() {
        // 获取兵力阈值百分比
        double threshold = com.example.espoints.config.ModConfig.lowReinforcementThreshold.get();
        
        // 如果阈值为0%，则不播放音频
        if (threshold <= 0.0) {
            return;
        }
        
        String attackerTeam = getAttackerTeam();
        String defenderTeam = getDefenderTeam();
        
        if (attackerTeam != null && defenderTeam != null) {
            // 获取双方当前兵力和初始兵力
            int attackerReinforcements = getTeamReinforcements(attackerTeam);
            int defenderReinforcements = getTeamReinforcements(defenderTeam);
            int attackerInitial = getTeamInitialReinforcements(attackerTeam);
            int defenderInitial = getTeamInitialReinforcements(defenderTeam);
            
            // 计算双方兵力百分比
            double attackerPercentage = 0.0;
            double defenderPercentage = 0.0;
            
            if (attackerInitial > 0) {
                attackerPercentage = (double)attackerReinforcements / attackerInitial * 100.0;
            }
            
            if (defenderInitial > 0) {
                defenderPercentage = (double)defenderReinforcements / defenderInitial * 100.0;
            }
            
            // 检查是否有一方兵力低于阈值
            boolean isLowReinforcement = (attackerPercentage <= threshold || defenderPercentage <= threshold);
            
            // 发送音频播放消息
            if (isLowReinforcement) {
                // 播放音频
                com.example.espoints.network.PlayLowReinforcementAudioMessage.broadcastToAll(true);
                ModLogger.info("一方兵力低于阈值 " + threshold + "%，开始播放背水一战音频");
            }
        }
    }
    
    /**
     * 结束行动并显示胜负结果
     * @param winnerTeam 胜利队伍
     * @param loserTeam 失败队伍
     */
    private void endOperationModeWithResult(String winnerTeam, String loserTeam) {
        MinecraftServer server = ESPointsMod.getServer();
        if (server == null) return;

        String canonicalWinner = EspetroTeamBridge.canonicalizeTeamName(winnerTeam);
        String canonicalLoser = EspetroTeamBridge.canonicalizeTeamName(loserTeam);
        
        ModLogger.info("行动结束：" + canonicalWinner + " 胜利，" + canonicalLoser + " 失败");
        
        // 发送停止音频的消息，让音频在5秒内逐渐减小音量到停止播放
        com.example.espoints.network.PlayLowReinforcementAudioMessage.broadcastToAll(false);
        
        // 获取当前时间和结束时间（4秒后）
        long endTime = System.currentTimeMillis() + 4000;
        
        // 为胜利队伍和失败队伍的玩家设置HUD计时器
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String playerTeamName = EspetroTeamBridge.getServerPlayerTeam(player);
            if (playerTeamName == null) {
                continue;
            }
            if (playerTeamName.equals(canonicalWinner)) {
                // 使用网络消息直接通知客户端显示胜利消息
                com.example.espoints.api.HCRAPI.showWinMessage(player.getUUID());
                player.sendSystemMessage(Component.literal("你的队伍胜利了！"));
            } else if (playerTeamName.equals(canonicalLoser)) {
                // 使用网络消息直接通知客户端显示失败消息
                com.example.espoints.api.HCRAPI.showLoseMessage(player.getUUID());
                player.sendSystemMessage(Component.literal("你的队伍失败了！"));
            }
        }
        
        // 根据结束行为处理行动结束
        if (endBehavior.equals("terminate")) {
            // 终止行动
            stopOperationMode();
            ModLogger.info("行动已终止，兵力耗尽");
        } else if (endBehavior.equals("loop")) {
            // 重启行动
            startOperationMode(totalBatches, endBehavior);
            ModLogger.info("行动已重启，开始新的循环");
        }
    }
    
    /**
     * 启动行动模式
     * @param totalBatches 总批数
     * @param endBehavior 结束行为：terminate(终止)或loop(循环)
     */
    public void startOperationMode(int totalBatches, String endBehavior) {
        bindEspetroTeams();
        currentBatch = 1;
        this.totalBatches = totalBatches;
        this.endBehavior = endBehavior.toLowerCase();
        operationModeRunning = true;
        operationPointSnapshots.clear();
        
        // 重置所有队伍的兵力，将当前兵力恢复为初始兵力
        for (String team : teamInitialReinforcements.keySet()) {
            int initialReinforcements = teamInitialReinforcements.get(team);
            teamReinforcements.put(team, initialReinforcements);
            ModLogger.info("队伍 " + team + " 兵力已重置为初始值：" + initialReinforcements);
        }
        
        // 不属于 Espetro 攻防阵营的玩家视作观战者，不参与占点。
        MinecraftServer server = ESPointsMod.getServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!EspetroTeamBridge.isEspetroTeamPlayer(player)) {
                    ModLogger.info("玩家 " + player.getName().getString() + " 未加入 Espetro 阵营，被视为观战者");
                }
            }
        }
        
        // 清空现有据点，准备按计划创建
        clearAllCapturePoints();
        
        // 创建当前批次的据点
        createCurrentBatchPoints();
        
        ModLogger.info("行动模式已启动，当前批次：" + currentBatch + ", 总批数：" + totalBatches + ", 结束行为：" + this.endBehavior);
        
        // 同步到客户端
        syncOperationModeToClients();
    }
    
    /**
     * 创建当前批次的据点
     */
    private void createCurrentBatchPoints() {
        // 清除当前所有据点
        clearAllCapturePoints();
        
        // 获取防守方队伍名称
        String defenderTeam = getDefenderTeam();
        
        // 统计创建的据点数量
        int createdCount = 0;
        
        // 遍历所有计划据点，筛选出当前批次的据点
        for (PlannedCapturePoint plannedPoint : plannedPointsMap.values()) {
            if (plannedPoint.getBatch() == currentBatch) {
                // 创建新的据点实例，但暂时不同步到客户端
                CapturePoint point = createCapturePointObjectForBatch(plannedPoint);
                if (point != null) {
                    // 添加到据点映射
                    capturePoints.put(point.getName(), point);
                    ModLogger.info("已创建批次 " + currentBatch + " 的据点：" + point.getName());
                    
                    // 如果有防守方队伍，设置据点初始状态为已被防守方占领
                    if (defenderTeam != null && !defenderTeam.isEmpty()) {
                        // 直接设置据点状态为已占领，占领者为防守方
                        point.setCaptorName(defenderTeam);
                        point.setProgress(100);
                        point.setState(CaptureState.CAPTURED);
                        // 设置显示状态为已占领，确保HUD正确显示
                        point.setDisplayState(DisplayState.CAPTURED);
                        ModLogger.info("据点 " + point.getName() + " 已默认归防守方 " + defenderTeam + " 占领");
                    }
                    createdCount++;
                }
            }
        }
        
        // 所有据点创建并设置完成后，统一同步到客户端
        syncToAllClients();
        
        ModLogger.info("已创建批次 " + currentBatch + " 的所有据点，共 " + createdCount + " 个");
    }
    
    /**
     * 为批次创建据点对象，不立即同步到客户端
     * @param plannedPoint 计划据点
     * @return 据点对象
     */
    private CapturePoint createCapturePointObjectForBatch(PlannedCapturePoint plannedPoint) {
        try {
            // 检查据点数量是否超过限制
            if (capturePoints.size() >= 7) {
                ModLogger.warn("创建据点失败：已达最大据点数量（7个）");
                return null;
            }
            
            // 检查据点名称是否已存在
            if (capturePoints.containsKey(plannedPoint.getName())) {
                ModLogger.warn("创建据点失败：据点名称 " + plannedPoint.getName() + " 已存在");
                return null;
            }
            
            // 检查坐标是否有效（构成有效的长方体区域）
            if (plannedPoint.getPos1().equals(plannedPoint.getPos2())) {
                ModLogger.warn("创建据点失败：两点需构成有效长方体区域");
                return null;
            }
            
            // 创建据点实例但不立即同步
            CapturePoint point = new CapturePoint(plannedPoint.getName(), plannedPoint.getPos1(), plannedPoint.getPos2(), plannedPoint.getBatch());
            
            ModLogger.info("据点 " + plannedPoint.getName() + " (批次 " + plannedPoint.getBatch() + ") 创建成功，区域：(" + plannedPoint.getPos1().getX() + ", " + plannedPoint.getPos1().getY() + ", " + plannedPoint.getPos1().getZ() + ") - (" + plannedPoint.getPos2().getX() + ", " + plannedPoint.getPos2().getY() + ", " + plannedPoint.getPos2().getZ() + ")");
            
            return point;
        } catch (Exception e) {
            ModLogger.error("创建据点时发生异常: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 结束行动模式
     */
    public void stopOperationMode() {
        operationModeRunning = false;
        currentBatch = 1;
        progressRecoveryTimers.clear();
        
        // 清空所有据点
        clearAllCapturePoints();
        
        ModLogger.info("行动模式已结束");
        
        // 同步到客户端
        syncOperationModeToClients();
    }
    
    /**
     * 进入下一个批次
     * @return 是否成功进入下一批次
     */
    public boolean nextBatch() {
        int nextBatch = currentBatch + 1;
        
        // 检查是否存在下一批次的计划据点
        boolean hasNextBatch = false;
        for (PlannedCapturePoint plannedPoint : plannedPointsMap.values()) {
            if (plannedPoint.getBatch() == nextBatch) {
                hasNextBatch = true;
                break;
            }
        }
        
        if (hasNextBatch) {
            snapshotCurrentOperationPoints();
            currentBatch = nextBatch;
            
            // 创建下一批次的据点
            createCurrentBatchPoints();
            
            ModLogger.info("行动已进入下一批次：" + currentBatch);
            
            // 同步到客户端
            syncOperationModeToClients();
            
            return true;
        }
        return false;
    }
    
    /**
     * 获取当前批次
     * @return 当前批次
     */
    public int getCurrentBatch() {
        return currentBatch;
    }
    
    /**
     * 设置总批数
     * @param totalBatches 总批数
     */
    public void setTotalBatches(int totalBatches) {
        this.totalBatches = totalBatches;
    }
    
    /**
     * 获取总批数
     * @return 总批数
     */
    public int getTotalBatches() {
        return totalBatches;
    }
    
    /**
     * 设置结束行为
     * @param endBehavior 结束行为：terminate(终止)或loop(循环)
     */
    public void setEndBehavior(String endBehavior) {
        this.endBehavior = endBehavior;
    }
    
    /**
     * 获取结束行为
     * @return 结束行为
     */
    public String getEndBehavior() {
        return endBehavior;
    }
    
    /**
     * 从计划据点中计算总批次数量
     * @return 总批次数量
     */
    public int calculateTotalBatches() {
        int maxBatch = 0;
        for (PlannedCapturePoint plannedPoint : plannedPointsMap.values()) {
            if (plannedPoint.getBatch() > maxBatch) {
                maxBatch = plannedPoint.getBatch();
            }
        }
        return maxBatch;
    }
    
    /**
     * 检查是否需要进入下一批次
     * @param server 服务器实例
     */
    private void checkBatchProgression(MinecraftServer server) {
        if (server == null) return;
        
        // 获取当前批次的所有据点
        List<CapturePoint> currentPoints = new ArrayList<>();
        for (CapturePoint point : capturePoints.values()) {
            if (point.getBatch() == currentBatch) {
                currentPoints.add(point);
            }
        }
        
        // 如果当前批次没有据点，不需要处理
        if (currentPoints.isEmpty()) {
            return;
        }
        
        // 获取进攻方队伍名称
        String attackerTeam = getAttackerTeam();
        if (attackerTeam == null || attackerTeam.isEmpty()) {
            return;
        }
        
        // 检查当前批次所有据点是否都被占领，并且占领者是进攻方
        boolean allCapturedByAttacker = true;
        for (CapturePoint point : currentPoints) {
            if (point.getState() != CaptureState.CAPTURED || 
                !EspetroTeamBridge.isSameTeam(point.getCaptorName(), attackerTeam)) {
                allCapturedByAttacker = false;
                break;
            }
        }
        
        // 如果所有据点都被进攻方占领，处理批次推进或结束
        if (allCapturedByAttacker) {
            // 每完成一个批次，给进攻方增加200兵力
            if (grantEspetroAttackReinforcement(BATCH_COMPLETION_ATTACK_REINFORCEMENT)) {
                ModLogger.info("批次 " + currentBatch + " 完成，已通过 Espetro 兵力接口为进攻方增加 " + BATCH_COMPLETION_ATTACK_REINFORCEMENT + " 兵力");
            } else {
                ModLogger.info("批次 " + currentBatch + " 完成，未检测到 Espetro，跳过进攻方兵力增援");
            }
            
            // 向所有进攻方玩家发送批次完成消息
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                String playerTeam = EspetroTeamBridge.getServerPlayerTeam(player);
                if (attackerTeam.equals(playerTeam)) {
                    String reinforcementText = ModList.get().isLoaded(ESPETRO_MOD_ID)
                            ? "进攻方获得 " + BATCH_COMPLETION_ATTACK_REINFORCEMENT + " 兵力增援！"
                            : "未安装 Espetro，跳过兵力增援。";
                    player.sendSystemMessage(Component.literal("§6[据点] §e第 " + currentBatch + " 批次据点已全部占领！" + reinforcementText));
                }
            }
            
            // 检查是否是最后一批次
            if (currentBatch == totalBatches) {
                // 最后一批次被占领，根据结束行为处理
                ModLogger.info("最后批次 " + currentBatch + " 所有据点已被进攻方占领，根据结束行为 '" + endBehavior + "' 处理");
                
                // [已禁用] 不再通过清除守方兵力来触发胜负，直接处理结束行为
                /* 原逻辑：清除守方全部兵力 -> checkWinLossCondition 触发 endOperationModeWithResult
                String defenderTeam = getDefenderTeam();
                if (defenderTeam != null && !defenderTeam.isEmpty()) {
                    clearTeamReinforcements(defenderTeam);
                    ModLogger.info("最后批次被占领，已清除守方 " + defenderTeam + " 全部兵力");
                }
                */
                
                // 直接判定进攻方胜利（使用已声明的 attackerTeam 变量）
                String defenderTeam = getDefenderTeam();
                if (attackerTeam != null && defenderTeam != null) {
                    endOperationModeWithResult(attackerTeam, defenderTeam);
                }
            } else {
                // 不是最后一批次，进入下一批次
                ModLogger.info("当前批次 " + currentBatch + " 所有据点已被进攻方占领，准备进入下一批次");
                nextBatch();
            }
        }
    }

    private boolean grantEspetroAttackReinforcement(int amount) {
        if (!ModList.get().isLoaded(ESPETRO_MOD_ID)) {
            return false;
        }

        try {
            Class<?> managerClass = Class.forName(ESPETRO_TROOP_COUNT_MANAGER_CLASS);
            Object manager = managerClass.getMethod("getInstance").invoke(null);
            managerClass.getMethod("modifyAttackTroops", int.class).invoke(manager, amount);
            return true;
        } catch (Exception e) {
            ModLogger.warn("调用 Espetro 兵力接口失败，已跳过进攻方兵力增援: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 按批次获取计划中的据点
     * @param batch 批次号
     * @return 该批次的计划据点集合
     */
    public List<CapturePoint> getPlannedPointsByBatch(int batch) {
        List<CapturePoint> result = new ArrayList<>();
        for (PlannedCapturePoint plannedPoint : plannedPointsMap.values()) {
            if (plannedPoint.getBatch() == batch) {
                // 创建CapturePoint对象并添加到结果列表
                CapturePoint point = new CapturePoint(plannedPoint.getName(), plannedPoint.getPos1(), plannedPoint.getPos2(), plannedPoint.getBatch());
                result.add(point);
            }
        }
        return result;
    }
    
    /**
     * 获取当前批次的据点
     * @return 当前批次的据点集合
     */
    public Set<CapturePoint> getCurrentBatchPoints() {
        Set<CapturePoint> currentPoints = new HashSet<>();
        for (CapturePoint point : capturePoints.values()) {
            currentPoints.add(point);
        }
        return currentPoints;
    }
    
    /**
     * 检查是否是行动模式且正在运行
     * @return 行动模式是否正在运行
     */
    public boolean isOperationModeRunning() {
        return operationModeRunning;
    }
    
    /**
     * 获取所有计划据点
     * @return 所有计划据点的集合
     */
    public Collection<PlannedCapturePoint> getAllPlannedCapturePoints() {
        return plannedPointsMap.values();
    }
    
    /**
     * 获取计划据点映射
     * @return 计划据点映射
     */
    public Map<String, PlannedCapturePoint> getPlannedPointsMap() {
        return plannedPointsMap;
    }
    
    /**
     * 获取队伍角色映射
     * @return 队伍角色映射
     */
    public Map<String, String> getTeamRoles() {
        bindEspetroTeams();
        return teamRoles;
    }
    
    /**
     * 获取队伍兵力映射
     * @return 队伍兵力映射
     */
    public Map<String, Integer> getTeamReinforcementsMap() {
        bindEspetroTeams();
        return teamReinforcements;
    }
    
    /**
     * 清空所有队伍角色
     */
    public void clearTeamRoles() {
        teamRoles.clear();
        teamReinforcements.clear();
        teamInitialReinforcements.clear();
        bindEspetroTeams();
        ModLogger.info("队伍角色已重置为 Espetro 攻防阵营");
        
        // 同步到客户端
        syncOperationModeToClients();
    }
    
    /**
     * 从服务器同步行动模式状态
     * @param operationModeRunning 行动模式是否正在运行
     * @param currentBatch 当前批次
     * @param totalBatches 总批数
     * @param endBehavior 结束行为
     * @param teamRoles 队伍角色映射
     * @param teamReinforcements 队伍兵力映射
     * @param teamInitialReinforcements 队伍初始兵力映射
     */
    public void syncOperationModeFromServer(boolean operationModeRunning, int currentBatch, int totalBatches,
                                           String endBehavior, Map<String, String> teamRoles,
                                           Map<String, Integer> teamReinforcements,
                                           Map<String, Integer> teamInitialReinforcements) {
        this.operationModeRunning = operationModeRunning;
        this.currentBatch = currentBatch;
        this.totalBatches = totalBatches;
        this.endBehavior = endBehavior;
        
        this.teamRoles.clear();
        this.teamRoles.putAll(normalizeTeamRoleMap(teamRoles));
        
        this.teamReinforcements.clear();
        this.teamReinforcements.putAll(normalizeTeamIntegerMap(teamReinforcements));
        
        this.teamInitialReinforcements.clear();
        this.teamInitialReinforcements.putAll(normalizeTeamIntegerMap(teamInitialReinforcements));
        bindEspetroTeams();
        
        ModLogger.info("客户端行动模式状态已同步");
    }
    
    /**
     * 同步行动模式状态到所有客户端
     */
    private void syncOperationModeToClients() {
        if (!ESPointsMod.isServerRunning()) {
            return;
        }

        bindEspetroTeams();
        
        com.example.espoints.network.SyncOperationModeMessage.broadcastToAll(
            operationModeRunning,
            currentBatch,
            totalBatches,
            endBehavior,
            teamRoles,
            teamReinforcements,
            teamInitialReinforcements
        );
    }

    private Map<String, String> normalizeTeamRoleMap(Map<String, String> roles) {
        Map<String, String> normalizedRoles = new HashMap<>();
        if (roles != null) {
            for (Map.Entry<String, String> entry : roles.entrySet()) {
                String role = entry.getValue() == null ? "" : entry.getValue().toLowerCase(Locale.ROOT);
                if (!"attacker".equals(role) && !"defender".equals(role)) {
                    continue;
                }

                String canonicalTeam = EspetroTeamBridge.canonicalizeTeamName(entry.getKey());
                if (canonicalTeam == null) {
                    canonicalTeam = "attacker".equals(role) ? EspetroTeamBridge.ATTACK : EspetroTeamBridge.DEFEND;
                }
                normalizedRoles.put(canonicalTeam, role);
            }
        }
        normalizedRoles.put(EspetroTeamBridge.ATTACK, "attacker");
        normalizedRoles.put(EspetroTeamBridge.DEFEND, "defender");
        return normalizedRoles;
    }

    private Map<String, Integer> normalizeTeamIntegerMap(Map<String, Integer> values) {
        Map<String, Integer> normalizedValues = new HashMap<>();
        if (values != null) {
            for (Map.Entry<String, Integer> entry : values.entrySet()) {
                String canonicalTeam = EspetroTeamBridge.canonicalizeTeamName(entry.getKey());
                Integer value = entry.getValue();
                if (canonicalTeam != null && value != null) {
                    normalizedValues.put(canonicalTeam, value);
                }
            }
        }
        normalizedValues.putIfAbsent(EspetroTeamBridge.ATTACK, 50);
        normalizedValues.putIfAbsent(EspetroTeamBridge.DEFEND, 50);
        return normalizedValues;
    }
    
    /**
     * 从映射中恢复计划据点
     * @param plannedPointsMap 计划据点映射
     */
    public void restorePlannedPointsFromMap(Map<String, PlannedCapturePoint> plannedPointsMap) {
        this.plannedPointsMap.clear();
        this.plannedPointsMap.putAll(plannedPointsMap);
        this.operationPointSnapshots.keySet().removeIf(pointName -> !this.plannedPointsMap.containsKey(pointName));
        ModLogger.info("已恢复 " + plannedPointsMap.size() + " 个计划据点");
    }
    
    /**
     * 从映射中恢复队伍角色
     * @param teamRoles 队伍角色映射
     */
    public void restoreTeamRolesFromMap(Map<String, String> teamRoles) {
        this.teamRoles.clear();
        this.teamRoles.putAll(normalizeTeamRoleMap(teamRoles));
        bindEspetroTeams();
        ModLogger.info("已恢复并绑定 Espetro 攻防阵营队伍角色");
    }
    
    /**
     * 获取所有计划据点的信息字符串列表
     * @return 计划据点信息字符串列表
     */
    public List<String> getPlannedPointsInfo() {
        List<String> infoList = new ArrayList<>();
        
        // 按批次分组
        Map<Integer, List<PlannedCapturePoint>> batchMap = new TreeMap<>();
        for (PlannedCapturePoint plannedPoint : plannedPointsMap.values()) {
            batchMap.computeIfAbsent(plannedPoint.getBatch(), k -> new ArrayList<>()).add(plannedPoint);
        }
        
        // 生成信息字符串
        for (Map.Entry<Integer, List<PlannedCapturePoint>> entry : batchMap.entrySet()) {
            int batch = entry.getKey();
            List<PlannedCapturePoint> points = entry.getValue();
            
            infoList.add("批次 " + batch + " (共 " + points.size() + " 个据点):");
            for (PlannedCapturePoint point : points) {
                BlockPos pos1 = point.getPos1();
                BlockPos pos2 = point.getPos2();
                infoList.add("  - " + point.getName() + "：(" + pos1.getX() + "," + pos1.getY() + "," + pos1.getZ() + ") - (" + pos2.getX() + "," + pos2.getY() + "," + pos2.getZ() + ")");
            }
        }
        
        return infoList;
    }
    
    /**
     * 更新所有据点的状态
     * @param level 世界实例
     */
    public void updateAllCapturePoints(Level level) {
        try {
            if (!(level instanceof ServerLevel)) return;
            
            MinecraftServer server = level.getServer();
            if (server == null) return;
            
            boolean shouldSync = false;
            
            for (CapturePoint point : capturePoints.values()) {
            // 获取在据点内的所有玩家，并过滤掉观众
            List<ServerPlayer> playersInPoint = new ArrayList<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (point.isPositionInside(player.blockPosition())) {
                    // 只有 Espetro 攻防阵营玩家参与占领，其他玩家视为观战者。
                    if (!isPlayerInTeam(player)) {
                        continue;
                    }
                    playersInPoint.add(player);
                    
                    // 记录玩家进入据点的时间
                    UUID playerUUID = player.getUUID();
                    if (!playerEnterTime.containsKey(playerUUID)) {
                        playerEnterTime.put(playerUUID, System.currentTimeMillis());
                    } else {
                        // 检查是否需要给予奖励
                        long enterTime = playerEnterTime.get(playerUUID);
                        long currentTime = System.currentTimeMillis();
                        long interval = ModConfig.pointRewardInterval.get() * 1000L; // 转换为毫秒
                        
                        if (currentTime - enterTime >= interval) {
                            // 给予玩家奖励
                            int rewardAmount = ModConfig.pointRewardAmount.get();
                            addPointsToPlayer(player, rewardAmount, "据点奖励");
                            
                            // 更新进入时间
                            playerEnterTime.put(playerUUID, currentTime);
                        }
                    }
                } else {
                    // 玩家不在据点内，移除进入时间记录
                    playerEnterTime.remove(player.getUUID());
                }
            }
            
            // 保存更新前的状态
            CaptureState oldState = point.getState();
            int oldProgress = point.getProgress();
            String oldCaptorName = point.getCaptorName();
            
            // 更新据点状态
            point.updateStatus(playersInPoint);
            
            // 进度现在完全由点内双方人数优势推动，旧的争夺后自动回满计时器只做清理。
            progressRecoveryTimers.remove(point);
            
            // 检查状态是否发生变化
                if (oldState != point.getState() || 
                    oldProgress != point.getProgress() || 
                    !oldCaptorName.equals(point.getCaptorName())) {
                    shouldSync = true; // 状态发生变化，需要同步
                    
                    // 检查是否是据点被占领的事件
                    if (oldState != CaptureState.CAPTURED && point.getState() == CaptureState.CAPTURED) {
                        // 据点被占领，给予玩家奖励
                        String captorName = point.getCaptorName();
                        if (captorName != null && !captorName.isEmpty()) {
                            ModLogger.info("占领者 " + captorName + " 占领据点 " + point.getName());
                            
                            // 检查是否是刷分操作：如果据点脱离已占领状态3秒内又回到已占领状态，不给予奖励
                            long scoreSpamCheckTime = System.currentTimeMillis();
                            Long lastLostTime = lastLostCaptureTime.get(point.getName());
                            boolean isScoreSpam = false;
                            if (lastLostTime != null) {
                                long timeDiff = scoreSpamCheckTime - lastLostTime;
                                if (timeDiff < 3000) { // 3秒内
                                    isScoreSpam = true;
                                    ModLogger.info("检测到刷分操作，不给予占领奖励：" + point.getName());
                                }
                            }
                            
                            // 只有非刷分操作才给予奖励
                            if (!isScoreSpam) {
                                giveCaptureReward(server, captorName, point.getName());
                            }
                            
                            // 记录占领信息
                            capturedInfoMap.put(point.getName(), new CapturedInfo(captorName, scoreSpamCheckTime));
                            // 清除失去占领状态的时间记录
                            lastLostCaptureTime.remove(point.getName());
                        }
                    } else if (oldState == CaptureState.CAPTURED && point.getState() != CaptureState.CAPTURED) {
                        // 据点失去占领状态，记录失去占领状态的时间，用于防止刷分
                        lastLostCaptureTime.put(point.getName(), System.currentTimeMillis());
                        // 移除占领信息
                        capturedInfoMap.remove(point.getName());
                    } else if (point.getState() == CaptureState.CAPTURED && !oldCaptorName.equals(point.getCaptorName())) {
                        // 占领者变更，更新占领信息
                        String captorName = point.getCaptorName();
                        if (captorName != null && !captorName.isEmpty()) {
                            capturedInfoMap.put(point.getName(), new CapturedInfo(captorName, System.currentTimeMillis()));
                            // 清除失去占领状态的时间记录
                            lastLostCaptureTime.remove(point.getName());
                        }
                    }
                }
                
                // 处理占领状态的持续奖励
                if (point.getState() == CaptureState.CAPTURED) {
                    String captorName = point.getCaptorName();
                    if (captorName != null && !captorName.isEmpty()) {
                        long rewardCheckTime = System.currentTimeMillis();
                        long delay = ModConfig.capturedRewardDelay.get() * 1000L;
                        long interval = ModConfig.capturedRewardInterval.get() * 1000L;
                        
                        // 获取或创建占领信息
                        CapturedInfo capturedInfo = capturedInfoMap.computeIfAbsent(point.getName(), 
                            k -> new CapturedInfo(captorName, rewardCheckTime));
                        
                        // 检查是否满足奖励条件
                        if (rewardCheckTime - capturedInfo.getCaptureTime() >= delay && 
                            rewardCheckTime - capturedInfo.getLastRewardTime() >= interval) {
                            // 给予占领者持续奖励
                            giveCapturedReward(server, captorName, point.getName());
                            
                            // 更新最后奖励时间
                            capturedInfo.setLastRewardTime(rewardCheckTime);
                        }
                    }
                } else {
                    // 据点不再被占领，移除占领信息
                    capturedInfoMap.remove(point.getName());
                }
            }
            
            // 行动模式下，检查是否需要进入下一批次
            if (isOperationModeRunning()) {
                checkBatchProgression(server);
            }
            
            // 如果状态发生变化或每隔一定时间，同步据点状态
            if (shouldSync || ++tickCounter % 40 == 0) { // 状态变化时立即同步，或每2秒定期同步
                syncToAllClients();
                tickCounter = 0;
            }
        } catch (Exception e) {
            ModLogger.error("更新据点状态时发生异常: " + e.getMessage());
        }
    }
    
    /**
     * 同步据点数据到所有客户端
     */
    public void syncToAllClients() {
        // 确保只在服务器端调用
        if (!ESPointsMod.isServerRunning()) {
            ModLogger.warn("尝试在客户端调用服务器同步方法，忽略");
            return;
        }
        
        try {
            MinecraftServer server = ESPointsMod.getServer();
            if (server == null) {
                return;
            }

            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) {
                return;
            }

            List<CapturePoint.SerializableCapturePoint> commonPoints =
                operationModeRunning ? null : getAllSerializablePoints();
            Map<String, List<CapturePoint.SerializableCapturePoint>> pointsByTeam = new HashMap<>();
            for (ServerPlayer player : players) {
                List<CapturePoint.SerializableCapturePoint> points = commonPoints;
                if (points == null) {
                    String teamName = Optional.ofNullable(EspetroTeamBridge.getServerPlayerTeam(player)).orElse("");
                    points = pointsByTeam.get(teamName);
                    if (points == null) {
                        points = getSerializablePointsForMap(player);
                        pointsByTeam.put(teamName, points);
                    }
                }
                SyncCapturePointsMessage.sendToPlayer(player, points);
            }
            com.example.espoints.network.SyncCapturePointOverviewMessage.broadcastToAll(getOverviewSerializablePoints());
        } catch (Exception e) {
            ModLogger.error("同步据点数据到所有客户端时发生异常: " + e.getMessage());
        }
    }
    
    /**
     * 获取据点数量
     * @return 据点数量
     */
    public int getCapturePointCount() {
        return capturePoints.size();
    }
    
    /**
     * 获取所有可序列化的据点数据
     * @return 可序列化的据点数据列表
     */
    public List<CapturePoint.SerializableCapturePoint> getAllSerializablePoints() {
        List<CapturePoint.SerializableCapturePoint> serializedPoints = new ArrayList<>();
        for (CapturePoint point : capturePoints.values()) {
            serializedPoints.add(point.toSerializable());
        }
        return serializedPoints;
    }

    /**
     * 获取据点总览使用的完整数据，不受战术地图视野规则限制。
     */
    public List<CapturePoint.SerializableCapturePoint> getOverviewSerializablePoints() {
        if (plannedPointsMap.isEmpty()) {
            List<CapturePoint.SerializableCapturePoint> points = getAllSerializablePoints();
            points.sort(Comparator.comparingInt((CapturePoint.SerializableCapturePoint point) -> point.batch)
                    .thenComparing(point -> point.name));
            return points;
        }

        String defenderTeam = getDefenderTeam();
        List<CapturePoint.SerializableCapturePoint> points = new ArrayList<>();
        for (PlannedCapturePoint plannedPoint : plannedPointsMap.values()) {
            CapturePoint activePoint = capturePoints.get(plannedPoint.getName());
            if (activePoint != null) {
                points.add(activePoint.toSerializable());
                continue;
            }

            CapturePoint.SerializableCapturePoint snapshot = operationPointSnapshots.get(plannedPoint.getName());
            points.add(snapshot != null ? snapshot : serializableFromPlannedPoint(plannedPoint, defenderTeam));
        }

        points.sort(Comparator.comparingInt((CapturePoint.SerializableCapturePoint point) -> point.batch)
                .thenComparing(point -> point.name));
        return points;
    }

    /**
     * 根据玩家攻守身份生成战术地图可见据点。
     */
    private List<CapturePoint.SerializableCapturePoint> getSerializablePointsForMap(ServerPlayer player) {
        if (!operationModeRunning) {
            return getAllSerializablePoints();
        }

        String playerTeamName = EspetroTeamBridge.getServerPlayerTeam(player);
        String attackerTeam = getAttackerTeam();
        String defenderTeam = getDefenderTeam();

        if (defenderTeam.equals(playerTeamName)) {
            return getAllPlannedPointsForMap(defenderTeam);
        }

        if (attackerTeam.equals(playerTeamName)) {
            return getAttackerVisiblePointsForMap(attackerTeam, defenderTeam);
        }

        return getCurrentBatchSerializablePoints();
    }

    private List<CapturePoint.SerializableCapturePoint> getAllPlannedPointsForMap(String defenderTeam) {
        List<CapturePoint.SerializableCapturePoint> points = new ArrayList<>();
        for (PlannedCapturePoint plannedPoint : plannedPointsMap.values()) {
            CapturePoint activePoint = capturePoints.get(plannedPoint.getName());
            if (activePoint != null) {
                points.add(activePoint.toSerializable());
            } else {
                CapturePoint.SerializableCapturePoint snapshot = operationPointSnapshots.get(plannedPoint.getName());
                points.add(snapshot != null ? snapshot : serializableFromPlannedPoint(plannedPoint, defenderTeam));
            }
        }
        points.sort(Comparator.comparingInt((CapturePoint.SerializableCapturePoint point) -> point.batch)
                .thenComparing(point -> point.name));
        return points;
    }

    private List<CapturePoint.SerializableCapturePoint> getAttackerVisiblePointsForMap(String attackerTeam, String defenderTeam) {
        List<CapturePoint.SerializableCapturePoint> points = new ArrayList<>();
        for (PlannedCapturePoint plannedPoint : plannedPointsMap.values()) {
            if (plannedPoint.getBatch() < currentBatch) {
                CapturePoint.SerializableCapturePoint snapshot = operationPointSnapshots.get(plannedPoint.getName());
                points.add(snapshot != null ? snapshot : serializableFromPlannedPoint(
                                plannedPoint,
                                attackerTeam,
                                CaptureState.CAPTURED,
                                DisplayState.CAPTURED,
                                100
                        ));
            } else if (plannedPoint.getBatch() == currentBatch) {
                CapturePoint activePoint = capturePoints.get(plannedPoint.getName());
                if (activePoint != null) {
                    points.add(activePoint.toSerializable());
                } else {
                    CapturePoint.SerializableCapturePoint snapshot = operationPointSnapshots.get(plannedPoint.getName());
                    points.add(snapshot != null ? snapshot : serializableFromPlannedPoint(plannedPoint, defenderTeam));
                }
            }
        }
        points.sort(Comparator.comparingInt((CapturePoint.SerializableCapturePoint point) -> point.batch)
                .thenComparing(point -> point.name));
        return points;
    }

    private void snapshotCurrentOperationPoints() {
        for (CapturePoint point : capturePoints.values()) {
            operationPointSnapshots.put(point.getName(), point.toSerializable());
        }
    }

    private List<CapturePoint.SerializableCapturePoint> getCurrentBatchSerializablePoints() {
        List<CapturePoint.SerializableCapturePoint> points = new ArrayList<>();
        for (CapturePoint point : capturePoints.values()) {
            if (point.getBatch() == currentBatch) {
                points.add(point.toSerializable());
            }
        }
        points.sort(Comparator.comparingInt((CapturePoint.SerializableCapturePoint point) -> point.batch)
                .thenComparing(point -> point.name));
        return points;
    }

    private CapturePoint.SerializableCapturePoint serializableFromPlannedPoint(PlannedCapturePoint plannedPoint, String defaultCaptor) {
        return serializableFromPlannedPoint(
                plannedPoint,
                defaultCaptor != null ? defaultCaptor : "",
                CaptureState.CAPTURED,
                DisplayState.CAPTURED,
                100
        );
    }

    private CapturePoint.SerializableCapturePoint serializableFromPlannedPoint(PlannedCapturePoint plannedPoint,
                                                                              String captorName,
                                                                              CaptureState state,
                                                                              DisplayState displayState,
                                                                              int progress) {
        return new CapturePoint.SerializableCapturePoint(
                plannedPoint.getName(),
                plannedPoint.getPos1(),
                plannedPoint.getPos2(),
                plannedPoint.getBatch(),
                state,
                displayState,
                captorName,
                progress
        );
    }
    
    /**
     * 从服务器同步据点数据（完全替换）
     * @param serializedPoints 序列化的据点列表
     */
    public void syncFromServer(List<CapturePoint.SerializableCapturePoint> serializedPoints) {
        // 确保只在客户端调用
        if (ESPointsMod.isServerRunning()) {
            ModLogger.warn("尝试在服务器端调用客户端同步方法，忽略");
            return;
        }
        
        try {
            // 清空现有据点
            capturePoints.clear();
            
            // 重建据点数据
            for (CapturePoint.SerializableCapturePoint sp : serializedPoints) {
                CapturePoint point = new CapturePoint(sp.name, sp.pos1, sp.pos2, sp.batch);
                point.restoreFromSerializable(sp);
                capturePoints.put(sp.name, point);
            }
            
            ModLogger.info("客户端据点数据已同步，共 " + capturePoints.size() + " 个据点");
        } catch (Exception e) {
            ModLogger.error("客户端据点数据同步失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 更新客户端据点数据（增量更新）
     * @param serializedPoints 序列化的据点列表
     */
    public void updateCapturePoints(List<CapturePoint.SerializableCapturePoint> serializedPoints) {
        // 确保只在客户端调用
        if (ESPointsMod.isServerRunning()) {
            ModLogger.warn("尝试在服务器端调用客户端更新方法，忽略");
            return;
        }
        
        try {
            // 更新现有据点或添加新据点
            for (CapturePoint.SerializableCapturePoint sp : serializedPoints) {
                CapturePoint point = capturePoints.get(sp.name);
                if (point != null) {
                    // 更新现有据点状态
                    point.restoreFromSerializable(sp);
                } else {
                    // 添加新的据点
                    point = new CapturePoint(sp.name, sp.pos1, sp.pos2, sp.batch);
                    point.restoreFromSerializable(sp);
                    capturePoints.put(sp.name, point);
                }
            }
            
            ModLogger.info("客户端据点数据已更新，共 " + capturePoints.size() + " 个据点");
        } catch (Exception e) {
            ModLogger.error("客户端据点数据更新失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    

    
    /**
     * 同步玩家位置到客户端
     */
    private void syncPlayerPositions() {
        MinecraftServer server = ESPointsMod.getServer();
        if (server == null) {
            return;
        }
        
        // 收集所有在线玩家的位置
        Map<UUID, com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition> positions = new HashMap<>();
        
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerUUID = player.getUUID();
            String playerName = player.getName().getString();
            String teamName = Optional.ofNullable(EspetroTeamBridge.getServerPlayerTeam(player)).orElse("");
            syncMapDataIfPlayerTeamChanged(player, teamName);
            syncEspetroBastionsToPlayer(player);

            PlayerMetadata metadata = new PlayerMetadata(playerName, teamName);
            boolean includeMetadata = !metadata.equals(lastBroadcastPlayerMetadata.put(playerUUID, metadata));
            
            // 创建玩家位置对象
            com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition pos = includeMetadata
                ? new com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition(
                    player.getX(), player.getY(), player.getZ(), playerName, teamName, player.getYRot())
                : com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition.positionOnly(
                    player.getX(), player.getY(), player.getZ(), player.getYRot());
            
            positions.put(playerUUID, pos);
        }

        lastBroadcastPlayerMetadata.keySet().retainAll(positions.keySet());
        
        // 发送到所有客户端
        com.example.espoints.network.SyncPlayerPositionsMessage.broadcastToAll(positions);
    }
    
    /**
     * 向指定玩家同步所有在线玩家的位置数据
     * 用于新玩家登录时立即获取其他玩家位置
     * @param player 目标玩家
     */
    private void syncPlayerPositionsToPlayer(ServerPlayer player) {
        MinecraftServer server = ESPointsMod.getServer();
        if (server == null) {
            return;
        }
        
        // 收集所有在线玩家的位置
        Map<UUID, com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition> positions = new HashMap<>();
        
        for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
            UUID playerUUID = onlinePlayer.getUUID();
            String playerName = onlinePlayer.getName().getString();
            String teamName = Optional.ofNullable(EspetroTeamBridge.getServerPlayerTeam(onlinePlayer)).orElse("");
            
            // 创建玩家位置对象
            com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition pos = new com.example.espoints.network.SyncPlayerPositionsMessage.PlayerPosition(
                onlinePlayer.getX(),
                onlinePlayer.getY(),
                onlinePlayer.getZ(),
                playerName,
                teamName,
                onlinePlayer.getYRot()
            );
            
            positions.put(playerUUID, pos);
        }
        
        // 向指定玩家发送位置数据
        com.example.espoints.network.NetworkHandler.INSTANCE.send(
            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
            new com.example.espoints.network.SyncPlayerPositionsMessage(positions)
        );
        
        ModLogger.info("已向玩家 " + player.getName().getString() + " 发送所有在线玩家位置数据");
    }

    private void syncEspetroBastionsToPlayer(ServerPlayer player) {
        List<SyncBastionsMessage.BastionInfo> bastions = getVisibleEspetroBastions(player);
        List<SyncBastionsMessage.BaseInfo> bases = getVisibleEspetroBases(player);
        List<SyncBastionsMessage.VehicleSupplyStationInfo> vehicleSupplyStations =
            getVisibleEspetroVehicleSupplyStations(player);
        BastionSyncState previous = lastBastionSyncByPlayer.get(player.getUUID());
        if (previous != null
                && previous.bastions().equals(bastions)
                && previous.bases().equals(bases)
                && previous.vehicleSupplyStations().equals(vehicleSupplyStations)) {
            return;
        }

        lastBastionSyncByPlayer.put(player.getUUID(),
            new BastionSyncState(
                List.copyOf(bastions),
                List.copyOf(bases),
                List.copyOf(vehicleSupplyStations)));
        SyncBastionsMessage.sendToPlayer(player, bastions, bases, vehicleSupplyStations);
    }

    private List<SyncBastionsMessage.BastionInfo> getVisibleEspetroBastions(ServerPlayer player) {
        List<SyncBastionsMessage.BastionInfo> visibleBastions = new ArrayList<>();
        if (!ModList.get().isLoaded(ESPETRO_MOD_ID)) {
            return visibleBastions;
        }

        try {
            String visibleTeam = getVisibleEspetroBastionTeam(player);
            if (visibleTeam == null || visibleTeam.isEmpty()) {
                return visibleBastions;
            }

            Class<?> managerClass = Class.forName(ESPETRO_BASTION_MANAGER_CLASS);
            Object manager = managerClass.getMethod("getInstance").invoke(null);
            Method getTeamBastionsMethod = managerClass.getMethod("getTeamBastions", String.class);
            Iterable<?> bastions = (Iterable<?>) getTeamBastionsMethod.invoke(manager, visibleTeam);

            for (Object bastion : bastions) {
                if (bastion == null) {
                    continue;
                }

                if (!isEspetroBastionActive(bastion)) {
                    continue;
                }

                BlockPos pos = getEspetroBastionPosition(managerClass, manager, bastion);
                if (pos == null) {
                    continue;
                }

                String bastionTeam = (String) bastion.getClass().getMethod("getTeam").invoke(bastion);
                if (!visibleTeam.equals(bastionTeam)) {
                    continue;
                }

                String name = (String) bastion.getClass().getMethod("getName").invoke(bastion);
                visibleBastions.add(new SyncBastionsMessage.BastionInfo(name, bastionTeam, pos));
            }
        } catch (Exception e) {
            ModLogger.warn("同步 Espetro 兵站信息失败，已跳过: " + e.getMessage());
        }

        return visibleBastions;
    }

    private List<SyncBastionsMessage.BaseInfo> getVisibleEspetroBases(ServerPlayer player) {
        List<SyncBastionsMessage.BaseInfo> bases = new ArrayList<>();
        if (!ModList.get().isLoaded(ESPETRO_MOD_ID)) {
            return bases;
        }

        try {
            String visibleTeam = getVisibleEspetroBastionTeam(player);
            if (visibleTeam == null || visibleTeam.isEmpty()) {
                return bases;
            }

            Class<?> spawnPointConfigClass = Class.forName(ESPETRO_SPAWN_POINT_CONFIG_CLASS);
            Method getAllSpawnPointsMethod = spawnPointConfigClass.getMethod("getAllSpawnPoints");
            Object result = getAllSpawnPointsMethod.invoke(null);
            if (!(result instanceof Map<?, ?> spawnPoints)) {
                return bases;
            }

            for (Map.Entry<?, ?> entry : spawnPoints.entrySet()) {
                String team = EspetroTeamBridge.canonicalizeTeamName(String.valueOf(entry.getKey()));
                if (team == null) {
                    continue;
                }
                if (!visibleTeam.equals(team)) {
                    continue;
                }

                Object spawnPoint = entry.getValue();
                if (spawnPoint == null) {
                    continue;
                }

                double x = readDoubleField(spawnPoint, "x");
                double y = readDoubleField(spawnPoint, "y");
                double z = readDoubleField(spawnPoint, "z");
                float yaw = readFloatField(spawnPoint, "yaw");
                BlockPos pos = BlockPos.containing(x, y, z);
                bases.add(new SyncBastionsMessage.BaseInfo(
                    EspetroTeamBridge.displayName(team) + "主基地",
                    team,
                    pos,
                    yaw
                ));
            }
        } catch (Exception e) {
            ModLogger.warn("同步 Espetro 主基地信息失败，已跳过: " + e.getMessage());
        }

        return bases;
    }

    private List<SyncBastionsMessage.VehicleSupplyStationInfo> getVisibleEspetroVehicleSupplyStations(ServerPlayer player) {
        List<SyncBastionsMessage.VehicleSupplyStationInfo> stations = new ArrayList<>();
        if (!ModList.get().isLoaded(ESPETRO_MOD_ID)) {
            return stations;
        }

        try {
            String visibleTeam = getVisibleEspetroBastionTeam(player);
            if (visibleTeam == null || visibleTeam.isEmpty()) {
                return stations;
            }

            for (SyncBastionsMessage.VehicleSupplyStationInfo station
                    : getAllEspetroVehicleSupplyStations(player.getServer())) {
                if (visibleTeam.equals(station.getTeam())) {
                    stations.add(station);
                }
            }
        } catch (Exception e) {
            ModLogger.warn("同步 Espetro 载具补给站信息失败，已跳过: " + e.getMessage());
        }

        return stations;
    }

    private List<SyncBastionsMessage.VehicleSupplyStationInfo> getAllEspetroVehicleSupplyStations(MinecraftServer server) {
        if (server == null) {
            cachedEspetroVehicleSupplyStations = List.of();
            cachedEspetroVehicleSupplyStationTick = Long.MIN_VALUE;
            return cachedEspetroVehicleSupplyStations;
        }

        long currentTick = server.getTickCount();
        if (cachedEspetroVehicleSupplyStationTick == currentTick) {
            return cachedEspetroVehicleSupplyStations;
        }

        Map<String, SyncBastionsMessage.VehicleSupplyStationInfo> stationsById = new LinkedHashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity == null
                        || entity.isRemoved()
                        || !isEspetroVehicleSupplyStationEntity(entity)) {
                    continue;
                }

                String stationTeam = getEspetroVehicleSupplyStationTeam(entity);
                if (stationTeam == null) {
                    continue;
                }

                BlockPos pos = getEspetroVehicleSupplyStationPosition(entity);
                String stationId = getEspetroVehicleSupplyStationId(entity);
                String groupKey = stationId == null || stationId.isBlank()
                    ? stationTeam + ":" + pos.asLong()
                    : stationId;
                stationsById.putIfAbsent(groupKey, new SyncBastionsMessage.VehicleSupplyStationInfo(
                    getEspetroVehicleSupplyStationName(entity),
                    stationTeam,
                    pos
                ));
            }
        }

        List<SyncBastionsMessage.VehicleSupplyStationInfo> stations = new ArrayList<>(stationsById.values());
        stations.sort(Comparator
            .comparing(SyncBastionsMessage.VehicleSupplyStationInfo::getName)
            .thenComparingInt(info -> info.getPos().getX())
            .thenComparingInt(info -> info.getPos().getZ()));
        cachedEspetroVehicleSupplyStations = List.copyOf(stations);
        cachedEspetroVehicleSupplyStationTick = currentTick;
        return cachedEspetroVehicleSupplyStations;
    }

    private boolean isEspetroVehicleSupplyStationEntity(Entity entity) {
        if (entity.getTags().contains(ESPETRO_VEHICLE_SUPPLY_STATION_TAG)) {
            return true;
        }

        CompoundTag data = entity.getPersistentData();
        if (data.contains(ESPETRO_VEHICLE_SUPPLY_STATION_TEAM_KEY)
                || data.contains(ESPETRO_VEHICLE_SUPPLY_STATION_ID_KEY)
                || data.contains(ESPETRO_VEHICLE_SUPPLY_STATION_X_KEY)
                || data.contains(ESPETRO_VEHICLE_SUPPLY_STATION_Y_KEY)
                || data.contains(ESPETRO_VEHICLE_SUPPLY_STATION_Z_KEY)) {
            return true;
        }

        Component customName = entity.getCustomName();
        return customName != null && customName.getString().contains("载具补给站");
    }

    private String getEspetroVehicleSupplyStationTeam(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        String team = EspetroTeamBridge.canonicalizeTeamName(
            data.getString(ESPETRO_VEHICLE_SUPPLY_STATION_TEAM_KEY));
        if (team != null) {
            return team;
        }

        for (String tag : entity.getTags()) {
            String fromSupplyPrefix = readTeamSuffix(tag, ESPETRO_VEHICLE_SUPPLY_STATION_TEAM_KEY + "_");
            if (fromSupplyPrefix != null) {
                return fromSupplyPrefix;
            }

            String fromTeamPrefix = readTeamSuffix(tag, "espetro_team_");
            if (fromTeamPrefix != null) {
                return fromTeamPrefix;
            }
        }

        return inferEspetroVehicleSupplyStationTeam(getEspetroVehicleSupplyStationPosition(entity));
    }

    private String inferEspetroVehicleSupplyStationTeam(BlockPos stationPos) {
        String nearestBaseTeam = findNearestEspetroBaseTeam(stationPos);
        if (nearestBaseTeam != null) {
            return nearestBaseTeam;
        }
        return findNearestEspetroBastionTeam(stationPos);
    }

    private String findNearestEspetroBaseTeam(BlockPos stationPos) {
        try {
            Class<?> spawnPointConfigClass = Class.forName(ESPETRO_SPAWN_POINT_CONFIG_CLASS);
            Method getAllSpawnPointsMethod = spawnPointConfigClass.getMethod("getAllSpawnPoints");
            Object result = getAllSpawnPointsMethod.invoke(null);
            if (!(result instanceof Map<?, ?> spawnPoints)) {
                return null;
            }

            String nearestTeam = null;
            double nearestDistanceSquared = Double.MAX_VALUE;
            for (Map.Entry<?, ?> entry : spawnPoints.entrySet()) {
                String team = EspetroTeamBridge.canonicalizeTeamName(String.valueOf(entry.getKey()));
                Object spawnPoint = entry.getValue();
                if (team == null || spawnPoint == null) {
                    continue;
                }

                BlockPos basePos = BlockPos.containing(
                    readDoubleField(spawnPoint, "x"),
                    readDoubleField(spawnPoint, "y"),
                    readDoubleField(spawnPoint, "z")
                );
                double distanceSquared = horizontalDistanceSquared(stationPos, basePos);
                if (distanceSquared < nearestDistanceSquared) {
                    nearestDistanceSquared = distanceSquared;
                    nearestTeam = team;
                }
            }
            return nearestTeam;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String findNearestEspetroBastionTeam(BlockPos stationPos) {
        try {
            Class<?> managerClass = Class.forName(ESPETRO_BASTION_MANAGER_CLASS);
            Object manager = managerClass.getMethod("getInstance").invoke(null);
            Method getTeamBastionsMethod = managerClass.getMethod("getTeamBastions", String.class);

            String nearestTeam = null;
            double nearestDistanceSquared = Double.MAX_VALUE;
            for (String candidateTeam : List.of("ATTACK", "DEFEND")) {
                Object result = getTeamBastionsMethod.invoke(manager, candidateTeam);
                if (!(result instanceof Iterable<?> bastions)) {
                    continue;
                }

                for (Object bastion : bastions) {
                    if (bastion == null || !isEspetroBastionActive(bastion)) {
                        continue;
                    }

                    BlockPos bastionPos = getEspetroBastionPosition(managerClass, manager, bastion);
                    if (bastionPos == null) {
                        continue;
                    }

                    String team = EspetroTeamBridge.canonicalizeTeamName(
                        String.valueOf(bastion.getClass().getMethod("getTeam").invoke(bastion)));
                    if (team == null) {
                        team = candidateTeam;
                    }

                    double distanceSquared = horizontalDistanceSquared(stationPos, bastionPos);
                    if (distanceSquared < nearestDistanceSquared) {
                        nearestDistanceSquared = distanceSquared;
                        nearestTeam = team;
                    }
                }
            }
            return nearestTeam;
        } catch (Exception ignored) {
            return null;
        }
    }

    private double horizontalDistanceSquared(BlockPos first, BlockPos second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private String readTeamSuffix(String tag, String prefix) {
        if (tag == null || !tag.startsWith(prefix)) {
            return null;
        }
        return EspetroTeamBridge.canonicalizeTeamName(tag.substring(prefix.length()));
    }

    private String getEspetroVehicleSupplyStationId(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        if (data.hasUUID(ESPETRO_VEHICLE_SUPPLY_STATION_ID_KEY)) {
            return data.getUUID(ESPETRO_VEHICLE_SUPPLY_STATION_ID_KEY).toString();
        }

        String stringId = data.getString(ESPETRO_VEHICLE_SUPPLY_STATION_ID_KEY);
        if (!stringId.isBlank()) {
            return stringId;
        }

        String tagPrefix = ESPETRO_VEHICLE_SUPPLY_STATION_ID_KEY + "_";
        for (String tag : entity.getTags()) {
            if (tag != null && tag.startsWith(tagPrefix)) {
                return tag.substring(tagPrefix.length());
            }
        }
        return null;
    }

    private BlockPos getEspetroVehicleSupplyStationPosition(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        if (data.contains(ESPETRO_VEHICLE_SUPPLY_STATION_X_KEY)
                && data.contains(ESPETRO_VEHICLE_SUPPLY_STATION_Y_KEY)
                && data.contains(ESPETRO_VEHICLE_SUPPLY_STATION_Z_KEY)) {
            return new BlockPos(
                data.getInt(ESPETRO_VEHICLE_SUPPLY_STATION_X_KEY),
                data.getInt(ESPETRO_VEHICLE_SUPPLY_STATION_Y_KEY),
                data.getInt(ESPETRO_VEHICLE_SUPPLY_STATION_Z_KEY)
            );
        }

        return entity.blockPosition();
    }

    private String getEspetroVehicleSupplyStationName(Entity entity) {
        Component customName = entity.getCustomName();
        if (customName != null && !customName.getString().isBlank()) {
            return customName.getString();
        }
        return "载具补给站";
    }

    private double readDoubleField(Object target, String fieldName) throws ReflectiveOperationException {
        Object value = target.getClass().getField(fieldName).get(target);
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }

    private float readFloatField(Object target, String fieldName) throws ReflectiveOperationException {
        Object value = target.getClass().getField(fieldName).get(target);
        return value instanceof Number number ? number.floatValue() : 0.0F;
    }

    private String getVisibleEspetroBastionTeam(ServerPlayer player) throws ReflectiveOperationException {
        return EspetroTeamBridge.getServerPlayerTeam(player);
    }

    private String getEspetroBastionTeamFromHcrTeamName(String teamName) {
        return EspetroTeamBridge.canonicalizeTeamName(teamName);
    }

    private boolean isEspetroBastionActive(Object bastion) throws ReflectiveOperationException {
        try {
            Method method = bastion.getClass().getMethod("isActive");
            Object result = method.invoke(bastion);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (NoSuchMethodException e) {
            return true;
        }
    }

    private BlockPos getEspetroBastionPosition(Class<?> managerClass, Object manager, Object bastion) throws ReflectiveOperationException {
        Method recordedPositionMethod = findEspetroBastionPositionMethod(managerClass, bastion.getClass());
        if (recordedPositionMethod != null) {
            Object result = recordedPositionMethod.invoke(manager, bastion);
            if (result instanceof BlockPos) {
                return (BlockPos) result;
            }
        }

        BlockPos armorStandPosition = invokeOptionalBlockPosGetter(bastion, "getArmorStandPosition");
        if (armorStandPosition != null) {
            return armorStandPosition;
        }

        BlockPos basePosition = invokeOptionalBlockPosGetter(bastion, "getPosition");
        return basePosition != null ? basePosition.above() : null;
    }

    private Method findEspetroBastionPositionMethod(Class<?> managerClass, Class<?> bastionClass) {
        for (Method method : managerClass.getMethods()) {
            if (!"getRecordedArmorStandPosition".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            if (parameterType.isAssignableFrom(bastionClass)) {
                return method;
            }
        }

        return null;
    }

    private BlockPos invokeOptionalBlockPosGetter(Object target, String methodName) throws ReflectiveOperationException {
        Method method;
        try {
            method = target.getClass().getMethod(methodName);
        } catch (NoSuchMethodException e) {
            return null;
        }

        Object result = method.invoke(target);
        return result instanceof BlockPos ? (BlockPos) result : null;
    }

    /**
     * 处理玩家登录事件
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            playerNameMap.put(player.getUUID(), player.getName().getString());
            String playerTeam = Optional.ofNullable(EspetroTeamBridge.getServerPlayerTeam(player)).orElse("");
            playerTeamNameMap.put(player.getUUID(), playerTeam);
            
            // 向新登录的玩家同步配置
            com.example.espoints.network.SyncConfigMessage.sendToPlayer(player);
            
            // 向新登录的玩家同步地图玩家显示配置
            com.example.espoints.network.SyncMapPlayerDisplayMessage.sendToPlayer(player);

            // 向新登录的玩家同步数据包驱动的战术地图配置。
            com.example.espoints.network.SyncTacticalMapConfigMessage.sendToPlayer(player);
            com.example.espoints.network.SyncTacticalMapBackgroundMessage.sendToPlayer(player);
            
            // 向新登录的玩家同步据点数据
            syncToClient(player);
            com.example.espoints.tactical.TacticalMarkerManager.sendTo(player);
            
            // 立即向新登录的玩家同步所有玩家位置数据，解决中途加入玩家无法看到其他玩家的问题
            syncPlayerPositionsToPlayer(player);
            
            // 立即向新登录的玩家同步行动模式状态，确保新玩家获得最新的行动模式信息
            com.example.espoints.network.SyncOperationModeMessage.sendToPlayer(
                player,
                operationModeRunning,
                currentBatch,
                totalBatches,
                endBehavior,
                teamRoles,
                teamReinforcements,
                teamInitialReinforcements
            );
            
            // 不在 Espetro 攻防阵营中的玩家视为观战者。
            if (!isPlayerInTeam(player)) {
                ModLogger.info("玩家 " + player.getName().getString() + " 未加入 Espetro 阵营，被视为观战者");
            }
        }
    }
    
    /**
     * 处理玩家登出事件
     * @param event 玩家登出事件
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        playerNameMap.remove(player.getUUID());
        playerTeamNameMap.remove(player.getUUID());
        playerEnterTime.remove(player.getUUID());
        lastBroadcastPlayerMetadata.remove(player.getUUID());
        lastBastionSyncByPlayer.remove(player.getUUID());
    }
    
    /**
     * 处理玩家复活事件
     * @param event 玩家复活事件
     */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        playerNameMap.put(player.getUUID(), player.getName().getString());
        playerTeamNameMap.put(player.getUUID(), Optional.ofNullable(EspetroTeamBridge.getPlayerTeam(player)).orElse(""));
    }

    private void syncMapDataIfPlayerTeamChanged(ServerPlayer player, String currentTeamName) {
        UUID playerUUID = player.getUUID();
        String previousTeamName = playerTeamNameMap.put(playerUUID, currentTeamName);
        if (previousTeamName != null && !previousTeamName.equals(currentTeamName)) {
            ModLogger.info("玩家 " + player.getName().getString() + " 队伍从 " + previousTeamName + " 变更为 " + currentTeamName + "，重新同步战术地图据点视野");
            syncToClient(player);
            syncPlayerPositionsToPlayer(player);
            syncEspetroBastionsToPlayer(player);
            com.example.espoints.tactical.TacticalMarkerManager.sendTo(player);
        }
    }
    
    /**
     * 处理服务器Tick事件
     * @param event Tick事件
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.side != LogicalSide.SERVER || event.phase != TickEvent.Phase.END) {
            return;
        }

        com.example.espoints.tactical.TacticalMarkerManager.tick();

        updateCapturePointsForEspetroDeploying();

        // 每隔CHECK_INTERVAL tick检查一次据点状态
        if (++tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            
            // 获取服务器实例
            MinecraftServer server = ESPointsMod.getServer();
            if (server != null) {
                ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
                if (overworld != null) {
                    updateAllCapturePoints(overworld);
                }
            }
        }
        
        // 每20tick（1秒）同步一次玩家位置和战术地图兵站信息
        if (++playerPositionSyncTimer >= PLAYER_POSITION_SYNC_INTERVAL) {
            playerPositionSyncTimer = 0;
            syncPlayerPositions();
        }
    }

    private void updateCapturePointsForEspetroDeploying() {
        if (plannedPointsMap.isEmpty() || !ModList.get().isLoaded(ESPETRO_MOD_ID)) {
            return;
        }

        String phaseName = getEspetroCurrentPhaseName();
        if ("WAITING_FOR_PLAYERS".equals(phaseName)) {
            espetroDeployingCapturePointsActivated = false;
            if (!tacticalMarkersClearedForWaiting) {
                com.example.espoints.tactical.TacticalMarkerManager.reset();
                tacticalMarkersClearedForWaiting = true;
            }
            return;
        }

        tacticalMarkersClearedForWaiting = false;

        if (!"DEPLOYING".equals(phaseName)) {
            return;
        }

        if (espetroDeployingCapturePointsActivated) {
            if (operationModeRunning && capturePoints.isEmpty()) {
                createCurrentBatchPoints();
                syncOperationModeToClients();
            }
            return;
        }

        espetroDeployingCapturePointsActivated = true;
        if (!operationModeRunning) {
            int plannedTotalBatches = calculateTotalBatches();
            int detectedTotalBatches = Math.max(totalBatches, plannedTotalBatches);
            if (detectedTotalBatches <= 0) {
                return;
            }
            startOperationMode(detectedTotalBatches, endBehavior == null || endBehavior.isEmpty() ? "terminate" : endBehavior);
            ModLogger.info("检测到 Espetro 进入部署阶段，已按 " + detectedTotalBatches + " 个批次自动显示当前批次据点");
        } else if (capturePoints.isEmpty()) {
            createCurrentBatchPoints();
            syncOperationModeToClients();
            ModLogger.info("检测到 Espetro 进入部署阶段，已同步当前批次据点");
        }
    }

    private String getEspetroCurrentPhaseName() {
        try {
            Class<?> managerClass = Class.forName(ESPETRO_GAME_STATE_MANAGER_CLASS);
            Object manager = managerClass.getMethod("getInstance").invoke(null);
            Object phase = managerClass.getMethod("getCurrentPhase").invoke(manager);
            return phase instanceof Enum<?> enumPhase ? enumPhase.name() : String.valueOf(phase);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 处理玩家死亡事件，给予击杀者奖励或友军击杀惩罚，并扣除死亡玩家所在队伍的兵力
     */
    @SubscribeEvent
    public void onPlayerDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        // 检查是否是玩家死亡
        if (event.getEntity() instanceof ServerPlayer victim) {
            // [已禁用] 兵力扣除：玩家死亡不再扣减队伍兵力
            /*
            // 检查行动模式是否正在运行
            if (isOperationModeRunning()) {
                // 获取死亡玩家所在的队伍
                Team victimTeam = victim.getTeam();
                if (victimTeam != null) {
                    // 扣除死亡玩家所在队伍的一点兵力
                    deductTeamReinforcements(victimTeam.getName(), 1);
                }
            }
            */
            
            // 检查是否是玩家被玩家击杀
            if (event.getSource().getEntity() instanceof ServerPlayer killer) {
                // 确保受害者和击杀者不是同一个人
                if (!victim.getUUID().equals(killer.getUUID())) {
                    // 检查是否是友军击杀
                    if (isFriendlyFire(killer, victim)) {
                        // 友军击杀，扣除点数
                        handleFriendlyFire(killer, victim);
                    } else {
                        // 正常击杀，给予奖励
                        int rewardAmount = ModConfig.killRewardAmount.get();
                        addPointsToPlayer(killer, rewardAmount, "击杀玩家：" + victim.getName().getString());
                        
                        ModLogger.info("玩家 " + killer.getName().getString() + " 击杀了 " + victim.getName().getString() + "，获得了 " + rewardAmount + " 点数");
                    }
                }
            }
        }
    }
    
    /**
     * 检查是否是友军击杀
     * @param killer 击杀者
     * @param victim 受害者
     * @return 如果是友军击杀返回true，否则返回false
     */
    private boolean isFriendlyFire(ServerPlayer killer, ServerPlayer victim) {
        return EspetroTeamBridge.isSameTeam(
                EspetroTeamBridge.getServerPlayerTeam(killer),
                EspetroTeamBridge.getServerPlayerTeam(victim)
        );
    }
    
    /**
     * 处理友军击杀，扣除点数
     * @param killer 击杀者
     * @param victim 受害者
     */
    private void handleFriendlyFire(ServerPlayer killer, ServerPlayer victim) {
        // 检查是否启用友军击杀惩罚
        if (!ModConfig.enableFriendlyFirePenalty.get()) {
            return;
        }
        
        // 获取惩罚点数
        int penaltyAmount = ModConfig.friendlyFirePenalty.get();
        
        // 使用API扣除点数
        removePointsFromPlayer(killer, penaltyAmount, "友军击杀：" + victim.getName().getString());
        
        ModLogger.info("玩家 " + killer.getName().getString() + " 击杀了友军 " + victim.getName().getString() + "，扣除了 " + penaltyAmount + " 点数");
    }
    
    /**
     * 使用反射为玩家扣除点数，带自定义原因
     * @param player 目标玩家
     * @param points 要扣除的点数
     * @param reason 扣分原因
     */
    private void removePointsFromPlayer(ServerPlayer player, int points, String reason) {
        // 验证参数
        if (player == null || points <= 0) {
            ModLogger.warn("无效的参数：player=" + player + ", points=" + points);
            return;
        }
        
        // 确保只在服务器端调用
        if (player.level().isClientSide()) {
            ModLogger.warn("尝试在客户端调用PlayerPointsAPI，这不会生效");
            return;
        }
        
        try {
            // 使用反射调用PlayerPointsAPI.removePoints方法
            Class<?> apiClass = Class.forName("com.hcrzb.hcrzbshop.api.PlayerPointsAPI");
            
            // 调用不带reason参数的removePoints方法，根据API文档，这是唯一的removePoints方法
            Method removePointsMethod = apiClass.getMethod("removePoints", Player.class, int.class);
            boolean success = (Boolean) removePointsMethod.invoke(null, player, points);
            
            if (success) {
                // 手动发送系统消息给玩家，告知扣分原因
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("你因为" + reason + "被扣了" + points + "点！"));
                ModLogger.info("为玩家 " + player.getName().getString() + " 扣除了 " + points + " 点数，原因：" + reason);
            } else {
                ModLogger.warn("为玩家 " + player.getName().getString() + " 扣除点数失败，原因：" + reason);
            }
        } catch (ClassNotFoundException e) {
            ModLogger.warn("未找到HCRZBShop API类，确认已安装HCR ZB Shop模组");
        } catch (NoSuchMethodException e) {
            ModLogger.warn("未找到PlayerPointsAPI.removePoints方法: " + e.getMessage());
        } catch (Exception e) {
            ModLogger.warn("无法调用PlayerPointsAPI.removePoints: " + e.getMessage());
        }
    }
    
    /**
     * 同步据点数据到指定客户端
     * @param player 目标玩家
     */
    public void syncToClient(ServerPlayer player) {
        // 确保只在服务器端调用
        if (!ESPointsMod.isServerRunning()) {
            ModLogger.warn("尝试在客户端调用服务器同步方法，忽略");
            return;
        }
        
        try {
            SyncCapturePointsMessage.sendToPlayer(player, getSerializablePointsForMap(player));
            com.example.espoints.network.SyncCapturePointOverviewMessage.sendToPlayer(player, getOverviewSerializablePoints());
        } catch (Exception e) {
            ModLogger.error("同步据点数据到客户端时发生异常: " + e.getMessage());
        }
    }
    
    /**
     * 获取玩家名称
     * @param playerUUID 玩家UUID
     * @return 玩家名称
     */
    public String getPlayerName(UUID playerUUID) {
        return playerNameMap.getOrDefault(playerUUID, "Unknown");
    }
    
    /**
     * 给予占领据点奖励
     * @param server 服务器实例
     * @param captorName 占领者名称（队伍名称或玩家名称）
     * @param pointName 据点名称
     */
    private void giveCaptureReward(MinecraftServer server, String captorName, String pointName) {
        if (server == null || captorName == null || captorName.isEmpty()) {
            return;
        }
        
        int rewardAmount = ModConfig.captureRewardAmount.get();
        String canonicalCaptor = EspetroTeamBridge.canonicalizeTeamName(captorName);
        if (canonicalCaptor == null) {
            return;
        }
        
        // 向符合条件的玩家发放奖励
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // 只有非观众玩家才能获得奖励
            if (!isPlayerInTeam(player)) {
                continue; // 观众玩家，跳过
            }
            if (canonicalCaptor.equals(EspetroTeamBridge.getServerPlayerTeam(player))) {
                // 给予玩家奖励
                addPointsToPlayer(player, rewardAmount, "占领据点：" + pointName);
            }
        }
    }
    
    /**
     * 检查玩家是否在队伍中
     * @param player 玩家对象
     * @return 是否在队伍中
     */
    private boolean isPlayerInTeam(ServerPlayer player) {
        return EspetroTeamBridge.isEspetroTeamPlayer(player);
    }
    
    /**
     * 给予持续占领据点奖励
     * @param server 服务器实例
     * @param captorName 占领者名称（队伍名称或玩家名称）
     * @param pointName 据点名称
     */
    private void giveCapturedReward(MinecraftServer server, String captorName, String pointName) {
        if (server == null || captorName == null || captorName.isEmpty()) {
            return;
        }
        
        int rewardAmount = ModConfig.capturedRewardAmount.get();
        String canonicalCaptor = EspetroTeamBridge.canonicalizeTeamName(captorName);
        if (canonicalCaptor == null) {
            return;
        }
        
        // 向符合条件的玩家发放奖励
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // 只有非观众玩家才能获得奖励
            if (!isPlayerInTeam(player)) {
                continue; // 观众玩家，跳过
            }
            if (canonicalCaptor.equals(EspetroTeamBridge.getServerPlayerTeam(player))) {
                // 给予玩家奖励
                addPointsToPlayer(player, rewardAmount, "持续占领据点：" + pointName);
            }
        }
    }
    
    /**
     * 使用反射为玩家添加点数
     * @param player 目标玩家
     * @param points 要添加的点数
     */
    private void addPointsToPlayer(ServerPlayer player, int points) {
        addPointsToPlayer(player, points, "API调用");
    }
    
    /**
     * 使用反射为玩家添加点数，带自定义原因
     * @param player 目标玩家
     * @param points 要添加的点数
     * @param reason 加点原因
     */
    private void addPointsToPlayer(ServerPlayer player, int points, String reason) {
        // 验证参数
        if (player == null || points <= 0) {
            ModLogger.warn("无效的参数：player=" + player + ", points=" + points);
            return;
        }
        
        // 确保只在服务器端调用
        if (player.level().isClientSide()) {
            ModLogger.warn("尝试在客户端调用PlayerPointsAPI，这不会生效");
            return;
        }
        
        try {
            // 使用反射调用PlayerPointsAPI.addPoints方法
            Class<?> apiClass = Class.forName("com.hcrzb.hcrzbshop.api.PlayerPointsAPI");
            try {
                // 尝试调用带reason参数的方法
                Method addPointsMethod = apiClass.getMethod("addPoints", Player.class, int.class, String.class);
                boolean success = (Boolean) addPointsMethod.invoke(null, player, points, reason);
                if (success) {
                    ModLogger.info("为玩家 " + player.getName().getString() + " 添加了 " + points + " 点数，原因：" + reason);
                } else {
                    ModLogger.warn("为玩家 " + player.getName().getString() + " 添加点数失败，原因：" + reason);
                }
            } catch (NoSuchMethodException e) {
                // 如果没有带reason参数的方法，使用不带reason的方法
                Method addPointsMethod = apiClass.getMethod("addPoints", Player.class, int.class);
                boolean success = (Boolean) addPointsMethod.invoke(null, player, points);
                if (success) {
                    ModLogger.info("为玩家 " + player.getName().getString() + " 添加了 " + points + " 点数");
                } else {
                    ModLogger.warn("为玩家 " + player.getName().getString() + " 添加点数失败");
                }
            }
        } catch (ClassNotFoundException e) {
            ModLogger.warn("未找到HCRZBShop API类，确认已安装HCR ZB Shop模组");
        } catch (Exception e) {
            ModLogger.warn("无法调用PlayerPointsAPI.addPoints: " + e.getMessage());
        }
    }
    
    
}
