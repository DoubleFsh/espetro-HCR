package com.example.espoints.capturepoint;

import com.example.espoints.util.EspetroTeamBridge;
import com.example.espoints.util.ModLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/**
 * 据点类 - 表示一个据点区域及其状态
 * 实现了可序列化接口，用于网络传输和持久化存储
 */
public class CapturePoint {
    // 据点基本信息
    private final String name;           // 据点名称
    private final BlockPos pos1;         // 第一个坐标点
    private final BlockPos pos2;         // 第二个坐标点
    private final int batch;             // 据点所属批次
    
    // 据点状态信息
    private CaptureState state;          // 当前核心状态
    private DisplayState displayState;   // 当前显示状态
    private String captorName;           // 占领者名称
    private int progress;                // 占领进度 (0-100)
    private double preciseProgress;      // 按 elapsed tick 积分，避免短检查间隔加速
    private long capturedStateStartTime; // 已占领状态开始时间（用于进度恢复）
    
    // 边界坐标（预计算以提高性能）
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    
    /**
     * 构造函数 - 创建一个新的据点
     * @param name 据点名称
     * @param pos1 第一个坐标点
     * @param pos2 第二个坐标点
     */
    public CapturePoint(String name, BlockPos pos1, BlockPos pos2) {
        this(name, pos1, pos2, 1); // 默认批次为1
    }
    
    /**
     * 构造函数 - 创建一个新的据点，带批次信息
     * @param name 据点名称
     * @param pos1 第一个坐标点
     * @param pos2 第二个坐标点
     * @param batch 据点所属批次
     */
    public CapturePoint(String name, BlockPos pos1, BlockPos pos2, int batch) {
        this.name = name;
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.batch = batch;
        
        // 计算边界坐标
        this.minX = Math.min(pos1.getX(), pos2.getX());
        this.maxX = Math.max(pos1.getX(), pos2.getX());
        this.minY = Math.min(pos1.getY(), pos2.getY());
        this.maxY = Math.max(pos1.getY(), pos2.getY());
        this.minZ = Math.min(pos1.getZ(), pos2.getZ());
        this.maxZ = Math.max(pos1.getZ(), pos2.getZ());
        
        // 初始化状态
        this.state = CaptureState.NEUTRAL;
        this.displayState = DisplayState.NEUTRAL;
        this.captorName = "";
        this.progress = 0;
        this.preciseProgress = 0.0D;
        this.capturedStateStartTime = 0;
        
        ModLogger.debug("创建据点: " + name + " (批次 " + batch + ") 从 (" + pos1.getX() + "," + pos1.getY() + "," + pos1.getZ() + ") 到 (" + pos2.getX() + "," + pos2.getY() + "," + pos2.getZ() + ")");
        
    }
    
    /**
     * 检查指定位置是否在据点范围内
     * @param pos 要检查的位置
     * @return 如果位置在据点范围内则返回true，否则返回false
     */
    public boolean isPositionInside(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        
        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }
    
    /**
     * 更新据点状态
     * @param playersInPoint 在据点内的玩家列表
     */
    public void updateStatus(List<? extends Player> playersInPoint) {
        updateStatus(playersInPoint, 40);
    }

