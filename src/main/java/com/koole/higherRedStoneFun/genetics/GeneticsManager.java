package com.koole.higherRedStoneFun.genetics;

import com.koole.higherRedStoneFun.core.Keys;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 基因数据管理器：负责基因组在「物品 / 作物方块 / 动物实体」三处的读写，
 * 以及测序、拼接、注入三条核心流程。
 *
 * <p>存储位置的选择：</p>
 * <ul>
 *   <li><b>物品</b>（种子、基因组、生物样本）→ 物品的 PersistentDataContainer。</li>
 *   <li><b>作物方块</b> → 所在区块的 PersistentDataContainer。
 *       该 API 版本已移除方块级 PDC，而区块 PDC 免费、无上限、
 *       且随区块一起存盘/卸载，正好适合「一株作物一条基因」的场景。</li>
 *   <li><b>动物实体</b> → 实体的 PDC，随实体保存。</li>
 * </ul>
 */
public final class GeneticsManager {

    /** 样本类型。 */
    public enum SampleKind {
        SEED, ANIMAL, MINERAL
    }

    private static final String CHUNK_PREFIX = "hrf_crop_";

    private final Keys keys;

    public GeneticsManager(Keys keys) {
        this.keys = keys;
    }

    // ------------------------------------------------------------------
    // 物品上的基因组
    // ------------------------------------------------------------------

    @Nullable
    public Genome genomeOf(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        String raw = meta.getPersistentDataContainer().get(keys.genome(), PersistentDataType.STRING);
        return raw == null ? null : Genome.parse(raw);
    }

