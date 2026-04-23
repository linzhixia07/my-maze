package com.maze.domain;

/**
 * Data-only description of one maze difficulty tier: logical grid size and carving biases.
 * The canonical level table lives in {@link com.maze.service.MazeService}.
 */
public final class MazeLevelProfile {
    private final int logicalCols;
    private final int logicalRows;
    private final int straightContinuationDeduction;
    private final int straightBonusMultiplier;
    private final int turnExploreBonus;
    private final double postShuffleSwapProbability;
    private final int scoreJitterExclusiveMax;

    public MazeLevelProfile(int logicalCols, int logicalRows, int straightContinuationDeduction,
                            int straightBonusMultiplier, int turnExploreBonus,
                            double postShuffleSwapProbability, int scoreJitterExclusiveMax) {
        this.logicalCols = logicalCols;
        this.logicalRows = logicalRows;
        this.straightContinuationDeduction = straightContinuationDeduction;
        this.straightBonusMultiplier = straightBonusMultiplier;
        this.turnExploreBonus = turnExploreBonus;
        this.postShuffleSwapProbability = postShuffleSwapProbability;
        this.scoreJitterExclusiveMax = scoreJitterExclusiveMax;
    }

    public MazeLevelProfile withLogicalGrid(int newLogicalCols, int newLogicalRows) {
        return new MazeLevelProfile(newLogicalCols, newLogicalRows, straightContinuationDeduction,
                straightBonusMultiplier, turnExploreBonus, postShuffleSwapProbability, scoreJitterExclusiveMax);
    }

    public int getLogicalCols() {
        return logicalCols;
    }

    public int getLogicalRows() {
        return logicalRows;
    }

    public int getStraightContinuationDeduction() {
        return straightContinuationDeduction;
    }

    public int getStraightBonusMultiplier() {
        return straightBonusMultiplier;
    }

    public int getTurnExploreBonus() {
        return turnExploreBonus;
    }

    public double getPostShuffleSwapProbability() {
        return postShuffleSwapProbability;
    }

    public int getScoreJitterExclusiveMax() {
        return scoreJitterExclusiveMax;
    }
}
