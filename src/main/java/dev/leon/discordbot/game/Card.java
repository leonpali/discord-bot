package dev.leon.discordbot.game;

public record Card(Rank rank, Suit suit) {

    @Override
    public String toString() {
        return rank.label() + suit.symbol();
    }
}
