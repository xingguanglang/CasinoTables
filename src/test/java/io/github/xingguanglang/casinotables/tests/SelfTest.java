package io.github.xingguanglang.casinotables.tests;

import io.github.xingguanglang.casinotables.flight.FlightControls;
import io.github.xingguanglang.casinotables.flight.FlightRules;
import io.github.xingguanglang.casinotables.poker.PokerCard;
import io.github.xingguanglang.casinotables.poker.PokerChips;
import io.github.xingguanglang.casinotables.poker.PokerHandEvaluator;
import io.github.xingguanglang.casinotables.poker.LuckyDeal;
import io.github.xingguanglang.casinotables.poker.PokerMoney;
import io.github.xingguanglang.casinotables.poker.PokerArenaStyle;
import io.github.xingguanglang.casinotables.blackjack.BlackjackHand;
import io.github.xingguanglang.casinotables.blackjack.BlackjackSeatHand;
import io.github.xingguanglang.casinotables.arena.ArenaShape;
import io.github.xingguanglang.casinotables.poker.CasinoBot;
import io.github.xingguanglang.casinotables.poker.PokerBotStrategy;
import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

public final class SelfTest {
    private SelfTest() {
    }

    public static void main(String[] args) {
        // 自检跑真实的取文本路径：绑定 jar 里那份 en_US.yml，
        // 于是「代码用了语言文件里不存在的键」会在构建期就炸掉。
        io.github.xingguanglang.casinotables.Messages.bindStandalone(loadLanguage("en_US"));
        testPoker();
        testPokerChips();
        testPokerMoney();
        testContestedPot();
        testDeferredReasons();
        testButtonLabelWidth();
        testChipsFitHotbar();
        testPokerArenaStyles();
        testFlightRules();
        testFlightControls();
        testCasinoBots();
        testBlackjack();
        testArenaStyles();
        testArenaShapes();
        testComputedMessageKeys();
        testEveryLiteralKeyExists(args);
        testBlocksGoThroughArenaBlocks(args);
        testLanguageFiles();
        testBotArchetypes();
        check(io.github.xingguanglang.casinotables.Messages.missingKeys().isEmpty(),
                "en_US.yml 缺少代码实际用到的键：" + io.github.xingguanglang.casinotables.Messages.missingKeys());
        System.out.println("CasinoTables self-tests passed");
    }

    private static void testPokerChips() {
        check(PokerChips.value(Material.DIAMOND_BLOCK) == 1000, "钻石块面值错误");
        check(PokerChips.value(Material.GOLD_BLOCK) == 200, "金块面值错误");
        check(PokerChips.value(Material.EMERALD_BLOCK) == 50, "绿宝石块面值错误");
        check(PokerChips.value(Material.REDSTONE_BLOCK) == 15, "红石块面值错误");
        check(PokerChips.value(Material.IRON_BLOCK) == 5, "铁块面值错误");
        check(PokerChips.value(Material.COPPER_BLOCK) == 1, "铜块面值错误");
        check(PokerChips.value(Material.SAND) == 0, "沙子不应再成为筹码");
        check(PokerChips.value(Material.STONE) == 0, "普通方块不应成为筹码");
        for (int amount : List.of(0, 1, 14, 15, 49, 50, 199, 200, 1271, 1000000)) {
            int rebuilt = PokerChips.breakdown(amount).entrySet().stream()
                    .mapToInt(entry -> entry.getKey().value() * entry.getValue()).sum();
            check(rebuilt == amount, "筹码自动合并后金额不守恒：" + amount);
        }
        check(PokerChips.summary(1271).equals(
                        "Diamond Block x1, Gold Block x1, Emerald Block x1, "
                                + "Redstone Block x1, Iron Block x1, Copper Block x1"),
                "筹码合并顺序错误");
        check(PokerChips.summary(0).equals("none"), "零筹码应显示 summary-empty");
        for (int amount = PokerChips.MIN_OPENING_COUNT; amount <= 10000; amount++) {
            int rebuilt = PokerChips.playableBreakdown(amount).entrySet().stream()
                    .mapToInt(entry -> entry.getKey().value() * entry.getValue()).sum();
            check(rebuilt == amount, "可下注筹码分配后金额不守恒：" + amount);
            check(PokerChips.playableBreakdown(amount).values().stream()
                    .allMatch(count -> count >= PokerChips.MIN_OPENING_COUNT),
                    "可下注筹码中出现少于 5 个的面额：" + amount);
        }
        check(PokerChips.split(Material.DIAMOND_BLOCK).target().material() == Material.GOLD_BLOCK
                        && PokerChips.split(Material.DIAMOND_BLOCK).count() == 5,
                "钻石筹码应分解为 5 个金筹码");
        check(PokerChips.split(Material.GOLD_BLOCK).target().material() == Material.IRON_BLOCK
                        && PokerChips.split(Material.GOLD_BLOCK).count() == 40,
                "金筹码应分解为至少 5 个等值小筹码");
        check(PokerChips.split(Material.REDSTONE_BLOCK).target().material() == Material.COPPER_BLOCK
                        && PokerChips.split(Material.REDSTONE_BLOCK).count() == 15,
                "红石筹码应等值分解为 15 个铜筹码");
        check(PokerChips.split(Material.IRON_BLOCK).target().material() == Material.COPPER_BLOCK
                        && PokerChips.split(Material.IRON_BLOCK).count() == 5,
                "铁筹码应分解为 5 个铜筹码");
        check(PokerChips.split(Material.COPPER_BLOCK) == null, "一元铜块筹码不应继续分解");
    }

    private static void testPokerArenaStyles() {
        check(PokerArenaStyle.parse("1") == PokerArenaStyle.CLASSIC, "赌场模板 1 映射错误");
        check(PokerArenaStyle.parse("glass") == PokerArenaStyle.LUMINOUS, "玻璃赌场别名映射错误");
        check(PokerArenaStyle.parse("outdoor") == PokerArenaStyle.NATURE, "自然赌场别名映射错误");
        // parse() 现在也认当前语言里的显示名，服主换语言后仍能用名字选。
        check(PokerArenaStyle.parse("Open Garden") == PokerArenaStyle.NATURE, "按语言文件里的名字解析失败");
        check(ArenaShape.parse("Open Air") == ArenaShape.OPEN, "按语言文件里的形状名解析失败");
        check(PokerArenaStyle.byId(99) == PokerArenaStyle.CLASSIC, "非法赌场模板应回退为经典版");
    }

