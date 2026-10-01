package com.koole.higherRedStoneFun.recipes;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 配方类型（对应粘液科技的 RecipeType）。
 *
 * <p>与原版不同：这里把「机器界面布局」与「配方匹配规则」放在同一个对象里，
 * 机器只要声明自己的 RecipeType 就能复用整套界面与处理逻辑，
 * 不需要为每种机器单独写一个类。</p>
 *
 * <p>之所以用常量对象而不是 enum：这样后续可以从配置文件动态创建新的
 * 配方类型（v0.2 计划），而 enum 做不到。</p>
 */
public final class RecipeType {

    /**
     * 增强工作台：54 格，左侧 3x3 输入、右侧 3x3 结果区、最右列放红石燃料。
     *
     * <p>槽位布局：</p>
     * <pre>
     *   列     1    2    3    4    5    6    7    8    9
     *   行1   [框] [框] [框] [框] [框] [框] [框] [框] [燃]   <- 8 = 红石燃料槽
     *   行2   [入] [入] [入] [框] [框] [出] [出] [出] [能]
     *   行3   [入] [入] [入] [框] [框] [出] [出] [出] [能]
     *   行4   [入] [入] [入] [框] [框] [出] [出] [出] [能]
     *   行5   [框] [框] [框] [状] [框] [框] [框] [框] [能]
     *   行6   [框] [框] [框] [框] [框] [框] [框] [框] [能]
     * </pre>
     *
     * <p><b>注意</b>：早期版本只有 27 格，却把输入槽写成
     * {@code {10,11,12,19,20,21,28,29,30}}——28/29/30 越界，被
     * {@code MachineInstance.setSlot} 静默丢弃，导致「3x3 网格」实际只有
     * 2x3 能用。现在格子数够了，9 格输入全部有效。</p>
     */
    public static final RecipeType ENHANCED_CRAFTING = new RecipeType(
            "enhanced_crafting", "增强工作台", 54, new int[]{24},
            new int[]{9, 10, 11, 18, 19, 20, 27, 28, 29}, Material.CRAFTING_TABLE, false, false);

    /** 增强工作台的完整结果区（3x3，主产物落在中心 24）。 */
    public static final int[] CRAFTING_OUTPUT_AREA = {24, 14, 15, 16, 23, 25, 32, 33, 34};

    /** 增强工作台的输入区（3x3）。 */
    public static final int[] CRAFTING_INPUT_AREA = {9, 10, 11, 18, 19, 20, 27, 28, 29};

    /** 增强工作台的红石燃料槽（最右列顶端）。 */
    public static final int CRAFTING_FUEL_SLOT = 8;

    /** 增强工作台右侧的能量显示列（燃料槽下方，自上而下）。 */
    public static final int[] CRAFTING_ENERGY_COLUMN = {17, 26, 35, 44, 53};

    /** 增强工作台的状态位（第五行第三列）。 */
    public static final int CRAFTING_STATUS_SLOT = 39;

    /** 研磨：1 输入 -> 1~2 输出，燃料驱动。 */
    public static final RecipeType GRINDING = new RecipeType(
            "grinding", "研磨", 27, new int[]{15}, new int[]{11}, Material.GRINDSTONE, false, false);

    /** 压制：1 输入 -> 1 输出，燃料驱动。 */
    public static final RecipeType PRESSING = new RecipeType(
            "pressing", "压制", 27, new int[]{15}, new int[]{11}, Material.PISTON, false, false);

    /** 熔炼：1 输入 -> 1 输出，燃料驱动，速度快于原版熔炉。 */
    public static final RecipeType SMELTING = new RecipeType(
            "smelting", "熔炼", 27, new int[]{15}, new int[]{11}, Material.BLAST_FURNACE, false, false);

    /** 离心：1 输入 -> 多输出（含副产物概率），燃料驱动。 */
    public static final RecipeType CENTRIFUGE = new RecipeType(
            "centrifuge", "离心分离", 27, new int[]{13, 14, 15}, new int[]{11},
            Material.CAULDRON, false, false);

    /** 锯木：1 输入 -> 多输出，燃料驱动。 */
    public static final RecipeType SAWING = new RecipeType(
            "sawing", "锯切", 27, new int[]{14, 15}, new int[]{11},
            Material.STONECUTTER, false, false);

    /** 电力熔炼：1 输入 -> 1 输出，耗电。 */
    public static final RecipeType ELECTRIC_SMELTING = new RecipeType(
            "electric_smelting", "电力熔炼", 27, new int[]{15}, new int[]{11},
            Material.BLAST_FURNACE, true, false);

