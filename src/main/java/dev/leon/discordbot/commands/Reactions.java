package dev.leon.discordbot.commands;

import java.util.OptionalInt;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;

final class Reactions {
    static final Emoji READY = Emoji.fromUnicode("✅");

    private static final char VARIATION_SELECTOR = '️';
    private static final char KEYCAP = '⃣';

    private Reactions() {}

    static Emoji number(int number) {
        return Emoji.fromUnicode("" + number + VARIATION_SELECTOR + KEYCAP);
    }

    static OptionalInt numberOf(EmojiUnion emoji) {
        if (emoji.getType() != Emoji.Type.UNICODE) {
            return OptionalInt.empty();
        }
        String name = emoji.getName().replace(String.valueOf(VARIATION_SELECTOR), "");
        if (name.length() == 2 && name.charAt(1) == KEYCAP && name.charAt(0) >= '1' && name.charAt(0) <= '9') {
            return OptionalInt.of(name.charAt(0) - '0');
        }
        return OptionalInt.empty();
    }

    static boolean isReady(EmojiUnion emoji) {
        return emoji.getType() == Emoji.Type.UNICODE && emoji.getName().equals(READY.getName());
    }
}