    private static void testPokerMoney() {
        check(PokerMoney.carryAmount(5000.75, 2000) == 2000, "真实余额高于上限时必须按上限买入");
        check(PokerMoney.carryAmount(1234.99, 2000) == 1234, "真实余额不足上限时必须只带入整数余额");
        check(PokerMoney.carryAmount(0.9, 2000) == 0, "不足 1 币不能生成筹码");
        check(PokerMoney.carryAmount(Double.NaN, 2000) == 0, "非法余额不能生成筹码");
        check(PokerMoney.topUpRoom(200, 120, 50, 0, true) == 30,
                "补码上限必须计入手上筹码与本手已下注筹码");
        check(PokerMoney.topUpRoom(200, 120, 50, 20, true) == 10,
                "补码上限必须计入已经锁定到下一手的筹码");
        check(PokerMoney.topUpRoom(200, 120, 50, 20, false) == 60,
                "结算后不得重复计算已经归入底池的本手下注");
    }

    /**
     * 抽水只能落在被跟过的那部分底池上。这条规则以前只在摊牌路径成立，
     * 弃牌收底把赢家自己没被跟的注也抽了，等于凭空罚钱——锁死两条路径同用一套算法。
     */
    private static void testContestedPot() {
        // 全员弃牌：UTG 加到 500 无人跟，只有 0-10、10-20 两层有多人出钱。
        check(PokerMoney.contestedPot(new int[]{500, 20, 10, 0, 0, 0}) == 50,
                "无人跟注的超额下注不能计入可抽水底池");
        // 两家跟到底：整池都被争夺过。
        check(PokerMoney.contestedPot(new int[]{200, 200, 0, 0, 0, 0}) == 400, "对等跟注应全额可抽水");
        // 三家，其中一家 all-in 较少：边池仍是被争夺的。
        check(PokerMoney.contestedPot(new int[]{300, 300, 100, 0, 0, 0}) == 700, "边池应计入可抽水底池");
        // 只有一个人下注，其余全部弃牌且没投过一分钱。
        check(PokerMoney.contestedPot(new int[]{80, 0, 0, 0, 0, 0}) == 0, "无人跟注时不得抽水");
        check(PokerMoney.contestedPot(new int[]{0, 0, 0, 0, 0, 0}) == 0, "空底池不得抽水");
        // 可抽水部分永远不能超过底池本身。
        int[][] cases = {{500, 20, 10, 0, 0, 0}, {300, 300, 100, 0, 0, 0}, {7, 7, 7, 3, 0, 0},
                {1000, 1, 0, 0, 0, 0}, {50, 50, 50, 50, 50, 50}};
        for (int[] contribution : cases) {
            int pot = 0;
            for (int value : contribution) pot += value;
            int contested = PokerMoney.contestedPot(contribution);
            check(contested >= 0 && contested <= pot, "可抽水底池必须落在 0 与底池之间：" + contested + "/" + pot);
        }
    }

    /**
     * 历史记录存的是键和参数，不是渲染死的句子。服主中途换语言时，
     * 老记录必须跟着变成新语言，而升级前写下的旧格式记录必须照旧能读出来。
     */
    private static void testDeferredReasons() {
        io.github.xingguanglang.casinotables.Reason showdown =
                io.github.xingguanglang.casinotables.Reason.of("poker.reason.showdown");
        check(showdown.key().equals("poker.reason.showdown"), "理由必须记住自己的键");
        check(showdown.args().isEmpty(), "无占位符的理由不该带参数");
        check(!showdown.render().startsWith("<missing:"), "理由的键在语言文件里必须存在");
        check(showdown.toString().equals(showdown.render()), "toString 必须等于渲染结果");

        io.github.xingguanglang.casinotables.Reason timeout =
                io.github.xingguanglang.casinotables.Reason.of("poker.reason.timeout", "player", "Steve");
        check(timeout.render().contains("Steve"), "带占位符的理由必须把参数填进去");
        check(!timeout.render().contains("{player}"), "占位符不能原样漏给玩家");

        // 新格式优先，且真的按语言文件渲染。
        String fromStored = io.github.xingguanglang.casinotables.Reason.render(
                timeout.toStored(), "stale text written by an older build");
        check(fromStored.equals(timeout.render()), "存下来的键必须能还原成同一句话");
        // 没有新格式时退回旧的渲染文本，绝不能把历史读成空白。
        check(io.github.xingguanglang.casinotables.Reason.render(null, "showdown").equals("showdown"),
                "旧格式记录必须原样读出");
        check(io.github.xingguanglang.casinotables.Reason.render(null, null).isEmpty(),
                "两边都没有时应返回空串而不是崩溃");
    }

    /**
     * 世界里的实体按钮一格一个，标签只有约一格的横向余地。
     *
     * <p>德州那五个控制按钮曾经用「Confirm Bet / Check」这种长文案，在英文下整排糊成一团——
     * 中文两个字看不出问题，换成英文就压到邻居身上了。翻译改长了没人拦得住，所以这里按
     * 可见宽度卡死：去掉 MiniMessage 标签后，中日韩字算两格，其余算一格。
     */
    private static void testButtonLabelWidth() {
        int budget = 10;
        java.util.regex.Pattern tag = java.util.regex.Pattern.compile("<[^>]*>");
        List<String> keys = new ArrayList<>();
        for (String action : List.of("call", "confirm", "withdraw", "all-in", "fold")) {
            keys.add("poker.arena.control." + action);
        }
        for (io.github.xingguanglang.casinotables.blackjack.BlackjackAction action
                : io.github.xingguanglang.casinotables.blackjack.BlackjackAction.values()) {
            keys.add(action.key());
        }
        for (String code : List.of("en_US", "zh_CN")) {
            org.bukkit.configuration.file.YamlConfiguration yaml = loadLanguage(code);
            for (String key : keys) {
                String raw = yaml.getString(key);
                check(raw != null, code + " 缺少按钮文案 " + key);
                String plain = tag.matcher(raw).replaceAll("");
                int width = 0;
                for (int i = 0; i < plain.length(); i++) {
                    char c = plain.charAt(i);
                    width += (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3000 && c <= 0x303F) ? 2 : 1;
                }
                check(width <= budget, code + " 的按钮文案过宽，会压到相邻按钮上："
                        + key + " = " + plain + "（" + width + " > " + budget + "）");
            }
        }
    }

