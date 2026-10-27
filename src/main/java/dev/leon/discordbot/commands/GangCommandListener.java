package dev.leon.discordbot.commands;

import dev.leon.discordbot.game.Card;
import dev.leon.discordbot.game.GameManager;
import dev.leon.discordbot.game.GameSession;
import dev.leon.discordbot.game.GameSession.Advance;
import dev.leon.discordbot.game.GameSession.TakeResult;
import dev.leon.discordbot.game.Player;
import dev.leon.discordbot.game.RoundView;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GangCommandListener extends ListenerAdapter {

    private final GameManager games = new GameManager();

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) {
            return;
        }

        String command = event.getMessage().getContentRaw().strip().toLowerCase();
        switch (command) {
            case "!gang-create" -> create(event);
            case "!gang-join" -> join(event);
            case "!gang-start" -> start(event);
            case "!gang-end" -> end(event);
            default -> {}
        }
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        onOverviewReaction(event, true);
    }

    @Override
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        onOverviewReaction(event, false);
    }

    private void create(MessageReceivedEvent event) {
        Player host = playerOf(event);
        if (games.create(event.getChannel().getIdLong(), host) == null) {
            reply(event, "A game is already running in this channel. Type `!gang-join` to join it.");
            return;
        }
        reply(event, "**%s** created a game! Type `!gang-join` to join (max %d players). Host types `!gang-start` to deal."
                .formatted(host.name(), GameSession.MAX_PLAYERS));
    }

    private void join(MessageReceivedEvent event) {
        GameSession session = sessionOrReply(event);
        if (session == null) {
            return;
        }

        Player player = playerOf(event);
        String message = switch (session.join(player)) {
            case JOINED -> "**%s** joined the game (%d/%d players)."
                    .formatted(player.name(), session.playerCount(), GameSession.MAX_PLAYERS);
            case ALREADY_JOINED -> "You're already in the game.";
            case FULL -> "The game is full (%d/%d players).".formatted(GameSession.MAX_PLAYERS, GameSession.MAX_PLAYERS);
            case ALREADY_STARTED -> "This game has already started.";
        };
        reply(event, message);
    }

    private void start(MessageReceivedEvent event) {
        GameSession session = sessionOrReply(event);
        if (session == null || !isHostOrReply(event, session, "start the game")) {
            return;
        }
        if (session.isStarted()) {
            reply(event, "The game is already running. Use `!gang-end` to stop it.");
            return;
        }
        if (session.playerCount() < GameSession.MIN_PLAYERS) {
            reply(event, "Need at least %d players to start.".formatted(GameSession.MIN_PLAYERS));
            return;
        }

        Map<Player, List<Card>> hands = session.dealHands();
        String names = hands.keySet().stream().map(Player::name).collect(Collectors.joining(", "));
        reply(event, "Dealing cards to %s. Check your DMs!".formatted(names));

        GuildMessageChannel channel = event.getGuildChannel();
        hands.forEach((player, cards) -> sendHand(channel, player, cards));
        postOverview(channel, session);
    }

    private void end(MessageReceivedEvent event) {
        GameSession session = sessionOrReply(event);
        if (session == null || !isHostOrReply(event, session, "end the game")) {
            return;
        }
        games.end(event.getChannel().getIdLong());
        reply(event, "Game ended.");
    }

    private void onOverviewReaction(GenericMessageReactionEvent event, boolean added) {
        if (!event.isFromGuild()) {
            return;
        }
        long userId = event.getUserIdLong();
        GameSession session = games.get(event.getChannel().getIdLong());
        if (session == null
                || userId == event.getJDA().getSelfUser().getIdLong()
                || !session.isOverview(event.getMessageIdLong())
                || !session.hasPlayer(userId)) {
            return;
        }

        if (Reactions.isReady(event.getEmoji())) {
            session.setReady(userId, added);
        } else {
            OptionalInt number = Reactions.numberOf(event.getEmoji());
            if (number.isEmpty()) {
                return;
            }
            boolean changed = added
                    ? take(event, session, userId, number.getAsInt())
                    : session.release(userId, number.getAsInt());
            if (!changed) {
                return;
            }
        }

        GuildMessageChannel channel = event.getGuildChannel();
        session.advanceIfComplete().ifPresentOrElse(
                advance -> completeRound(channel, session, advance),
                () -> refreshOverview(channel, session));
    }

    private boolean take(GenericMessageReactionEvent event, GameSession session, long userId, int number) {
        return switch (session.take(userId, number)) {
            case TakeResult.OutOfRange() -> {
                removeNumberReaction(event, number, userId);
                yield false;
            }
            case TakeResult.AlreadyYours() -> false;
            case TakeResult.Taken(Integer previousNumber, Player previousHolder) -> {
                if (previousNumber != null) {
                    removeNumberReaction(event, previousNumber, userId);
                }
                if (previousHolder != null) {
                    removeNumberReaction(event, number, previousHolder.userId());
                }
                yield true;
            }
        };
    }

    private void completeRound(GuildMessageChannel channel, GameSession session, Advance advance) {
        long overviewId = advance.completedOverviewId();
        channel.editMessageById(overviewId, GameMessages.lockedIn(advance.completed())).queue();
        if (canManageMessages(channel)) {
            channel.clearReactionsById(overviewId).queue();
        }

        if (advance.finished()) {
            channel.sendMessage(GameMessages.showdown(session.showdown(), session.history())).queue();
        } else {
            postOverview(channel, session);
        }
    }

    private void postOverview(GuildMessageChannel channel, GameSession session) {
        RoundView view = session.view();
        channel.sendMessage(GameMessages.overview(view)).queue(message -> {
            if (!session.attachOverview(view.round(), message.getIdLong())) {
                return;
            }
            for (int number = 1; number <= view.byNumber().size(); number++) {
                message.addReaction(Reactions.number(number)).queue();
            }
            message.addReaction(Reactions.READY).queue();
        });
    }

    private void refreshOverview(GuildMessageChannel channel, GameSession session) {
        RoundView view = session.view();
        if (view.overviewMessageId() != 0) {
            channel.editMessageById(view.overviewMessageId(), GameMessages.overview(view)).queue();
        }
    }

    private void sendHand(GuildMessageChannel channel, Player player, List<Card> cards) {
        channel.getJDA().retrieveUserById(player.userId())
                .flatMap(user -> user.openPrivateChannel())
                .flatMap(dm -> dm.sendMessage(GameMessages.hand(cards)))
                .queue(
                        ok -> {},
                        error -> channel.sendMessage(
                                        "Couldn't DM **%s** their cards. They need to allow DMs from server members."
                                                .formatted(player.name()))
                                .queue());
    }

    private static void removeNumberReaction(GenericMessageReactionEvent event, int number, long userId) {
        GuildMessageChannel channel = event.getGuildChannel();
        if (!canManageMessages(channel)) {
            return;
        }
        long messageId = event.getMessageIdLong();
        event.getJDA().retrieveUserById(userId)
                .flatMap(user -> channel.removeReactionById(messageId, Reactions.number(number), user))
                .queue();
    }

    private static boolean canManageMessages(GuildMessageChannel channel) {
        return channel.getGuild().getSelfMember().hasPermission(channel, Permission.MESSAGE_MANAGE);
    }

    private GameSession sessionOrReply(MessageReceivedEvent event) {
        GameSession session = games.get(event.getChannel().getIdLong());
        if (session == null) {
            reply(event, "No game in this channel. Type `!gang-create` to start one.");
        }
        return session;
    }

    private static boolean isHostOrReply(MessageReceivedEvent event, GameSession session, String action) {
        if (session.isHost(event.getAuthor().getIdLong())) {
            return true;
        }
        reply(event, "Only the host (**%s**) can %s.".formatted(session.host().name(), action));
        return false;
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
