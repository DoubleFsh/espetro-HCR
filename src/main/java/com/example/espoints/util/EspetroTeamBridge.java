package com.example.espoints.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Normalizes HCRpoints team logic to Espetro's canonical ATTACK/DEFEND sides.
 */
public final class EspetroTeamBridge {
    public static final String ATTACK = "ATTACK";
    public static final String DEFEND = "DEFEND";
    public static final String ATTACK_SCOREBOARD_TEAM = "espetro_attack";
    public static final String DEFEND_SCOREBOARD_TEAM = "espetro_defend";

    private static final String ESPETRO_CLASS_NAME = "org.espetro.Espetro";
    private static final String ESPETRO_API_CLASS_NAME = "org.espetro.api.EspetroAPI";
    private static final String ESPETRO_VOTE_MANAGER_CLASS_NAME = "org.espetro.team.VoteManager";
    private static final String ESPETRO_SQUAD_MANAGER_CLASS_NAME = "org.espetro.team.SquadManager";
    private static final String ESPETRO_CLIENT_GAME_STATE_CLASS_NAME = "org.espetro.client.gui.ClientGameState";
    private static final String ESPETRO_CLIENT_TACTICAL_STATE_CLASS_NAME =
        "org.espetro.client.gui.ClientTacticalState";
    private static final int DEFAULT_TEAMMATE_COLOR = 0xFFFFFFFF;

    private EspetroTeamBridge() {
    }

    public static String getPlayerTeam(Player player) {
        if (player == null) {
            return null;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            return getServerPlayerTeam(serverPlayer);
        }

        // 玩家实体上的记分板队伍随换队立即更新，优先级高于客户端缓存。
        String scoreboardTeam = getScoreboardTeam(player.getTeam());
        if (scoreboardTeam != null) {
            return scoreboardTeam;
        }

        String clientTeam = getClientPlayerTeam();
        if (clientTeam != null) {
            return clientTeam;
        }
        return null;
    }

    public static String getServerPlayerTeam(ServerPlayer player) {
        if (player == null) {
            return null;
        }

        // 服务端分发以实体当前记分板队伍为准，避免职业缓存换队后短暂滞后。
        String scoreboardTeam = getScoreboardTeam(player.getTeam());
        if (scoreboardTeam != null) {
            return scoreboardTeam;
        }

        String espetroTeam = getEspetroPlayerTeam(player);
        if (espetroTeam != null) {
            return espetroTeam;
        }
        return null;
    }

    public static String getScoreboardTeam(Team team) {
        if (team == null) {
            return null;
        }

        String canonical = canonicalizeTeamName(team.getName());
        if (canonical != null) {
            return canonical;
        }

        if (team instanceof PlayerTeam playerTeam) {
            return canonicalizeTeamName(playerTeam.getDisplayName().getString());
        }

        return null;
    }

    public static boolean isEspetroTeamPlayer(Player player) {
        return getPlayerTeam(player) != null;
    }

    public static boolean isSameTeam(String left, String right) {
        String leftTeam = canonicalizeTeamName(left);
        String rightTeam = canonicalizeTeamName(right);
        return leftTeam != null && leftTeam.equals(rightTeam);
    }

    public static String canonicalizeTeamName(String teamName) {
        if (teamName == null || teamName.isBlank()) {
            return null;
        }

        String stripped = stripMinecraftFormatting(teamName).trim();
        String normalized = stripped.toLowerCase(Locale.ROOT);
        if (ATTACK.equalsIgnoreCase(stripped)
                || ATTACK_SCOREBOARD_TEAM.equals(normalized)
                || "attacker".equals(normalized)) {
            return ATTACK;
        }
        if (DEFEND.equalsIgnoreCase(stripped)
                || DEFEND_SCOREBOARD_TEAM.equals(normalized)
                || "defender".equals(normalized)) {
            return DEFEND;
        }

        if (normalized.contains("attack")
                || normalized.contains("attacker")
                || stripped.contains("进攻")
                || stripped.contains("攻方")) {
            return ATTACK;
        }
        if (normalized.contains("defend")
                || normalized.contains("defender")
                || stripped.contains("防守")
                || stripped.contains("守方")) {
            return DEFEND;
        }

        return null;
    }

    public static String scoreboardTeamId(String canonicalTeam) {
        String team = canonicalizeTeamName(canonicalTeam);
        if (ATTACK.equals(team)) {
            return ATTACK_SCOREBOARD_TEAM;
        }
        if (DEFEND.equals(team)) {
            return DEFEND_SCOREBOARD_TEAM;
        }
        return "";
    }

    public static String roleForTeam(String canonicalTeam) {
        String team = canonicalizeTeamName(canonicalTeam);
        if (ATTACK.equals(team)) {
            return "attacker";
        }
        if (DEFEND.equals(team)) {
            return "defender";
        }
        return null;
    }

    public static String displayName(String canonicalTeam) {
        String team = canonicalizeTeamName(canonicalTeam);
        if (ATTACK.equals(team)) {
            return "进攻方";
        }
        if (DEFEND.equals(team)) {
            return "防守方";
        }
        return canonicalTeam == null ? "" : canonicalTeam;
    }

