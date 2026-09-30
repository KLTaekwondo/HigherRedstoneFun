package com.koole.higherRedStoneFun.machines.logic;

import com.koole.higherRedStoneFun.core.Text;
import com.koole.higherRedStoneFun.genetics.GeneticsManager;
import com.koole.higherRedStoneFun.genetics.Genome;
import com.koole.higherRedStoneFun.machines.MachineInstance;
import com.koole.higherRedStoneFun.machines.MachineLogic;
import org.bukkit.inventory.ItemStack;

/**
 * 基因测序仪逻辑。
 *
 * <p>工作流：把「生物样本」和「基因引物」放进机器，消耗电力后
 * 解析出一个基因组。样本本身携带的基因组（如果有）会作为解析结果的
 * 起点，这就是「选育」的第一步——玩家需要先去野外找到高等级的植株。</p>
 */
public final class SequencerLogic implements MachineLogic {

    public static final int SAMPLE_SLOT = 11;
    public static final int PRIMER_SLOT = 12;
    public static final int OUTPUT_SLOT = 15;

    /** 一次测序的耗电量。 */
    private static final long ENERGY_PER_RUN = 2_400L;
    /** 测序耗时（tick）。 */
    private static final int DURATION = 200;

    private final GeneticsManager genetics;
    private final int tier;

    public SequencerLogic(GeneticsManager genetics, int tier) {
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
        ItemStack sample = machine.getSlot(SAMPLE_SLOT);
        ItemStack primer = machine.getSlot(PRIMER_SLOT);

        if (sample == null || sample.getType().isAir()
                || primer == null || primer.getType().isAir()
                || !com.koole.higherRedStoneFun.items.ItemRegistry.get().is(primer, "hrf_gene_primer")) {
            machine.requestPower(false);
            if (machine.activeRecipe() != null) {
                machine.clearRecipe();
            }
            return false;
        }

        if (!canOutput(machine)) {
            machine.requestPower(false);
            return false;
        }

        machine.requestPower(true);

        // 进度推进需要电
        long perTick = ENERGY_PER_RUN / DURATION;
        if (machine.energyStored() < perTick) {
            return false;
        }

        if (machine.activeRecipe() == null) {
            machine.setActiveRecipe("sequencing", DURATION);
        }

        machine.consumeEnergy(perTick);
        machine.advance();

        if (machine.progress() >= machine.totalTicks()) {
            complete(machine, sample);
            return true;
        }
        return true;
    }

    private boolean canOutput(MachineInstance machine) {
        ItemStack out = machine.getSlot(OUTPUT_SLOT);
        return out == null || out.getType().isAir();
    }

    private void complete(MachineInstance machine, ItemStack sample) {
        // 消耗样本与引物
        shrink(machine, SAMPLE_SLOT);
        shrink(machine, PRIMER_SLOT);
        machine.clearRecipe();

        // 解析基因组：如果样本本身带基因，则以它为基础强化
        GeneticsManager.SampleKind kind = genetics.sampleKindOf(sample);
        String source = genetics.sampleSourceOf(sample);
        Genome base = genetics.genomeOf(sample);

        Genome result;
        if (base != null) {
            // 样本自带基因：测序等于「读取并小幅提升」
            result = genetics.splice(base, genetics.sequence(
                    kind == null ? GeneticsManager.SampleKind.SEED : kind,
                    source == null ? "unknown" : source, tier), 0.15D);
        } else {
            result = genetics.sequence(
                    kind == null ? GeneticsManager.SampleKind.SEED : kind,
                    source == null ? "unknown" : source, tier);
        }

        boolean animal = kind == GeneticsManager.SampleKind.ANIMAL;
        ItemStack genomeItem = com.koole.higherRedStoneFun.items.ItemRegistry.get()
                .create(animal ? "hrf_genome_animal" : "hrf_genome_plant", 1);
        genetics.applyGenome(genomeItem, result);
        machine.setSlot(OUTPUT_SLOT, genomeItem);
        machine.incrementCompleted();
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
            return "<gray>插入生物样本与基因引物开始测序";
        }
        double ratio = (double) machine.progress() / machine.totalTicks();
        return "<green>测序中 " + Text.bar(ratio, 10, Text.colorFor(ratio), "dark_gray");
    }

    public int tier() {
        return tier;
    }
}
