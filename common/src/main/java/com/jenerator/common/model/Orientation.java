package com.jenerator.common.model;

public enum Orientation {
    PORTRAIT_9_16(1080, 1920),
    LANDSCAPE_16_9(1920, 1080);

    private final int width;
    private final int height;

    Orientation(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
