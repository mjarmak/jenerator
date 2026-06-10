package com.jenerator.common.model;

public enum DurationPreset {
    ONE_MINUTE(55, 60, 58),
    AUTO_SHORT(45, 180, 90);

    private final int minSeconds;
    private final int maxSeconds;
    private final int targetSeconds;

    DurationPreset(int minSeconds, int maxSeconds, int targetSeconds) {
        this.minSeconds = minSeconds;
        this.maxSeconds = maxSeconds;
        this.targetSeconds = targetSeconds;
    }

    public int minSeconds() {
        return minSeconds;
    }

    public int maxSeconds() {
        return maxSeconds;
    }

    public int targetSeconds() {
        return targetSeconds;
    }
}
