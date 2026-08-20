package io.github.casinotables.arena;

/**
 * 竞技场实体身上打的记分板标签，全插件唯一出处。
 *
 * <p>集中在这里是有教训的：监听器曾经手抄过这三个字符串，其中一个抄成了早已删除的玩法的旧名字，
 * 于是 21 点荷官的右键保护静默失效——玩家点荷官会弹出村民交易界面，而一模一样的德州荷官却是好的。
 * 编译器抓不到抄错的字面量，所以谁也不许再写字面量，一律引用这里的常量。
 */
public final class ArenaTags {
    /** 飞行棋棋子实体。 */
    public static final String FLIGHT_PIECE = "casinotables_flight_piece";
    /** 德州牌桌上的荷官与全息文字。 */
    public static final String POKER_DISPLAY = "casinotables_poker_display";
    /** 21 点牌桌上的荷官与全息文字。 */
    public static final String BLACKJACK_ENTITY = "casinotables_blackjack_display";

    private ArenaTags() { }
}