    /** 电力研磨：1 输入 -> 多输出，耗电。 */
    public static final RecipeType ELECTRIC_GRINDING = new RecipeType(
            "electric_grinding", "电力研磨", 27, new int[]{14, 15}, new int[]{11},
            Material.GRINDSTONE, true, false);

    /** 组装：多输入 -> 1 输出，耗电，属于高级合成。 */
    public static final RecipeType ASSEMBLING = new RecipeType(
            "assembling", "组装", 45, new int[]{24},
            new int[]{10, 11, 12, 19, 20, 21, 28, 29, 30}, Material.CRAFTER, true, false);

    /** 分子重组：多输入 -> 1 输出，耗电极高，属于终局合成。 */
    public static final RecipeType MOLECULAR = new RecipeType(
            "molecular", "分子重组", 45, new int[]{24},
            new int[]{10, 11, 12, 19, 20, 21, 28, 29, 30}, Material.BEACON, true, false);

    /** 基因测序：生物样本 + 引物 -> 基因组数据。 */
    public static final RecipeType SEQUENCING = new RecipeType(
            "sequencing", "基因测序", 27, new int[]{15}, new int[]{11, 12},
            Material.SCULK_SENSOR, true, true);

    /** 基因拼接：基因组 + 基因组 + 拼接酶 -> 新基因组。 */
    public static final RecipeType SPLICING = new RecipeType(
            "splicing", "基因拼接", 45, new int[]{24},
            new int[]{10, 11, 12, 19, 20, 21}, Material.SMITHING_TABLE, true, true);

    /** 基因注入：种子/胚胎 + 基因组 -> 改良种子/胚胎。 */
    public static final RecipeType INJECTION = new RecipeType(
            "injection", "基因注入", 27, new int[]{15}, new int[]{11, 12},
            Material.BREWING_STAND, true, true);

    /** 培育舱：改良种子 + 电力 -> 作物产物（体外培育）。 */
    public static final RecipeType CULTIVATION = new RecipeType(
            "cultivation", "培育舱", 27, new int[]{15}, new int[]{11},
            Material.BEEHIVE, true, true);

    /** 所有已定义的类型，供图鉴与自检使用。 */
    public static final List<RecipeType> ALL = List.of(
            ENHANCED_CRAFTING, GRINDING, PRESSING, SMELTING, CENTRIFUGE, SAWING,
            ELECTRIC_SMELTING, ELECTRIC_GRINDING, ASSEMBLING, MOLECULAR,
            SEQUENCING, SPLICING, INJECTION, CULTIVATION);

    private final String key;
    private final String displayName;
    private final int inventorySize;
    private final int[] outputSlots;
    private final int[] inputSlots;
    private final Material icon;
    private final boolean electric;
    private final boolean genetics;

    private RecipeType(String key, String displayName, int inventorySize, int[] outputSlots, int[] inputSlots,
                       Material icon, boolean electric, boolean genetics) {
        this.key = key;
        this.displayName = displayName;
        this.inventorySize = inventorySize;
        this.outputSlots = outputSlots;
        this.inputSlots = inputSlots;
        this.icon = icon;
        this.electric = electric;
        this.genetics = genetics;
    }

    /** 小写标识符，用于命令与配置。 */
    public String name() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public int inventorySize() {
        return inventorySize;
    }

    /** 全部输出槽（离心/锯切这类一次产出多种副产物的机器会用到）。 */
    public int[] outputSlots() {
        return outputSlots.clone();
    }

    /** 主输出槽（单产物机器使用）。 */
    public int outputSlot() {
        return outputSlots[0];
    }

    public int[] inputSlots() {
        return inputSlots.clone();
    }

    public Material icon() {
        return icon;
    }

    /** 是否需要电力驱动。 */
    public boolean electric() {
        return electric;
    }

    /** 是否属于基因模块（用于权限与界面差异）。 */
    public boolean genetics() {
        return genetics;
    }

    /** 判断槽位是否为输入槽。 */
    public boolean isInputSlot(int slot) {
        for (int s : inputSlots) {
            if (s == slot) {
                return true;
            }
        }
        return false;
    }

    /** 判断槽位是否为输出槽。 */
    public boolean isOutputSlot(int slot) {
        for (int s : outputSlots) {
            if (s == slot) {
                return true;
            }
        }
        return false;
    }

    /** 输入槽列表（可变副本，便于机器填充）。 */
    public List<Integer> inputSlotList() {
        List<Integer> list = new ArrayList<>(inputSlots.length);
        for (int s : inputSlots) {
            list.add(s);
        }
        return list;
    }

    @Override
    public String toString() {
        return displayName + Arrays.toString(inputSlots) + "->" + Arrays.toString(outputSlots);
    }
}
