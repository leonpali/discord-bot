package dev.leon.discordbot.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReactionsTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9})
    void numberEmojiRoundTrips(int number) {
        String name = Reactions.number(number).getName();
        assertEquals(number + "️⃣", name);
        assertEquals(OptionalInt.of(number), Reactions.numberOf(Emoji.fromFormatted(name)));
    }

    @Test
    void acceptsKeycapsWithoutTheVariationSelector() {
        assertEquals(OptionalInt.of(4), Reactions.numberOf(Emoji.fromFormatted("4⃣")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0️⃣", "#️⃣", "✅", "👍", "4", "<:custom:123456789012345678>"})
    void ignoresAnythingThatIsNotOneToNine(String formatted) {
        assertTrue(Reactions.numberOf(Emoji.fromFormatted(formatted)).isEmpty());
    }

    @Test
    void recognisesTheReadyEmoji() {
        assertTrue(Reactions.isReady(Emoji.fromFormatted("✅")));
        assertFalse(Reactions.isReady(Emoji.fromFormatted("☑️")));
        assertFalse(Reactions.isReady(Emoji.fromFormatted("<:check:123456789012345678>")));
    }
}
