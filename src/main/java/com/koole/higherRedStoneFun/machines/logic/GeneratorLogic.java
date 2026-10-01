package com.koole.higherRedStoneFun.machines.logic;

import com.koole.higherRedStoneFun.items.ItemRegistry;
import com.koole.higherRedStoneFun.machines.MachineInstance;
import com.koole.higherRedStoneFun.machines.MachineLogic;
import org.bukkit.inventory.ItemStack;

/**
 * 通用燃料发电机逻辑。
 *
 * <p>相比粘液科技的发电机，这里做了两点优化：</p>
 * <ol>
 *   <li><b>燃料槽与产出解耦</b>：发电机只负责产出红石流能，不直接吐物品；
 *       副产物（如空桶）会留在机器里等玩家取走，避免刷物品。</li>
 *   <li><b>空转停机</b>：没有燃料时不参与能源网络调度，
 *       能源网络会跳过它，从而减少无效计算。</li>
 * </ol>
 */
public class GeneratorLogic implements MachineLogic {

    /** 燃料槽。 */
    public static final int FUEL_SLOT = 0;
    /** 副产物槽（例如岩浆桶用完后留下的空桶）。 */
    public static final int BYPRODUCT_SLOT = 1;

    private final long outputPerTick;
    private final boolean consumeOnIdle;

    public GeneratorLogic(long outputPerTick) {
        this(outputPerTick, true);
    }

    /**
     * @param outputPerTick 每 tick 产能（HRE）
     * @param consumeOnIdle 是否在没有负载时也消耗燃料（默认 true，行为可预期）
     */
    public GeneratorLogic(long outputPerTick, boolean consumeOnIdle) {
        this.outputPerTick = Math.max(1L, outputPerTick);
        this.consumeOnIdle = consumeOnIdle;
    }

    @Override
    public boolean energyAware() {
        return true;
    }

    @Override
    public long generate(MachineInstance machine) {
        // 缓冲已满说明电网没人用能：按配置决定是否继续烧燃料
        if (machine.energyStored() >= machine.definition().bufferCapacity() && !consumeOnIdle) {
            return 0L;
        }

        if (!machine.hasFuel() && !refuel(machine)) {
            return 0L;
        }
        machine.consumeFuelTick();

        machine.addGenerated(outputPerTick);
        return outputPerTick;
    }

    /** 从燃料槽取一个红石补充燃料 tick。 */
    protected boolean refuel(MachineInstance machine) {
        ItemStack fuel = machine.getSlot(FUEL_SLOT);
        if (fuel == null || fuel.getType().isAir()) {
            return false;
        }
        int ticks = FuelValues.ticksFor(fuel.getType());
        if (ticks <= 0) {
            return false;
        }

        ItemStack copy = fuel.clone();
        copy.setAmount(copy.getAmount() - 1);
        machine.setSlot(FUEL_SLOT, copy.getAmount() <= 0 ? null : copy);

        // 岩浆桶等容器返还空桶
        ItemStack remainder = containerRemainder(fuel);
        if (remainder != null) {
            depositByproduct(machine, remainder);
        }

        machine.addFuelTicks(ticks);
        return true;
    }

    /** 返还容器物品。 */
    protected ItemStack containerRemainder(ItemStack fuel) {
        return switch (fuel.getType()) {
            case LAVA_BUCKET -> new ItemStack(org.bukkit.Material.BUCKET);
            case WATER_BUCKET -> new ItemStack(org.bukkit.Material.BUCKET);
            case MILK_BUCKET -> new ItemStack(org.bukkit.Material.BUCKET);
            case POWDER_SNOW_BUCKET -> new ItemStack(org.bukkit.Material.BUCKET);
            default -> null;
        };
    }

    /** 把副产物放进副产物槽，放不下就掉在机器旁。 */
    protected void depositByproduct(MachineInstance machine, ItemStack stack) {
        ItemStack existing = machine.getSlot(BYPRODUCT_SLOT);
        if (existing == null || existing.getType().isAir()) {
            machine.setSlot(BYPRODUCT_SLOT, stack);
            return;
        }
        if (ItemRegistry.get().sameItem(existing, stack)
                && existing.getAmount() + stack.getAmount() <= existing.getMaxStackSize()) {
            ItemStack merged = existing.clone();
            merged.setAmount(existing.getAmount() + stack.getAmount());
            machine.setSlot(BYPRODUCT_SLOT, merged);
            return;
        }
        machine.location().getWorld()
                .dropItemNaturally(machine.location().clone().add(0.5, 1.0, 0.5), stack);
    }

    public long outputPerTick() {
        return outputPerTick;
    }

    @Override
    public String statusLine(MachineInstance machine) {
        if (!machine.hasFuel()) {
            return "<red>无燃料";
        }
        return "<green>运行中 <dark_gray>| <gray>燃料余量 <white>" + machine.fuelTicks() + " <gray>tick";
    }
}
