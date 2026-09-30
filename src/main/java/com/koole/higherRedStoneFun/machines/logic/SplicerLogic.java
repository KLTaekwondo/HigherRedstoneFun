package com.koole.higherRedStoneFun.machines.logic;

import com.koole.higherRedStoneFun.core.Text;
import com.koole.higherRedStoneFun.genetics.GeneticsManager;
import com.koole.higherRedStoneFun.genetics.Genome;
import com.koole.higherRedStoneFun.items.ItemRegistry;
import com.koole.higherRedStoneFun.machines.MachineInstance;
import com.koole.higherRedStoneFun.machines.MachineLogic;
import org.bukkit.inventory.ItemStack;

/**
 * 基因拼接机逻辑。
 *
 * <p>这是基因玩法的核心机器：把两个基因组拼在一起，每个基因位独立取较优值，
 * 并有概率发生突变 +1。玩家因此可以「把 A 的高生长和 B 的高产量合到一起」，
 * 逐步逼近全满级的完美基因组。</p>
 *
 * <p>机器等级越高，突变概率越高，使得升级机器本身有明确的意义。</p>
 */
public final class SplicerLogic implements MachineLogic {

    public static final int GENOME_A_SLOT = 10;
    public static final int GENOME_B_SLOT = 11;
    public static final int ENZYME_SLOT = 12;
    public static final int OUTPUT_SLOT = 24;

    private static final long ENERGY_PER_RUN = 6_000L;
    private static final int DURATION = 300;

    private final GeneticsManager genetics;
    private final double mutationChance;

    public SplicerLogic(GeneticsManager genetics, double mutationChance) {
        this.genetics = genetics;
        this.mutationChance = Math.max(0.0D, Math.min(0.9D, mutationChance));
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
        ItemStack a = machine.getSlot(GENOME_A_SLOT);
        ItemStack b = machine.getSlot(GENOME_B_SLOT);
        ItemStack enzyme = machine.getSlot(ENZYME_SLOT);

        Genome genomeA = genetics.genomeOf(a);
        Genome genomeB = genetics.genomeOf(b);

        boolean ready = genomeA != null && genomeB != null
                && enzyme != null && ItemRegistry.get().is(enzyme, "hrf_enzyme");

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
            machine.setActiveRecipe("splicing", DURATION);
        }

        machine.consumeEnergy(perTick);
        machine.advance();

        if (machine.progress() >= machine.totalTicks()) {
            complete(machine, genomeA, genomeB, isAnimal(a, b));
            return true;
        }
        return true;
    }

    private boolean canOutput(MachineInstance machine) {
        ItemStack out = machine.getSlot(OUTPUT_SLOT);
        return out == null || out.getType().isAir();
    }

    /** 判断这组基因组属于动物还是植物（在消耗之前调用）。 */
    private static boolean isAnimal(ItemStack a, ItemStack b) {
        ItemRegistry registry = ItemRegistry.get();
        // 只要有一方是动物基因组，结果就按动物处理（动物基因组更稀有，不应被降级）
        return registry.is(a, "hrf_genome_animal") || registry.is(b, "hrf_genome_animal");
    }

    private void complete(MachineInstance machine, Genome a, Genome b, boolean animal) {
        shrink(machine, GENOME_A_SLOT);
        shrink(machine, GENOME_B_SLOT);
        shrink(machine, ENZYME_SLOT);
        machine.clearRecipe();

        Genome result = genetics.splice(a, b, mutationChance);

        ItemStack out = ItemRegistry.get().create(animal ? "hrf_genome_animal" : "hrf_genome_plant", 1);
        genetics.applyGenome(out, result);
        machine.setSlot(OUTPUT_SLOT, out);
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
            return "<gray>放入两个基因组与拼接酶开始拼接";
        }
        double ratio = (double) machine.progress() / machine.totalTicks();
        return "<light_purple>拼接中 " + Text.bar(ratio, 10, Text.colorFor(ratio), "dark_gray")
                + " <dark_gray>突变率 " + (int) (mutationChance * 100) + "%";
    }
}