    /** 使用 Espetro 名牌规则为战术地图上的队友图标着色。 */
    public static int getMapPlayerColor(String playerName) {
        try {
            Class<?> tacticalStateClass = Class.forName(ESPETRO_CLIENT_TACTICAL_STATE_CLASS_NAME);
            Method method = tacticalStateClass.getMethod("getNameColor", String.class);
            Object result = method.invoke(null, playerName);
            return result instanceof Number color ? color.intValue() : DEFAULT_TEAMMATE_COLOR;
        } catch (ReflectiveOperationException ignored) {
            return DEFAULT_TEAMMATE_COLOR;
        }
    }

    /** 服务端权威权限：指挥官或任一小队队长。 */
    public static boolean canPlaceTacticalMarker(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        if (isCommander(player) || isSquadLeader(player)) {
            return true;
        }

        return false;
    }

    private static boolean isCommander(ServerPlayer player) {
        if (invokeStaticBoolean(ESPETRO_API_CLASS_NAME, "isCommander",
                new Class<?>[] {java.util.UUID.class}, player.getUUID())) {
            return true;
        }

        try {
            Class<?> voteManagerClass = Class.forName(ESPETRO_VOTE_MANAGER_CLASS_NAME);
            Object voteManager = voteManagerClass.getMethod("getInstance").invoke(null);

            Method isCommanderMethod = voteManagerClass.getMethod("isCommander", java.util.UUID.class);
            Object directResult = isCommanderMethod.invoke(voteManager, player.getUUID());
            if (Boolean.TRUE.equals(directResult)) {
                return true;
            }

            Method getAttackCommander = voteManagerClass.getMethod("getAttackCommander");
            Method getDefendCommander = voteManagerClass.getMethod("getDefendCommander");
            return player.getUUID().equals(getAttackCommander.invoke(voteManager))
                || player.getUUID().equals(getDefendCommander.invoke(voteManager));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean isSquadLeader(ServerPlayer player) {
        if (invokeStaticBoolean(ESPETRO_API_CLASS_NAME, "isSquadLeader",
                new Class<?>[] {java.util.UUID.class}, player.getUUID())) {
            return true;
        }

        try {
            Class<?> squadManagerClass = Class.forName(ESPETRO_SQUAD_MANAGER_CLASS_NAME);
            Object squadManager = squadManagerClass.getMethod("getInstance").invoke(null);
            Method getSquadSnapshots = squadManagerClass.getMethod("getSquadSnapshots", String.class);

            String team = getServerPlayerTeam(player);
            if (team != null && isSquadLeaderInSnapshots(
                    getSquadSnapshots.invoke(squadManager, team), player.getUUID())) {
                return true;
            }

            return isSquadLeaderInSnapshots(getSquadSnapshots.invoke(squadManager, ATTACK), player.getUUID())
                || isSquadLeaderInSnapshots(getSquadSnapshots.invoke(squadManager, DEFEND), player.getUUID());
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean isSquadLeaderInSnapshots(Object snapshotsObject, java.util.UUID playerId)
        throws ReflectiveOperationException {
        if (!(snapshotsObject instanceof Iterable<?> snapshots)) {
            return false;
        }

        for (Object squad : snapshots) {
            Object membersObject = squad.getClass().getField("members").get(squad);
            if (!(membersObject instanceof Iterable<?> members)) {
                continue;
            }

            for (Object member : members) {
                Object memberId = member.getClass().getField("uuid").get(member);
                Object leader = member.getClass().getField("leader").get(member);
                if (playerId.equals(memberId) && Boolean.TRUE.equals(leader)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean invokeStaticBoolean(String className, String methodName,
                                               Class<?>[] parameterTypes, Object... args) {
        try {
            Class<?> targetClass = Class.forName(className);
            Method method = targetClass.getMethod(methodName, parameterTypes);
            return Boolean.TRUE.equals(method.invoke(null, args));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static String getEspetroPlayerTeam(ServerPlayer player) {
        try {
            Class<?> espetroClass = Class.forName(ESPETRO_CLASS_NAME);
            Method method = espetroClass.getMethod("getPlayerTeam", ServerPlayer.class);
            Object result = method.invoke(null, player);
            return result instanceof String team ? canonicalizeTeamName(team) : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String getClientPlayerTeam() {
        try {
            Class<?> clientGameStateClass = Class.forName(ESPETRO_CLIENT_GAME_STATE_CLASS_NAME);
            Method method = clientGameStateClass.getMethod("getPlayerTeam");
            Object result = method.invoke(null);
            return result instanceof String team ? canonicalizeTeamName(team) : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String stripMinecraftFormatting(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean skipNext = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (skipNext) {
                skipNext = false;
                continue;
            }
            if (current == '\u00a7' && i + 1 < value.length()) {
                skipNext = true;
                continue;
            }
            builder.append(current);
        }
        return builder.toString();
    }
}