    /**
     * 六种面额必须全都待在快捷栏里。
     *
     * <p>21 点曾经在手上放了五件功能道具，于是一元和五元被挤进背包——补零头最常用的两枚
     * 恰恰摸不到。这里把「保留格 + 面额数 ≤ 快捷栏」这条算术关系钉死。
     */
    private static void testChipsFitHotbar() {
        int denominations = PokerChips.denominations().size();
        int spare = io.github.xingguanglang.casinotables.poker.CasinoChipInventory.maxReservedForHotbar();
        check(spare == io.github.xingguanglang.casinotables.poker.CasinoChipInventory.HOTBAR_SIZE
                        - denominations,
                "可保留格数应等于快捷栏减去面额数");
        // 21 点留两格（最低注、确认下注），必须还放得下全部面额。
        check(2 <= spare, "留给功能道具的格子太多，最小面额会被挤出快捷栏：面额 " + denominations
                + " 种，最多只能保留 " + spare + " 格");
    }

    private static void testPoker() {
        long straightFlush = score("AS", "KS", "QS", "JS", "10S", "2D", "3C");
        long quads = score("9S", "9H", "9D", "9C", "AS", "2D", "3C");
        long fullHouse = score("8S", "8H", "8D", "7C", "7S", "2D", "3C");
        check(straightFlush > quads, "同花顺必须大于四条");
        check(quads > fullHouse, "四条必须大于葫芦");
        check(PokerHandEvaluator.name(straightFlush).equals("Straight Flush"), "牌型名称错误");
        check(PokerHandEvaluator.name(quads).equals("Four of a Kind"), "四条名称错误");
        check(PokerHandEvaluator.name(fullHouse).equals("Full House"), "葫芦名称错误");
        check(score("6S", "5H", "4D", "3C", "2S") > score("AS", "5H", "4D", "3C", "2S"),
                "六高顺子必须大于轮子顺");
        check(score("AS", "AH", "KD", "KC", "QS") > score("AD", "AC", "KH", "KS", "JS"),
                "两对踢脚比较错误");
        check(PokerHandEvaluator.describeCurrent(List.of(
                new PokerCard(9, PokerCard.Suit.SPADES),
                new PokerCard(9, PokerCard.Suit.HEARTS),
                new PokerCard(14, PokerCard.Suit.DIAMONDS),
                new PokerCard(7, PokerCard.Suit.CLUBS),
                new PokerCard(2, PokerCard.Suit.SPADES))).equals("Pair of 9s"),
                "当前最大牌型应显示一对 9");
    }

    private static void testFlightRules() {
        check(FlightRules.normalDestination(-1, 6) == 0,
                "基地棋子掷到 6 只能起飞到起点，不能额外走 6 格");
        check(FlightRules.normalDestination(-1, 5) == -1, "非 6 点不能起飞");
        check(FlightRules.normalDestination(7, 6) == 13, "场上棋子应正常按点数前进");
        check(FlightRules.normalDestination(FlightRules.FINISHED - 2, 3) == FlightRules.FINISHED - 1,
                "超过终点时必须按多出的点数折返");
        check(FlightRules.normalDestination(FlightRules.OUTER + 2, 3) == FlightRules.OUTER + 5,
                "进入六格终点航道后仍应逐格前进");
        check(FlightRules.movementPath(FlightRules.FINISHED - 2, 3, 0)
                        .equals(List.of(FlightRules.FINISHED - 1, FlightRules.FINISHED,
                                FlightRules.FINISHED - 1)),
                "折返动画必须先到终点，再逐格退回");
        int afterBounce = FlightRules.normalDestination(FlightRules.FINISHED - 2, 3);
        check(afterBounce == FlightRules.FINISHED - 1
                        && FlightRules.normalDestination(afterBounce, 1) == FlightRules.FINISHED
                        && FlightRules.movementPath(afterBounce, 1, 0).equals(List.of(FlightRules.FINISHED)),
                "终点航道反弹后，下一回合必须能够继续向终点前进");
        check(FlightRules.shortcutDestination(0, 15) == 27, "中央飞跃航线应速通 12 格");
        check(FlightRules.shortcutDestination(0, 4) == 8, "落在同色混凝土应跳跃 4 格");
        check(FlightRules.startIndex(0) == 44 && FlightRules.startIndex(1) == 5
                        && FlightRules.startIndex(2) == 18 && FlightRules.startIndex(3) == 31,
                "四种颜色必须使用基地旁且不打乱四色序列的起点");
        check(FlightRules.TRACK_CELLS == 48 && FlightRules.TRACK_CELLS / 4 == 12,
                "外圈必须是每象限12格、总计48格");
        for (int cell = 0; cell < FlightRules.TRACK_CELLS; cell++) {
            check(FlightRules.trackColorIndex(cell) == cell % 4,
                    "外圈必须严格按红黄蓝绿循环，格子=" + cell);
        }
        for (int color = 0; color < 4; color++) {
            check(FlightRules.trackColorIndex(FlightRules.startIndex(color)) == color,
                    "矿物出生点也必须占据对应颜色的序位，颜色=" + color);
        }
        check(FlightRules.trackIndex(0, 0) == 44 && FlightRules.trackIndex(0, 1) == 43
                        && FlightRules.trackIndex(0, FlightRules.OUTER - 1) == 1,
                "棋子必须从基地旁起点绕外圈后进入自己的终点航道");
        check(FlightRules.shortcutDestination(2, 0) == 0,
                "矿物出生点不能被误判为四色混凝土跳跃格");
        check(FlightRules.movementPath(7, 3, 0).equals(List.of(8, 9, 10)),
                "普通移动必须逐格经过每一格");
        check(FlightRules.movementPath(-1, 6, 0).equals(List.of(0)),
                "基地起飞路径必须只到起点");
        int[] occupiedFinish = {FlightRules.FINISHED, FlightRules.OUTER, -1, -1};
        check(occupiedFinish[0] == FlightRules.FINISHED
                        && FlightRules.movementPath(occupiedFinish[1], 6, 0)
                        .equals(List.of(FlightRules.OUTER + 1, FlightRules.OUTER + 2,
                                FlightRules.OUTER + 3, FlightRules.OUTER + 4,
                                FlightRules.OUTER + 5, FlightRules.FINISHED)),
                "已有棋子占据终点时，其他棋子仍必须能够生成完整进终点路径");
        check(FlightRules.movementPath(14, 1, 0).equals(List.of(15))
                        && FlightRules.shortcutDestination(0, 15) == 27,
                "中央飞跃前必须先逐格落到跳跃口，再沿捷径飞行");
        check(FlightRules.movementPath(3, 1, 0).equals(List.of(4))
                        && FlightRules.shortcutDestination(0, 4) == 8,
                "同色跳跃前必须先逐格落到跳跃格，再执行飞跃");
    }

