package com.koole.higherRedStoneFun.core;

import org.bukkit.Material;

import java.util.function.Predicate;

/**
 * 材质匹配辅助。
 *
 * <p>Paper 的 {@link org.bukkit.Tag} 里<b>没有</b>玻璃标签
 * （只有 {@code IMPERMEABLE}、{@code SMELTS_TO_GLASS} 等），
 * 所以玻璃族的宽容匹配需要自己实现。</p>
 *
 * <p>用「名称模式」而不是穷举 Material 列表，这样游戏以后新增
 * 玻璃变体时会自动支持，不需要改代码。</p>
 */
public final class Materials {

    /**
     * 不是方块、或不该算作「结构玻璃」的物品，即使名字里带 GLASS 也要排除。
     *
     * <p>注意 LEGACY_* 系列已被标记为待删除且不是可放置方块，
     * 因此依赖 {@code isBlock()} 把它们挡掉即可，不需要显式列举。</p>
     */
    private static final Predicate<Material> GLASS_EXCLUSIONS = m ->
            m == Material.GLASS_BOTTLE || m == Material.SPYGLASS;

    /**
     * 玻璃族：玻璃、染色玻璃、玻璃板、染色玻璃板、遮光玻璃。
     *
     * <p>玩家用哪种玻璃都可以，不会因为「放的是浅灰色玻璃而不是普通玻璃」
     * 导致结构不成立。</p>
     */
    public static final Predicate<Material> GLASS_LIKE = m ->
            m != null
                    && !m.isAir()
                    && m.isBlock()
                    && m.name().contains("GLASS")
                    && !GLASS_EXCLUSIONS.test(m);

    /** 金属机身族：铁块、铜块、金块等（用于将来的其它结构）。 */
    public static final Predicate<Material> METAL_BLOCK = m ->
            m == Material.IRON_BLOCK
                    || m == Material.COPPER_BLOCK
                    || m == Material.GOLD_BLOCK
                    || m == Material.NETHERITE_BLOCK;

    private Materials() {
    }

    /** 判断是否为玻璃族方块。 */
    public static boolean isGlassLike(Material material) {
        return GLASS_LIKE.test(material);
    }

    /** 人类可读的玻璃族描述，用于提示文本。 */
    public static String glassLabel() {
        return "玻璃";
    }

    /**
     * 方块的中文名。
     *
     * <p>用于多方块结构的展示——<b>结构里全是原版方块</b>，
     * 所以必须显示原版方块的名字（「工作台」），而不是机器的名字
     * （「增强工作台」）。后者会让玩家以为要在那里放一台机器。</p>
     *
     * <p>只覆盖本插件用作机器外观的方块；其余回退到英文名美化。</p>
     */
    private static final java.util.Map<Material, String> BLOCK_NAMES = java.util.Map.ofEntries(
            java.util.Map.entry(Material.CRAFTING_TABLE, "工作台"),
            java.util.Map.entry(Material.GRINDSTONE, "砂轮"),
            java.util.Map.entry(Material.PISTON, "活塞"),
            java.util.Map.entry(Material.STICKY_PISTON, "黏性活塞"),
            java.util.Map.entry(Material.BLAST_FURNACE, "高炉"),
            java.util.Map.entry(Material.FURNACE, "熔炉"),
            java.util.Map.entry(Material.SMOKER, "烟熏炉"),
            java.util.Map.entry(Material.CAULDRON, "炼药锅"),
            java.util.Map.entry(Material.STONECUTTER, "切石机"),
            java.util.Map.entry(Material.DAYLIGHT_DETECTOR, "阳光探测器"),
            java.util.Map.entry(Material.MAGMA_BLOCK, "岩浆块"),
            java.util.Map.entry(Material.RESPAWN_ANCHOR, "重生锚"),
            java.util.Map.entry(Material.COPPER_BLOCK, "铜块"),
            java.util.Map.entry(Material.AMETHYST_BLOCK, "紫水晶块"),
            java.util.Map.entry(Material.TARGET, "标靶"),
            java.util.Map.entry(Material.CRAFTER, "合成器"),
            java.util.Map.entry(Material.BEACON, "信标"),
            java.util.Map.entry(Material.SCULK_SENSOR, "幽匿感测体"),
            java.util.Map.entry(Material.CALIBRATED_SCULK_SENSOR, "校准幽匿感测体"),
            java.util.Map.entry(Material.SCULK_SHRIEKER, "幽匿尖啸体"),
            java.util.Map.entry(Material.SMITHING_TABLE, "锻造台"),
            java.util.Map.entry(Material.BREWING_STAND, "酿造台"),
            java.util.Map.entry(Material.BEEHIVE, "蜂巢"),
            java.util.Map.entry(Material.LODESTONE, "磁石"),
            java.util.Map.entry(Material.IRON_BLOCK, "铁块"),
            java.util.Map.entry(Material.NETHERITE_BLOCK, "下界合金块"),
            java.util.Map.entry(Material.GLASS, "玻璃"),
            java.util.Map.entry(Material.BOOK, "书"),
            java.util.Map.entry(Material.OAK_SIGN, "橡木告示牌")
    );

    /** 方块的中文名；没有收录时回退到英文名美化。 */
    public static String blockName(Material material) {
        if (material == null) {
            return "未知方块";
        }
        String known = BLOCK_NAMES.get(material);
        if (known != null) {
            return known;
        }
        return material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
