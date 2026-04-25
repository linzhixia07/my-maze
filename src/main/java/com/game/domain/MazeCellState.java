package com.game.domain;

public final class MazeCellState {
    private MazeCellState() {
    }

    public static final int ROAD = 1;
    public static final int WALL = 1 << 1;
    public static final int VISITED = 1 << 2;
}
