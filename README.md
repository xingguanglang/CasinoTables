# CasinoTables

**English** · [中文说明](#中文说明)

Texas Hold'em, Blackjack and Ludo played on **real, built-in-world tables** — not in a chat window
and not in an inventory GUI. Every table is a physical arena in its own void world: seats, a dealer,
pressure-plate buttons, floating card displays, and chips you actually pick up and place.

Requires **Paper 1.21 – 26.2** and the **Vault API** with an economy plugin.

**Download:** [GitHub Releases](https://github.com/xingguanglang/CasinoTables/releases) ·
[Modrinth](https://modrinth.com/mod/casinotables) ·
[SpigotMC](https://www.spigotmc.org/resources/138146/)

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
| Hothead | Novice | Plays almost anything, raises on impulse |
| Calling Station | Novice | Rarely raises, rarely folds |
| The Rock | Regular | Only good hands, but plays them softly |
| Maniac | Regular | Extremely aggressive, reads hands poorly |
| The Assassin | Pro | Tight-aggressive, folds and pressures correctly |
| Old Fox | Pro | Reads accurately and bluffs at the right moments |

Seat names carry a `BOT·` prefix in game, so The Rock appears as `BOT·The Rock`.

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

Every player-facing string lives in a language file — there is no hardcoded text anywhere. English
and Chinese both ship, and both are written to `plugins/CasinoTables/lang/` on first start, so you
can read or edit either without unzipping the jar. Set `language:` in `config.yml` to pick one.

Those extracted files take priority over the copies inside the jar. That is what keeps your own
wording safe across updates — but it cuts both ways: **if a release rewords a message you already
have on disk, you will keep the old wording until you delete that file** and let it regenerate. Keys
you delete, and keys added by a new release, always fall back to the jar, so nothing ever ends up
blank.

Adding a language means dropping a new file into that folder and pointing `language:` at it.

Hand histories store the message key rather than the finished sentence, so switching `language:`
re-renders the whole archive instead of leaving it half in the old language.

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
itself. Pass `-Versions 1.21,1.21.5,26.2` to choose the list; every release is checked against all
fourteen Paper versions in the supported range. Both harnesses copy Vault and EssentialsX out of the
surrounding server folder, so they only run in that layout.

The build also runs a self-test suite: hand evaluation, blackjack rules, rake on contested pots,
arena geometry with no gaps a player could fall through, bot behaviour, and the message-key contract
in both directions — every key the code asks for must exist, and every key in the language files
must have a reader.

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

---
---

# 中文说明

[English](#casinotables) · **中文**

在**世界里真盖出来的牌桌**上打德州扑克、21 点和飞行棋——不是聊天框刷字，也不是背包 GUI。每张桌子都是
一座独立虚空世界里的实体场地：座位、荷官、按钮、悬浮的牌面，还有你真的能捡起来放下去的筹码。

需要 **Paper 1.21 – 26.2**，以及 **Vault API** 加任意经济插件。

**下载：**[GitHub Releases](https://github.com/xingguanglang/CasinoTables/releases) ·
[Modrinth](https://modrinth.com/mod/casinotables) ·
[SpigotMC](https://www.spigotmc.org/resources/138146/)

---

## 长什么样

每局游戏自己盖一间房，牌桌关闭时拆干净，并把每位玩家的背包、坐标、游戏模式、血量和状态效果原样还回去。

- **德州扑克** —— 1–6 名真人，空位由 BOT 补满。实体筹码：从快捷栏拿起筹码丢进座位正前方那块 3×3 的
  下注区，丢多少就是下多少。座位旁有补齐跟注、确认、收回、全下、弃牌五个按钮。底牌只有本人看得见，
  靠的是逐玩家的全息显示。
- **21 点** —— 1–6 名真人各自对庄，所以一个人也能开一整局。要牌、停牌、加倍、最多分成四手、保险都有。
  庄家软 17 停牌，黑杰克赔 3:2。下注方式和德州一样用实体筹码。
- **飞行棋** —— 2–4 人，每人 2/3/4 只羊做棋子，完整 48 格棋盘，带同色跳跃、中央飞越、撞子回家和
  掷六再来一次。

## 筹码是真的物品

面额是钻石块 1000、金块 200、绿宝石块 50、红石块 15、铁块 5、铜块 1。对着空气右键把大筹码拆小，
潜行右键合并回去。每一轮下注开始时插件会重算一遍手上的面额搭配，保证你永远有零钱跟注。

## 关于钱

筹码就是 Vault 货币，一比一。玩家按 `min(余额, 携带上限)` 买入，坐下时扣款，离场时结算回去。
抽水可配置，默认只对被争夺过的底池抽 0.5%——没人跟注、原样退回的筹码一分不抽。21 点完全不抽水：
庄家的规则优势本身就是赌场优势。

万一付款失败（经济插件抽风、玩家掉线），金额会写进待付文件并自动重试。钱不会无声无息地消失。

## BOT

德州的空位由六种本地 BOT 性格补满，每桌随机分配，水平从新手到高手：

| BOT | 水平 | 打法 |
|---|---|---|
| 莽夫 | 新手 | 什么牌都想玩，全凭冲动加注 |
| 跟注站 | 新手 | 几乎不主动加注，也几乎不弃牌 |
| 铁石 | 中级 | 只玩好牌，但打得软 |
| 疯子 | 中级 | 极度激进，牌力判断很差 |
| 刺客 | 高手 | 紧凶，该弃就弃该压就压 |
| 老狐狸 | 高手 | 算得准，还会挑时机诈唬 |

游戏里座位名带 `BOT·` 前缀，所以「铁石」显示为 `BOT·The Rock`。

水平不是摆设：它决定蒙特卡洛采样次数、牌力误判幅度，以及有多认真对待底池赔率——所以新手是真的会
看错牌，不是只换了个下注习惯。BOT 全部本地运算，**不调用任何 AI 服务，不产生网络请求**。它们的筹码
不碰 Vault，每手自动补到携带上限。

真人入座时被踢掉的是随机一个 BOT，不是固定第一个。

## 外观

13 种装潢 × 4 种房型，两者独立选择，共 52 种组合。

装潢：皇家绿毯、海晶玻璃、露天自然、午夜黑金、绯红丝绒、深海蓝调、紫晶殿堂、樱花庭院、沙漠绿洲、
地狱熔岩、极地冰宫、蒸汽紫铜、末地虚空。

房型：方形、圆形、八边形、开放式。

每种装潢的地板、墙、天花、牌桌、银行和下注区材质都是分开设定的。

## 命令

游戏内 `/casino help` 有完整列表。别名：`/ct`、`/tables`。

| 命令 | 作用 |
|---|---|
| `/casino` | 打开菜单 |
| `/casino create poker \| blackjack \| flight [赌注]` | 创建房间 |
| `/casino invite <玩家> [玩法] [赌注]` | 邀请某人 |
| `/casino start` | 房主开局 |
| `/casino casino [1-13]` | 切换牌桌装潢 |
| `/casino shape [1-4]` | 切换房间轮廓 |
| `/casino blinds <小盲> [大盲]` | 设置德州盲注 |
| `/casino buyin <上限>` | 设置筹码携带上限 |
| `/casino history` / `bjhistory` | 你最近的牌局 |
| `/casino leave` | 离开牌桌 |

管理员（`casinotables.admin`）：

| 命令 | 作用 |
|---|---|
| `/casino config set poker-rake <0-20>` | 抽水百分比 |
| `/casino luck poker <玩家> <1-100\|off>` | 暗中调整某人的牌运 |
| `/casino peek [玩家] [on\|off]` | 看到全部底牌 |
| `/casino floppeek [玩家] [on\|off]` | 提前看到尚未发出的公牌 |
| `/casino reload` | 重载配置和语言文件 |

`peek` 和 `floppeek` 可以指定玩家名，授予给别的在线玩家。给正在牌桌上的人开 `peek` 等于让他看到
对手的底牌——这是管理和排错工具，不是观战功能。

## 权限

| 节点 | 默认 |
|---|---|
| `casinotables.use` | 所有人 |
| `casinotables.admin` | OP |

## 语言

所有面向玩家的文字都在语言文件里，代码中没有任何写死的句子。英文和中文都随插件提供，首次启动时
两份都会释放到 `plugins/CasinoTables/lang/`，想照着改措辞不用解压 jar。在 `config.yml` 里用
`language:` 选择。

**释放出来的那份优先级高于 jar 内的副本。** 这正是"升级不会覆盖你改过的措辞"的原理，但反过来也成立：
**如果某个版本改了一句你磁盘上已经有的文案，你会一直看到旧的，直到删掉那个文件让它重新生成。**
你删掉的键、以及新版本新增的键，都会自动回落到 jar 内的版本，所以不会出现空白消息。

加一门新语言只需要往那个目录丢一个文件，再把 `language:` 指过去。

历史记录存的是消息键而不是渲染好的句子，所以中途改 `language:` 会让**整个历史跟着换语言**，不会留下
半中半英的列表。

## 构建

需要 JDK 21 或更高。Maven 是通用路径，不依赖别的东西：

```
mvn -B package
```

产物在 `target/`。插件编译成 Java 21 字节码，这也是单个构建能覆盖 Paper 1.21 到 26.2 的原因。

仓库里还带着作者用的 Windows 开发脚本。它们不走 Maven，直接拿本地服务端 `libraries/` 目录里现成的
paper-api 编译，并且假设项目位于 `<paper服务端>/plugin-src/CasinoTables`。这是便利，不是必需：

```
powershell -ExecutionPolicy Bypass -File build.ps1
```

另外附带两个测试脚手架：

```
powershell -ExecutionPolicy Bypass -File test-server.ps1      # 用本地 Paper 启动一次
powershell -ExecutionPolicy Bypass -File test-versions.ps1    # 在 1.21、1.21.8、26.2 上各启动一次
```

`test-versions.ps1` 会自动下载所需的 Paper 服务端、校验哈希、在临时目录逐个启动、确认插件加载后
没有自我禁用，最后清理干净。用 `-Versions 1.21,1.21.5,26.2` 可以指定版本列表；每次发布都会跑完
支持范围内全部十四个 Paper 版本。两个脚手架都从外层服务端目录复制 Vault 和 EssentialsX，所以只能
在那个目录结构下运行。

构建过程还会跑一套自检：牌型判定、21 点规则、被争夺底池的抽水、场地几何（不留玩家能掉下去的缝）、
BOT 行为，以及消息键的双向契约——代码要的每个键必须存在，语言文件里的每个键也必须有人读。

## 许可证

CasinoTables 是自由软件，采用 **GNU General Public License v3.0**。完整条款见 [LICENSE](LICENSE)。

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

对一个 Minecraft 插件来说这条款的实际含义是：任何人都可以运行、研究和修改它，但如果**分发**修改过
的版本，就必须以同样的许可证提供对应源码。只是在服务器上运行——哪怕是公开的、收费的服务器——不算
分发，不产生任何义务。

因为 GPL-3.0 要求接收者能拿到源码，所以发布 jar 的同时要公开源码（通常就是一个公开仓库），并在资源
页上给出链接。
