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
     * 增强工作台的原料区（3x3，占列 2-4）。
     *
     * <p>声明顺序必须在 {@link #ENHANCED_CRAFTING} 之前，否则是非法前向引用。</p>
     */
    public static final int[] CRAFTING_INPUT_AREA = {10, 11, 12, 19, 20, 21, 28, 29, 30};

    /**
     * 增强工作台的成品区（3x3 九格，占列 6-8，中心 24 在前）。
     *
     * <p>顺序即优先级：合成出的产物先落 24，满了才依次往 14/15/16… 扩。
     * {@code outputSlots()[0]} 必须保持 24，因为 {@code outputSlot()}（主产物格）
     * 取的就是第一个元素。</p>
     */
    public static final int[] CRAFTING_OUTPUT_AREA = {24, 14, 15, 16, 23, 25, 32, 33, 34};

    /**
     * 增强工作台：54 格。左侧 3x3 原料区（列 2-4）、正中预览格（22，点击即合成）、
     * 右侧 3x3 成品区（列 6-8，存放产物）、最右列放红石燃料（8）与燃料余量条。
     *
     * <p>槽位布局：</p>
     * <pre>
     *   列     1    2    3    4    5    6    7    8    9
     *   行1   [框] [框] [框] [框] [框] [框] [框] [框] [燃]   <- 8  = 红石燃料槽
     *   行2   [框] [入] [入] [入] [框] [出] [出] [出] [能]
     *   行3   [框] [入] [入] [入] [预] [出] [出] [出] [能]   <- 22 = 预览格（点击合成）
     *   行4   [框] [入] [入] [入] [框] [出] [出] [出] [能]
     *   行5   [框] [框] [框] [状] [框] [框] [框] [框] [能]   <- 39 = 状态位
     *   行6   [框] [框] [框] [框] [框] [框] [框] [框] [能]
     * </pre>
     *
     * <p>原料区占列 2-4 而不是列 1-3：这样列 5 成为唯一中缝，预览格落在整个
     * 界面的正中，视觉是「左料 → 中预览 → 右成品」。同时这 9 个槽号与
     * 组装机 / 基因拼接机 / 分子重组机的输入区完全一致，多方块机器共用一套坐标。</p>
     *
     * <p><b>注意（同一个坑踩过两次）</b>：这套 {@code {10,11,12,19,20,21,28,29,30}}
     * 正是当年出事的那一组——那时机器只有 27 格，28/29/30 越界被
     * {@code MachineInstance.setSlot} 静默丢弃，「3x3 网格」实际只有 2x3 能用。
     * 现在 54 格，9 格输入全部有效。</p>
     *
     * <p>三者性质不同，别搞混：</p>
     * <ul>
     *   <li><b>预览格 22</b>：虚拟输出。里面的东西只是界面预览，
     *       <b>永远不写回机器状态</b>，因此拆机器也带不走（见
     *       {@code CraftingTableLogic} 的防复制说明）。点击它才真正扣材料、出产物。</li>
     *   <li><b>成品区 9 格</b>：真实存储。产物由预览格结算后放进这里，随机器存档，
     *       拆机器会掉出来。</li>
     *   <li>其余格子：装饰。</li>
     * </ul>
     */
    public static final RecipeType ENHANCED_CRAFTING = new RecipeType(
            "enhanced_crafting", "增强工作台", 54, CRAFTING_OUTPUT_AREA.clone(),
            CRAFTING_INPUT_AREA.clone(), Material.CRAFTING_TABLE, false, false);

    /** 增强工作台的预览格（点击它即合成一次，产物进入成品区）。 */
    public static final int CRAFTING_PREVIEW_SLOT = 22;

    /** 增强工作台的红石燃料槽（最右列顶端）。 */
    public static final int CRAFTING_FUEL_SLOT = 8;

    /** 增强工作台右侧的能量显示列（燃料槽下方，自上而下）。 */
    public static final int[] CRAFTING_ENERGY_COLUMN = {17, 26, 35, 44, 53};

    /** 增强工作台的状态位（第五行第四列）。 */
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

    /** 红石流能熔炼：1 输入 -> 1 输出，耗能。 */
    public static final RecipeType ELECTRIC_SMELTING = new RecipeType(
            "electric_smelting", "红石流能熔炼", 27, new int[]{15}, new int[]{11},
            Material.BLAST_FURNACE, true, false);

    /** 红石流能研磨：1 输入 -> 多输出，耗能。 */
    public static final RecipeType ELECTRIC_GRINDING = new RecipeType(
            "electric_grinding", "红石流能研磨", 27, new int[]{14, 15}, new int[]{11},
            Material.GRINDSTONE, true, false);

    /** 组装：多输入 -> 1 输出，耗能，属于高级合成。 */
    public static final RecipeType ASSEMBLING = new RecipeType(
            "assembling", "组装", 45, new int[]{24},
            new int[]{10, 11, 12, 19, 20, 21, 28, 29, 30}, Material.CRAFTER, true, false);

    /** 分子重组：多输入 -> 1 输出，耗能极高，属于终局合成。 */
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

    /**
     * 基因注入：种子/胚胎 + 基因组 (+ 稳定剂) -> 改良种子/胚胎。
     *
     * <p>槽 13 是 {@code InjectorLogic.STABILIZER_SLOT}（稳定剂，可选）。
     * 早期这里漏声明了它，导致稳定剂槽在界面上是装饰格、放不进东西，
     * 「稳定剂可选」这条文案实际上是假的。</p>
     */
    public static final RecipeType INJECTION = new RecipeType(
            "injection", "基因注入", 27, new int[]{15}, new int[]{11, 12, 13},
            Material.BREWING_STAND, true, true);

    /** 培育舱：改良种子 + 红石流能 -> 作物产物（体外培育）。 */
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

    /** 是否需要红石流能驱动（HRE 网络供能）。 */
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