    private static void testFlightControls() {
        check(FlightControls.action(Material.STONE_BUTTON) == FlightControls.ROLL,
                "石按钮必须对应远程掷骰");
        for (int piece = 0; piece < 4; piece++) {
            check(FlightControls.action(FlightControls.pieceMaterial(piece)) == piece,
                    "快捷栏棋子按钮编号映射错误：" + piece);
        }
        check(FlightControls.action(Material.STONE) == FlightControls.NONE,
                "普通物品不应触发飞行棋快捷操作");
    }

    private static void testCasinoBots() {
        PokerCard[] royalDraw = {
                new PokerCard(14, PokerCard.Suit.SPADES), new PokerCard(13, PokerCard.Suit.SPADES)
        };
        List<PokerCard> royalBoard = List.of(
                new PokerCard(12, PokerCard.Suit.SPADES), new PokerCard(11, PokerCard.Suit.SPADES),
                new PokerCard(10, PokerCard.Suit.SPADES), new PokerCard(2, PokerCard.Suit.CLUBS),
                new PokerCard(3, PokerCard.Suit.DIAMONDS));
        PokerCard[] weak = {
                new PokerCard(2, PokerCard.Suit.CLUBS), new PokerCard(7, PokerCard.Suit.DIAMONDS)
        };
        List<PokerCard> weakBoard = List.of(
                new PokerCard(14, PokerCard.Suit.HEARTS), new PokerCard(13, PokerCard.Suit.CLUBS),
                new PokerCard(9, PokerCard.Suit.SPADES), new PokerCard(5, PokerCard.Suit.DIAMONDS),
                new PokerCard(3, PokerCard.Suit.CLUBS));
        double royalEquity = PokerBotStrategy.estimateEquity(royalDraw, royalBoard, 5, 80, new Random(11));
        double weakEquity = PokerBotStrategy.estimateEquity(weak, weakBoard, 5, 80, new Random(11));
        check(royalEquity > 0.99, "德州 BOT 应识别不可击败的皇家同花顺");
        check(weakEquity < royalEquity, "德州 BOT 必须区分强牌和弱牌");


        CasinoBot.Profile profile = CasinoBot.profile(0);
        check(!CasinoBot.id("poker", new java.util.UUID(1, 2), 0)
                        .equals(CasinoBot.id("poker", new java.util.UUID(1, 2), 1)),
                "不同 BOT 座位必须使用不同虚拟 UUID");
    }

    private static long score(String... values) {
        List<PokerCard> cards = new ArrayList<>();
        for (String value : values) {
            char suitCode = value.charAt(value.length() - 1);
            String rankText = value.substring(0, value.length() - 1);
            int rank = switch (rankText) {
                case "A" -> 14;
                case "K" -> 13;
                case "Q" -> 12;
                case "J" -> 11;
                default -> Integer.parseInt(rankText);
            };
            PokerCard.Suit suit = switch (suitCode) {
                case 'S' -> PokerCard.Suit.SPADES;
                case 'H' -> PokerCard.Suit.HEARTS;
                case 'D' -> PokerCard.Suit.DIAMONDS;
                case 'C' -> PokerCard.Suit.CLUBS;
                default -> throw new IllegalArgumentException(value);
            };
            cards.add(new PokerCard(rank, suit));
        }
        return PokerHandEvaluator.evaluate(cards);
    }

    private static void testBlackjack() {
        PokerCard aceSpades = new PokerCard(14, PokerCard.Suit.SPADES);
        PokerCard king = new PokerCard(13, PokerCard.Suit.HEARTS);
        PokerCard queen = new PokerCard(12, PokerCard.Suit.CLUBS);
        PokerCard six = new PokerCard(6, PokerCard.Suit.DIAMONDS);
        PokerCard five = new PokerCard(5, PokerCard.Suit.CLUBS);
        PokerCard nine = new PokerCard(9, PokerCard.Suit.SPADES);

        check(BlackjackHand.cardValue(aceSpades) == 11, "A 应先按 11 计算");
        check(BlackjackHand.cardValue(king) == 10, "K 应按 10 计算");
        check(BlackjackHand.cardValue(six) == 6, "小牌应按面值计算");

        // 黑杰克与普通 21 的区别。
        List<PokerCard> natural = List.of(aceSpades, king);
        check(BlackjackHand.value(natural) == 21, "A+K 应为 21 点");
        check(BlackjackHand.blackjack(natural), "起手 A+K 必须判定为黑杰克");
        List<PokerCard> threeCard21 = List.of(six, five, new PokerCard(10, PokerCard.Suit.HEARTS));
        check(BlackjackHand.value(threeCard21) == 21, "6+5+10 应为 21 点");
        check(!BlackjackHand.blackjack(threeCard21), "三张牌凑成的 21 不是黑杰克");

        // A 自动降为 1，避免爆牌。
        List<PokerCard> softToHard = List.of(aceSpades, six, nine);
        check(BlackjackHand.value(softToHard) == 16, "A+6+9 时 A 必须降为 1，得 16");
        check(!BlackjackHand.soft(softToHard), "A 已降为 1 时不再是软牌");
        List<PokerCard> soft17 = List.of(aceSpades, six);
        check(BlackjackHand.value(soft17) == 17 && BlackjackHand.soft(soft17), "A+6 应为软 17");
        List<PokerCard> twoAces = List.of(aceSpades, new PokerCard(14, PokerCard.Suit.HEARTS));
        check(BlackjackHand.value(twoAces) == 12 && BlackjackHand.soft(twoAces), "两张 A 应为软 12");

        // 爆牌与庄家停牌线。
        List<PokerCard> busted = List.of(king, queen, five);
        check(BlackjackHand.bust(busted) && BlackjackHand.value(busted) == 25, "K+Q+5 应为爆牌 25");
        check(!BlackjackHand.dealerMustHit(soft17), "软 17 时荷官必须停牌");
        check(BlackjackHand.dealerMustHit(List.of(king, six)), "硬 16 时荷官必须要牌");
        check(!BlackjackHand.dealerMustHit(List.of(king, new PokerCard(7, PokerCard.Suit.CLUBS))),
                "硬 17 时荷官必须停牌");

        // 分牌判定：同点数即可，K+Q 同为 10 点也算。
        check(BlackjackHand.splittable(List.of(king, queen)), "K+Q 同为 10 点应可分牌");
        check(BlackjackHand.splittable(List.of(six, new PokerCard(6, PokerCard.Suit.SPADES))), "一对 6 应可分牌");
        check(!BlackjackHand.splittable(List.of(six, five)), "6+5 不能分牌");
        check(!BlackjackHand.splittable(List.of(king, queen, five)), "三张牌不能分牌");

        // 分牌得到的 21 不享受 3:2。
        BlackjackSeatHand split = new BlackjackSeatHand(100);
        split.fromSplit(true);
        split.add(aceSpades);
        split.add(king);
        check(split.value() == 21 && !split.blackjack(), "分牌后的 21 不应算作黑杰克");
        BlackjackSeatHand fresh = new BlackjackSeatHand(100);
        fresh.add(aceSpades);
        fresh.add(king);
        check(fresh.blackjack(), "未分牌的起手 21 应算黑杰克");

        // 分 A 之后不能再要牌，也不能继续双倍。
        BlackjackSeatHand splitAce = new BlackjackSeatHand(100);
        splitAce.fromSplit(true);
        splitAce.splitAce(true);
        splitAce.add(aceSpades);
        splitAce.add(six);
        check(!splitAce.canHit(), "分 A 之后不能继续要牌");
        check(!splitAce.canDouble(10000), "分 A 之后不能双倍");

        // 双倍与分牌的前置条件。
        BlackjackSeatHand pair = new BlackjackSeatHand(100);
        pair.add(six);
        pair.add(new PokerCard(6, PokerCard.Suit.SPADES));
        check(pair.canDouble(100), "筹码足够时前两张牌可以双倍");
        check(!pair.canDouble(99), "筹码不足时不能双倍");
        check(pair.canSplit(100, 1, 4), "筹码足够且未达上限时可以分牌");
        check(!pair.canSplit(100, 4, 4), "手数达到上限后不能再分牌");
        pair.add(five);
        check(!pair.canDouble(100), "已经三张牌时不能双倍");
    }

