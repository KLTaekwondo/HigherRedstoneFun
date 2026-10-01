package com.koole.higherRedStoneFun.machines.logic;

import com.koole.higherRedStoneFun.core.Text;
import com.koole.higherRedStoneFun.genetics.CropMapping;
import com.koole.higherRedStoneFun.genetics.GeneticsManager;
import com.koole.higherRedStoneFun.genetics.Genome;
import com.koole.higherRedStoneFun.items.ItemRegistry;
import com.koole.higherRedStoneFun.machines.MachineInstance;
import com.koole.higherRedStoneFun.machines.MachineLogic;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 基因培育舱：把改良种子的基因「变现」为实际产物。
 *
 * <p>这是基因模块的经济出口，也是玩家投入大量红石流能与时间去选育的回报。
 * 关键设计：<b>产出与基因等级直接挂钩</b>——</p>
 *
 * <ul>
 *   <li>生长等级高 -> 每次培育耗时更短</li>
 *   <li>产量等级高 -> 每次产出的作物更多</li>
 *   <li>活力等级高 -> 有概率不消耗种子（相当于自留种）</li>
 * </ul>
 *
 * <p>因此一条「完美基因」的生产线相比野生种子可以有数倍的效率差距，
 * 而达成完美基因需要多代拼接，形成完整的目标链。</p>
 */
public final class GrowthChamberLogic implements MachineLogic {

    public static final int SEED_SLOT = 11;
    public static final int OUTPUT_SLOT = 15;

    /** 基础培育耗时（tick），会被生长基因缩短。 */
    private static final int BASE_DURATION = 400;
    /** 每次培育消耗的 HRE。 */
    private static final long ENERGY_PER_RUN = 3_600L;

    private final GeneticsManager genetics;

    public GrowthChamberLogic(GeneticsManager genetics) {
        this.genetics = genetics;
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
        ItemStack seed = machine.getSlot(SEED_SLOT);
        Genome genome = genetics.genomeOf(seed);
        String seedId = ItemRegistry.get().idOf(seed);

        if (seed == null || seed.getType().isAir() || seedId == null) {
            machine.requestPower(false);
            if (machine.activeRecipe() != null) {
                machine.clearRecipe();
            }
            return false;
        }

        // 优先按改良种子 ID 查表，其次按样本来源键查表
        CropMapping.Crop crop = CropMapping.bySeed(seedId);
        if (crop == null) {
            String source = genetics.sampleSourceOf(seed);
            if (source != null) {
                crop = CropMapping.bySample(source);
            }
        }
        if (crop == null) {
            // 不是可培育的种子（例如空白模板）
            machine.requestPower(false);
            if (machine.activeRecipe() != null) {
                machine.clearRecipe();
            }
            return false;
        }

        if (!canOutput(machine, crop)) {
            machine.requestPower(false);
            return false;
        }

        machine.requestPower(true);

        // 生长基因缩短耗时
        int duration = genome == null ? BASE_DURATION
                : Math.max(40, (int) Math.round(BASE_DURATION / genome.growthMultiplier()));

        if (machine.activeRecipe() == null) {
            machine.setActiveRecipe("cultivating:" + crop.seedItemId(), duration);
        }

        long perTick = Math.max(1L, ENERGY_PER_RUN / Math.max(1, duration));
        if (machine.energyStored() < perTick) {
            return false;
        }
        machine.consumeEnergy(perTick);
        machine.advance();

        if (machine.progress() >= machine.totalTicks()) {
            harvest(machine, crop, genome);
            return true;
        }
        return true;
    }

    private boolean canOutput(MachineInstance machine, CropMapping.Crop crop) {
        ItemStack out = machine.getSlot(OUTPUT_SLOT);
        if (out == null || out.getType().isAir()) {
            return true;
        }
        // 已有产物时，检查还能不能继续堆叠
        return out.getType() == crop.produce() && out.getAmount() < out.getMaxStackSize();
    }

    private void harvest(MachineInstance machine, CropMapping.Crop crop, Genome genome) {
        machine.clearRecipe();

        double yieldMultiplier = genome == null ? 1.0D : genome.yieldMultiplier();
        int amount = Math.max(1, (int) Math.round(crop.baseYield() * yieldMultiplier));

        ItemStack produce = new ItemStack(crop.produce(), amount);

        ItemStack existing = machine.getSlot(OUTPUT_SLOT);
        if (existing == null || existing.getType().isAir()) {
            machine.setSlot(OUTPUT_SLOT, produce);
        } else if (ItemRegistry.get().sameItem(existing, produce)) {
            int space = existing.getMaxStackSize() - existing.getAmount();
            int moved = Math.min(space, produce.getAmount());
            ItemStack merged = existing.clone();
            merged.setAmount(existing.getAmount() + moved);
            machine.setSlot(OUTPUT_SLOT, merged);
            if (moved < produce.getAmount()) {
                ItemStack leftover = produce.clone();
                leftover.setAmount(produce.getAmount() - moved);
                machine.location().getWorld()
                        .dropItemNaturally(machine.location().clone().add(0.5, 1.0, 0.5), leftover);
            }
        }

        // 活力基因：有概率不消耗种子（自留种）
        double keepChance = genome == null ? 0.0D : genome.regrowChance();
        if (ThreadLocalRandom.current().nextDouble() >= keepChance) {
            shrink(machine, SEED_SLOT);
        }

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
        ItemStack seed = machine.getSlot(SEED_SLOT);
        Genome genome = genetics.genomeOf(seed);
        String geneInfo = genome == null
                ? "<dark_gray>无基因数据"
                : "<green>" + genome.shortSummary();

        if (machine.activeRecipe() == null) {
            return "<gray>等待种子 <dark_gray>| " + geneInfo;
        }
        double ratio = (double) machine.progress() / machine.totalTicks();
        return "<green>培育中 " + Text.bar(ratio, 10, Text.colorFor(ratio), "dark_gray")
                + " <dark_gray>| " + geneInfo;
    }
}
