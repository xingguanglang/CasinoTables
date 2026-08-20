# CasinoTables

Texas Hold'em, Blackjack and Ludo played on **real, built-in-world tables** — not in a chat window
and not in an inventory GUI. Every table is a physical arena in its own void world: seats, a dealer,
pressure-plate buttons, floating card displays, and chips you actually pick up and place.

Requires **Paper 1.21 – 26.2** and **Vault** with an economy plugin.

---

## What it looks like

Each game builds its own room, then tears it down and restores every player's inventory, position,
gamemode, health and effects when the table closes.

- **Texas Hold'em** — 1–6 real players, empty seats filled by bots. Physical chips: you take chips
  from your hotbar and drop them into the 3×3 betting zone in front of your seat. How many you drop
  is your bet. Buttons for call-fill, confirm, reclaim, all-in and fold. Hole cards are shown only
  to their owner via per-player holograms.
- **Blackjack** — 1–6 real players, each playing against the dealer, so a single player is a full
  game. Hit, stand, double down, split (up to four hands) and insurance. Dealer stands on soft 17,
  blackjack pays 3:2. Same physical-chip betting as Hold'em.
- **Ludo** — 2–4 players, 2/3/4 sheep pieces each, a full 48-square board with colour-coded jumps,
  a centre leap, capture-and-send-home and roll-a-six-to-go-again.

## Chips are real items

Denominations are diamond block 1000, gold block 200, emerald block 50, redstone block 15,
iron block 5, copper block 1. Right-click a chip in the air to break it into smaller ones, sneak
right-click to merge back up. The plugin recalculates a sensible spread at the start of each
betting round so you always hold change small enough to call with.

## Money

Chips are Vault currency, one-to-one. Players buy in for
`min(balance, carry limit)`, which is withdrawn when they sit down and paid back when they leave.
Rake is configurable and defaults to 0.5% of contested pots only — chips returned uncontested are
never raked. Blackjack takes no rake at all: the dealer's rule advantage *is* the house edge.

If a payout ever fails (economy plugin hiccup, player offline), the amount is written to a pending
file and retried automatically. Money is never silently dropped.

## Bots

Empty Hold'em seats are filled by six local bot archetypes, assigned randomly per table, ranging
from novice to expert:

| Bot | Tier | Style |
|---|---|---|
| Reckless | Novice | Plays almost anything, raises on impulse |
| Calling Station | Novice | Rarely raises, rarely folds |
| Rock | Intermediate | Only good hands, but plays them softly |
| Berserker | Intermediate | Extremely aggressive, reads hands poorly |
| Ice | Expert | Tight-aggressive, folds and pressures correctly |
| Old Fox | Expert | Reads accurately and bluffs at the right moments |

Skill is not cosmetic. It scales the Monte-Carlo sample count, adds a hand-strength misread margin,
and weights how strictly the bot respects pot odds — so a novice genuinely misjudges hands rather
than just betting differently. Bots run entirely locally with no AI service and no network calls.
Bot chips never touch Vault; they are topped up to the carry limit each hand.

When a real player joins, a random bot is bumped rather than always the first seat.

## Looks

13 decors × 4 room shapes, chosen independently, so 52 combinations.

Decors: Royal Green, Sea Lantern, Open Garden, Midnight Gold, Crimson Velvet, Deep Azure,
Amethyst Hall, Cherry Courtyard, Desert Oasis, Nether Lava, Polar Ice, Steam Copper, End Void.

Shapes: Rectangle, Round, Octagon, Open Air.

Each decor sets floor, wall, ceiling, table, bank and betting-zone materials independently.

## Commands

Run `/casino help` in game for the full list. Aliases: `/ct`, `/tables`.

| Command | What it does |
|---|---|
| `/casino` | Open the menu |
| `/casino create poker \| blackjack \| flight [wager]` | Create a room |
| `/casino invite <player> [game] [wager]` | Invite someone |
| `/casino start` | Host starts the game |
| `/casino casino [1-13]` | Switch table decor |
| `/casino shape [1-4]` | Switch room shape |
| `/casino blinds <small> [big]` | Set Hold'em blinds |
| `/casino buyin <limit>` | Set the chip carry limit |
| `/casino history` / `bjhistory` | Your recent hands |
| `/casino leave` | Leave the table |

Admin (`casinotables.admin`):

| Command | What it does |
|---|---|
| `/casino config set poker-rake <0-20>` | Rake percentage |
| `/casino luck poker <player> <1-100\|off>` | Secretly weight someone's cards |
| `/casino peek [player] [on\|off]` | See every hole card |
| `/casino floppeek [player] [on\|off]` | See community cards before they are dealt |
| `/casino reload` | Reload config and language files |

`peek` and `floppeek` can be granted to another online player by naming them. Granting `peek` to
someone sitting at a table lets them see their opponents' cards — it is an administration and
debugging tool, not a spectator feature.

## Permissions

| Node | Default |
|---|---|
| `casinotables.use` | everyone |
| `casinotables.admin` | op |

## Language

Every player-facing string lives in `lang/en_US.yml`. English is the default; `zh_CN.yml` ships
alongside it. Set `language:` in `config.yml` to pick one.

The files are copied into `plugins/CasinoTables/lang/` on first start. Edits there are preserved
across updates, and any key you delete falls back to the copy inside the jar — so a new release
never wipes your wording, and never leaves a blank message either.

Adding a language means dropping a new file into that folder and pointing `language:` at it.

## Building

Needs a JDK 21 or newer. Maven is the portable route and needs nothing else:

```
mvn -B package
```

The jar lands in `target/`. The plugin targets Java 21 bytecode, which is what lets a single
build cover Paper 1.21 through 26.2.

The repository also carries the Windows development scripts the plugin was written with. They
build without Maven by compiling against a Paper API jar already present in a local server's
`libraries/` folder, and they expect this project to sit in `<paper-server>/plugin-src/CasinoTables`.
They are convenience, not a requirement:

```
powershell -ExecutionPolicy Bypass -File build.ps1
```

Two test harnesses are included:

```
powershell -ExecutionPolicy Bypass -File test-server.ps1      # boot once on the local Paper build
powershell -ExecutionPolicy Bypass -File test-versions.ps1    # boot on 1.21, 1.21.8 and 26.2
```

`test-versions.ps1` downloads the Paper server jars it needs, verifies their checksums, boots each
one in a throwaway folder, checks the plugin enables without self-disabling, and cleans up after
itself. Both harnesses copy Vault and EssentialsX out of the surrounding server folder, so they
only run in that layout.

The build also runs a self-test suite covering hand evaluation, blackjack rules, maze-free arena
geometry, bot behaviour and language-file key parity between `en_US` and `zh_CN`.

## License

CasinoTables is free software licensed under the **GNU General Public License v3.0**.
The full text is in [LICENSE](LICENSE).

```
Copyright (C) 2026  xingguanglang

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

What this means in practice for a Minecraft plugin: anyone may run it, study it and modify it, but
if they **distribute** a modified build they must ship the corresponding source under the same
licence. Simply running it on a server — even a public, paid one — is not distribution and triggers
no obligation.

Because GPL-3.0 requires that recipients can get the source, publish the source alongside the jar
(a public repository is the usual way) and link to it from the resource page.