    /**
     * Updates the state by real elapsed game ticks. Forty ticks preserves the
     * historical five-point step; any configured sampling interval therefore
     * has the same wall-clock capture duration.
     */
    public void updateStatus(List<? extends Player> playersInPoint, int elapsedTicks) {
        if (elapsedTicks < 0) {
            throw new IllegalArgumentException("elapsedTicks must not be negative");
        }
        try {
            ModLogger.debug("更新据点 " + name + " 状态，当前状态: " + state + ", 玩家数量: " + playersInPoint.size());
            
            switch (state) {
                case NEUTRAL:
                    handleNeutralState(playersInPoint, elapsedTicks);
                    break;
                case CAPTURING_FLAG:
                    handleCapturingFlagState(playersInPoint, elapsedTicks);
                    break;
                case CONTESTED:
                    handleContestedState(playersInPoint, elapsedTicks);
                    break;
                case CAPTURING_CONTESTED:
                    handleCapturingContestedState(playersInPoint, elapsedTicks);
                    break;
                case CAPTURING_DOWN:
                    handleCapturingDownState(playersInPoint, elapsedTicks);
                    break;
                case CAPTURED:
                    handleCapturedState(playersInPoint, elapsedTicks);
                    break;
            }
            
            // 更新显示状态
            updateDisplayState(playersInPoint);
            
            ModLogger.debug("据点 " + name + " 更新后状态: " + state + ", 显示状态: " + getDisplayState() + ", 进度: " + progress + ", 占领者: " + captorName);
        } catch (Exception e) {
            ModLogger.error("更新据点状态时发生异常: " + e.getMessage());
        }
    }
    
    /**
     * 处理中立状态
     */
    private void handleNeutralState(List<? extends Player> playersInPoint, int elapsedTicks) {
        Map<String, Integer> teamGroups = getTeamGroups(playersInPoint);
        if (teamGroups.isEmpty()) {
            resetToNeutral("据点 " + name + " 保持中立状态，无阵营玩家在点内");
            return;
        }

        pushNeutralProgressByMajority(teamGroups, elapsedTicks);
    }
    
    /**
     * 处理升旗状态
     */
    private void handleCapturingFlagState(List<? extends Player> playersInPoint,
                                          int elapsedTicks) {
        Map<String, Integer> teamGroups = getTeamGroups(playersInPoint);
        if (teamGroups.isEmpty()) {
            resetToNeutral("据点 " + name + " 玩家离开，回到中立状态");
            return;
        }

        pushNeutralProgressByMajority(teamGroups, elapsedTicks);
    }
    
    /**
     * 处理争夺状态
     */
    private void handleContestedState(List<? extends Player> playersInPoint,
                                      int elapsedTicks) {
        Map<String, Integer> teamGroups = getTeamGroups(playersInPoint);
        if (teamGroups.isEmpty()) {
            // 玩家全部离开，回到已占领状态
            state = CaptureState.CAPTURED;
            capturedStateStartTime = 0; // 重置已占领状态开始时间
            ModLogger.debug("据点 " + name + " 玩家离开，回到已占领状态");
            return;
        }

        pushCapturedProgressByMajority(teamGroups, elapsedTicks);
    }
    
    /**
     * 处理升旗争夺状态
     */
    private void handleCapturingContestedState(List<? extends Player> playersInPoint,
                                               int elapsedTicks) {
        Map<String, Integer> teamGroups = getTeamGroups(playersInPoint);
        if (teamGroups.isEmpty()) {
            resetToNeutral("据点 " + name + " 玩家全部离开，回到中立状态");
            return;
        }

        pushNeutralProgressByMajority(teamGroups, elapsedTicks);
    }
    
    /**
     * 处理降旗状态
     */
    private void handleCapturingDownState(List<? extends Player> playersInPoint,
                                          int elapsedTicks) {
        Map<String, Integer> teamGroups = getTeamGroups(playersInPoint);
        if (teamGroups.isEmpty()) {
            // 玩家全部离开，根据当前进度决定状态
            if (preciseProgress <= 0.0D) {
                resetToNeutral("据点 " + name + " 玩家离开，进度已耗尽，回到中立状态");
            } else {
                // 进度还很高，回到已占领状态
                state = CaptureState.CAPTURED;
                capturedStateStartTime = 0; // 重置已占领状态开始时间
                ModLogger.debug("据点 " + name + " 玩家离开，进度仍高，回到已占领状态");
            }
            return;
        }

        pushCapturedProgressByMajority(teamGroups, elapsedTicks);
    }
    
