package com.maze.game;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

@Service
public class MazeService {
    private static final int DEFAULT_COLS = 21;
    private static final int DEFAULT_ROWS = 21;
    private static final int MIN_SIZE = 11;
    private static final int MAX_SIZE = 29;
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 5;
    public static final int DEFAULT_LEVEL = 4;
    private static final int MAX_RETRY = 300;
    /**
     * Canonical difficulty tiers: grid size and carving biases (branching / path winding).
     * Levels 1..5 use logical sizes 16..20 with conservative tuning to keep generation stable
     * under wide-corridor budget constraints.
     */
    private static final Map<Integer, MazeLevelProfile> LEVEL_PROFILES_BY_NUMBER;

    static {
        Map<Integer, MazeLevelProfile> table = new HashMap<Integer, MazeLevelProfile>();
        table.put(1, new MazeLevelProfile(13, 13, 25, 7, 12, 0.08d, 95));
        table.put(2, new MazeLevelProfile(15, 15, 35, 8, 18, 0.12d, 95));
        table.put(3, new MazeLevelProfile(17, 17, 45, 9, 24, 0.16d, 95));
        table.put(4, new MazeLevelProfile(19, 19, 52, 10, 30, 0.20d, 95));
        table.put(5, new MazeLevelProfile(21, 21, 58, 10, 36, 0.24d, 95));
        LEVEL_PROFILES_BY_NUMBER = Collections.unmodifiableMap(table);
    }

    private final Random random = new SecureRandom();

    public int clampLevel(Integer level) {
        if (level == null) {
            return DEFAULT_LEVEL;
        }
        int value = level;
        if (value < MIN_LEVEL) {
            return MIN_LEVEL;
        }
        if (value > MAX_LEVEL) {
            return MAX_LEVEL;
        }
        return value;
    }

    public GameState generate(Integer requestedCols, Integer requestedRows) {
        return generate(requestedCols, requestedRows, null);
    }

