package com.maze.game;

import java.util.Objects;

public class MazePoint {
    private final int x;
    private final int y;

    public MazePoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MazePoint)) {
            return false;
        }
        MazePoint mazePoint = (MazePoint) o;
        return x == mazePoint.x && y == mazePoint.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