    private static void testArenaStyles() {
        // 下注区亮起来时必须是完整的实心亮方块；曾经误用灯笼导致地板变成一片挂灯。
        Set<Material> allowedZoneActive = Set.of(
                Material.SEA_LANTERN, Material.GLOWSTONE, Material.SHROOMLIGHT,
                Material.OCHRE_FROGLIGHT, Material.PEARLESCENT_FROGLIGHT, Material.VERDANT_FROGLIGHT);

        check(PokerArenaStyle.count() == 13, "赌场装修应有 13 种");
        Set<Integer> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (PokerArenaStyle style : PokerArenaStyle.values()) {
            PokerArenaStyle.Palette palette = style.palette();
            String label = style.display();
            check(ids.add(style.id()), "赌场装修编号重复：" + style.id());
            check(names.add(label), "赌场装修名称重复：" + label);
            check(style.id() >= 1 && style.id() <= PokerArenaStyle.count(), "装修编号必须连续：" + style.id());
            check(PokerArenaStyle.byId(style.id()) == style, "byId 未能取回同一装修：" + style.id());
            check(PokerArenaStyle.parse(Integer.toString(style.id())) == style, "编号解析失败：" + style.id());
            check(PokerArenaStyle.parse(style.name()) == style, "枚举名解析失败：" + style.name());

            // tableOverlay / tableOverlayRim 允许为空，其余材料都必须填。
            check(palette.floor() != null && palette.floorTrim() != null, label + " 缺地面材料");
            check(palette.wall() != null && palette.wallAccent() != null && palette.wallBand() != null,
                    label + " 缺墙体材料");
            check(palette.ceiling() != null && palette.ceilingAccent() != null, label + " 缺天花材料");
            check(palette.tableTop() != null && palette.tableRim() != null, label + " 缺牌桌材料");
            check(palette.tableGlowA() != null && palette.tableGlowB() != null, label + " 缺桌面底层材料");
            check(palette.bankBody() != null && palette.bankTrim() != null && palette.bankTop() != null,
                    label + " 缺银行材料");
            check(palette.zoneIdle() != null && palette.zoneActive() != null, label + " 缺下注区材料");
            check(palette.decorLog() != null && palette.decorLeaves() != null && palette.decorLamp() != null,
                    label + " 缺露天装饰材料");

            check(allowedZoneActive.contains(palette.zoneActive()),
                    label + " 的下注区激活方块必须是完整亮方块，不能是灯笼这类小物件：" + palette.zoneActive());
            check(palette.zoneActive() != palette.zoneIdle(), label + " 的下注区激活色与闲置色应能区分");
            check(palette.tableTop() != palette.tableRim(), label + " 桌面与桌沿应能区分");

            // 地板、墙、天花必须各用各的方块，不能一整套沿用。
            if (!palette.outdoor()) {
                check(palette.floor() != palette.wall(), label + " 的地板与墙体用了同一种方块");
                check(palette.wall() != palette.ceiling(), label + " 的墙体与天花用了同一种方块");
                check(palette.floor() != palette.ceiling(), label + " 的地板与天花用了同一种方块");
            }
            check(style.chipBaseDy() == (palette.hasOverlay() ? 2 : 1),
                    label + " 的筹码基准高度与覆盖层不一致");
        }
        check(PokerArenaStyle.parse("不存在的装修") == null, "未知装修名应返回 null");
        check(PokerArenaStyle.byId(999) == PokerArenaStyle.CLASSIC, "非法编号应回退到经典装修");

        // 最初三种的关键外观必须保持原样，不能因为抽调色板被改味。
        PokerArenaStyle.Palette classic = PokerArenaStyle.CLASSIC.palette();
        check(classic.floor() == Material.SMOOTH_QUARTZ && classic.tableTop() == Material.GREEN_CONCRETE
                && classic.tableRim() == Material.GOLD_BLOCK && !classic.hasOverlay(),
                "皇家绿毯的外观被改动了");
        PokerArenaStyle.Palette luminous = PokerArenaStyle.LUMINOUS.palette();
        check(luminous.hasOverlay() && luminous.tableGlowA() == Material.GLOWSTONE
                && luminous.tableGlowB() == Material.SEA_LANTERN,
                "海晶玻璃的发光棋盘格被改动了");
        PokerArenaStyle.Palette nature = PokerArenaStyle.NATURE.palette();
        check(nature.outdoor() && nature.floor() == Material.GRASS_BLOCK
                && nature.tableTop() == Material.GREEN_WOOL
                && nature.tableRim() == Material.STRIPPED_OAK_WOOD
                && nature.decorLog() == Material.OAK_LOG && nature.decorLamp() == Material.LANTERN,
                "露天自然的外观被改动了");
    }