    public GameState generate(Integer requestedCols, Integer requestedRows, Integer level) {
        int resolvedLevel;
        MazeLevelProfile profile;
        if (level != null) {
            resolvedLevel = clampLevel(level);
            profile = LEVEL_PROFILES_BY_NUMBER.get(resolvedLevel);
        } else {
            resolvedLevel = DEFAULT_LEVEL;
            int cols = normalizeSize(requestedCols, DEFAULT_COLS);
            int rows = normalizeSize(requestedRows, DEFAULT_ROWS);
            profile = LEVEL_PROFILES_BY_NUMBER.get(DEFAULT_LEVEL).withLogicalGrid(cols, rows);
        }
        int wideCorridorBudget = getWideCorridorBudget(resolvedLevel);
        int cols = profile.getLogicalCols();
        int rows = profile.getLogicalRows();
        int width = cols * 2 + 1;
        int height = rows * 2 + 1;
        int middleY = height / 2;
        for (int i = 0; i < MAX_RETRY; i++) {
            int[][] maze = createMazeBuffer(width, height);
            boolean[][] visited = new boolean[rows][cols];
            int[] usedWideCorridorBudget = new int[] {0};
            carveByRecursiveBacktracking(cols / 2, rows / 2, visited, maze, null, profile,
                    wideCorridorBudget, usedWideCorridorBudget);
            if (!allLogicalCellsVisited(visited)) {
                continue;
            }

            MazePoint goal = new MazePoint(width / 2, height / 2);
            maze[goal.getY()][goal.getX()] = MazeCellState.ROAD;
            applyBoundaryLockdown(maze);
            openEntriesAtMiddle(maze, middleY);
            openGoalApproaches(maze, goal);
            if (wouldCreateSolid2x2Block(maze)) {
                continue;
            }
            if (countSolid2x3Blocks(maze) > wideCorridorBudget) {
                continue;
            }

            MazePoint startA = new MazePoint(0, middleY);
            MazePoint startB = new MazePoint(width - 1, middleY);
            int shortestA = bfsShortestDistance(maze, startA, goal);
            int shortestB = bfsShortestDistance(maze, startB, goal);
            if (!isBalancedShortestPath(shortestA, shortestB)) {
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
                                              Direction previousDirection, MazeLevelProfile profile,
                                              int wideCorridorBudget, int[] usedWideCorridorBudget) {
        visited[cellY][cellX] = true;
        int mazeX = cellX * 2 + 1;
        int mazeY = cellY * 2 + 1;
        maze[mazeY][mazeX] = MazeCellState.ROAD;

        List<Direction> directions = buildDirections(previousDirection, random.nextInt(4), profile);
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
            if (!canOpenConnector(maze, wallX, wallY, nextMazeX, nextMazeY,
                    wideCorridorBudget, usedWideCorridorBudget)) {
                continue;
            }
            maze[wallY][wallX] = MazeCellState.ROAD;
            maze[nextMazeY][nextMazeX] = MazeCellState.ROAD;
            carveByRecursiveBacktracking(nextCellX, nextCellY, visited, maze, direction, profile,
                    wideCorridorBudget, usedWideCorridorBudget);
        }
    }

    /**
     * Before carving a connector, simulate opening the wall cell and the next cell center.
     * Hard reject 2x2 blocks. 2x3 / 3x2 blocks consume a per-level budget.
     */
    private boolean canOpenConnector(int[][] maze, int wallX, int wallY, int nextMazeX, int nextMazeY,
                                     int wideCorridorBudget, int[] usedWideCorridorBudget) {
        if (!isInside(wallX, wallY, maze) || !isInside(nextMazeX, nextMazeY, maze)) {
            return false;
        }
        int beforeSolid2x3 = countSolid2x3Blocks(maze);
        int oldWall = maze[wallY][wallX];
        int oldNext = maze[nextMazeY][nextMazeX];
        maze[wallY][wallX] = MazeCellState.ROAD;
        maze[nextMazeY][nextMazeX] = MazeCellState.ROAD;
        boolean ok = false;
        if (!wouldCreateSolid2x2Block(maze)) {
            int createdSolid2x3 = Math.max(0, countSolid2x3Blocks(maze) - beforeSolid2x3);
            if (usedWideCorridorBudget[0] + createdSolid2x3 <= wideCorridorBudget) {
                usedWideCorridorBudget[0] += createdSolid2x3;
                ok = true;
            }
        }
        maze[wallY][wallX] = oldWall;
        maze[nextMazeY][nextMazeX] = oldNext;
        return ok;
    }

    private boolean wouldCreateSolid2x2Block(int[][] maze) {
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
        return false;
    }

    private int countSolid2x3Blocks(int[][] maze) {
        int h = maze.length;
        int w = maze[0].length;
        int count = 0;
        for (int y = 0; y < h - 1; y++) {
            for (int x = 0; x < w - 2; x++) {
                if (isTraversable(maze, x, y) && isTraversable(maze, x + 1, y) && isTraversable(maze, x + 2, y)
                        && isTraversable(maze, x, y + 1) && isTraversable(maze, x + 1, y + 1)
                        && isTraversable(maze, x + 2, y + 1)) {
                    count++;
                }
            }
        }
        for (int y = 0; y < h - 2; y++) {
            for (int x = 0; x < w - 1; x++) {
                if (isTraversable(maze, x, y) && isTraversable(maze, x + 1, y)
                        && isTraversable(maze, x, y + 1) && isTraversable(maze, x + 1, y + 1)
                        && isTraversable(maze, x, y + 2) && isTraversable(maze, x + 1, y + 2)) {
                    count++;
                }
            }
        }
        return count;
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

    private List<Direction> buildDirections(Direction previousDirection, int straightBonus, MazeLevelProfile profile) {
        List<Direction> directions = new ArrayList<Direction>();
        directions.add(Direction.UP);
        directions.add(Direction.DOWN);
        directions.add(Direction.LEFT);
        directions.add(Direction.RIGHT);
        Collections.shuffle(directions, random);
        if (previousDirection == null) {
            return directions;
        }
        directions.sort((a, b) -> Integer.compare(
                scoreDirection(b, previousDirection, straightBonus, profile),
                scoreDirection(a, previousDirection, straightBonus, profile)));
        if (random.nextDouble() < profile.getPostShuffleSwapProbability()) {
            Collections.swap(directions, 0, 1 + random.nextInt(Math.min(3, directions.size() - 1)));
        }
        return directions;
    }

    private int scoreDirection(Direction candidate, Direction previousDirection, int straightBonus,
                               MazeLevelProfile profile) {
        int score = random.nextInt(profile.getScoreJitterExclusiveMax());
        if (candidate == previousDirection) {
            score -= profile.getStraightContinuationDeduction() + straightBonus * profile.getStraightBonusMultiplier();
        } else {
            score += profile.getTurnExploreBonus();
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

    private void openGoalApproaches(int[][] maze, MazePoint goal) {
        for (Direction direction : Direction.values()) {
            int nx = goal.getX() + direction.getDx();
            int ny = goal.getY() + direction.getDy();
            if (!isInside(nx, ny, maze)) {
                continue;
            }
            maze[ny][nx] = MazeCellState.ROAD;
        }
    }

    private int getWideCorridorBudget(int level) {
        if (level <= 2) {
            return 1;
        }
        if (level <= 4) {
            return 2;
        }
        return 3;
    }

    /**
     * After generation, two BFS runs yield shortest path lengths La and Lb from each entry to
     * the goal. Reject when the imbalance exceeds 10% of their average, i.e. when
     * absolute difference is greater than ceil of 5% of (La + Lb).
     */
    private boolean isBalancedShortestPath(int shortestA, int shortestB) {
        if (shortestA <= 0 || shortestB <= 0) {
            return false;
        }
        int diff = Math.abs(shortestA - shortestB);
        return diff <= Math.ceil((shortestA + shortestB) * 0.05d);
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
