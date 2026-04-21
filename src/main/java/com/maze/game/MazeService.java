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
    private static final int DEFAULT_COLS = 21;
    private static final int DEFAULT_ROWS = 21;
    private static final int MIN_SIZE = 11;
    private static final int MAX_SIZE = 29;
    private static final int MAX_RETRY = 300;
    private static final double FAIRNESS_THRESHOLD = 0.15d;
    private final Random random = new SecureRandom();

    public GameState generate(Integer requestedCols, Integer requestedRows) {
        int cols = normalizeSize(requestedCols, DEFAULT_COLS);
        int rows = normalizeSize(requestedRows, DEFAULT_ROWS);
        int width = cols * 2 + 1;
        int height = rows * 2 + 1;
        int middleY = height / 2;
        for (int i = 0; i < MAX_RETRY; i++) {
            int[][] maze = createMazeBuffer(width, height);
            boolean[][] visited = new boolean[rows][cols];
            carveByRecursiveBacktracking(cols / 2, rows / 2, visited, maze, null);
            if (!allLogicalCellsVisited(visited)) {
                continue;
            }

            MazePoint goal = new MazePoint(width / 2, height / 2);
            maze[goal.getY()][goal.getX()] = MazeCellState.ROAD;
            applyBoundaryLockdown(maze);
            openEntriesAtMiddle(maze, middleY);
            if (wouldCreateWideOpenCorridor(maze)) {
                continue;
            }

            MazePoint startA = new MazePoint(0, middleY);
            MazePoint startB = new MazePoint(width - 1, middleY);
            int distanceA = bfsShortestDistance(maze, startA, goal);
            int distanceB = bfsShortestDistance(maze, startB, goal);
            if (!isFair(distanceA, distanceB)) {
                continue;
            }
            return new GameState(maze, goal, startA, startB);
        }
        throw new IllegalStateException("Unable to generate fair maze.");
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

    private void carveByRecursiveBacktracking(int cellX, int cellY, boolean[][] visited, int[][] maze,
                                              Direction previousDirection) {
        visited[cellY][cellX] = true;
        int mazeX = cellX * 2 + 1;
        int mazeY = cellY * 2 + 1;
        maze[mazeY][mazeX] = MazeCellState.ROAD;

        List<Direction> directions = buildDirections(previousDirection, random.nextInt(4));
        for (Direction direction : directions) {
            int nextCellX = cellX + direction.getDx();
            int nextCellY = cellY + direction.getDy();
            if (!isInsideCell(nextCellX, nextCellY, visited) || visited[nextCellY][nextCellX]) {
                continue;
            }
            int wallX = mazeX + direction.getDx();
            int wallY = mazeY + direction.getDy();
            int nextMazeX = nextCellX * 2 + 1;
            int nextMazeY = nextCellY * 2 + 1;
            if (!canOpenConnector(maze, wallX, wallY, nextMazeX, nextMazeY)) {
                continue;
            }
            maze[wallY][wallX] = MazeCellState.ROAD;
            maze[nextMazeY][nextMazeX] = MazeCellState.ROAD;
            carveByRecursiveBacktracking(nextCellX, nextCellY, visited, maze, direction);
        }
    }

    /**
     * Before carving a connector, simulate opening the wall cell and the next cell center.
     * Rejects if that would create a solid 2x2 or 2x3 block of traversable cells (path wider than one cell).
     */
    private boolean canOpenConnector(int[][] maze, int wallX, int wallY, int nextMazeX, int nextMazeY) {
        if (!isInside(wallX, wallY, maze) || !isInside(nextMazeX, nextMazeY, maze)) {
            return false;
        }
        int oldWall = maze[wallY][wallX];
        int oldNext = maze[nextMazeY][nextMazeX];
        maze[wallY][wallX] = MazeCellState.ROAD;
        maze[nextMazeY][nextMazeX] = MazeCellState.ROAD;
        boolean ok = !wouldCreateWideOpenCorridor(maze);
        maze[wallY][wallX] = oldWall;
        maze[nextMazeY][nextMazeX] = oldNext;
        return ok;
    }

    private boolean wouldCreateWideOpenCorridor(int[][] maze) {
        int h = maze.length;
        int w = maze[0].length;
        for (int y = 0; y < h - 1; y++) {
            for (int x = 0; x < w - 1; x++) {
                if (isTraversable(maze, x, y) && isTraversable(maze, x + 1, y)
                        && isTraversable(maze, x, y + 1) && isTraversable(maze, x + 1, y + 1)) {
                    return true;
                }
            }
        }
        for (int y = 0; y < h - 1; y++) {
            for (int x = 0; x < w - 2; x++) {
                if (isTraversable(maze, x, y) && isTraversable(maze, x + 1, y) && isTraversable(maze, x + 2, y)
                        && isTraversable(maze, x, y + 1) && isTraversable(maze, x + 1, y + 1)
                        && isTraversable(maze, x + 2, y + 1)) {
                    return true;
                }
            }
        }
        for (int y = 0; y < h - 2; y++) {
            for (int x = 0; x < w - 1; x++) {
                if (isTraversable(maze, x, y) && isTraversable(maze, x + 1, y)
                        && isTraversable(maze, x, y + 1) && isTraversable(maze, x + 1, y + 1)
                        && isTraversable(maze, x, y + 2) && isTraversable(maze, x + 1, y + 2)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isTraversable(int[][] maze, int x, int y) {
        return (maze[y][x] & MazeCellState.WALL) != MazeCellState.WALL;
    }

    private boolean allLogicalCellsVisited(boolean[][] visited) {
        for (int y = 0; y < visited.length; y++) {
            for (int x = 0; x < visited[0].length; x++) {
                if (!visited[y][x]) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isInsideCell(int x, int y, boolean[][] visited) {
        return y >= 0 && y < visited.length && x >= 0 && x < visited[0].length;
    }

    private List<Direction> buildDirections(Direction previousDirection, int straightBonus) {
        List<Direction> directions = new ArrayList<Direction>();
        directions.add(Direction.UP);
        directions.add(Direction.DOWN);
        directions.add(Direction.LEFT);
        directions.add(Direction.RIGHT);
        Collections.shuffle(directions, random);
        if (previousDirection == null) {
            return directions;
        }
        // Strongly prefer turns: more zig-zags and side branches (misleading dead ends) for younger players.
        directions.sort((a, b) -> Integer.compare(
                scoreDirection(b, previousDirection, straightBonus),
                scoreDirection(a, previousDirection, straightBonus)));
        if (random.nextDouble() < 0.28d) {
            Collections.swap(directions, 0, 1 + random.nextInt(Math.min(3, directions.size() - 1)));
        }
        return directions;
    }

    private int scoreDirection(Direction candidate, Direction previousDirection, int straightBonus) {
        int score = random.nextInt(95);
        if (candidate == previousDirection) {
            score -= 48 + straightBonus * 10;
        } else {
            score += 28;
        }
        return score;
    }

    private void applyBoundaryLockdown(int[][] maze) {
        int height = maze.length;
        int width = maze[0].length;
        for (int x = 0; x < width; x++) {
            maze[0][x] = MazeCellState.WALL;
            maze[height - 1][x] = MazeCellState.WALL;
        }
        for (int y = 0; y < height; y++) {
            maze[y][0] = MazeCellState.WALL;
            maze[y][width - 1] = MazeCellState.WALL;
        }
    }

    private void openEntriesAtMiddle(int[][] maze, int middleY) {
        int width = maze[0].length;
        if (middleY <= 0 || middleY >= maze.length - 1) {
            throw new IllegalStateException("Invalid middle entry coordinate.");
        }
        maze[middleY][0] = MazeCellState.ROAD;
        maze[middleY][1] = MazeCellState.ROAD;
        maze[middleY][width - 1] = MazeCellState.ROAD;
        maze[middleY][width - 2] = MazeCellState.ROAD;
    }

    private boolean isFair(int distanceA, int distanceB) {
        if (distanceA <= 0 || distanceB <= 0) {
            return false;
        }
        int max = Math.max(distanceA, distanceB);
        int diff = Math.abs(distanceA - distanceB);
        return diff <= Math.ceil(max * FAIRNESS_THRESHOLD);
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
