package com.maze.generator;

import com.maze.domain.GameState;
import com.maze.domain.MazeLevelProfile;

/**
 * 迷宫生成器接口
 */
public interface MazeGenerator {

    /**
     * 生成标准竞速模式迷宫
     *
     * @param profile 关卡配置
     * @param wideCorridorBudget 宽走廊预算
     * @return 游戏状态
     */
    GameState generateRaceMaze(MazeLevelProfile profile, int wideCorridorBudget);

    /**
     * 生成抓人模式迷宫
     *
     * @param profile 关卡配置
     * @return 游戏状态，包含特殊的起点位置
     */
    GameState generateChaseMaze(MazeLevelProfile profile);
}