    /**
     * 处理已占领状态
     */
    private void handleCapturedState(List<? extends Player> playersInPoint,
                                     int elapsedTicks) {
        // 记录已占领状态开始时间
        if (capturedStateStartTime == 0) {
            capturedStateStartTime = System.currentTimeMillis();
        }
        
        Map<String, Integer> teamGroups = getTeamGroups(playersInPoint);
        if (teamGroups.isEmpty()) {
            // 无玩家在场，保持已占领状态
            ModLogger.debug("据点 " + name + " 无玩家在场，保持已占领状态");
        } else {
            pushCapturedProgressByMajority(teamGroups, elapsedTicks);
        }
        
        // 检查进度是否未满，如果未满且已占领状态持续5秒以上，将进度设置为满
        if (state == CaptureState.CAPTURED && progress < 100) {
            long currentTime = System.currentTimeMillis();
            long duration = currentTime - capturedStateStartTime;
            if (duration >= 5000) { // 5秒
                setPreciseProgress(100.0D);
                ModLogger.debug("据点 " + name + " 已占领状态持续5秒，进度自动恢复到100%");
            }
        }
    }

    private void pushNeutralProgressByMajority(Map<String, Integer> teamGroups,
                                               int elapsedTicks) {
        String majorityTeam = getMajorityTeam(teamGroups);
        if (majorityTeam == null) {
            state = CaptureState.CAPTURING_CONTESTED;
            ModLogger.debug("据点 " + name + " 双方人数相同，升旗进度暂停，队伍数量: " + teamGroups.size());
            return;
        }

        if (captorName == null || captorName.isEmpty()) {
            captorName = majorityTeam;
        }

        if (EspetroTeamBridge.isSameTeam(majorityTeam, captorName)) {
            changeProgress(1, elapsedTicks);
            if (preciseProgress >= 100.0D) {
                captureForTeam(majorityTeam);
            } else {
                state = CaptureState.CAPTURING_FLAG;
                ModLogger.debug("据点 " + name + " 多数方 " + majorityTeam + " 推进升旗，进度: " + progress + "，人数: " + teamGroups.get(majorityTeam));
            }
            return;
        }

        changeProgress(-1, elapsedTicks);
        if (preciseProgress <= 0.0D) {
            captorName = majorityTeam;
            state = CaptureState.CAPTURING_FLAG;
            ModLogger.debug("据点 " + name + " 原升旗进度被压制清空，多数方切换为 " + majorityTeam);
        } else {
            state = CaptureState.CAPTURING_CONTESTED;
            ModLogger.debug("据点 " + name + " 多数方 " + majorityTeam + " 正在压制 " + captorName + " 的升旗进度，进度: " + progress);
        }
    }

    private void pushCapturedProgressByMajority(Map<String, Integer> teamGroups,
                                                int elapsedTicks) {
        if (captorName == null || captorName.isEmpty()) {
            pushNeutralProgressByMajority(teamGroups, elapsedTicks);
            return;
        }

        String majorityTeam = getMajorityTeam(teamGroups);
        if (majorityTeam == null) {
            state = CaptureState.CONTESTED;
            capturedStateStartTime = 0;
            ModLogger.debug("据点 " + name + " 双方人数相同，降旗/恢复进度暂停，队伍数量: " + teamGroups.size());
            return;
        }

        if (EspetroTeamBridge.isSameTeam(majorityTeam, captorName)) {
            changeProgress(1, elapsedTicks);
            state = CaptureState.CAPTURED;
            if (preciseProgress >= 100.0D) {
                capturedStateStartTime = 0;
            }
            ModLogger.debug("据点 " + name + " 占领方 " + captorName + " 人数占优，恢复进度: " + progress);
            return;
        }

        changeProgress(-1, elapsedTicks);
        capturedStateStartTime = 0;
        if (preciseProgress <= 0.0D) {
            resetToNeutral("据点 " + name + " 被多数方 " + majorityTeam + " 降旗完成，回到中立状态");
        } else {
            state = CaptureState.CAPTURING_DOWN;
            ModLogger.debug("据点 " + name + " 多数方 " + majorityTeam + " 正在降旗，进度: " + progress + "，占领方: " + captorName);
        }
    }

