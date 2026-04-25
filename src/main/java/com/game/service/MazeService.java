package com.game.service;

import com.game.domain.Direction;
import com.game.domain.GameState;
import com.game.domain.MazeCellState;
import com.game.domain.MazeLevelProfile;
import com.game.domain.Player;
import com.game.domain.PlayerId;
import com.game.generator.MazeGenerator;
import com.game.generator.RecursiveBacktrackGenerator;
import com.game.strategy.ChaseModeStrategy;
import com.game.strategy.GameModeStrategy;
import com.game.strategy.TwinRaceStrategy;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class MazeService {
    public static final String MODE_TWIN_RACE = "TWIN_RACE";
    public static final String MODE_CHASE = "CHASE";
    private static final int CHASE_FIXED_LOGICAL_SIZE = 19;
    private static final int DEFAULT_COLS = 21;
    private static final int DEFAULT_ROWS = 21;
    private static final int MIN_SIZE = 11;
    private static final int MAX_SIZE = 29;
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 5;
    public static final int DEFAULT_LEVEL = 4;

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

    private final MazeGenerator mazeGenerator;
    private final GameModeStrategy twinRaceStrategy;
    private final GameModeStrategy chaseModeStrategy;

    public MazeService() {
        this.mazeGenerator = new RecursiveBacktrackGenerator();
        this.twinRaceStrategy = new TwinRaceStrategy();
        this.chaseModeStrategy = new ChaseModeStrategy();
    }

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
        return generate(requestedCols, requestedRows, null, MODE_TWIN_RACE);
    }

    public GameState generate(Integer requestedCols, Integer requestedRows, Integer level) {
        return generate(requestedCols, requestedRows, level, MODE_TWIN_RACE);
    }

    public GameState generate(Integer requestedCols, Integer requestedRows, Integer level, String gameMode) {
        String resolvedMode = normalizeGameMode(gameMode);

        if (MODE_CHASE.equals(resolvedMode)) {
            MazeLevelProfile chaseProfile = LEVEL_PROFILES_BY_NUMBER.get(DEFAULT_LEVEL)
                    .withLogicalGrid(CHASE_FIXED_LOGICAL_SIZE, CHASE_FIXED_LOGICAL_SIZE);
            return mazeGenerator.generateChaseMaze(chaseProfile);
        }

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
        return mazeGenerator.generateRaceMaze(profile, wideCorridorBudget);
    }

    public MoveResult tryMove(GameState state, PlayerId playerId, Direction direction) {
        return tryMove(state, playerId, direction, MODE_TWIN_RACE, null);
    }

    public MoveResult tryMove(GameState state, PlayerId playerId, Direction direction, String gameMode) {
        return tryMove(state, playerId, direction, gameMode, null);
    }

    public MoveResult tryMove(GameState state, PlayerId playerId, Direction direction, String gameMode, PlayerId chaseChaserId) {
        String resolvedMode = normalizeGameMode(gameMode);

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

        GameModeStrategy strategy = MODE_CHASE.equals(resolvedMode) ? chaseModeStrategy : twinRaceStrategy;
        PlayerId winner = strategy.checkWinner(state, playerId, nextX, nextY, chaseChaserId);

        if (winner != null) {
            state.setWinner(winner);
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

    private int getWideCorridorBudget(int level) {
        if (level <= 2) {
            return 1;
        }
        if (level <= 4) {
            return 2;
        }
        return 3;
    }

    private String normalizeGameMode(String gameMode) {
        if (gameMode == null) {
            return MODE_TWIN_RACE;
        }
        String normalized = gameMode.trim().toUpperCase();
        if (MODE_CHASE.equals(normalized)) {
            return MODE_CHASE;
        }
        return MODE_TWIN_RACE;
    }
}
