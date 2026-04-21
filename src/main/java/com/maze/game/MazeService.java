package com.maze.game;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

@Service
public class MazeService {
    private static final int DEFAULT_COLS = 15;
    private static final int DEFAULT_ROWS = 15;
    private static final int MIN_SIZE = 9;
    private static final int MAX_SIZE = 21;
    private static final double ROOM_CHANCE = 0.16d;
    private final Random random = new SecureRandom();

    public GameState generate(Integer requestedCols, Integer requestedRows) {
        int cols = normalizeSize(requestedCols, DEFAULT_COLS);
        int rows = normalizeSize(requestedRows, DEFAULT_ROWS);
        int width = cols * 2 + 1;
        int height = rows * 2 + 1;
        int[][] maze = createMazeBuffer(width, height);
        int centerCellX = cols / 2;
        int centerCellY = rows / 2;
        int leftCellMaxX = centerCellX - 1;
        boolean[][] visitedLeft = new boolean[rows][centerCellX];
        carveLeftHalf(0, centerCellY, visitedLeft, maze, leftCellMaxX, null);
        mirrorCenterSymmetry(maze);

        int centerX = width / 2;
        int centerY = height / 2;
        maze[centerY][centerX] = MazeCellState.ROAD;
        maze[centerY][centerX - 1] = MazeCellState.ROAD;
        maze[centerY][centerX + 1] = MazeCellState.ROAD;
        maze[centerY][centerX - 2] = MazeCellState.ROAD;
        maze[centerY][centerX + 2] = MazeCellState.ROAD;

        MazePoint goal = new MazePoint(centerX, centerY);
        MazePoint startA = new MazePoint(0, centerY);
        MazePoint startB = new MazePoint(width - 1, centerY);
        maze[startA.getY()][startA.getX()] = MazeCellState.ROAD;
        maze[startB.getY()][startB.getX()] = MazeCellState.ROAD;
        maze[centerY][1] = MazeCellState.ROAD;
        maze[centerY][width - 2] = MazeCellState.ROAD;

        int distanceA = bfsShortestDistance(maze, startA, goal);
        int distanceB = bfsShortestDistance(maze, startB, goal);
        if (distanceA <= 0 || distanceA != distanceB) {
            return generate(requestedCols, requestedRows);
        }
        return new GameState(maze, goal, startA, startB);
    }

    public MoveResult tryMove(GameState state, PlayerId playerId, Direction direction) {
        if (state.isGameOver()) {
            return new MoveResult(false, true, state.getWinner());
        }
        Player current = state.getPlayer(playerId);
        int nextX = current.getX() + direction.getDx();
        int nextY = current.getY() + direction.getDy();
        if (!isInside(nextX, nextY, state.getMaze())) {
            return new MoveResult(false, state.isGameOver(), state.getWinner());
        }
        if ((state.getMaze()[nextY][nextX] & MazeCellState.WALL) == MazeCellState.WALL) {
            return new MoveResult(false, state.isGameOver(), state.getWinner());
        }
        if (state.isOccupiedByOther(playerId, nextX, nextY)) {
            return new MoveResult(false, state.isGameOver(), state.getWinner());
        }
        state.movePlayer(playerId, nextX, nextY);
        if (nextX == state.getGoal().getX() && nextY == state.getGoal().getY()) {
            state.setWinner(playerId);
        }
        return new MoveResult(true, state.isGameOver(), state.getWinner());
    }

    public static class MoveResult {
        private final boolean moved;
        private final boolean gameOver;
        private final PlayerId winner;

        public MoveResult(boolean moved, boolean gameOver, PlayerId winner) {
            this.moved = moved;
            this.gameOver = gameOver;
            this.winner = winner;
        }

        public boolean isMoved() {
            return moved;
        }

        public boolean isGameOver() {
            return gameOver;
        }

        public PlayerId getWinner() {
            return winner;
        }
    }

