package dev.leon.discordbot.game;

public enum Round {
    PRE_FLOP("Pre-flop", 0),
    FLOP("Flop", 3),
    TURN("Turn", 1),
    RIVER("River", 1);

    private final String label;
    private final int cardsRevealed;

    Round(String label, int cardsRevealed) {
        this.label = label;
        this.cardsRevealed = cardsRevealed;
    }

    public String label() {
        return label;
    }

    public int number() {
        return ordinal() + 1;
    }

    public int cardsRevealed() {
        return cardsRevealed;
    }

    public boolean isLast() {
        return this == RIVER;
    }

    public Round next() {
        if (isLast()) {
            throw new IllegalStateException(this + " is the last round");
        }
        return values()[ordinal() + 1];
    }
}
