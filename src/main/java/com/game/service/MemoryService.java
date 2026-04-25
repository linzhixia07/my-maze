package com.game.service;

import com.game.domain.PlayerId;
import com.game.memory.MemoryCard;
import com.game.memory.MemoryGameState;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class MemoryService {
    private static final int MIN_DIMENSION = 2;
    private static final int MAX_DIMENSION = 10;
    private final Random random = new SecureRandom();

    public MemoryGameState start(int rows, int cols, List<String> vocabulary) {
        int normalizedRows = normalizeDimension(rows);
        int normalizedCols = normalizeDimension(cols);
        int total = normalizedRows * normalizedCols;
        if (total % 2 != 0) {
            throw new IllegalArgumentException("Total card count must be even.");
        }

        List<String> pool = vocabulary == null ? Collections.<String>emptyList() : vocabulary;
        int pairCount = total / 2;
        if (pairCount > pool.size()) {
            throw new IllegalArgumentException("Vocabulary is not enough for requested board size.");
        }

        List<String> picked = new ArrayList<String>(pool);
        shuffle(picked);
        picked = picked.subList(0, pairCount);

        List<MemoryCard> deck = new ArrayList<MemoryCard>();
        for (int i = 0; i < picked.size(); i++) {
            String word = picked.get(i);
            deck.add(new MemoryCard(word + "-1-" + i, word));
            deck.add(new MemoryCard(word + "-2-" + i, word));
        }
        shuffle(deck);
        return new MemoryGameState(deck, normalizedRows, normalizedCols);
    }

    public TurnResult flip(MemoryGameState state, int index) {
        if (state.isGameOver() || state.isInputLocked() || state.isPendingResolve()) {
            return TurnResult.noop();
        }
        if (index < 0 || index >= state.getDeck().size()) {
            return TurnResult.noop();
        }
        MemoryCard card = state.getDeck().get(index);
        if (card.isMatched() || card.isFlipped() || state.getFlippedIndices().size() >= 2) {
            return TurnResult.noop();
        }

        card.setFlipped(true);
        state.getFlippedIndices().add(index);
        if (state.getFlippedIndices().size() < 2) {
            return TurnResult.firstFlip();
        }

        int firstIndex = state.getFlippedIndices().get(0);
        int secondIndex = state.getFlippedIndices().get(1);
        MemoryCard first = state.getDeck().get(firstIndex);
        MemoryCard second = state.getDeck().get(secondIndex);
        if (first.getWord().equals(second.getWord())) {
            first.setMatched(true);
            second.setMatched(true);
            state.setMatchedCount(state.getMatchedCount() + 2);
            PlayerId current = state.getCurrentPlayer();
            int score = state.getScoreBoard().get(current) + 1;
            state.getScoreBoard().put(current, score);
            state.setTieBreakTick(state.getTieBreakTick() + 1);
            Map<Integer, Integer> playerReach = state.getReachTime().get(current);
            if (!playerReach.containsKey(score)) {
                playerReach.put(score, state.getTieBreakTick());
            }

            state.getFlippedIndices().clear();
            switchPlayer(state);
            finishIfNeeded(state);
            return TurnResult.match();
        }

        state.setPendingResolve(true);
        state.setInputLocked(true);
        return TurnResult.mismatch();
    }

    public void resolveMismatch(MemoryGameState state) {
        if (!state.isPendingResolve() || state.getFlippedIndices().size() != 2) {
            return;
        }
        int firstIndex = state.getFlippedIndices().get(0);
        int secondIndex = state.getFlippedIndices().get(1);
        state.getDeck().get(firstIndex).setFlipped(false);
        state.getDeck().get(secondIndex).setFlipped(false);
        state.getFlippedIndices().clear();
        state.setPendingResolve(false);
        state.setInputLocked(false);
        switchPlayer(state);
    }

    public List<String> loadVocabulary() throws IOException {
        ClassPathResource resource = new ClassPathResource("config/dict.txt");
        String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        List<String> words = new ArrayList<String>();
        String[] lines = content.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String word = lines[i].trim();
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }

    private int normalizeDimension(int v) {
        if (v < MIN_DIMENSION) {
            return MIN_DIMENSION;
        }
        if (v > MAX_DIMENSION) {
            return MAX_DIMENSION;
        }
        return v;
    }

    private void switchPlayer(MemoryGameState state) {
        state.setCurrentPlayer(state.getCurrentPlayer() == PlayerId.A ? PlayerId.B : PlayerId.A);
    }

    private void finishIfNeeded(MemoryGameState state) {
        if (state.getMatchedCount() != state.getDeck().size()) {
            return;
        }

        int scoreA = state.getScoreBoard().get(PlayerId.A);
        int scoreB = state.getScoreBoard().get(PlayerId.B);
        PlayerId winner = PlayerId.A;
        if (scoreB > scoreA) {
            winner = PlayerId.B;
        } else if (scoreA == scoreB) {
            Integer reachA = state.getReachTime().get(PlayerId.A).get(scoreA);
            Integer reachB = state.getReachTime().get(PlayerId.B).get(scoreB);
            int safeA = reachA == null ? Integer.MAX_VALUE : reachA;
            int safeB = reachB == null ? Integer.MAX_VALUE : reachB;
            winner = safeA <= safeB ? PlayerId.A : PlayerId.B;
        }
        state.setWinner(winner);
        state.setGameOver(true);
        state.setInputLocked(true);
        state.setPendingResolve(false);
    }

    private <T> void shuffle(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T temp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, temp);
        }
    }

    public static class TurnResult {
        private final boolean changed;
        private final boolean mismatchNeedsResolve;

        private TurnResult(boolean changed, boolean mismatchNeedsResolve) {
            this.changed = changed;
            this.mismatchNeedsResolve = mismatchNeedsResolve;
        }

        public static TurnResult noop() {
            return new TurnResult(false, false);
        }

        public static TurnResult firstFlip() {
            return new TurnResult(true, false);
        }

        public static TurnResult match() {
            return new TurnResult(true, false);
        }

        public static TurnResult mismatch() {
            return new TurnResult(true, true);
        }

        public boolean isChanged() {
            return changed;
        }

        public boolean isMismatchNeedsResolve() {
            return mismatchNeedsResolve;
        }
    }
}
