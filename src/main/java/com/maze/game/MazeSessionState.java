package com.maze.game;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MazeSessionState {
    private final int[][] maze;
    private final Player player;
    private final MazePoint start;
    private final MazePoint goal;
    private final Set<MazePoint> breadcrumbSet;

    public MazeSessionState(int[][] maze, MazePoint start, MazePoint goal) {
        this.maze = maze;
        this.start = start;
        this.goal = goal;
        this.player = new Player(start.getX(), start.getY());
        this.breadcrumbSet = new LinkedHashSet<MazePoint>();
        this.maze[start.getY()][start.getX()] = this.maze[start.getY()][start.getX()] | MazeCellState.CURRENT;
        markVisited(start.getX(), start.getY());
    }

    public int[][] getMaze() {
        return maze;
    }

    public Player getPlayer() {
        return player;
    }

    public MazePoint getStart() {
        return start;
    }

    public MazePoint getGoal() {
        return goal;
    }

    public List<MazePoint> getBreadcrumbs() {
        return new ArrayList<MazePoint>(breadcrumbSet);
    }

    public void markVisited(int x, int y) {
        maze[y][x] = maze[y][x] | MazeCellState.VISITED;
        breadcrumbSet.add(new MazePoint(x, y));
    }

    public void movePlayerTo(int x, int y) {
        clearCurrentFlag(player.getX(), player.getY());
        player.moveTo(x, y);
        maze[y][x] = maze[y][x] | MazeCellState.CURRENT;
        markVisited(x, y);
    }

    private void clearCurrentFlag(int x, int y) {
        maze[y][x] = maze[y][x] & ~MazeCellState.CURRENT;
    }
}
