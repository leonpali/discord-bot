package dev.leon.discordbot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class SlashCommandListener extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "ping" -> event.reply("Pong! Gateway ping: " + event.getJDA().getGatewayPing() + " ms")
                    .setEphemeral(true)
                    .queue();
            case "hello" -> event.reply("Hello, " + event.getUser().getAsMention() + "!").queue();
            default -> event.reply("Unknown command").setEphemeral(true).queue();
        }
    }
}
