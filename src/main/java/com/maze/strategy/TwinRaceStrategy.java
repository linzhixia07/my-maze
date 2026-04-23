package com.maze.strategy;

import com.maze.domain.GameState;
import com.maze.domain.PlayerId;

/**
 * 双人竞速模式：谁先到达终点谁获胜
 */
public class TwinRaceStrategy implements GameModeStrategy {

    @Override
    public PlayerId checkWinner(GameState state, PlayerId playerId, int nextX, int nextY, PlayerId chaserId) {
        if (nextX == state.getGoal().getX() && nextY == state.getGoal().getY()) {
            return playerId;
        }
        return null;
    }

    @Override
    public String getModeName() {
        return "TWIN_RACE";
    }
}