    private static void testBotArchetypes() {
        check(CasinoBot.archetypeCount() == 6, "BOT 原型应有 6 种");
        Set<String> labels = new HashSet<>();
        double weakest = 1.0;
        double strongest = 0.0;
        double mostAggressive = 0.0;
        for (int index = 0; index < CasinoBot.archetypeCount(); index++) {
            CasinoBot.Profile profile = CasinoBot.profile(index);
            check(labels.add(profile.label()), "BOT 名称重复：" + profile.label());
            check(profile.skill() >= 0.0 && profile.skill() <= 1.0, profile.label() + " 的水平应在 0～1");
            check(profile.aggression() >= 0.0 && profile.aggression() <= 1.0, profile.label() + " 攻击性越界");
            check(profile.bluff() >= 0.0 && profile.bluff() <= 1.0, profile.label() + " 诈唬率越界");
            check(CasinoBot.name(index).startsWith("BOT·"), "BOT 名字必须带前缀");
            weakest = Math.min(weakest, profile.skill());
            strongest = Math.max(strongest, profile.skill());
            mostAggressive = Math.max(mostAggressive, profile.aggression());
        }
        check(weakest < 0.3, "应当存在明显的新手 BOT");
        check(strongest > 0.8, "应当存在明显的高手 BOT");
        check(mostAggressive > 0.85, "应当存在明显激进的 BOT");

        // 水平直接影响采样量与误判幅度：新手采样更少、看错牌更多。
        CasinoBot.Profile novice = CasinoBot.profile(0);
        CasinoBot.Profile expert = CasinoBot.profile(5);
        check(novice.skill() < expert.skill(), "0 号应比 5 号弱");
        check(novice.scaledSamples(200) < expert.scaledSamples(200), "新手的采样量应低于高手");
        check(novice.misreadRange() > expert.misreadRange(), "新手的牌力误判幅度应大于高手");
        check(expert.scaledSamples(200) <= 200, "采样量不应超过基准值");
        check(novice.scaledSamples(200) >= 20, "采样量不能低于下限");

        // 每桌随机分配，六种各出现一次。
        List<Integer> order = CasinoBot.shuffledArchetypes(new Random(7));
        check(order.size() == CasinoBot.archetypeCount(), "打乱后数量应保持不变");
        check(new HashSet<>(order).size() == order.size(), "打乱后不应出现重复原型");

        // 高手在人多时收敛诈唬，新手不分场合；用同一随机种子对比决策倾向。
        PokerCard[] weak = {new PokerCard(2, PokerCard.Suit.CLUBS), new PokerCard(7, PokerCard.Suit.DIAMONDS)};
        List<PokerCard> board = List.of(
                new PokerCard(14, PokerCard.Suit.HEARTS), new PokerCard(13, PokerCard.Suit.CLUBS),
                new PokerCard(9, PokerCard.Suit.SPADES));
        int noviceFolds = 0;
        int expertFolds = 0;
        for (int seed = 0; seed < 60; seed++) {
            if (PokerBotStrategy.decide(weak, board, 4, 1000, 200, 300, 120, novice,
                    new Random(seed)).action() == PokerBotStrategy.Action.FOLD) noviceFolds++;
            if (PokerBotStrategy.decide(weak, board, 4, 1000, 200, 300, 120, expert,
                    new Random(seed)).action() == PokerBotStrategy.Action.FOLD) expertFolds++;
        }
        check(expertFolds > noviceFolds, "面对明显的坏牌，高手应比新手更常弃牌");
    }

    /**
     * 装潢名、房型名、玩法名是用枚举名拼出来的键，静态搜索找不到它们，
     * 漏一条语言文件条目在游戏里就是一句 {@code <missing: ...>}。这里逐个取一遍。
     */
    /** 用枚举名等值拼出来的键。静态搜源码找不到它们，只能在这里列全。 */
    private static Set<String> computedKeys() {
        Set<String> keys = new java.util.TreeSet<>();
        for (PokerArenaStyle style : PokerArenaStyle.values()) {
            String id = style.name().toLowerCase(java.util.Locale.ROOT);
            keys.add("decor." + id + ".name");
            keys.add("decor." + id + ".description");
        }
        for (ArenaShape shape : ArenaShape.values()) {
            String id = shape.name().toLowerCase(java.util.Locale.ROOT);
            keys.add("shape." + id + ".name");
            keys.add("shape." + id + ".description");
        }
        for (io.github.xingguanglang.casinotables.GameType type : io.github.xingguanglang.casinotables.GameType.values()) {
            keys.add("game." + type.name().toLowerCase(java.util.Locale.ROOT) + ".display");
        }
        // FlightArena.COLOR_KEYS 和 FlightManager.rankKey() 拼出来的，同样躲开静态搜索。
        for (String color : List.of("red", "yellow", "blue", "green")) keys.add("flight.color." + color);
        for (String rank : List.of("first", "second", "third", "other")) {
            keys.add("flight.result.entry." + rank);
        }
        return keys;
    }

    private static void testComputedMessageKeys() {
        List<String> texts = new ArrayList<>();
        for (PokerArenaStyle style : PokerArenaStyle.values()) {
            texts.add(style.display());
            texts.add(style.description());
        }
        for (ArenaShape shape : ArenaShape.values()) {
            texts.add(shape.display());
            texts.add(shape.description());
        }
        for (io.github.xingguanglang.casinotables.GameType type : io.github.xingguanglang.casinotables.GameType.values()) {
            texts.add(type.display());
        }
        for (String text : texts) {
            check(text != null && !text.isBlank(), "拼接键取到了空文本");
            check(!text.startsWith("<missing:"), "语言文件缺少拼接键：" + text);
        }
    }

