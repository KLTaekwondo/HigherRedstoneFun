package com.koole.higherRedStoneFun.machines.logic;

import org.bukkit.Material;

/**
 * 燃料热值表。
 *
 * <h2>红石是唯一的燃料</h2>
 *
 * <p>本插件的机器<b>不烧煤，只烧红石</b>。这是刻意的设计选择，理由是：</p>
 *
 * <ul>
 *   <li><b>主题自洽</b>：插件叫 HigherRedStoneFun，机器靠红石运转才立得住。</li>
 *   <li><b>认知简单</b>：玩家只需要记一条规则——「机器的燃料是红石」。
 *       如果同时支持煤/木板/岩浆，玩家反而要记一堆。</li>
 *   <li><b>给红石一个核心位置</b>：红石从「只用来做红石电路」变成贯穿整个
 *       科技树的必需品——它既是燃料，也是电力层的原料。</li>
 * </ul>
 *
 * <h2>为什么不做成「第三种能源」</h2>
 *
 * <p>「烧红石」和「烧煤」在机制上完全一样，只是物品不同。把它做成独立能源
 * 只会多一套概念，玩法上没有任何新东西。所以红石归入<b>燃料层</b>，
 * 作为其中（也是唯一）的燃料。</p>
 *
 * <p>三层结构：手动（增强工作台）→ 红石燃料（基础机器）→ 电力（高级机器）。</p>
 */
public final class FuelValues {

    /** 1 个红石能提供的运行 tick 数（= 80 秒机器运转）。 */
    public static final int REDSTONE_TICKS = 1_600;

    /** 1 个红石块 = 9 个红石。 */
    public static final int REDSTONE_BLOCK_TICKS = REDSTONE_TICKS * 9;

    private FuelValues() {
    }

    /**
     * 返回该物品作为燃料能提供的运行 tick 数，0 表示不可燃。
     *
     * <p>只认红石与红石块。其它原版燃料（煤、木板、岩浆桶…）在这里
     * <b>一律返回 0</b>——这是刻意的，不是遗漏。</p>
     */
    public static int ticksFor(Material material) {
        if (material == null || material.isAir()) {
            return 0;
        }
        if (material == Material.REDSTONE) {
            return REDSTONE_TICKS;
        }
        if (material == Material.REDSTONE_BLOCK) {
            return REDSTONE_BLOCK_TICKS;
        }
        return 0;
    }

    /** 判断该物品能否作为本插件机器的燃料。 */
    public static boolean isFuel(Material material) {
        return ticksFor(material) > 0;
    }

    /** 燃料的中文名，用于界面与提示。 */
    public static String fuelName() {
        return "红石";
    }
}
