package com.maze.game;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
public class MazeService {
    private static final int DEFAULT_COLS = 9;
    private static final int DEFAULT_ROWS = 9;
    private static final int MIN_SIZE = 5;
    private static final int MAX_SIZE = 19;
    private final Random random = new SecureRandom();

    public MazeSessionState generate(Integer requestedCols, Integer requestedRows) {
        int cols = normalizeSize(requestedCols, DEFAULT_COLS);
        int rows = normalizeSize(requestedRows, DEFAULT_ROWS);
        int width = cols * 2 + 1;
        int height = rows * 2 + 1;

        int[][] maze = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                maze[y][x] = MazeCellState.WALL;
            }
        }

        boolean[][] visitedCells = new boolean[rows][cols];
        carveByRecursiveBacktracking(0, 0, visitedCells, maze);

        MazePoint start = new MazePoint(1, 1);
        MazePoint goal = new MazePoint(width - 2, height - 2);
        maze[goal.getY()][goal.getX()] = MazeCellState.ROAD;

        return new MazeSessionState(maze, start, goal);
    }

    public boolean tryMove(MazeSessionState state, Direction direction) {
        int currentX = state.getPlayer().getX();
        int currentY = state.getPlayer().getY();
        int nextX = currentX + direction.getDx();
        int nextY = currentY + direction.getDy();
        if (!isInside(nextX, nextY, state.getMaze())) {
            return false;
        }
        if ((state.getMaze()[nextY][nextX] & MazeCellState.WALL) == MazeCellState.WALL) {
            return false;
        }
        state.movePlayerTo(nextX, nextY);
        return true;
    }

    public boolean isWin(MazeSessionState state) {
        return state.getPlayer().getX() == state.getGoal().getX()
                && state.getPlayer().getY() == state.getGoal().getY();
    }

    private int normalizeSize(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        int normalized = Math.max(MIN_SIZE, Math.min(MAX_SIZE, value));
        if (normalized % 2 == 0) {
            normalized += 1;
        }
        return normalized;
    }

    private boolean isInside(int x, int y, int[][] maze) {
        return y >= 0 && y < maze.length && x >= 0 && x < maze[0].length;
    }

    private void carveByRecursiveBacktracking(int cellX, int cellY, boolean[][] visitedCells, int[][] maze) {
        visitedCells[cellY][cellX] = true;
        int mazeX = cellX * 2 + 1;
        int mazeY = cellY * 2 + 1;
        maze[mazeY][mazeX] = MazeCellState.ROAD;

        List<Direction> directions = new ArrayList<Direction>();
        directions.add(Direction.UP);
        directions.add(Direction.DOWN);
        directions.add(Direction.LEFT);
        directions.add(Direction.RIGHT);
        Collections.shuffle(directions, random);

        for (Direction direction : directions) {
            int nextCellX = cellX + direction.getDx();
            int nextCellY = cellY + direction.getDy();
            if (!isInsideCell(nextCellX, nextCellY, visitedCells) || visitedCells[nextCellY][nextCellX]) {
                continue;
            }

            int wallX = mazeX + direction.getDx();
            int wallY = mazeY + direction.getDy();
            int nextMazeX = nextCellX * 2 + 1;
            int nextMazeY = nextCellY * 2 + 1;

            maze[wallY][wallX] = MazeCellState.ROAD;
            maze[nextMazeY][nextMazeX] = MazeCellState.ROAD;
            carveByRecursiveBacktracking(nextCellX, nextCellY, visitedCells, maze);
        }
    }

    private boolean isInsideCell(int x, int y, boolean[][] visitedCells) {
        return y >= 0 && y < visitedCells.length && x >= 0 && x < visitedCells[0].length;
    }
}
