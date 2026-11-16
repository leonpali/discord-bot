# discord-bot

A Discord bot written in Java that hosts a **cooperative poker game** in a
server channel. Players don't bet against each other. Instead, the whole
table wins or loses together: without seeing each other's cards, they have
to agree on a ranking of their hands from weakest to strongest. A few
utility slash commands are included as well.

## How the game works

Each player gets two private cards (sent by DM). Over four rounds, community
cards are revealed on a shared board, just like in Texas Hold'em:

| Round | Name | Board after the round starts |
|---|---|---|
| 1 | Pre-flop | no cards |
| 2 | Flop | 3 cards |
| 3 | Turn | 4 cards |
| 4 | River | 5 cards |

In every round, each player claims a **number from 1 to N** (N = number of
players), where **1 means "I think my hand is the weakest"** and N means
"I think it's the strongest". Players claim numbers by reacting to the
bot's round message with the keycap emoji (1️⃣, 2️⃣, …). Claiming a number
someone else holds takes it from them, so the table can negotiate by
"stealing" numbers back and forth. When everyone holds a number and has
reacted with ✅, the round locks in and the next card(s) are revealed.

After the River comes the **showdown**: the bot evaluates every player's best
five-card poker hand out of their two cards plus the five board cards. The
table **wins** if the hands are in non-decreasing order of strength by the
numbers claimed in the final round (equal hands may be in either order).
Otherwise the bot shows the correct order. The numbers claimed in each
earlier round are listed too, so you can see how the table's read changed.

## Commands

### Game (plain text messages in a server channel)

| Command | Who | What it does |
|---|---|---|
| `!gang-create` | anyone | Creates a game in this channel. The creator becomes the host and joins automatically. One game per channel. |
| `!gang-join` | anyone | Joins the game in this channel (before it starts; max 9 players). |
| `!gang-start` | host | Deals two cards to each player by DM and posts the first round. |
| `!gang-end` | host | Ends and removes the game in this channel. |

During a round, players interact only through reactions on the round message:

- **Number emoji** (1️⃣ … 9️⃣): claim that number. Removing your reaction gives the number up.
- **✅**: mark yourself ready. Removing it un-readies you.

To play again after a showdown, the host runs `!gang-end` and then someone
runs `!gang-create`.

### Slash commands

| Command | What it does |
|---|---|
| `/ping` | Replies (only visible to you) with the bot's gateway latency. |
| `/hello` | Greets you by mention. |

## Setup

### Requirements

