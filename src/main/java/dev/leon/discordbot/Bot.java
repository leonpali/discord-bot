package dev.leon.discordbot;

import dev.leon.discordbot.commands.SlashCommandListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Bot {
    private static final Logger log = LoggerFactory.getLogger(Bot.class);

    private Bot() {}

    public static void main(String[] args) throws InterruptedException {
        String token = System.getenv("DISCORD_TOKEN");
        if (token == null || token.isBlank()) {
            log.error("DISCORD_TOKEN environment variable is not set");
            System.exit(1);
        }

        JDA jda = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES)
                .addEventListeners(new SlashCommandListener())
                .build();

        jda.updateCommands()
                .addCommands(
                        Commands.slash("ping", "Check the bot's gateway latency"),
                        Commands.slash("hello", "Get a greeting from the bot"))
                .queue();

        jda.awaitReady();
        log.info("Logged in as {}", jda.getSelfUser().getAsTag());
    }
}
