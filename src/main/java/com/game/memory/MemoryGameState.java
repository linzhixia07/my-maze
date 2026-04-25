package com.game.memory;

import com.game.domain.PlayerId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemoryGameState {
    private final List<MemoryCard> deck;
    private final List<Integer> flippedIndices;
    private final EnumMap<PlayerId, Integer> scoreBoard;
    private final EnumMap<PlayerId, Map<Integer, Integer>> reachTime;
    private int matchedCount;
    private int tieBreakTick;
    private PlayerId currentPlayer;
    private boolean inputLocked;
    private boolean gameOver;
    private PlayerId winner;
    private final int rows;
    private final int cols;
    private boolean pendingResolve;

    public MemoryGameState(List<MemoryCard> deck, int rows, int cols) {
        this.deck = deck;
        this.rows = rows;
        this.cols = cols;
        this.flippedIndices = new ArrayList<Integer>();
        this.scoreBoard = new EnumMap<PlayerId, Integer>(PlayerId.class);
        this.reachTime = new EnumMap<PlayerId, Map<Integer, Integer>>(PlayerId.class);
        this.scoreBoard.put(PlayerId.A, 0);
        this.scoreBoard.put(PlayerId.B, 0);
        this.reachTime.put(PlayerId.A, new HashMap<Integer, Integer>());
        this.reachTime.put(PlayerId.B, new HashMap<Integer, Integer>());
        this.currentPlayer = PlayerId.A;
        this.inputLocked = false;
        this.gameOver = false;
        this.pendingResolve = false;
    }

    public List<MemoryCard> getDeck() {
        return deck;
    }

    public List<Integer> getFlippedIndices() {
        return flippedIndices;
    }

    public Map<PlayerId, Integer> getScoreBoard() {
        return scoreBoard;
    }

    public Map<PlayerId, Map<Integer, Integer>> getReachTime() {
        return reachTime;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public void setMatchedCount(int matchedCount) {
        this.matchedCount = matchedCount;
    }

    public int getTieBreakTick() {
        return tieBreakTick;
    }

    public void setTieBreakTick(int tieBreakTick) {
        this.tieBreakTick = tieBreakTick;
    }

    public PlayerId getCurrentPlayer() {
        return currentPlayer;
    }

    public void setCurrentPlayer(PlayerId currentPlayer) {
        this.currentPlayer = currentPlayer;
    }

    public boolean isInputLocked() {
        return inputLocked;
    }

    public void setInputLocked(boolean inputLocked) {
        this.inputLocked = inputLocked;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
    }

    public PlayerId getWinner() {
        return winner;
    }

    public void setWinner(PlayerId winner) {
        this.winner = winner;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public boolean isPendingResolve() {
        return pendingResolve;
    }

    public void setPendingResolve(boolean pendingResolve) {
        this.pendingResolve = pendingResolve;
    }
}