    private int[][] createMazeBuffer(int width, int height) {
        int[][] maze = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                maze[y][x] = MazeCellState.WALL;
            }
        }
        return maze;
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

    private void carveLeftHalf(int cellX, int cellY, boolean[][] visitedLeft, int[][] maze,
                               int leftCellMaxX, Direction previousDirection) {
        visitedLeft[cellY][cellX] = true;
        int mazeX = cellX * 2 + 1;
        int mazeY = cellY * 2 + 1;
        maze[mazeY][mazeX] = MazeCellState.ROAD;

        List<Direction> directions = buildDirections(previousDirection);
        for (Direction direction : directions) {
            int nextCellX = cellX + direction.getDx();
            int nextCellY = cellY + direction.getDy();
            if (!isInsideLeftCell(nextCellX, nextCellY, visitedLeft, leftCellMaxX) || visitedLeft[nextCellY][nextCellX]) {
                continue;
            }
            int wallX = mazeX + direction.getDx();
            int wallY = mazeY + direction.getDy();
            int nextMazeX = nextCellX * 2 + 1;
            int nextMazeY = nextCellY * 2 + 1;
            maze[wallY][wallX] = MazeCellState.ROAD;
            maze[nextMazeY][nextMazeX] = MazeCellState.ROAD;
            maybeCreateRoom(maze, wallX, wallY, direction);
            carveLeftHalf(nextCellX, nextCellY, visitedLeft, maze, leftCellMaxX, direction);
        }
    }

    private void mirrorCenterSymmetry(int[][] maze) {
        int height = maze.length;
        int width = maze[0].length;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int mirrorX = width - 1 - x;
                int mirrorY = height - 1 - y;
                if ((maze[y][x] & MazeCellState.WALL) == MazeCellState.WALL) {
                    maze[mirrorY][mirrorX] = MazeCellState.WALL;
                } else {
                    maze[mirrorY][mirrorX] = MazeCellState.ROAD;
                }
            }
        }
    }

    private void maybeCreateRoom(int[][] maze, int wallX, int wallY, Direction direction) {
        if (random.nextDouble() > ROOM_CHANCE) {
            return;
        }
        List<Direction> sideDirections = new ArrayList<Direction>();
        if (direction == Direction.UP || direction == Direction.DOWN) {
            sideDirections.add(Direction.LEFT);
            sideDirections.add(Direction.RIGHT);
        } else {
            sideDirections.add(Direction.UP);
            sideDirections.add(Direction.DOWN);
        }
        Collections.shuffle(sideDirections, random);
        Direction side = sideDirections.get(0);
        int sx = wallX + side.getDx();
        int sy = wallY + side.getDy();
        int sx2 = sx + direction.getDx();
        int sy2 = sy + direction.getDy();
        if (!isInside(sx, sy, maze) || !isInside(sx2, sy2, maze)) {
            return;
        }
        maze[sy][sx] = MazeCellState.ROAD;
        maze[sy2][sx2] = MazeCellState.ROAD;
    }

    private boolean isInsideLeftCell(int x, int y, boolean[][] visitedLeft, int leftCellMaxX) {
        return y >= 0 && y < visitedLeft.length && x >= 0 && x <= leftCellMaxX;
    }

    private List<Direction> buildDirections(Direction previousDirection) {
        List<Direction> directions = new ArrayList<Direction>();
        directions.add(Direction.UP);
        directions.add(Direction.DOWN);
        directions.add(Direction.LEFT);
        directions.add(Direction.RIGHT);
        Collections.shuffle(directions, random);
        if (previousDirection == null) {
            return directions;
        }
        // Prefer turns to reduce very long straight channels.
        directions.sort((a, b) -> Integer.compare(scoreDirection(b, previousDirection), scoreDirection(a, previousDirection)));
        return directions;
    }

    private int scoreDirection(Direction candidate, Direction previousDirection) {
        int score = random.nextInt(50);
        if (candidate == previousDirection) {
            score -= 20;
        } else {
            score += 20;
        }
        return score;
    }

    private int bfsShortestDistance(int[][] maze, MazePoint start, MazePoint goal) {
        int[][] dist = new int[maze.length][maze[0].length];
        for (int y = 0; y < dist.length; y++) {
            for (int x = 0; x < dist[0].length; x++) {
                dist[y][x] = -1;
            }
        }
        Queue<MazePoint> queue = new LinkedList<MazePoint>();
        queue.offer(start);
        dist[start.getY()][start.getX()] = 0;
        while (!queue.isEmpty()) {
            MazePoint current = queue.poll();
            if (current.getX() == goal.getX() && current.getY() == goal.getY()) {
                return dist[current.getY()][current.getX()];
            }
            for (Direction direction : Direction.values()) {
                int nx = current.getX() + direction.getDx();
                int ny = current.getY() + direction.getDy();
                if (!isInside(nx, ny, maze)) {
                    continue;
                }
                if ((maze[ny][nx] & MazeCellState.WALL) == MazeCellState.WALL || dist[ny][nx] >= 0) {
                    continue;
                }
                dist[ny][nx] = dist[current.getY()][current.getX()] + 1;
                queue.offer(new MazePoint(nx, ny));
            }
        }
        return -1;
    }

}
