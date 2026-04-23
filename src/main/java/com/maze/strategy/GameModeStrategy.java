package com.maze.strategy;

import com.maze.domain.GameState;
import com.maze.domain.PlayerId;

/**
 * 游戏模式策略接口
 */
public interface GameModeStrategy {

    /**
     * 检查移动后的胜负状态
     *
     * @param state 游戏状态
     * @param playerId 移动的玩家
     * @param nextX 目标位置X
     * @param nextY 目标位置Y
     * @param chaserId 抓人模式中的抓人方（可选）
     * @return 获胜者，如果游戏未结束返回 null
     */
    PlayerId checkWinner(GameState state, PlayerId playerId, int nextX, int nextY, PlayerId chaserId);

    /**
     * 获取模式名称
     */
    String getModeName();
}
