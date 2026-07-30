package com.example.espoints.network;

public final class PacketValidation {
    public static final double MAX_WORLD_COORDINATE = 30_000_000.0D;

    private PacketValidation() {
    }

    public static int checkedCount(int value, int maximum, String field) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(
                "Invalid " + field + " count: " + value + " (max " + maximum + ")");
        }
        return value;
    }

    public static double checkedCoordinate(double value, String field) {
        if (!Double.isFinite(value) || Math.abs(value) > MAX_WORLD_COORDINATE) {
            throw new IllegalArgumentException("Invalid " + field + " coordinate: " + value);
        }
        return value;
    }

    public static double checkedOptionalY(double value) {
        if (Double.isNaN(value)) {
            return value;
        }
        return checkedCoordinate(value, "y");
    }
}