- **JDK 25** (the project compiles with `--release 25`)
- A Discord account with access to the
  [Discord Developer Portal](https://discord.com/developers/applications)

Maven doesn't need to be installed: the repository includes the Maven
Wrapper (`mvnw` / `mvnw.cmd`).

### 1. Create the bot in Discord

1. In the Developer Portal, create a **New Application**, open the **Bot**
   tab and copy the **token** (keep it secret).
2. On the same tab, enable the **Message Content Intent**. The bot reads
   `!gang-…` commands from plain messages, so it needs this intent.
3. Under **OAuth2 → URL Generator**, select the scopes `bot` and
   `applications.commands`, and these bot permissions:
   - View Channels, Send Messages, Read Message History, Add Reactions
   - **Manage Messages** (recommended): lets the bot remove a player's
     reaction when their number is taken by someone else, and clear the
     reactions when a round locks in. The game still works without it, but
     stale reactions stay on the message.
4. Open the generated URL and add the bot to your server.

Players must allow DMs from server members, since that is how their cards are
sent. If a DM fails, the bot says so in the channel.

### 2. Run it

The bot reads its token from the `DISCORD_TOKEN` environment variable and
exits if it isn't set.

**Run from source:**

```sh
# Linux / macOS
export DISCORD_TOKEN=your-token-here
./mvnw exec:java
```

```powershell
# Windows (PowerShell)
$env:DISCORD_TOKEN = "your-token-here"
.\mvnw.cmd exec:java
```

**Or build a standalone jar** (all dependencies included) and run that:

```sh
./mvnw package
java -jar target/discord-bot-0.1.0-SNAPSHOT.jar
```

When the bot is connected, it logs `Logged in as <bot name>`. The slash
commands are registered on startup.

**Run the tests** (no Discord token needed):

```sh
./mvnw test
```

`./mvnw package` also runs the tests and stops if any fail.

## Deployment (Raspberry Pi)

GitHub builds and publishes the bot; the Pi only downloads it. Nothing from
GitHub runs on the Pi and no port has to be opened on your router, which keeps
this safe for a public repository.

```text
push to main ──► GitHub Actions ──► tests ──► ARM64 Docker image ──► ghcr.io/leonpali/discord-bot
                                                                              │
Raspberry Pi: a timer checks every 5 minutes, pulls a new image, restarts ◄───┘
```

### CI/CD workflow

`.github/workflows/ci-cd.yml` runs on every push and pull request:

- **`test`** runs `./mvnw verify` on a GitHub-hosted machine.
- **`image`** (pushes to `main` only, after the tests pass) builds the
  `Dockerfile` natively on GitHub's free ARM64 runner and pushes it to the
  GitHub Container Registry as `latest` and `sha-<commit>`. Pull requests,
  including ones from forks, never publish anything.

The workflow only uses the built-in `GITHUB_TOKEN`; no secrets need to be set
up, and the Discord token never leaves the Pi.

### One-time setup

1. **Make the image public.** After the first successful run, open the
   package on your GitHub profile (**Packages → discord-bot → Package
   settings**) and set its visibility to **Public** if it isn't already. The
   Pi can then pull it without logging in.
2. **Prepare the Pi.** Use a Pi 3, 4 or 5 with **64-bit** Raspberry Pi OS and
   install Docker:
   ```sh
   curl -fsSL https://get.docker.com | sh
   ```
3. **Copy the `deploy/` folder** to `/opt/discord-bot` on the Pi and add the
   token:
   ```sh
   sudo mkdir -p /opt/discord-bot
   sudo cp deploy/* /opt/discord-bot/
   cd /opt/discord-bot
   sudo cp .env.example .env
   sudo nano .env            # set DISCORD_TOKEN
   sudo chmod 600 .env
   ```
4. **Start the bot and the update timer:**
   ```sh
   sudo docker compose up -d
   sudo cp discord-bot-update.service discord-bot-update.timer /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now discord-bot-update.timer
   ```

The container restarts automatically after crashes and reboots. Useful
commands on the Pi:

```sh
sudo docker compose logs -f                      # follow the bot's log
sudo systemctl start discord-bot-update          # check for an update right now
systemctl list-timers discord-bot-update.timer   # when the next check runs
```

A new version restarts the bot, which ends any game in progress, since games
live in memory. To roll back, set the image in `docker-compose.yml` to an
older `sha-…` tag and run `sudo docker compose up -d`.

## Technical overview

### Stack

| Component | Purpose |
|---|---|
| [JDA 6](https://github.com/discord-jda/JDA) (Java Discord API) | Gateway connection, events, messages, reactions, slash commands. The voice/Opus dependency is excluded to keep the jar small. |
| Logback | Logging to stdout (`src/main/resources/logback.xml`) |
| maven-shade-plugin | Builds the runnable fat jar |
| exec-maven-plugin | `./mvnw exec:java` |
| JUnit 6 + maven-surefire-plugin | Unit tests (`./mvnw test`) |

### Project layout

```text
src/main/java/dev/leon/discordbot/
├── Bot.java                       entry point: reads the token, builds JDA, registers slash commands
├── commands/
│   ├── SlashCommandListener.java  /ping and /hello
│   ├── GangCommandListener.java   !gang-* commands and reactions → game actions; sends and edits messages
│   ├── GameMessages.java          text of the overview, locked-in, showdown and hand messages
│   └── Reactions.java             the ✅ and 1️⃣–9️⃣ emoji: creating and recognising them
└── game/                          game logic with no Discord dependencies
    ├── GameManager.java           one GameSession per channel (ConcurrentHashMap)
    ├── GameSession.java           players, hands, board, number claims, ready state, round advancement
    ├── Round.java                 Pre-flop → Flop → Turn → River, with the cards each one reveals
    ├── RoundView.java             immutable snapshot of the current round, used to render messages
    ├── RoundRecord.java           a finished round: its board and who held which number
    ├── Deck.java                  52 cards shuffled with SecureRandom; deal and burn
    ├── Card.java, Rank.java, Suit.java
    ├── HandEvaluator.java         best 5-card hand out of 7
    ├── HandValue.java             comparable hand strength plus a readable description
    ├── HandCategory.java          high card … royal flush
    ├── Showdown.java              final hands, win/loss check, weakest-to-strongest order
    └── Player.java

src/test/java/dev/leon/discordbot/ unit tests (see "Tests" below)

Dockerfile                         builds the runnable image (see "Deployment")
.github/workflows/ci-cd.yml        tests every push and PR; publishes the image from main
deploy/                            docker-compose.yml, .env.example and the update timer for the Pi
```

The `game` package knows nothing about Discord, so the rules can be read
(and tested) on their own. In `commands`, `GangCommandListener` turns Discord
events into game actions and sends the results; `GameMessages` decides what
those messages say, and `Reactions` handles the emoji.

### Bot startup

`Bot` creates a lightweight JDA client (`JDABuilder.createLight`, which
disables member and presence caching) with three gateway intents:
`GUILD_MESSAGES`, `MESSAGE_CONTENT` (to read the `!gang-…` commands) and
`GUILD_MESSAGE_REACTIONS` (to see number and ✅ reactions). It registers
both listeners, then registers the `/ping` and `/hello` slash commands
globally.

### Game flow

1. **Dealing.** `GameSession.dealHands()` shuffles a fresh deck, deals two
   cards to each player in turn (like a real dealer), and starts the
   Pre-flop. Hands are sent to players by DM.
2. **Rounds.** Each `Round` knows how many cards it reveals. Starting a round
   clears all claims and ready flags; on the Flop the session burns one card
   and deals three, on the Turn and River it burns one and deals one.
3. **Round message.** The bot renders an overview from a `RoundView`
   snapshot: the board, who holds which number, who is ready, the most recent
   action (e.g. "Alice took 2 from Bob") and earlier rounds. Once the message
   is sent, `attachOverview` stores its ID in the session and the bot adds the
   reactions 1️⃣ … N️⃣ and ✅. Reactions on any other message are ignored.
4. **Claims.** `take(userId, number)` returns a sealed `TakeResult`:
   - `Taken(previousNumber, previousHolder)`: the claim succeeded. The
     listener removes the claimer's old number reaction and the previous
     holder's reaction on this number, so the reactions always mirror the
     game state.
   - `AlreadyYours`: nothing changes.
   - `OutOfRange`: the number is higher than the player count; the reaction is
     removed.

   Removing a reaction calls `release`, which gives the number up.
5. **Locking in.** After every change the listener calls
   `advanceIfComplete()`. While someone is missing a number or a ✅, it
   returns nothing and the bot just edits the overview. Once everyone has
   both, the session saves the round as a `RoundRecord`, starts the next
   round, and returns an `Advance`. The bot then edits the old message to
   "locked in" (rendered from that record), clears its reactions, and posts
   the next round's overview. After the River, `Advance.finished()` is true
   and the bot posts the showdown instead.

### Hand evaluation

`HandEvaluator.best` tries all 21 five-card combinations of a player's seven
cards and keeps the strongest. For one combination, `evaluate`:

- counts cards per rank and sorts the ranks by **(count, rank)**, both
  descending. For a full house of kings over fours that gives `[K, 4]`, for
  two pair `[high pair, low pair, kicker]`, and so on. This list is the
  **tiebreak**.
- detects a flush (one suit) and a straight (five distinct ranks spanning
  four steps), including the **wheel** A-2-3-4-5, where the ace counts low
  and the straight's high card is the five.
- picks the category from those facts, from `ROYAL_FLUSH` down to `HIGH_CARD`.

`HandValue.compareTo` compares the category first and then the tiebreak ranks
in order. That handles kickers and makes truly equal hands compare as 0.
`Showdown.win()` checks that the hands, sorted by claimed number, never
decrease in strength. If they do, the table loses, and the bot prints
`Showdown.weakestToStrongest()` as the correct order, using `<` and `=` for
ties.

### Concurrency

JDA delivers events on its own threads and runs `queue()` callbacks
asynchronously, so several reactions can arrive at the same time:

- `GameManager` stores sessions in a `ConcurrentHashMap`, and
  `create` uses `putIfAbsent`, so two simultaneous `!gang-create` commands
  can't both succeed.
- Every `GameSession` method that touches game state is `synchronized`, and
  each multi-step update happens inside one such method. For example,
  `advanceIfComplete()` checks that the round is complete and advances it in
  one step, so two simultaneous ✅ reactions can't both advance the round.
  The listener never locks the session itself.
- Messages are rendered from `view()`, an immutable snapshot taken in one
  step, so a message never mixes state from before and after a change.
- When a round message is sent, `attachOverview` only stores its ID if the
  session is still in the same round. A slow callback can't overwrite the ID
  of a newer round.

### Tests

The tests live in `src/test/java` and run without a Discord connection.

| Test class | What it checks |
|---|---|
| `HandEvaluatorTest` | Every one of the 2,598,960 five-card hands gets the right category (matching the known poker counts), plus kickers, the wheel, ties and display order |
| `GameSessionTest` | Joining, dealing, taking/stealing/releasing numbers, readiness, round advancement, the board growing 3/1/1, the showdown, and which overview message counts |
| `ShowdownTest` | Win/loss, ties in either order, weakest-to-strongest ranking |
| `GameMessagesTest` | The exact text of the overview, locked-in and showdown messages |
| `ReactionsTest` | Parsing 1️⃣–9️⃣ and ✅, ignoring every other emoji |
| `DeckTest`, `RoundTest`, `GameManagerTest` | 52 unique cards, round order and reveals, one game per channel |

`TestCards` turns short notation like `"Ah 10c Ks"` into cards so hands are
easy to write in tests. The Discord listeners aren't unit tested; they need a
live (or mocked) JDA connection.

### Limitations

- Game state lives in memory only. Restarting the bot ends all games.
- One game per channel, 1 to 9 players (limited by the keycap emoji 1️⃣–9️⃣).
- Cards can't be re-dealt once a game has started. If a player didn't get
  their DM, end the game and create a new one.
