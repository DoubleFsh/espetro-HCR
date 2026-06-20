package com.example.hcrpoints.util;

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

    private EspetroTeamBridge() {
    }

    public static String getPlayerTeam(Player player) {
        if (player == null) {
            return null;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            String espetroTeam = getServerPlayerTeam(serverPlayer);
            if (espetroTeam != null) {
                return espetroTeam;
            }
        }

        return getScoreboardTeam(player.getTeam());
    }

    public static String getServerPlayerTeam(ServerPlayer player) {
        if (player == null) {
            return null;
        }

        String espetroTeam = getEspetroPlayerTeam(player);
        if (espetroTeam != null) {
            return espetroTeam;
        }

        return getScoreboardTeam(player.getTeam());
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
