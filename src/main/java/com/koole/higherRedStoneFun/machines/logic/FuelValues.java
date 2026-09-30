package com.koole.higherRedStoneFun.machines.logic;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.HashMap;
import java.util.Map;

/**
 * 燃料热值表。
 *
 * <p>相比直接调用 {@code Material.isFuel()}（原版只给一个固定燃烧时间），
 * 这里做了一层显式映射，好处是可以给特殊燃料（岩浆桶、烈焰粉、
 * 本插件的「压缩燃料棒」）设定更高的热值，形成一条可升级的燃料线。</p>
 */
public final class FuelValues {

    private static final Map<Material, Integer> OVERRIDES = new HashMap<>();

    /** 原版燃料的默认倍率：把原版燃烧时间换算成本插件的 tick 数。 */
    private static final int VANILLA_MULTIPLIER = 4;

    /** 兜底值：未知但可燃烧的物品给 100 tick。 */
    private static final int DEFAULT_TICKS = 100;

    static {
        // 高能燃料：给玩家明确的升级目标
        OVERRIDES.put(Material.LAVA_BUCKET, 20_000);
        OVERRIDES.put(Material.BLAZE_ROD, 2_400);
        OVERRIDES.put(Material.BLAZE_POWDER, 1_200);
        OVERRIDES.put(Material.COAL_BLOCK, 16_000);
        OVERRIDES.put(Material.DRIED_KELP_BLOCK, 4_000);
        OVERRIDES.put(Material.MAGMA_CREAM, 800);
        OVERRIDES.put(Material.BAMBOO_BLOCK, 600);
    }

    private FuelValues() {
    }

    /** 返回该物品作为燃料能提供的运行 tick 数，0 表示不可燃。 */
    public static int ticksFor(Material material) {
        if (material == null || material.isAir()) {
            return 0;
        }
        Integer override = OVERRIDES.get(material);
        if (override != null) {
            return override;
        }
        if (material.isFuel()) {
            // 原版燃烧时间（tick）放大，使本插件机器明显快于熔炉
            int vanilla = vanillaBurnTicks(material);
            return vanilla > 0 ? vanilla * VANILLA_MULTIPLIER : DEFAULT_TICKS;
        }
        return 0;
    }

    /** 判断该物品能否作为本插件机器的燃料。 */
    public static boolean isFuel(Material material) {
        return ticksFor(material) > 0;
    }

    /**
     * 原版燃烧时间。
     *
     * <p>Paper API 未直接暴露燃烧时间表，这里对常见燃料做一次近似，
     * 其余可燃物统一按 200 tick 处理。</p>
     */
    private static int vanillaBurnTicks(Material material) {
        if (material == Material.COAL || material == Material.CHARCOAL) {
            return 1_600;
        }
        if (material == Material.COAL_BLOCK) {
            return 16_000;
        }
        if (Tag.PLANKS.isTagged(material) || Tag.LOGS.isTagged(material)) {
            return 300;
        }
        if (Tag.SAPLINGS.isTagged(material)) {
            return 100;
        }
        if (Tag.WOOL.isTagged(material)) {
            return 100;
        }
        if (material == Material.STICK || material == Material.BAMBOO) {
            return 100;
        }
        if (material == Material.CRAFTING_TABLE || material == Material.CHEST) {
            return 300;
        }
        if (material == Material.PAPER || material == Material.BOOK) {
            return 100;
        }
        return 200;
    }
}
