package com.koole.higherRedStoneFun.core;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * 插件所有 {@link NamespacedKey} 的集中管理点。
 *
 * <p>这些 key 用于把自定义物品 / 机器 / 基因数据持久化到
 * {@code PersistentDataContainer} 中，从而彻底避免依赖 NBT-API 之类的第三方库。</p>
 */
public final class Keys {

    private static Keys instance;

    private final NamespacedKey itemId;
    private final NamespacedKey blockId;
    private final NamespacedKey machineOwner;
    private final NamespacedKey genome;
    private final NamespacedKey genomePreview;
    private final NamespacedKey sampleKind;
    private final NamespacedKey sampleSource;
    private final NamespacedKey energyStored;
    private final NamespacedKey energyCapacity;
    private final NamespacedKey machineTier;
    private final NamespacedKey protectedBlock;
    private final NamespacedKey geneSeed;
    private final NamespacedKey geneEmbryo;
    private final NamespacedKey entityGenome;
    private final NamespacedKey researchId;
    private final NamespacedKey toolMode;

    private Keys(Plugin plugin) {
        this.itemId = new NamespacedKey(plugin, "item_id");
        this.blockId = new NamespacedKey(plugin, "block_id");
        this.machineOwner = new NamespacedKey(plugin, "machine_owner");
        this.genome = new NamespacedKey(plugin, "genome");
        this.genomePreview = new NamespacedKey(plugin, "genome_preview");
        this.sampleKind = new NamespacedKey(plugin, "sample_kind");
        this.sampleSource = new NamespacedKey(plugin, "sample_source");
        this.energyStored = new NamespacedKey(plugin, "energy_stored");
        this.energyCapacity = new NamespacedKey(plugin, "energy_capacity");
        this.machineTier = new NamespacedKey(plugin, "machine_tier");
        this.protectedBlock = new NamespacedKey(plugin, "protected_block");
        this.geneSeed = new NamespacedKey(plugin, "gene_seed");
        this.geneEmbryo = new NamespacedKey(plugin, "gene_embryo");
        this.entityGenome = new NamespacedKey(plugin, "entity_genome");
        this.researchId = new NamespacedKey(plugin, "research_id");
        this.toolMode = new NamespacedKey(plugin, "tool_mode");
    }

    public static void init(Plugin plugin) {
        if (instance == null) {
            instance = new Keys(plugin);
        }
    }

    public static Keys get() {
        if (instance == null) {
            throw new IllegalStateException("Keys 尚未初始化，请先调用 Keys.init(plugin)");
        }
        return instance;
    }

    public NamespacedKey itemId() {
        return itemId;
    }

    public NamespacedKey blockId() {
        return blockId;
    }

    public NamespacedKey machineOwner() {
        return machineOwner;
    }

    public NamespacedKey genome() {
        return genome;
    }

    public NamespacedKey genomePreview() {
        return genomePreview;
    }

    public NamespacedKey sampleKind() {
        return sampleKind;
    }

    public NamespacedKey sampleSource() {
        return sampleSource;
    }

    public NamespacedKey energyStored() {
        return energyStored;
    }

    public NamespacedKey energyCapacity() {
        return energyCapacity;
    }

    public NamespacedKey machineTier() {
        return machineTier;
    }

    public NamespacedKey protectedBlock() {
        return protectedBlock;
    }

    public NamespacedKey geneSeed() {
        return geneSeed;
    }

    public NamespacedKey geneEmbryo() {
        return geneEmbryo;
    }

    public NamespacedKey entityGenome() {
        return entityGenome;
    }

    public NamespacedKey researchId() {
        return researchId;
    }

    public NamespacedKey toolMode() {
        return toolMode;
    }
}
