package com.koole.higherRedStoneFun.machines.logic;

import com.koole.higherRedStoneFun.core.Text;
import com.koole.higherRedStoneFun.genetics.CropMapping;
import com.koole.higherRedStoneFun.genetics.GeneticsManager;
import com.koole.higherRedStoneFun.genetics.Genome;
import com.koole.higherRedStoneFun.items.ItemRegistry;
import com.koole.higherRedStoneFun.machines.MachineInstance;
import com.koole.higherRedStoneFun.machines.MachineLogic;
import org.bukkit.inventory.ItemStack;

/**
 * 基因注入器逻辑。
 *
 * <p>把「基因组」写进「空白种子模板」或「空白胚胎」，产出可以直接种植 /
 * 孵化的改良种子与胚胎。这是基因数据从机器回到世界的关键一步。</p>
 *
 * <p>两条产线共用同一台机器：</p>
 * <ul>
 *   <li>空白种子模板 + 植物基因组 -> 对应作物的改良种子</li>
 *   <li>空白胚胎 + 动物基因组 -> 对应物种的胚胎（物种信息写在基因组里）</li>
 * </ul>
 */
public final class InjectorLogic implements MachineLogic {

    public static final int TARGET_SLOT = 11;
    public static final int GENOME_SLOT = 12;
    public static final int STABILIZER_SLOT = 13;
    public static final int OUTPUT_SLOT = 15;

    private static final long ENERGY_PER_RUN = 3_600L;
    private static final int DURATION = 240;

    private final GeneticsManager genetics;
    private final int tier;

    public InjectorLogic(GeneticsManager genetics, int tier) {
        this.genetics = genetics;
        this.tier = tier;
    }

    @Override
    public boolean energyAware() {
        return true;
    }

    @Override
    public long demand(MachineInstance machine) {
        if (!machine.isRequestingPower()) {
            return 0L;
        }
        return Math.max(0L, machine.definition().bufferCapacity() - machine.energyStored());
    }

    @Override
    public boolean tick(MachineInstance machine) {
        ItemStack target = machine.getSlot(TARGET_SLOT);
        ItemStack genomeItem = machine.getSlot(GENOME_SLOT);
        Genome genome = genetics.genomeOf(genomeItem);

        boolean ready = target != null && !target.getType().isAir() && genome != null;
        if (!ready || !canOutput(machine)) {
            machine.requestPower(false);
            if (machine.activeRecipe() != null) {
                machine.clearRecipe();
            }
            return false;
        }

        machine.requestPower(true);

        long perTick = ENERGY_PER_RUN / DURATION;
        if (machine.energyStored() < perTick) {
            return false;
        }
        if (machine.activeRecipe() == null) {
            machine.setActiveRecipe("injection", DURATION);
        }
        machine.consumeEnergy(perTick);
        machine.advance();

        if (machine.progress() >= machine.totalTicks()) {
            complete(machine, target, genomeItem, genome);
            return true;
        }
        return true;
    }

    private boolean canOutput(MachineInstance machine) {
        ItemStack out = machine.getSlot(OUTPUT_SLOT);
        return out == null || out.getType().isAir();
    }

    private void complete(MachineInstance machine, ItemStack target, ItemStack genomeItem, Genome genome) {
        ItemRegistry registry = ItemRegistry.get();
        String targetId = registry.idOf(target);

        ItemStack result = null;

        if ("hrf_seed_template".equals(targetId)) {
            // 植物路线：默认产出小麦种子；若基因组带有作物来源则产出对应作物
            String source = genetics.sampleSourceOf(genomeItem);
            CropMapping.Crop crop = source == null ? null : cropBySource(source);
            String seedId = crop == null ? "hrf_wheat_seeds" : crop.seedItemId();
            result = registry.create(seedId, 1);

        } else if ("hrf_empty_embryo".equals(targetId)) {
            // 动物路线：物种信息由动物基因组携带
            String source = genetics.sampleSourceOf(genomeItem);
            CropMapping.Animal animal = source == null ? null : CropMapping.byAnimal(source);
            if (animal == null) {
                // 物种未知：不消耗材料，提示玩家使用带物种信息的动物基因组
                machine.clearRecipe();
                machine.setSlot(OUTPUT_SLOT, null);
                return;
            }
            result = registry.create(animal.embryoItemId(), 1);
        }

        if (result == null) {
            machine.clearRecipe();
            return;
        }

        // 写入基因，并保留来源信息
        genetics.applyGenome(result, genome);
        if (genetics.sampleSourceOf(genomeItem) != null) {
            genetics.markSample(result, genetics.sampleKindOf(genomeItem) == null
                            ? GeneticsManager.SampleKind.SEED
                            : genetics.sampleKindOf(genomeItem),
                    genetics.sampleSourceOf(genomeItem));
        }

        shrink(machine, TARGET_SLOT);
        shrink(machine, GENOME_SLOT);
        // 稳定剂可选：有则消耗一个，提高等级下限（这里体现为一次小幅强化）
        if (registry.is(machine.getSlot(STABILIZER_SLOT), "hrf_stabilizer")) {
            shrink(machine, STABILIZER_SLOT);
            Genome boosted = genetics.splice(genome, genome, 0.25D);
            genetics.applyGenome(result, boosted);
        }

        machine.setSlot(OUTPUT_SLOT, result);
        machine.clearRecipe();
        machine.incrementCompleted();
    }

    /** 根据测序时记录的来源键反查作物。 */
    private static CropMapping.Crop cropBySource(String source) {
        // 来源键实际存的是样本物品 ID，例如 hrf_sample_wheat
        CropMapping.Crop crop = CropMapping.bySample(source);
        if (crop != null) {
            return crop;
        }
        // 兼容直接用作物名的情况
        return switch (source) {
            case "wheat" -> CropMapping.bySample("hrf_sample_wheat");
            case "carrot" -> CropMapping.bySample("hrf_sample_carrot");
            case "potato" -> CropMapping.bySample("hrf_sample_potato");
            case "beetroot" -> CropMapping.bySample("hrf_sample_beetroot");
            case "nether" -> CropMapping.bySample("hrf_sample_nether");
            case "berry" -> CropMapping.bySample("hrf_sample_berry");
            case "melon" -> CropMapping.bySample("hrf_sample_melon");
            case "pumpkin" -> CropMapping.bySample("hrf_sample_pumpkin");
            default -> null;
        };
    }

    private static void shrink(MachineInstance machine, int slot) {
        ItemStack stack = machine.getSlot(slot);
        if (stack == null) {
            return;
        }
        ItemStack copy = stack.clone();
        copy.setAmount(copy.getAmount() - 1);
        machine.setSlot(slot, copy.getAmount() <= 0 ? null : copy);
    }

    @Override
    public String statusLine(MachineInstance machine) {
        if (machine.activeRecipe() == null) {
            return "<gray>放入空白模板与基因组开始注入";
        }
        double ratio = (double) machine.progress() / machine.totalTicks();
        return "<green>注入中 " + Text.bar(ratio, 10, Text.colorFor(ratio), "dark_gray");
    }

    public int tier() {
        return tier;
    }
}