    /**
     * 静态扫一遍源码：所有写成字面量的 Messages.msg / msgList 键都必须在 en_US.yml 里存在。
     *
     * <p>只跑一遍游戏是覆盖不到全部取文本点的——空座位这种键就漏到过运行期，
     * 玩家看到的是 {@code <missing: ...>}。这里不靠执行，直接对着源码点名。
     */
    private static void testEveryLiteralKeyExists(String[] args) {
        java.nio.file.Path sourceRoot = java.nio.file.Path.of(
                args.length > 0 ? args[0] : System.getProperty("user.dir"), "src", "main", "java");
        check(java.nio.file.Files.isDirectory(sourceRoot), "找不到源码目录：" + sourceRoot);

        org.bukkit.configuration.file.YamlConfiguration en = loadLanguage("en_US");
        java.util.regex.Pattern literal = java.util.regex.Pattern.compile(
                "(?:Messages[.](?:msg|msgList)|Reason[.]of)[(][ ]*\"([^\"]+)\"");
        // Reason.of 也是取文本的入口：理由的键同样必须在语言文件里存在。
        // 用枚举名等值拼出来的键，交给 testComputedMessageKeys 逐个取一遍。
        java.util.regex.Pattern computed = java.util.regex.Pattern.compile(
                "(?:Messages[.](?:msg|msgList)|Reason[.]of)[(][ ]*\"[^\"]*\"[ ]*[+]");

        Set<String> missing = new java.util.TreeSet<>();
        Set<String> referenced = new java.util.TreeSet<>();
        java.util.regex.Pattern keyShaped = java.util.regex.Pattern.compile(
                "\"([a-z][a-z0-9-]*(?:[.][a-z0-9-]+)+)\"");
        int literals = 0;
        int computedSites = 0;
        try (java.util.stream.Stream<java.nio.file.Path> files =
                     java.nio.file.Files.walk(sourceRoot)) {
            for (java.nio.file.Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
                java.util.regex.Matcher shaped = keyShaped.matcher(text);
                while (shaped.find()) referenced.add(shaped.group(1));
                java.util.regex.Matcher computedMatcher = computed.matcher(text);
                while (computedMatcher.find()) computedSites++;
                java.util.regex.Matcher matcher = literal.matcher(text);
                while (matcher.find()) {
                    // 拼接键的前半截也长得像字面量；后面跟着 " + " 的一律交给 testComputedMessageKeys。
                    int after = matcher.end();
                    while (after < text.length() && text.charAt(after) == ' ') after++;
                    if (after < text.length() && text.charAt(after) == '+') continue;
                    String key = matcher.group(1);
                    referenced.add(key);
                    literals++;
                    if (!en.isString(key) && !en.isList(key)) {
                        missing.add(key + "  (" + file.getFileName() + ")");
                    }
                }
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError("读取源码失败：" + exception.getMessage());
        }
        check(literals > 500, "只扫到 " + literals + " 个字面量键，扫描大概率没生效");
        check(missing.isEmpty(), "en_US.yml 缺少代码里用到的键：" + missing);
        check(computedSites == COMPUTED_KEY_SITES,
                "拼接键的调用点从 " + COMPUTED_KEY_SITES + " 变成了 " + computedSites
                        + " 个；新增的那处要补进 testComputedMessageKeys");

        // 反向对账：语言文件里的每一条都必须有人读。
        // 没有这一条的时候，poker.seat.empty 这种死键能一直躺在服主会去编辑的文件里，
        // 服主改了半天发现游戏里毫无变化，却不知道为什么。
        // referenced 是「所有长得像键的字面量」，故意放宽：宁可漏报死键，也不能误杀在用的键。
        Set<String> reachable = new java.util.TreeSet<>(referenced);
        reachable.addAll(computedKeys());
        Set<String> dead = new java.util.TreeSet<>();
        for (String key : en.getKeys(true)) {
            if (en.isConfigurationSection(key)) continue;
            if (!reachable.contains(key)) dead.add(key);
        }
        check(dead.isEmpty(), "语言文件里这些键没有任何代码读取，应当删掉或接上：" + dead);
    }

    /** 用枚举名拼键的调用点数量，testComputedMessageKeys 覆盖的就是这些。 */
    private static final int COMPUTED_KEY_SITES = 8;

    /**
     * 摆建筑方块只能走 ArenaBlocks.set。
     *
     * <p>直接 setType 的树叶 persistent 是 false，原版会当成被砍断的树慢慢枯掉、
     * 掉一地树苗，露天风格的墙和装饰树就这么烂出洞来。这条检查盯的是「有人又绕过去了」。
     */
    private static void testBlocksGoThroughArenaBlocks(String[] args) {
        java.nio.file.Path sourceRoot = java.nio.file.Path.of(
                args.length > 0 ? args[0] : System.getProperty("user.dir"), "src", "main", "java");
        List<String> offenders = new ArrayList<>();
        java.util.regex.Pattern fixedMaterial =
                java.util.regex.Pattern.compile("[.]setType[(]Material[.]([A-Z_]+)");
        try (java.util.stream.Stream<java.nio.file.Path> files =
                     java.nio.file.Files.walk(sourceRoot)) {
            for (java.nio.file.Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                if (name.equals("ArenaBlocks.java")) continue;
                List<String> lines = java.nio.file.Files.readString(file,
                        java.nio.charset.StandardCharsets.UTF_8).lines().toList();
                for (int index = 0; index < lines.size(); index++) {
                    String line = lines.get(index);
                    if (!line.contains(".setType(")) continue;
                    // 规则是：材质来自调色板（也就是个变量）的，必须走 ArenaBlocks，
                    // 因为那个变量随时可能是树叶。写死的字面量当场就能看出是不是树叶，
                    // 不是就放行——比如飞行棋那颗紧接着要设朝向的石按钮。
                    java.util.regex.Matcher literal = fixedMaterial.matcher(line);
                    if (literal.find() && !literal.group(1).endsWith("_LEAVES")) continue;
                    offenders.add(name + ":" + (index + 1) + "  " + line.trim());
                }
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError("读取源码失败：" + exception.getMessage());
        }
        check(offenders.isEmpty(),
                "这些地方绕过了 ArenaBlocks.set 直接摆方块，树叶会枯萎：" + offenders);
    }

    private static void testArenaShapes() {
        check(ArenaShape.count() == 4, "赌场轮廓应有 4 种");
        int[][] fixtures = {{18, 15}, {-18, 15}, {18, -15}, {-18, -15}, {18, 12}, {18, -12},
                {10, 15}, {-10, 15}, {17, 10}, {-17, -10}, {19, 12}, {16, 15}, {18, 2}, {15, 6}};
        int[][] rooms = {{20, 18}, {20, 16}};
        for (ArenaShape shape : ArenaShape.values()) {
            check(ArenaShape.byId(shape.id()) == shape, "byId 未取回同一轮廓：" + shape.id());
            check(ArenaShape.parse(Integer.toString(shape.id())) == shape, "编号解析失败：" + shape.id());
            check(ArenaShape.parse(shape.name()) == shape, "枚举名解析失败：" + shape.name());
            for (int[] room : rooms) {
                int rx = shape.roomX(room[0]);
                int rz = shape.roomZ(room[1]);
                for (int[] fixture : fixtures) {
                    check(shape.inside(fixture[0], fixture[1], rx, rz),
                            shape.display() + " 把写死的设施 (" + fixture[0] + "," + fixture[1]
                                    + ") 甩到了轮廓外，会悬空");
                }
                // 中心必须在内部，外接矩形之外必须在外部。
                check(shape.inside(0, 0, rx, rz), shape.display() + " 的中心不在轮廓内");
                check(!shape.inside(rx + 1, 0, rx, rz), shape.display() + " 越过外接矩形仍算内部");
                check(!shape.inside(0, rz + 1, rx, rz), shape.display() + " 越过外接矩形仍算内部");

                // 外墙必须四连通闭合：内部格的任一四邻若在外部，自己就必须是墙。
                for (int x = -rx; x <= rx; x++) {
                    for (int z = -rz; z <= rz; z++) {
                        if (!shape.inside(x, z, rx, rz)) continue;
                        boolean touchesOutside = !shape.inside(x + 1, z, rx, rz)
                                || !shape.inside(x - 1, z, rx, rz)
                                || !shape.inside(x, z + 1, rx, rz)
                                || !shape.inside(x, z - 1, rx, rz);
                        check(touchesOutside == shape.boundary(x, z, rx, rz),
                                shape.display() + " 在 (" + x + "," + z + ") 的外墙判定不闭合，会留下能走出去的缝");
                        if (shape.boundary(x, z, rx, rz)) {
                            check(!shape.trim(x, z, rx, rz), shape.display() + " 的装饰环压在外墙上");
                        }
                    }
                }
            }
        }
        check(ArenaShape.RECTANGLE.roomX(20) == 20, "方形不应放大外接矩形");
        check(ArenaShape.ROUND.roomX(20) == 20 + ArenaShape.GROWTH, "非方形轮廓需要放大外接矩形");
        check(!ArenaShape.RECTANGLE.open() && ArenaShape.OPEN.open(), "开放式标记错误");
        check(ArenaShape.parse("不存在的轮廓") == null, "未知轮廓名应返回 null");
        check(ArenaShape.byId(999) == ArenaShape.RECTANGLE, "非法编号应回退到方形");

        // 没有墙的轮廓下，外接矩形里会有大片没有地板的格子。玩家离场判定必须按真实轮廓走，
        // 否则玩家能走出边缘掉进虚空，而移动回弹加免伤会把人永久卡住。
        for (ArenaShape shape : ArenaShape.values()) {
            int rx = shape.roomX(20);
            int rz = shape.roomZ(18);
            int floorless = 0;
            for (int x = -rx; x <= rx; x++) {
                for (int z = -rz; z <= rz; z++) {
                    if (!shape.inside(x, z, rx, rz)) floorless++;
                }
            }
            if (shape == ArenaShape.RECTANGLE) {
                check(floorless == 0, "方形的外接矩形应当被地板填满");
            } else {
                check(floorless > 0, shape.display() + " 应当有外接矩形内但无地板的格子，测试前提才成立");
            }
        }
    }

    private static void testLanguageFiles() {
        // 翻译最容易出的问题是「改了英文忘了改中文」，这里强制两份 key 完全一致。
        Set<String> english = languageKeys("en_US");
        Set<String> chinese = languageKeys("zh_CN");
        check(!english.isEmpty(), "en_US.yml 读不到任何键");
        check(!chinese.isEmpty(), "zh_CN.yml 读不到任何键");

        Set<String> missingInChinese = new java.util.TreeSet<>(english);
        missingInChinese.removeAll(chinese);
        check(missingInChinese.isEmpty(), "zh_CN.yml 缺少这些键：" + missingInChinese);

        Set<String> missingInEnglish = new java.util.TreeSet<>(chinese);
        missingInEnglish.removeAll(english);
        check(missingInEnglish.isEmpty(), "en_US.yml 缺少这些键：" + missingInEnglish);

        // 占位符也必须一一对应，否则某个语言下会显示成原始的 {player}。
        org.bukkit.configuration.file.YamlConfiguration en = loadLanguage("en_US");
        org.bukkit.configuration.file.YamlConfiguration zh = loadLanguage("zh_CN");
        for (String key : english) {
            Set<String> a = placeholders(String.valueOf(en.get(key)));
            Set<String> b = placeholders(String.valueOf(zh.get(key)));
            check(a.equals(b), "占位符不一致：" + key + " en=" + a + " zh=" + b);
        }
    }

    private static org.bukkit.configuration.file.YamlConfiguration loadLanguage(String code) {
        java.io.InputStream stream = SelfTest.class.getResourceAsStream("/lang/" + code + ".yml");
        check(stream != null, "找不到语言文件 lang/" + code + ".yml");
        try (java.io.InputStreamReader reader =
                     new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
            return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException exception) {
            throw new AssertionError("读取语言文件失败：" + code, exception);
        }
    }

    private static Set<String> languageKeys(String code) {
        org.bukkit.configuration.file.YamlConfiguration yaml = loadLanguage(code);
        Set<String> keys = new HashSet<>();
        for (String key : yaml.getKeys(true)) {
            if (!yaml.isConfigurationSection(key)) keys.add(key);
        }
        return keys;
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new java.util.TreeSet<>();
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("[{]([a-zA-Z0-9_-]+)[}]").matcher(text);
        while (matcher.find()) found.add(matcher.group(1));
        return found;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
