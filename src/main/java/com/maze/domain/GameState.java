package com.maze.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class GameState {
    private final int[][] maze;
    private final MazePoint goal;
    private final EnumMap<PlayerId, Player> players;
    private final EnumMap<PlayerId, Set<MazePoint>> breadcrumbs;
    private final ReentrantLock lock;
    private volatile boolean gameOver;
    private volatile PlayerId winner;

    public GameState(int[][] maze, MazePoint goal, MazePoint startA, MazePoint startB) {
        this.maze = maze;
        this.goal = goal;
        this.players = new EnumMap<PlayerId, Player>(PlayerId.class);
        this.breadcrumbs = new EnumMap<PlayerId, Set<MazePoint>>(PlayerId.class);
        this.lock = new ReentrantLock();
        this.players.put(PlayerId.A, new Player(startA.getX(), startA.getY()));
        this.players.put(PlayerId.B, new Player(startB.getX(), startB.getY()));
        this.breadcrumbs.put(PlayerId.A, new LinkedHashSet<MazePoint>());
        this.breadcrumbs.put(PlayerId.B, new LinkedHashSet<MazePoint>());
        markVisited(PlayerId.A, startA.getX(), startA.getY());
        markVisited(PlayerId.B, startB.getX(), startB.getY());
        this.gameOver = false;
    }

    public int[][] getMaze() {
        return maze;
    }

    public MazePoint getGoal() {
        return goal;
    }

    public Map<PlayerId, Player> getPlayers() {
        return Collections.unmodifiableMap(players);
    }

    public Map<PlayerId, List<MazePoint>> getBreadcrumbs() {
        return breadcrumbs.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new ArrayList<MazePoint>(e.getValue()),
                        (left, right) -> left,
                        () -> new EnumMap<PlayerId, List<MazePoint>>(PlayerId.class)
                ));
    }

    public Player getPlayer(PlayerId playerId) {
        return players.get(playerId);
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public PlayerId getWinner() {
        return winner;
    }

    public void markVisited(PlayerId playerId, int x, int y) {
        maze[y][x] = maze[y][x] | MazeCellState.VISITED;
        breadcrumbs.get(playerId).add(new MazePoint(x, y));
    }

    public void movePlayer(PlayerId playerId, int x, int y) {
        players.get(playerId).moveTo(x, y);
        markVisited(playerId, x, y);
    }

    public boolean isOccupiedByOther(PlayerId playerId, int x, int y) {
        PlayerId other = playerId == PlayerId.A ? PlayerId.B : PlayerId.A;
        Player otherPlayer = players.get(other);
        return otherPlayer.getX() == x && otherPlayer.getY() == y;
    }

    public void setWinner(PlayerId winner) {
        this.winner = winner;
        this.gameOver = true;
    }
}
