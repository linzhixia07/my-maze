package com.game.memory;

public class MemoryCard {
    private final String id;
    private final String word;
    private boolean flipped;
    private boolean matched;

    public MemoryCard(String id, String word) {
        this.id = id;
        this.word = word;
    }

    public String getId() {
        return id;
    }

    public String getWord() {
        return word;
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }
}
