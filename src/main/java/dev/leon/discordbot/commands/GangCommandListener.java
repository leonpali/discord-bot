package dev.leon.discordbot.commands;

import dev.leon.discordbot.game.Card;
import dev.leon.discordbot.game.GameManager;
import dev.leon.discordbot.game.GameSession;
import dev.leon.discordbot.game.Player;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
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
        if (session.players().size() < GameSession.MIN_PLAYERS) {
            reply(event, "Need at least %d players to start.".formatted(GameSession.MIN_PLAYERS));
            return;
        }

        Map<Player, List<Card>> hands = session.dealRound();
        String names = hands.keySet().stream().map(Player::name).collect(Collectors.joining(", "));
        reply(event, "Dealing cards to %s. Check your DMs!".formatted(names));

        MessageChannel channel = event.getChannel();
        hands.forEach((player, cards) -> sendHand(event, channel, player, cards));
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
