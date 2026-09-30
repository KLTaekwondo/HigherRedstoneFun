package com.koole.higherRedStoneFun.machines.logic;

import com.koole.higherRedStoneFun.machines.MachineInstance;
import com.koole.higherRedStoneFun.machines.MachineLogic;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * 核反应堆：终局发电机。
 *
 * <p>玩法设计：它是全插件最强的电源，但不是「放下去就完事」——
 * 必须持续投入燃料棒与冷却单元。一旦冷却耗尽，堆芯开始升温，
 * 温度满值后熔毁（爆炸并摧毁自身）。这把「电力」从单纯的数量优势
 * 变成了需要持续管理的风险机制，是 v0.1 里最有张力的机械玩法。</p>
 */
public final class ReactorLogic implements MachineLogic {

    /** 燃料棒槽。 */
    public static final int FUEL_SLOT = 0;
    /** 冷却剂槽。 */
    public static final int COOLANT_SLOT = 1;

    private static final String KEY_TEMPERATURE = "core_temperature";
    private static final String KEY_COOLANT = "coolant_ticks";

    /** 一根燃料棒可运行 tick。 */
    private static final int FUEL_TICKS = 30_000;
    /** 一个冷却单元可维持 tick。 */
    private static final int COOLANT_TICKS = 6_000;
    /** 温度单位为千分之一：满值 1000 时熔毁。 */
    private static final int TEMPERATURE_MAX = 1000;
    /** 每 tick 升温（无冷却时）。约 10 秒熔毁。 */
    private static final int HEAT_PER_TICK = 5;
    /** 每 tick 降温（有冷却时）。 */
    private static final int COOL_PER_TICK = 12;

    private final long outputPerTick;

    public ReactorLogic(long outputPerTick) {
        this.outputPerTick = Math.max(1L, outputPerTick);
    }

    @Override
    public boolean energyAware() {
        return true;
    }

    @Override
    public long generate(MachineInstance machine) {
        boolean fueled = machine.hasFuel() || refuel(machine);
        boolean cooled = ensureCoolant(machine);

        if (!fueled) {
            // 没有燃料：堆芯自然冷却
            coolDown(machine, COOL_PER_TICK);
            return 0L;
        }

        if (!cooled) {
            // 有燃料但无冷却：升温
            int temperature = machine.counter(KEY_TEMPERATURE, 0) + HEAT_PER_TICK;
            machine.setCounter(KEY_TEMPERATURE, temperature);
            if (temperature >= TEMPERATURE_MAX) {
                meltdown(machine);
            }
            return 0L;
        }

        // 正常发电
        machine.consumeFuelTick();
        consumeCoolant(machine);
        coolDown(machine, COOL_PER_TICK);

        machine.addGenerated(outputPerTick);
        return outputPerTick;
    }

    private void coolDown(MachineInstance machine, int amount) {
        int temperature = machine.counter(KEY_TEMPERATURE, 0);
        if (temperature <= 0) {
            return;
        }
        int next = Math.max(0, temperature - amount);
        if (next == 0) {
            machine.removeCounter(KEY_TEMPERATURE);
        } else {
            machine.setCounter(KEY_TEMPERATURE, next);
        }
    }

    // ------------------------------------------------------------------
    // 燃料与冷却
    // ------------------------------------------------------------------

    private boolean refuel(MachineInstance machine) {
        ItemStack fuel = machine.getSlot(FUEL_SLOT);
        if (fuel == null || fuel.getType().isAir() || !isReactorFuel(fuel.getType())) {
            return false;
        }
        shrink(machine, FUEL_SLOT);
        machine.addFuelTicks(FUEL_TICKS);
        return true;
    }

    private static boolean isReactorFuel(Material material) {
        return material == Material.NETHERITE_SCRAP
                || material == Material.ANCIENT_DEBRIS
                || material == Material.BLAZE_ROD
                || material == Material.COAL_BLOCK
                || material == Material.NETHER_STAR;
    }

    private boolean ensureCoolant(MachineInstance machine) {
        if (machine.counter(KEY_COOLANT, 0) > 0) {
            return true;
        }
        ItemStack cell = machine.getSlot(COOLANT_SLOT);
        if (cell == null || cell.getType().isAir() || !isCoolant(cell.getType())) {
            return false;
        }
        shrink(machine, COOLANT_SLOT);
        machine.setCounter(KEY_COOLANT, COOLANT_TICKS);
        return true;
    }

    private void consumeCoolant(MachineInstance machine) {
        int remaining = machine.counter(KEY_COOLANT, 0);
        if (remaining <= 1) {
            machine.removeCounter(KEY_COOLANT);
        } else {
            machine.setCounter(KEY_COOLANT, remaining - 1);
        }
    }

    private static boolean isCoolant(Material material) {
        return material == Material.BLUE_ICE
                || material == Material.PACKED_ICE
                || material == Material.ICE
                || material == Material.SNOWBALL
                || material == Material.POWDER_SNOW_BUCKET;
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

    // ------------------------------------------------------------------
    // 熔毁
    // ------------------------------------------------------------------

    private void meltdown(MachineInstance machine) {
        org.bukkit.Location loc = machine.location();
        org.bukkit.World world = loc.getWorld();
        if (world == null) {
            return;
        }
        org.bukkit.Bukkit.getLogger().warning("[HRF] 核反应堆在 " + loc + " 因失去冷却而熔毁。");

        // 先把机器本身清掉，避免爆炸后仍留下一个「幽灵机器」
        machine.removeCounter(KEY_TEMPERATURE);
        machine.removeCounter(KEY_COOLANT);
        machine.setEnergyStored(0L);

        // 摧毁机器方块并清空内部物品（熔毁会烧毁一切）
        world.getBlockAt(loc).setType(Material.AIR, false);
        for (int i = 0; i < machine.contents().length; i++) {
            machine.setSlot(i, null);
        }

        world.createExplosion(loc.clone().add(0.5, 0.5, 0.5), 6.0F, true, true);
        world.strikeLightningEffect(loc);
    }

    @Override
    public String statusLine(MachineInstance machine) {
        int temperature = machine.counter(KEY_TEMPERATURE, 0);
        if (temperature > 0) {
            double ratio = temperature / (double) TEMPERATURE_MAX;
            String color = com.koole.higherRedStoneFun.core.Text.colorFor(1.0D - ratio);
            return "<red>过热！ <gray>温度 <" + color + ">"
                    + com.koole.higherRedStoneFun.core.Text.bar(ratio, 10, color, "dark_gray")
                    + " <dark_gray>(" + (100 - temperature / 10) + "% 安全裕度)";
        }
        if (!machine.hasFuel()) {
            return "<red>缺少燃料棒";
        }
        if (machine.counter(KEY_COOLANT, 0) <= 0) {
            return "<red>缺少冷却剂！即将升温";
        }
        return "<green>堆芯稳定 <dark_gray>| <aqua>冷却余量 <white>"
                + machine.counter(KEY_COOLANT, 0) + " <gray>tick";
    }

    public long outputPerTick() {
        return outputPerTick;
    }
}
