package com.example.espoints.config;

/** Client-only tactical map image quality preference. */
public enum MapImageQuality {
    PERFORMANCE(0, "性能"),
    BALANCED(1, "平衡"),
    HIGH(2, "高清");

    private final int refinementLevels;
    private final String displayName;

    MapImageQuality(int refinementLevels, String displayName) {
        this.refinementLevels = refinementLevels;
        this.displayName = displayName;
    }

    public int refinementLevels() {
        return refinementLevels;
    }

    public String displayName() {
        return displayName;
    }
}
