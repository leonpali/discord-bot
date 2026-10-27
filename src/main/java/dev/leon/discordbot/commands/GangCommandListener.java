package dev.leon.discordbot.commands;

import dev.leon.discordbot.game.Card;
import dev.leon.discordbot.game.GameManager;
import dev.leon.discordbot.game.GameSession;
import dev.leon.discordbot.game.GameSession.TakeResult;
import dev.leon.discordbot.game.Player;
import dev.leon.discordbot.game.Showdown;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GangCommandListener extends ListenerAdapter {
    private static final String READY_EMOJI = "✅";

    private final GameManager games = new GameManager();

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) {
            return;
        }

        String[] parts = event.getMessage().getContentRaw().strip().toLowerCase().split("\\s+");
        switch (parts[0]) {
            case "!gang-create" -> create(event);
            case "!gang-join" -> join(event);
            case "!gang-start" -> start(event);
            case "!gang-end" -> end(event);
            case "!take" -> take(event, parts);
            default -> {}
        }
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        onReadyReaction(event, true);
    }

    @Override
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        onReadyReaction(event, false);
    }

    private void create(MessageReceivedEvent event) {
        Player host = playerOf(event);
        GameSession session = games.create(event.getChannel().getIdLong(), host);
        if (session == null) {
            reply(event, "A game is already running in this channel. Type `!gang-join` to join it.");
            return;
        }
        reply(event, "**%s** created a game! Type `!gang-join` to join (max %d players). Host types `!gang-start` to deal."
                .formatted(host.name(), GameSession.MAX_PLAYERS));
    }

    private void join(MessageReceivedEvent event) {
        GameSession session = games.get(event.getChannel().getIdLong());
        if (session == null) {
            reply(event, "No game in this channel. Type `!gang-create` to start one.");
            return;
        }
        if (session.isStarted()) {
            reply(event, "This game has already started.");
            return;
        }

        Player player = playerOf(event);
        if (session.hasPlayer(player.userId())) {
            reply(event, "You're already in the game.");
            return;
        }
        if (!session.join(player)) {
            reply(event, "The game is full (%d/%d players).".formatted(GameSession.MAX_PLAYERS, GameSession.MAX_PLAYERS));
            return;
        }
        reply(event, "**%s** joined the game (%d/%d players)."
                .formatted(player.name(), session.players().size(), GameSession.MAX_PLAYERS));
    }

    private void start(MessageReceivedEvent event) {
        GameSession session = games.get(event.getChannel().getIdLong());
        if (session == null) {
            reply(event, "No game in this channel. Type `!gang-create` to start one.");
            return;
        }
        if (session.host().userId() != event.getAuthor().getIdLong()) {
            reply(event, "Only the host (**%s**) can start the round.".formatted(session.host().name()));
            return;
        }
        if (session.isStarted()) {
            reply(event, "The game is already running. Use `!gang-end` to stop it.");
            return;
        }
        if (session.players().size() < GameSession.MIN_PLAYERS) {
            reply(event, "Need at least %d players to start.".formatted(GameSession.MIN_PLAYERS));
            return;
        }

        Map<Player, List<Card>> hands = session.dealHands();
        String names = hands.keySet().stream().map(Player::name).collect(Collectors.joining(", "));
        reply(event, "Dealing cards to %s. Check your DMs!".formatted(names));

        MessageChannel channel = event.getChannel();
        hands.forEach((player, cards) -> sendHand(event, channel, player, cards));
        postOverview(channel, session);
    }

    private void end(MessageReceivedEvent event) {
        long channelId = event.getChannel().getIdLong();
        GameSession session = games.get(channelId);
        if (session == null) {
            reply(event, "No game in this channel.");
            return;
        }
        if (session.host().userId() != event.getAuthor().getIdLong()) {
            reply(event, "Only the host (**%s**) can end the game.".formatted(session.host().name()));
            return;
        }
        games.end(channelId);
        reply(event, "Game ended.");
    }

    private void take(MessageReceivedEvent event, String[] parts) {
        GameSession session = games.get(event.getChannel().getIdLong());
        if (session == null || !session.isStarted() || session.isFinished()) {
            reply(event, "No round is running in this channel.");
            return;
        }
        long userId = event.getAuthor().getIdLong();
        if (!session.hasPlayer(userId)) {
            reply(event, "You're not in this game.");
            return;
        }

        int number;
        try {
            number = Integer.parseInt(parts.length > 1 ? parts[1] : "");
        } catch (NumberFormatException e) {
            reply(event, "Usage: `!take <number>`");
            return;
        }

        switch (session.take(userId, number)) {
            case TakeResult.OutOfRange(int max) -> reply(event, "Pick a number from 1 to %d.".formatted(max));
            case TakeResult.AlreadyYours() -> reply(event, "You already have %d.".formatted(number));
            case TakeResult.Taken() -> {
                deleteIfPermitted(event);
                refreshOverview(event.getChannel(), session);
                advanceIfComplete(event.getChannel(), session);
            }
        }
    }

    private void onReadyReaction(GenericMessageReactionEvent event, boolean isReady) {
        if (!event.isFromGuild() || !isReadyEmoji(event.getEmoji())) {
            return;
        }
        long userId = event.getUserIdLong();
        if (userId == event.getJDA().getSelfUser().getIdLong()) {
            return;
        }
        GameSession session = games.get(event.getChannel().getIdLong());
        if (session == null
                || session.overviewMessageId() != event.getMessageIdLong()
                || !session.hasPlayer(userId)) {
            return;
        }

        session.setReady(userId, isReady);
        refreshOverview(event.getChannel(), session);
        advanceIfComplete(event.getChannel(), session);
    }

    private void advanceIfComplete(MessageChannel channel, GameSession session) {
        boolean finished;
        synchronized (session) {
            if (!session.isRoundComplete()) {
                return;
            }
            channel.editMessageById(session.overviewMessageId(), renderOverview(session, true)).queue();
            finished = session.isFinalRound();
            if (finished) {
                session.finish();
            } else {
                session.nextRound();
            }
        }
        if (finished) {
            channel.sendMessage(renderShowdown(session)).queue();
        } else {
            postOverview(channel, session);
        }
    }

    private void postOverview(MessageChannel channel, GameSession session) {
        int round = session.round();
        channel.sendMessage(renderOverview(session, false)).queue(message -> {
            message.addReaction(Emoji.fromUnicode(READY_EMOJI)).queue();
            synchronized (session) {
                if (session.round() != round) {
                    return;
                }
                session.setOverviewMessageId(message.getIdLong());
                String current = renderOverview(session, false);
                if (!current.equals(message.getContentRaw())) {
                    message.editMessage(current).queue();
                }
            }
        });
    }

    private void refreshOverview(MessageChannel channel, GameSession session) {
        synchronized (session) {
            long messageId = session.overviewMessageId();
            if (messageId != 0) {
                channel.editMessageById(messageId, renderOverview(session, false)).queue();
            }
        }
    }

    private static String renderOverview(GameSession session, boolean lockedIn) {
        synchronized (session) {
            List<Player> players = session.players();
            StringBuilder sb = new StringBuilder();
            String title = "Round %d · %s".formatted(session.round(), stageName(session.round()));
            if (lockedIn) {
                sb.append("**%s: locked in**\n".formatted(title));
            } else {
                sb.append("**%s**: claim a number with `!take <number>`, then react %s when everyone has one.\n"
                        .formatted(title, READY_EMOJI));
            }

            List<Card> board = session.board();
            if (!board.isEmpty()) {
                sb.append("Board: **")
                        .append(board.stream().map(Card::toString).collect(Collectors.joining("  ")))
                        .append("**\n");
            }

            for (int number = 1; number <= players.size(); number++) {
                Player holder = session.holderOf(number);
                sb.append("`%d` ".formatted(number));
                if (holder == null) {
                    sb.append("-");
                } else {
                    sb.append("**").append(holder.name()).append("**");
                    if (!lockedIn && session.isReady(holder.userId())) {
                        sb.append(" ").append(READY_EMOJI);
                    }
                }
                sb.append('\n');
            }

            if (!lockedIn) {
                String unassigned = players.stream()
                        .filter(p -> !session.hasNumber(p.userId()))
                        .map(Player::name)
                        .collect(Collectors.joining(", "));
                if (!unassigned.isEmpty()) {
                    sb.append("No number yet: ").append(unassigned).append('\n');
                }
                if (session.lastAction() != null) {
                    sb.append("-# ").append(session.lastAction()).append('\n');
                }
                if (!session.history().isEmpty()) {
                    sb.append("\n**Earlier rounds**\n").append(renderHistory(session));
                }
            }
            return sb.toString();
        }
    }

    private static String renderShowdown(GameSession session) {
        Showdown showdown = session.showdown();
        StringBuilder sb = new StringBuilder("**Showdown**\n");
        sb.append("Board: **").append(joinCards(session.board())).append("**\n\n");

        for (Showdown.Entry entry : showdown.entries()) {
            sb.append("`%d` **%s**: %s → **%s** (%s)\n".formatted(
                    entry.number(),
                    entry.player().name(),
                    joinCards(entry.hand()),
                    entry.value().describe(),
                    joinCards(entry.value().cards())));
        }

        if (showdown.win()) {
            sb.append("\n## 🎉 Win! Your numbers match the hand ranking.\n");
        } else {
            List<Showdown.Entry> ranked = new ArrayList<>(showdown.entries());
            ranked.sort(Comparator.comparing(Showdown.Entry::value));
            StringBuilder order = new StringBuilder(ranked.getFirst().player().name());
            for (int i = 1; i < ranked.size(); i++) {
                boolean tie = ranked.get(i - 1).value().compareTo(ranked.get(i).value()) == 0;
                order.append(tie ? " = " : " < ").append(ranked.get(i).player().name());
            }
            sb.append("\n## ❌ Loss.\nCorrect order, weakest to strongest: ").append(order).append('\n');
        }

        sb.append("\n**Rounds**\n").append(renderHistory(session));
        sb.append("-# The host can `!gang-end` to close the game.");
        return sb.toString();
    }

    private static String joinCards(List<Card> cards) {
        return cards.stream().map(Card::toString).collect(Collectors.joining("  "));
    }

    private static String renderHistory(GameSession session) {
        StringBuilder sb = new StringBuilder();
        List<List<Player>> history = session.history();
        for (int i = 0; i < history.size(); i++) {
            List<Player> byNumber = history.get(i);
            sb.append(stageName(i + 1)).append(": ");
            for (int n = 0; n < byNumber.size(); n++) {
                Player holder = byNumber.get(n);
                if (n > 0) {
                    sb.append(" · ");
                }
                sb.append("`%d` %s".formatted(n + 1, holder == null ? "-" : holder.name()));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String stageName(int round) {
        return switch (round) {
            case 1 -> "Pre-flop";
            case 2 -> "Flop";
            case 3 -> "Turn";
            case 4 -> "River";
            default -> "";
        };
    }

    private void sendHand(MessageReceivedEvent event, MessageChannel channel, Player player, List<Card> cards) {
        String hand = cards.stream().map(Card::toString).collect(Collectors.joining("  "));
        event.getJDA().retrieveUserById(player.userId())
                .flatMap(user -> user.openPrivateChannel())
                .flatMap(dm -> dm.sendMessage("Your cards: **" + hand + "**"))
                .queue(
                        ok -> {},
                        error -> channel.sendMessage(
                                        "Couldn't DM **%s**. Enable DMs from server members and ask the host to deal again."
                                                .formatted(player.name()))
                                .queue());
    }

    private static void deleteIfPermitted(MessageReceivedEvent event) {
        if (event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_MANAGE)) {
            event.getMessage().delete().queue();
        }
    }

    private static boolean isReadyEmoji(EmojiUnion emoji) {
        return emoji.getType() == Emoji.Type.UNICODE && emoji.getName().equals(READY_EMOJI);
    }

    private static Player playerOf(MessageReceivedEvent event) {
        String name = event.getMember() != null
                ? event.getMember().getEffectiveName()
                : event.getAuthor().getEffectiveName();
        return new Player(event.getAuthor().getIdLong(), name);
    }

    private static void reply(MessageReceivedEvent event, String message) {
        event.getMessage().reply(message).mentionRepliedUser(false).queue();
    }
}
