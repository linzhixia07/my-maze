package com.game.strategy;

import com.game.domain.GameState;
import com.game.domain.Player;
import com.game.domain.PlayerId;

/**
 * 抓人模式：
 * - 抓人方抓到逃跑方则获胜
 * - 逃跑方到达终点则获胜
 */
public class ChaseModeStrategy implements GameModeStrategy {

    @Override
    public PlayerId checkWinner(GameState state, PlayerId playerId, int nextX, int nextY, PlayerId chaserId) {
        if (chaserId == null) {
            chaserId = PlayerId.A;
        }
        PlayerId runnerId = chaserId == PlayerId.A ? PlayerId.B : PlayerId.A;

        Player playerA = state.getPlayer(PlayerId.A);
        Player playerB = state.getPlayer(PlayerId.B);

        // 抓人方抓到逃跑方
        if (playerA.getX() == playerB.getX() && playerA.getY() == playerB.getY()) {
            return chaserId;
        }

        // 逃跑方到达终点
        if (playerId == runnerId && nextX == state.getGoal().getX() && nextY == state.getGoal().getY()) {
            return runnerId;
        }

        return null;
    }

    @Override
    public String getModeName() {
        return "CHASE";
    }
}
