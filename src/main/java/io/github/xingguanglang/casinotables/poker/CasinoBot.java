package io.github.xingguanglang.casinotables.poker;

import io.github.xingguanglang.casinotables.Messages;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** 本地赌场 BOT 的身份与性格原型：六种从新手到高手的打法，每桌随机分配。 */
public final class CasinoBot {
    /** 名字前缀是内部标记，namedBot() 靠它认出 BOT 座位，因此不进语言文件。 */
    private static final String BOT_PREFIX = "BOT·";

    /** 六种原型，按水平从低到高排列；名字与打法一一对应。 */
    private static final Profile[] PROFILES = {
            // 新手：牌力判断很差，什么牌都想玩，加注全凭冲动。
            new Profile("poker.bot.name.hothead", "poker.bot.tier.novice", 0.12, 0.22, 0.70, 0.22),
            // 新手：典型「跟注站」，几乎不主动加注，也几乎不弃牌。
            new Profile("poker.bot.name.calling-station", "poker.bot.tier.novice", 0.20, 0.30, 0.12, 0.02),
            // 中级：只玩好牌但打得软，赢小输大。
            new Profile("poker.bot.name.rock", "poker.bot.tier.regular", 0.52, 0.78, 0.34, 0.05),
            // 中级：极度激进的疯子，靠压迫取胜，牌力判断一般。
            new Profile("poker.bot.name.maniac", "poker.bot.tier.regular", 0.44, 0.26, 0.94, 0.30),
            // 高手：紧凶，牌力判断准，该弃就弃该压就压。
            new Profile("poker.bot.name.assassin", "poker.bot.tier.pro", 0.86, 0.72, 0.70, 0.12),
            // 高手：算得准且会挑时机诈唬。
            new Profile("poker.bot.name.old-fox", "poker.bot.tier.pro", 0.96, 0.56, 0.78, 0.24)
    };

    private CasinoBot() { }

    public static int archetypeCount() { return PROFILES.length; }

    /**
     * BOT 只存在于一局之内，这个 UUID 不落盘，所以换命名空间不会影响任何历史数据。
     */
    public static UUID id(String game, UUID host, int side) {
        return UUID.nameUUIDFromBytes(("CasinoTables:" + game + ":" + host + ":" + side)
                .getBytes(StandardCharsets.UTF_8));
    }

    public static String name(int archetype) { return BOT_PREFIX + profile(archetype).label(); }

    public static Profile profile(int archetype) {
        return PROFILES[Math.floorMod(archetype, PROFILES.length)];
    }

    public static boolean namedBot(String name) { return name != null && name.startsWith(BOT_PREFIX); }

    /** 每张牌桌开局时打乱一次原型顺序，同一张桌上六种打法各出现一次。 */
    public static List<Integer> shuffledArchetypes(Random random) {
        List<Integer> order = new ArrayList<>(PROFILES.length);
        for (int index = 0; index < PROFILES.length; index++) order.add(index);
        Collections.shuffle(order, random);
        return order;
    }

    /**
     * @param labelKey   展示名在语言文件中的键
     * @param tierKey    水平档位在语言文件中的键，仅用于展示
     * @param skill      0～1 的水平：越低越算不准牌力、越不看底池赔率，越容易犯错
     * @param tightness  越高越只玩好牌
     * @param aggression 越高越倾向加注而不是跟注
     * @param bluff      诈唬频率
     */
    public record Profile(String labelKey, String tierKey, double skill, double tightness,
                          double aggression, double bluff) {
        /** 展示名从语言文件读取。 */
        public String label() { return Messages.msg(labelKey); }

        /** 水平档位展示名从语言文件读取。 */
        public String tier() { return Messages.msg(tierKey); }

        /** 水平越低，蒙特卡洛采样越少，牌力估算自然越毛糙。 */
        public int scaledSamples(int base) {
            return Math.max(20, (int) Math.round(base * (0.22 + 0.78 * skill)));
        }

        /** 水平越低，对自己牌力的误判越大。 */
        public double misreadRange() { return 0.34 * (1.0 - skill); }
    }
}