    private String getMajorityTeam(Map<String, Integer> teamGroups) {
        String majorityTeam = null;
        int majorityCount = 0;
        boolean tied = false;

        for (Map.Entry<String, Integer> entry : teamGroups.entrySet()) {
            int count = entry.getValue();
            if (count > majorityCount) {
                majorityTeam = entry.getKey();
                majorityCount = count;
                tied = false;
            } else if (count == majorityCount) {
                tied = true;
            }
        }

        return majorityCount > 0 && !tied ? majorityTeam : null;
    }

    private void captureForTeam(String teamName) {
        captorName = teamName;
        setPreciseProgress(100.0D);
        state = CaptureState.CAPTURED;
        capturedStateStartTime = 0;
        ModLogger.info("据点 " + name + " 被占领，占领者: " + captorName);
    }

    private void resetToNeutral(String reason) {
        state = CaptureState.NEUTRAL;
        setPreciseProgress(0.0D);
        captorName = "";
        capturedStateStartTime = 0;
        ModLogger.debug(reason);
    }

    private void changeProgress(int direction, int elapsedTicks) {
        setPreciseProgress(CaptureProgressIntegrator.advance(
            preciseProgress, direction, elapsedTicks));
    }

    private void setPreciseProgress(double value) {
        preciseProgress = CaptureProgressIntegrator.clamp(value);
        progress = CaptureProgressIntegrator.display(preciseProgress);
    }
    
    /**
     * 更新显示状态
     */
    private void updateDisplayState(List<? extends Player> playersInPoint) {
        switch (state) {
            case NEUTRAL:
                displayState = DisplayState.NEUTRAL;
                // 确保中立状态下显示状态正确
                captorName = "";
                break;
            case CAPTURING_FLAG:
                if (!playersInPoint.isEmpty()) {
                    displayState = DisplayState.CAPTURING_FLAG_SINGLE;
                    // 不再在这里修改captorName，captorName由核心状态管理
                }
                break;
            case CONTESTED:
                displayState = DisplayState.CONTESTED_MULTI;
                break;
            case CAPTURING_CONTESTED:
                displayState = DisplayState.CAPTURING_CONTESTED_MULTI;
                break;
            case CAPTURING_DOWN:
                displayState = DisplayState.CAPTURING_DOWN;
                break;
            case CAPTURED:
                displayState = DisplayState.CAPTURED;
                break;
        }
    }
    
    /**
     * 根据队伍分组玩家
     * @param playersInPoint 玩家列表
     * @return 按照队伍分组的玩家映射
     */
    private Map<String, Integer> getTeamGroups(List<? extends Player> playersInPoint) {
        Map<String, Integer> teamGroups = new HashMap<>();
        
        for (Player player : playersInPoint) {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                continue;
            }
            String teamName = EspetroTeamBridge.getServerPlayerTeam(serverPlayer);
            if (teamName == null) {
                continue;
            }
            
            teamGroups.merge(teamName, 1, Integer::sum);
        }
        