    public void applyGenome(ItemStack stack, Genome genome) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.genome(), PersistentDataType.STRING, genome.serialize());

        // 把基因信息写进 lore，让玩家一眼看出好坏
        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        for (String line : genome.describe()) {
            lore.add(com.koole.higherRedStoneFun.core.Text.mm(line));
        }
        meta.lore(lore);

        if (genome.isPerfect()) {
            meta.setEnchantmentGlintOverride(true);
        }
        stack.setItemMeta(meta);
    }

    /** 读取并覆盖物品原有的基因 lore（用于刷新显示）。 */
    public void refreshGenomeLore(ItemStack stack) {
        Genome genome = genomeOf(stack);
        if (genome != null) {
            applyGenome(stack, genome);
        }
    }

    // ------------------------------------------------------------------
    // 生物样本
    // ------------------------------------------------------------------

    public void markSample(ItemStack stack, SampleKind kind, String source) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.sampleKind(), PersistentDataType.STRING, kind.name());
        pdc.set(keys.sampleSource(), PersistentDataType.STRING, source);
        stack.setItemMeta(meta);
    }

    @Nullable
    public SampleKind sampleKindOf(@Nullable ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer()
                .get(keys.sampleKind(), PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return SampleKind.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Nullable
    public String sampleSourceOf(@Nullable ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(keys.sampleSource(), PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------
    // 作物基因组（区块 PDC）
    // ------------------------------------------------------------------

    private static String cropKey(Location loc) {
        return CHUNK_PREFIX + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    public void setCropGenome(Location loc, Genome genome) {
        loc.getChunk().getPersistentDataContainer().set(
                cropKeyOf(loc), PersistentDataType.STRING, genome.serialize());
    }

    @Nullable
    public Genome getCropGenome(Location loc) {
        String raw = loc.getChunk().getPersistentDataContainer()
                .get(cropKeyOf(loc), PersistentDataType.STRING);
        return raw == null ? null : Genome.parse(raw);
    }

    public void clearCropGenome(Location loc) {
        loc.getChunk().getPersistentDataContainer().remove(cropKeyOf(loc));
    }

    private static String cropKeyStatic(Location loc) {
        return CHUNK_PREFIX + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    /** 物品上的基因组键与区块里作物基因组键共用同一个命名空间，便于统一管理。 */
    private org.bukkit.NamespacedKey cropKeyOf(Location loc) {
        return new org.bukkit.NamespacedKey(keys.genome().getNamespace(), cropKeyStatic(loc));
    }

    // ------------------------------------------------------------------
    // 动物基因组（实体 PDC）
    // ------------------------------------------------------------------

    public void setEntityGenome(Entity entity, Genome genome) {
        entity.getPersistentDataContainer().set(keys.entityGenome(), PersistentDataType.STRING, genome.serialize());
    }

    @Nullable
    public Genome getEntityGenome(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(keys.entityGenome(), PersistentDataType.STRING);
        return raw == null ? null : Genome.parse(raw);
    }

    public boolean hasEntityGenome(Entity entity) {
        return entity.getPersistentDataContainer().has(keys.entityGenome(), PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------
    // 遗传操作
    // ------------------------------------------------------------------

    /**
     * 测序：把一份生物样本解析成一个基因组。
     *
     * <p>野生样本解析出的基因组期望强度较低（每个基因位 0~2 级），
     * 想要更高等级必须靠拼接把不同样本的优点组合起来，
     * 这就是基因玩法的核心循环。</p>
     */
    public Genome sequence(SampleKind kind, String source, int machineTier) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        // 机器等级越高，解析出的基因起点越好
        int bonus = Math.max(0, machineTier - 1);
        int[] levels = new int[Gene.values().length];

        for (int i = 0; i < levels.length; i++) {
            // 基础 0~2，机器加成后 0~(2+bonus)
            int roll = random.nextInt(3 + bonus + 1);
            levels[i] = Math.min(Genome.MAX_LEVEL, roll);
        }
        return Genome.of(levels[0], levels[1], levels[2], levels[3]);
    }

    /**
     * 拼接：把两个基因组组合成一个新基因组。
     *
     * <p>规则：每个基因位独立地取双方的较大值，并有概率再 +1（突变）。
     * 因此玩家需要不断寻找「某个基因位特别高」的样本进行叠加，
     * 最终得到全满级的完美基因组。</p>
     */
    public Genome splice(Genome a, Genome b, double mutationChance) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int[] levels = new int[Gene.values().length];
        for (Gene gene : Gene.values()) {
            int best = Math.max(a.level(gene), b.level(gene));
            if (best < Genome.MAX_LEVEL && random.nextDouble() < mutationChance) {
                best++;
            }
            levels[gene.ordinal()] = best;
        }
        return Genome.of(levels[0], levels[1], levels[2], levels[3]);
    }

    /**
     * 遗传：繁殖时把父母的基因组混合，产生子代基因组。
     *
     * <p>每个基因位随机来自父方或母方，另有小概率突变 +1/-1，
     * 使得「优生优育」需要多代选育，而不是一步到位。</p>
     */
    public Genome inherit(Genome parentA, Genome parentB) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int[] levels = new int[Gene.values().length];
        for (Gene gene : Gene.values()) {
            int from = random.nextBoolean() ? parentA.level(gene) : parentB.level(gene);
            double roll = random.nextDouble();
            if (roll < 0.10D && from < Genome.MAX_LEVEL) {
                from++;
            } else if (roll > 0.95D && from > 0) {
                from--;
            }
            levels[gene.ordinal()] = from;
        }
        return Genome.of(levels[0], levels[1], levels[2], levels[3]);
    }

    /**
     * 生成一个随机基因组（用于让世界里的野生作物/动物自然携带基因）。
     *
     * @param quality 0.0~1.0，世界难度或维度可以据此调整
     */
    public Genome randomGenome(double quality) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double q = Math.max(0.0D, Math.min(1.0D, quality));
        int[] levels = new int[Gene.values().length];
        for (int i = 0; i < levels.length; i++) {
            if (random.nextDouble() < q) {
                levels[i] = random.nextInt(Genome.MAX_LEVEL + 1);
            }
        }
        return Genome.of(levels[0], levels[1], levels[2], levels[3]);
    }

    /** 随机返回一个基因位，用于「基因片段」物品。 */
    public Gene randomGene() {
        Gene[] genes = Gene.values();
        return genes[ThreadLocalRandom.current().nextInt(genes.length)];
    }
}
