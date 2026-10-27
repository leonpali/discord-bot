package dev.leon.discordbot.game;

public enum Rank {
    TWO("2", "2"),
    THREE("3", "3"),
    FOUR("4", "4"),
    FIVE("5", "5"),
    SIX("6", "6"),
    SEVEN("7", "7"),
    EIGHT("8", "8"),
    NINE("9", "9"),
    TEN("10", "10"),
    JACK("J", "Jack"),
    QUEEN("Q", "Queen"),
    KING("K", "King"),
    ACE("A", "Ace");

    private final String label;
    private final String word;

    Rank(String label, String word) {
        this.label = label;
        this.word = word;
    }

    public String label() {
        return label;
    }

    public String word() {
        return word;
    }

    public String plural() {
        return word + "s";
    }
}