        return teamGroups;
    }
    
    // Getter和Setter方法
    public String getName() { return name; }
    public BlockPos getPos1() { return pos1; }
    public BlockPos getPos2() { return pos2; }
    public CaptureState getState() { return state; }
    public void setState(CaptureState state) { this.state = state; }
    public DisplayState getDisplayState() { return displayState; }
    public void setDisplayState(DisplayState displayState) { this.displayState = displayState; }
    public String getCaptorName() { return captorName; }
    public void setCaptorName(String captorName) {
        String canonicalTeam = EspetroTeamBridge.canonicalizeTeamName(captorName);
        this.captorName = canonicalTeam != null ? canonicalTeam : captorName;
    }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { setPreciseProgress(progress); }
    
    /**
     * 获取据点信息字符串
     * @return 据点信息字符串
     */
    public String getInfoString() {
        return name + " (批次 " + batch + ") - " + state + " - " + progress + "% - 占领者: " + captorName;
    }
    
    /**
     * 获取据点所属批次
     * @return 据点所属批次
     */
    public int getBatch() {
        return batch;
    }
    
    /**
     * 可序列化的据点数据类
     * 用于在网络间传输据点数据
     */
    public static class SerializableCapturePoint {
        public final String name;
        public final BlockPos pos1;
        public final BlockPos pos2;
        public final int batch;
        public final CaptureState state;
        public final DisplayState displayState;
        public final String captorName;
        public final int progress;
        
        public SerializableCapturePoint(String name, BlockPos pos1, BlockPos pos2, int batch,
                                      CaptureState state, DisplayState displayState, 
                                      String captorName, int progress) {
            this.name = name;
            this.pos1 = pos1;
            this.pos2 = pos2;
            this.batch = batch;
            this.state = state;
            this.displayState = displayState;
            this.captorName = captorName;
            this.progress = progress;
        }
        
        /**
         * 从网络数据包读取据点信息
         * @param buf 网络数据包缓冲区
         * @return 可序列化的据点对象
         */
        public static SerializableCapturePoint fromNetwork(FriendlyByteBuf buf) {
            try {
                String name = buf.readUtf(32);
                BlockPos pos1 = buf.readBlockPos();
                BlockPos pos2 = buf.readBlockPos();
                int batch = buf.readVarInt();
                int stateId = buf.readUnsignedByte();
                int displayStateId = buf.readUnsignedByte();
                CaptureState[] states = CaptureState.values();
                DisplayState[] displayStates = DisplayState.values();
                if (stateId >= states.length || displayStateId >= displayStates.length) {
                    throw new IllegalArgumentException("未知的据点状态编号");
                }
                CaptureState state = states[stateId];
                DisplayState displayState = displayStates[displayStateId];
                String captorName = buf.readUtf(32);
                int progress = buf.readVarInt();
                validateNetworkFields(name, pos1, pos2, batch, captorName, progress);
                
                return new SerializableCapturePoint(name, pos1, pos2, batch, state, displayState, captorName, progress);
            } catch (Exception e) {
                ModLogger.error("从网络数据包读取据点信息时发生异常: " + e.getMessage());
                throw new IllegalArgumentException("无法解码据点信息", e);
            }
        }
        
        /**
         * 将据点信息写入网络数据包
         * @param buf 网络数据包缓冲区
         */
        public void toNetwork(FriendlyByteBuf buf) {
            validateNetworkFields(name, pos1, pos2, batch, captorName, progress);
            if (state == null || displayState == null) {
                throw new IllegalArgumentException("据点状态不能为空");
            }
            buf.writeUtf(name, 32);
            buf.writeBlockPos(pos1);
            buf.writeBlockPos(pos2);
            buf.writeVarInt(batch);
            buf.writeByte(state.ordinal());
            buf.writeByte(displayState.ordinal());
            buf.writeUtf(captorName, 32);
            buf.writeVarInt(progress);
        }

        private static void validateNetworkFields(
                String name, BlockPos pos1, BlockPos pos2, int batch,
                String captorName, int progress) {
            if (name == null || name.isBlank() || name.length() > 32
                || captorName == null || captorName.length() > 32
                || pos1 == null || pos2 == null
                || batch < 1 || batch > 64
                || progress < 0 || progress > 100) {
                throw new IllegalArgumentException("据点网络字段超出协议限制");
            }
        }
    }
    
    /**
     * 将据点转换为可序列化的对象
     * @return 可序列化的据点对象
     */
    public SerializableCapturePoint toSerializable() {
        return new SerializableCapturePoint(
            name, pos1, pos2, batch, state, displayState, captorName, progress
        );
    }
    
    /**
     * 从可序列化的对象恢复据点状态
     * @param serializable 可序列化的据点对象
     */
    public void restoreFromSerializable(SerializableCapturePoint serializable) {
        // 注意：name, pos1, pos2 是不可变的，不需要恢复
        this.state = serializable.state;
        this.displayState = serializable.displayState;
        this.captorName = serializable.captorName;
        setPreciseProgress(serializable.progress);
    }
}
