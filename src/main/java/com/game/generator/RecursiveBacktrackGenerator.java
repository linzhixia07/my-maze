package com.game.generator;

import com.game.domain.Direction;
import com.game.domain.GameState;
import com.game.domain.MazeCellState;
import com.game.domain.MazeLevelProfile;
import com.game.domain.MazePoint;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * 基于递归回溯算法的迷宫生成器
 */
public class RecursiveBacktrackGenerator implements MazeGenerator {

    private static final int MAX_RETRY = 300;

    private final Random random = new SecureRandom();

    @Override
    public GameState generateRaceMaze(MazeLevelProfile profile, int wideCorridorBudget) {
        int cols = profile.getLogicalCols();
        int rows = profile.getLogicalRows();
        int width = cols * 2 + 1;
        int height = rows * 2 + 1;
        int middleY = height / 2;

        for (int i = 0; i < MAX_RETRY; i++) {
            int[][] maze = createMazeBuffer(width, height);
            boolean[][] visited = new boolean[rows][cols];
            int[] usedWideCorridorBudget = new int[]{0};

            carveByRecursiveBacktracking(cols / 2, rows / 2, visited, maze, null, profile,
                    wideCorridorBudget, usedWideCorridorBudget);

            if (!allLogicalCellsVisited(visited)) {
                continue;
            }

            MazePoint goal = new MazePoint(width / 2, height / 2);
            maze[goal.getY()][goal.getX()] = MazeCellState.ROAD;
            applyBoundaryLockdown(maze);
            openEntriesAtMiddle(maze, middleY);
            openGoalArea(maze, goal, false);

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

    @Override
    public GameState generateChaseMaze(MazeLevelProfile profile) {
        int cols = profile.getLogicalCols();
        int rows = profile.getLogicalRows();
        int width = cols * 2 + 1;
        int height = rows * 2 + 1;
        MazePoint goal = new MazePoint(width / 2, height / 2);

        for (int i = 0; i < MAX_RETRY; i++) {
            int[][] maze = createMazeBuffer(width, height);
            boolean[][] visited = new boolean[rows][cols];
            int[] usedWideCorridorBudget = new int[]{0};

            carveByRecursiveBacktracking(cols / 2, rows / 2, visited, maze, null, profile, 4, usedWideCorridorBudget);

            if (!allLogicalCellsVisited(visited)) {
                continue;
            }

            applyBoundaryLockdown(maze);
            openGoalArea(maze, goal, true);

            MazePoint startB = pickChaseRunnerSpawn(maze, goal);
            if (startB == null) {
                continue;
            }

            MazePoint startA = pickChaserSpawn(maze, startB);
            if (startA == null) {
                continue;
            }

            if (bfsShortestDistance(maze, startB, goal) <= 0) {
                continue;
            }

            return new GameState(maze, goal, startA, startB);
        }
        return buildFallbackChaseGameState(width, height, goal);
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

    private boolean isInside(int x, int y, int[][] maze) {
        return y >= 0 && y < maze.length && x >= 0 && x < maze[0].length;
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

    private void openGoalArea(int[][] maze, MazePoint goal, boolean enableCenterPlaza) {
        if (enableCenterPlaza) {
            for (int y = goal.getY() - 1; y <= goal.getY() + 1; y++) {
                for (int x = goal.getX() - 1; x <= goal.getX() + 1; x++) {
                    if (isInside(x, y, maze)) {
                        maze[y][x] = MazeCellState.ROAD;
                    }
                }
            }
            return;
        }
        for (Direction direction : Direction.values()) {
            int nx = goal.getX() + direction.getDx();
            int ny = goal.getY() + direction.getDy();
            if (!isInside(nx, ny, maze)) {
                continue;
            }
            maze[ny][nx] = MazeCellState.ROAD;
        }
    }

    private boolean isBalancedShortestPath(int shortestA, int shortestB) {
        if (shortestA <= 0 || shortestB <= 0) {
            return false;
        }
        int diff = Math.abs(shortestA - shortestB);
        return diff <= Math.ceil((shortestA + shortestB) * 0.05d);
    }

    private int bfsShortestDistance(int[][] maze, MazePoint start, MazePoint goal) {
        if ((maze[start.getY()][start.getX()] & MazeCellState.WALL) == MazeCellState.WALL) {
            return -1;
        }
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

    private static final int CHASE_MIN_INITIAL_DISTANCE = 8;
    private static final int CHASE_MAX_INITIAL_DISTANCE = 10;

    private MazePoint pickChaseRunnerSpawn(int[][] maze, MazePoint goal) {
        List<MazePoint> allTraversable = new ArrayList<MazePoint>();
        List<Integer> allDistances = new ArrayList<Integer>();
        int farthestDistance = -1;
        for (int y = 1; y < maze.length - 1; y++) {
            for (int x = 1; x < maze[0].length - 1; x++) {
                if ((maze[y][x] & MazeCellState.WALL) == MazeCellState.WALL) {
                    continue;
                }
                if (x == goal.getX() && y == goal.getY()) {
                    continue;
                }
                int dist = bfsShortestDistance(maze, new MazePoint(x, y), goal);
                if (dist > 0) {
                    allTraversable.add(new MazePoint(x, y));
                    allDistances.add(dist);
                    farthestDistance = Math.max(farthestDistance, dist);
                }
            }
        }
        if (allTraversable.isEmpty()) {
            return null;
        }
        List<MazePoint> strongCandidates = new ArrayList<MazePoint>();
        int strongThreshold = Math.max(6, farthestDistance - 6);
        for (int i = 0; i < allTraversable.size(); i++) {
            if (allDistances.get(i) >= strongThreshold) {
                strongCandidates.add(allTraversable.get(i));
            }
        }
        if (!strongCandidates.isEmpty()) {
            Collections.shuffle(strongCandidates, random);
            return strongCandidates.get(0);
        }
        Collections.shuffle(allTraversable, random);
        return allTraversable.get(0);
    }

    private MazePoint pickChaserSpawn(int[][] maze, MazePoint runner) {
        List<MazePoint> idealCandidates = new ArrayList<MazePoint>();
        List<MazePoint> nearCandidates = new ArrayList<MazePoint>();
        List<MazePoint> fallbackCandidates = new ArrayList<MazePoint>();
        for (int y = 1; y < maze.length - 1; y++) {
            for (int x = 1; x < maze[0].length - 1; x++) {
                if ((maze[y][x] & MazeCellState.WALL) == MazeCellState.WALL) {
                    continue;
                }
                if (x == runner.getX() && y == runner.getY()) {
                    continue;
                }
                int toRunner = bfsShortestDistance(maze, new MazePoint(x, y), runner);
                if (toRunner >= CHASE_MIN_INITIAL_DISTANCE && toRunner <= CHASE_MAX_INITIAL_DISTANCE) {
                    idealCandidates.add(new MazePoint(x, y));
                } else if (toRunner >= CHASE_MIN_INITIAL_DISTANCE - 1 && toRunner <= CHASE_MAX_INITIAL_DISTANCE + 1) {
                    nearCandidates.add(new MazePoint(x, y));
                } else if (toRunner > 1) {
                    fallbackCandidates.add(new MazePoint(x, y));
                }
            }
        }
        if (!idealCandidates.isEmpty()) {
            Collections.shuffle(idealCandidates, random);
            return idealCandidates.get(0);
        }
        if (!nearCandidates.isEmpty()) {
            Collections.shuffle(nearCandidates, random);
            return nearCandidates.get(0);
        }
        if (!fallbackCandidates.isEmpty()) {
            Collections.shuffle(fallbackCandidates, random);
            return fallbackCandidates.get(0);
        }
        return null;
    }

    private GameState buildFallbackChaseGameState(int width, int height, MazePoint goal) {
        int[][] maze = createMazeBuffer(width, height);
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                if (x % 2 == 1 || y % 2 == 1) {
                    maze[y][x] = MazeCellState.ROAD;
                }
            }
        }
        applyBoundaryLockdown(maze);
        openGoalArea(maze, goal, true);
        MazePoint startB = new MazePoint(width - 2, 1);
        if ((maze[startB.getY()][startB.getX()] & MazeCellState.WALL) == MazeCellState.WALL) {
            startB = new MazePoint(width - 2, height - 2);
        }
        MazePoint startA = pickChaserSpawn(maze, startB);
        if (startA == null) {
            startA = new MazePoint(1, height - 2);
        }
        return new GameState(maze, goal, startA, startB);
    }
}
