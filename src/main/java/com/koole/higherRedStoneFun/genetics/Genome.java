package com.koole.higherRedStoneFun.genetics;

import com.koole.higherRedStoneFun.core.Text;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基因组：一条 4 位基因序列。
 *
 * <p>设计目标（对应粘液科技在「育种」方向上的空白）：让玩家能像真实的
 * 育种一样，把不同植株/动物的优良性状**定向组合**，而不是单纯靠堆数量
 * 刷随机。每个基因位独立遗传，因此「长得快」和「产量高」可以分别优化，
 * 也可以组合到同一株作物上——这就是玩家追求的最优解。</p>
 *
 * <p>基因位:</p>
 * <ol>
 *   <li>{@link Gene#GROWTH} 生长速度 —— 直接影响作物成熟所需随机刻</li>
 *   <li>{@link Gene#YIELD} 产量 —— 收获时额外掉落</li>
 *   <li>{@link Gene#RESILIENCE} 抗逆性 —— 减少踩踏/缺水等损失，免疫踩踏</li>
 *   <li>{@link Gene#VIGOR} 活力 —— 自动补种、自动传播、动物繁殖加成</li>
 * </ol>
 */
public final class Genome {

    /** 单条基因的最高等级。等级越高效果越强，但收益递减。 */
    public static final int MAX_LEVEL = 5;

    private final int[] levels;

    private Genome(int[] levels) {
        this.levels = levels;
    }

    /** 全 0 的野生基因组。 */
    public static Genome wild() {
        return new Genome(new int[Gene.values().length]);
    }

    public static Genome of(int growth, int yield, int resilience, int vigor) {
        return new Genome(new int[]{
                clamp(growth), clamp(yield), clamp(resilience), clamp(vigor)
        });
    }

    /** 从持久化字符串解析，格式如 {@code "2-1-0-3"}。 */
    public static Genome parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return wild();
        }
        String[] parts = raw.split("-");
        int[] levels = new int[Gene.values().length];
        for (int i = 0; i < levels.length && i < parts.length; i++) {
            try {
                levels[i] = clamp(Integer.parseInt(parts[i].trim()));
            } catch (NumberFormatException ex) {
                levels[i] = 0;
            }
        }
        return new Genome(levels);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX_LEVEL, value));
    }

    /** 序列化为持久化字符串。 */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels.length; i++) {
            if (i > 0) {
                sb.append('-');
            }
            sb.append(levels[i]);
        }
        return sb.toString();
    }

    public int level(Gene gene) {
        return levels[gene.ordinal()];
    }

    public Genome with(Gene gene, int level) {
        int[] copy = levels.clone();
        copy[gene.ordinal()] = clamp(level);
        return new Genome(copy);
    }

    /** 总基因强度（0..20），用于排序与「最优解」提示。 */
    public int total() {
        int sum = 0;
        for (int level : levels) {
            sum += level;
        }
        return sum;
    }

    public boolean isWild() {
        return total() == 0;
    }

    public int[] levels() {
        return levels.clone();
    }

    /** 是否是「完美基因组」（全满级）。 */
    public boolean isPerfect() {
        for (int level : levels) {
            if (level < MAX_LEVEL) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 效果计算
    // ------------------------------------------------------------------

    /**
     * 生长速度倍率。
     *
     * <p>采用「每级 +20%，但收益递减」的曲线：等级越高，边际收益越小。
     * 满级约 2.5 倍速，配合基因机器自身的加速可以接近 10 倍。</p>
     */
    public double growthMultiplier() {
        int level = level(Gene.GROWTH);
        if (level <= 0) {
            return 1.0D;
        }
        // 1 + 0.2L + 0.04L^2：L=5 时约 3.0
        return 1.0D + 0.20D * level + 0.04D * level * level;
    }

    /** 产量加成倍率（收获数量倍率）。 */
    public double yieldMultiplier() {
        int level = level(Gene.YIELD);
        if (level <= 0) {
            return 1.0D;
        }
        return 1.0D + 0.25D * level + 0.05D * level * level;
    }

    /** 抗逆性：0..1，满级完全免疫踩踏。 */
    public double resilienceRatio() {
        return Math.min(1.0D, level(Gene.RESILIENCE) / (double) MAX_LEVEL);
    }

    public boolean immuneToTrample() {
        return level(Gene.RESILIENCE) >= MAX_LEVEL;
    }

    /** 活力：自动补种概率 0..1。 */
    public double regrowChance() {
        return Math.min(1.0D, level(Gene.VIGOR) / (double) MAX_LEVEL);
    }

    /** 活力带来的额外繁殖概率（动物）。 */
    public double breedBonus() {
        return 0.15D * level(Gene.VIGOR);
    }

    /** 基因的种植/繁殖加速 tick 缩减量。 */
    public int tickReduction() {
        return 2 * level(Gene.GROWTH);
    }

    // ------------------------------------------------------------------
    // 展示
    // ------------------------------------------------------------------

    /** 生成一行简洁的基因摘要，格式 {@code 生2 产1 抗0 活3}。 */
    public String shortSummary() {
        StringBuilder sb = new StringBuilder();
        for (Gene gene : Gene.values()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(gene.shortName()).append(level(gene));
        }
        return sb.toString();
    }

    /** 生成多行详细展示，用于物品 lore。 */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (Gene gene : Gene.values()) {
            int level = level(gene);
            boolean max = level >= MAX_LEVEL;
            String color = max ? "gold" : (level > 0 ? "green" : "dark_gray");
            lines.add("<gray>" + gene.displayName() + ": <" + color + ">"
                    + com.koole.higherRedStoneFun.core.Text.bar(
                    level / (double) MAX_LEVEL, MAX_LEVEL, color, "dark_gray")
                    + " <" + color + ">" + level + "/" + MAX_LEVEL
                    + " <dark_gray>(" + gene.description() + ")");
        }
        lines.add("<gray>总强度: <white>" + total() + "/" + (MAX_LEVEL * Gene.values().length));
        return lines;
    }

    public Component display() {
        return Text.mm("<white>" + shortSummary());
    }

    @Override
    public String toString() {
        return serialize();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Genome genome)) {
            return false;
        }
        return java.util.Arrays.equals(levels, genome.levels);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(levels);
    }
}
