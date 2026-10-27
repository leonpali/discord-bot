package dev.leon.discordbot.commands;

import dev.leon.discordbot.game.Card;
import dev.leon.discordbot.game.Player;
import dev.leon.discordbot.game.Round;
import dev.leon.discordbot.game.RoundRecord;
import dev.leon.discordbot.game.RoundView;
import dev.leon.discordbot.game.Showdown;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

final class GameMessages {

    private GameMessages() {}

    static String hand(List<Card> cards) {
        return "Your cards: **" + cards(cards) + "**";
    }

    static String overview(RoundView view) {
        StringBuilder sb = new StringBuilder();
        sb.append("**%s**: react with a number to claim it, then react %s when everyone has one.\n"
                .formatted(title(view.round()), Reactions.READY.getFormatted()));
        appendBoard(sb, view.board());
        appendNumbers(sb, view.byNumber(), view::isReady);

        if (!view.unassigned().isEmpty()) {
            sb.append("No number yet: ").append(names(view.unassigned())).append('\n');
        }
        if (view.lastAction() != null) {
            sb.append("-# ").append(view.lastAction()).append('\n');
        }
        if (!view.history().isEmpty()) {
            sb.append("\n**Earlier rounds**\n").append(history(view.history()));
        }
        return sb.toString();
    }

    static String lockedIn(RoundRecord record) {
        StringBuilder sb = new StringBuilder("**%s: locked in**\n".formatted(title(record.round())));
        appendBoard(sb, record.board());
        appendNumbers(sb, record.byNumber(), player -> false);
        return sb.toString();
    }

    static String showdown(Showdown showdown, List<RoundRecord> history) {
        StringBuilder sb = new StringBuilder("**Showdown**\n");
        appendBoard(sb, showdown.board());
        sb.append('\n');

        for (Showdown.Entry entry : showdown.entries()) {
            sb.append("`%d` **%s**: %s → **%s** (%s)\n".formatted(
                    entry.number(),
                    entry.player().name(),
                    cards(entry.hand()),
                    entry.value().describe(),
                    cards(entry.value().cards())));
        }

        if (showdown.win()) {
            sb.append("\n## 🎉 Win! Your numbers match the hand ranking.\n");
        } else {
            sb.append("\n## ❌ Loss.\nCorrect order, weakest to strongest: ")
                    .append(rankedOrder(showdown.weakestToStrongest()))
                    .append('\n');
        }

        sb.append("\n**Rounds**\n").append(history(history));
        sb.append("-# The host can `!gang-end` to close the game.");
        return sb.toString();
    }

    private static String title(Round round) {
        return "Round %d · %s".formatted(round.number(), round.label());
    }

    private static void appendBoard(StringBuilder sb, List<Card> board) {
        if (!board.isEmpty()) {
            sb.append("Board: **").append(cards(board)).append("**\n");
        }
    }

    private static void appendNumbers(StringBuilder sb, List<Player> byNumber, Predicate<Player> isReady) {
        for (int i = 0; i < byNumber.size(); i++) {
            Player holder = byNumber.get(i);
            sb.append(Reactions.number(i + 1).getFormatted()).append(' ');
            if (holder == null) {
                sb.append('-');
            } else {
                sb.append("**").append(holder.name()).append("**");
                if (isReady.test(holder)) {
                    sb.append(' ').append(Reactions.READY.getFormatted());
                }
            }
            sb.append('\n');
        }
    }

    private static String history(List<RoundRecord> history) {
        StringBuilder sb = new StringBuilder();
        for (RoundRecord record : history) {
            sb.append(record.round().label()).append(": ");
            List<Player> byNumber = record.byNumber();
            for (int i = 0; i < byNumber.size(); i++) {
                if (i > 0) {
                    sb.append(" · ");
                }
                sb.append("`%d` %s".formatted(i + 1, byNumber.get(i).name()));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String rankedOrder(List<Showdown.Entry> ranked) {
        StringBuilder order = new StringBuilder(ranked.getFirst().player().name());
        for (int i = 1; i < ranked.size(); i++) {
            boolean tie = ranked.get(i - 1).value().compareTo(ranked.get(i).value()) == 0;
            order.append(tie ? " = " : " < ").append(ranked.get(i).player().name());
        }
        return order.toString();
    }

    private static String cards(List<Card> cards) {
        return cards.stream().map(Card::toString).collect(Collectors.joining("  "));
    }

    private static String names(List<Player> players) {
        return players.stream().map(Player::name).collect(Collectors.joining(", "));
    }
}
