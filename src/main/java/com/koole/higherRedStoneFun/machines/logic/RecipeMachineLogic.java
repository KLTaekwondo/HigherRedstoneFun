package com.koole.higherRedStoneFun.machines.logic;

import com.koole.higherRedStoneFun.items.ItemRegistry;
import com.koole.higherRedStoneFun.machines.MachineInstance;
import com.koole.higherRedStoneFun.machines.MachineLogic;
import com.koole.higherRedStoneFun.recipes.MachineRecipe;
import com.koole.higherRedStoneFun.recipes.RecipeOutput;
import com.koole.higherRedStoneFun.recipes.RecipeRegistry;
import com.koole.higherRedStoneFun.recipes.RecipeType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 通用配方机器逻辑。
 *
 * <p>燃料机器（研磨/压制/熔炼/离心/锯切）与红石流能机器（红石流能熔炼/红石流能研磨/组装）
 * 共用这一份实现，区别只在 {@code electric} 标志：</p>
 *
 * <ul>
 *   <li>燃料机器：从燃料槽烧红石换取运行 tick。</li>
 *   <li>红石流能机器：向能源网络申请红石流能，按缓冲里的 HRE 决定推进速度。</li>
 * </ul>
 *
 * <p>这正是对粘液科技「每种机器一个类」的优化：新增一台同类机器只需要
 * 注册一份配方和定义，不需要写新代码。</p>
 */
public class RecipeMachineLogic implements MachineLogic {

    /** 燃料槽的默认位置（研磨/压制/熔炼等 27 格机器）。 */
    public static final int FUEL_SLOT = 0;

    private final boolean electric;
    /** 每 tick 消耗的 HRE（红石流能机器）。 */
    private final long energyPerTick;
    /** 本机器燃料槽的位置。增强工作台布局不同，需要覆盖。 */
    private final int fuelSlot;

    public RecipeMachineLogic(boolean electric, long energyPerTick) {
        this(electric, energyPerTick, FUEL_SLOT);
    }

    public RecipeMachineLogic(boolean electric, long energyPerTick, int fuelSlot) {
        this.electric = electric;
        this.energyPerTick = Math.max(0L, energyPerTick);
        this.fuelSlot = Math.max(0, fuelSlot);
    }

    /** 本机器的燃料槽位置。 */
    public int fuelSlot() {
        return fuelSlot;
    }

    /** 该机器是否消耗红石流能。 */
    public boolean isElectric() {
        return electric;
    }

    /** 每 tick 消耗的 HRE。 */
    public long energyPerTick() {
        return energyPerTick;
    }

    /** 燃料驱动机器（烧红石）。 */
    public static RecipeMachineLogic fuel() {
        return new RecipeMachineLogic(false, 0L);
    }

    /** 红石流能驱动机器，参数为每 tick 的 HRE 消耗。 */
    public static RecipeMachineLogic electric(long hrePerTick) {
        return new RecipeMachineLogic(true, hrePerTick);
    }

    @Override
    public boolean energyAware() {
        return electric;
    }

    // ------------------------------------------------------------------
    // 能源网络交互
    // ------------------------------------------------------------------

    @Override
    public long demand(MachineInstance machine) {
        if (!electric) {
            return 0L;
        }
        if (!machine.isRequestingPower()) {
            return 0L;
        }
        long cap = machine.definition().bufferCapacity();
        return Math.max(0L, cap - machine.energyStored());
    }

    // ------------------------------------------------------------------
    // 主循环
    // ------------------------------------------------------------------

    @Override
    public boolean tick(MachineInstance machine) {
        // 先尝试把输入匹配成配方；匹配不到就清掉需求并返回
        MachineRecipe recipe = findRecipe(machine);
        if (recipe == null) {
            machine.requestPower(false);
            if (machine.activeRecipe() != null) {
                machine.clearRecipe();
                return true;
            }
            return false;
        }

        // 有活要干：申请红石流能（红石流能机器）
        machine.requestPower(true);

        // 红石流能机器检查缓冲，HRE 不够就等待网络充电
        if (electric) {
            if (machine.energyStored() < energyPerTick) {
                return false;
            }
        } else {
            // 燃料机器确保有燃料
            if (!machine.hasFuel() && !consumeFuelItem(machine)) {
                return false;
            }
        }

        // 开始一个新配方
        if (machine.activeRecipe() == null || !machine.activeRecipe().equals(recipe.id())) {
            machine.setActiveRecipe(recipe.id(), recipe.durationTicks());
        }

        // 扣除本 tick 的运行成本
        if (electric) {
            machine.consumeEnergy(energyPerTick);
        } else {
            machine.consumeFuelTick();
        }

        machine.advance();

        if (machine.progress() >= machine.totalTicks()) {
            finish(machine, recipe);
            return true;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 配方匹配
    // ------------------------------------------------------------------

    protected MachineRecipe findRecipe(MachineInstance machine) {
        RecipeType type = machine.definition().recipeType();
        List<ItemStack> inputs = collectInputs(machine, type);
        MachineRecipe recipe = RecipeRegistry.get().findMatch(type, inputs);
        if (recipe == null) {
            return null;
        }
        // 产物槽必须放得下，否则机器会「生产出来但无处可放」
        if (!canFitOutputs(machine, recipe)) {
            return null;
        }
        return recipe;
    }

    protected List<ItemStack> collectInputs(MachineInstance machine, RecipeType type) {
        List<ItemStack> inputs = new ArrayList<>();
        for (int slot : type.inputSlots()) {
            ItemStack stack = machine.getSlot(slot);
            if (stack != null && !stack.getType().isAir()) {
                inputs.add(stack);
            }
        }
        return inputs;
    }

    /** 检查输出槽能否容纳配方产物（同类物品且不超堆叠上限）。 */
    protected boolean canFitOutputs(MachineInstance machine, MachineRecipe recipe) {
        RecipeType type = machine.definition().recipeType();
        int[] outSlots = type.outputSlots();

        // 在副本上模拟放置，避免污染真实物品栏
        List<ItemStack> simulated = new ArrayList<>(outSlots.length);
        for (int slot : outSlots) {
            ItemStack existing = machine.getSlot(slot);
            simulated.add(existing == null ? null : existing.clone());
        }

        for (RecipeOutput output : recipe.outputs()) {
            if (output.chance() <= 0.0D) {
                continue;
            }
            if (!placeInto(simulated, output.stack())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 把物品放入给定槽位列表（就地修改列表内容）。
     *
     * <p>先尝试堆叠到已有同类物品上，再占用空槽。返回是否全部放下。</p>
     */
    protected boolean placeInto(List<ItemStack> slots, ItemStack stack) {
        int remaining = stack.getAmount();
        ItemRegistry registry = ItemRegistry.get();

        // 第一轮：堆叠到已有同类物品上
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            ItemStack slot = slots.get(i);
            if (slot == null || slot.getType().isAir()) {
                continue;
            }
            if (!registry.sameItem(slot, stack)) {
                continue;
            }
            int space = slot.getMaxStackSize() - slot.getAmount();
            if (space <= 0) {
                continue;
            }
            int move = Math.min(space, remaining);
            remaining -= move;
            ItemStack copy = slot.clone();
            copy.setAmount(slot.getAmount() + move);
            slots.set(i, copy);
        }

        // 第二轮：放入空槽
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            ItemStack slot = slots.get(i);
            if (slot != null && !slot.getType().isAir()) {
                continue;
            }
            int move = Math.min(stack.getMaxStackSize(), remaining);
            remaining -= move;
            ItemStack copy = stack.clone();
            copy.setAmount(move);
            slots.set(i, copy);
        }

        return remaining <= 0;
    }

    // ------------------------------------------------------------------
    // 完成一次生产
    // ------------------------------------------------------------------

    protected void finish(MachineInstance machine, MachineRecipe recipe) {
        RecipeType type = machine.definition().recipeType();

        // 消耗输入
        List<ItemStack> slots = new ArrayList<>(type.inputSlots().length);
        int[] inputSlots = type.inputSlots();
        for (int slot : inputSlots) {
            slots.add(machine.getSlot(slot));
        }
        RecipeRegistry.get().consume(recipe, slots);
        for (int i = 0; i < inputSlots.length; i++) {
            machine.setSlot(inputSlots[i], slots.get(i));
        }

        // 产出
        int[] outSlots = type.outputSlots();
        List<ItemStack> outputs = new ArrayList<>(outSlots.length);
        for (int slot : outSlots) {
            ItemStack existing = machine.getSlot(slot);
            outputs.add(existing == null ? null : existing.clone());
        }

        for (RecipeOutput output : recipe.outputs()) {
            double roll = ThreadLocalRandom.current().nextDouble();
            if (roll > output.chance()) {
                continue;
            }
            ItemStack produced = output.stack();
            if (!placeInto(outputs, produced)) {
                // 放不下就掉在机器旁边，避免物品凭空消失
                machine.location().getWorld()
                        .dropItemNaturally(machine.location().clone().add(0.5, 1.0, 0.5), produced);
            }
        }

        for (int i = 0; i < outSlots.length; i++) {
            machine.setSlot(outSlots[i], outputs.get(i));
        }

        machine.incrementCompleted();
        machine.clearRecipe();
    }

    // ------------------------------------------------------------------
    // 燃料
    // ------------------------------------------------------------------

    /** 尝试从燃料槽消耗一个燃料物品，成功返回 true。 */
    protected boolean consumeFuelItem(MachineInstance machine) {
        ItemStack fuel = machine.getSlot(fuelSlot);
        if (fuel == null || fuel.getType().isAir()) {
            return false;
        }
        int ticks = FuelValues.ticksFor(fuel.getType());
        if (ticks <= 0) {
            return false;
        }
        ItemStack copy = fuel.clone();
        copy.setAmount(copy.getAmount() - 1);
        machine.setSlot(fuelSlot, copy.getAmount() <= 0 ? null : copy);
        machine.addFuelTicks(ticks);
        return true;
    }

    @Override
    public String statusLine(MachineInstance machine) {
        if (machine.activeRecipe() == null) {
            return "<gray>待机";
        }
        double ratio = machine.totalTicks() == 0 ? 0.0D
                : (double) machine.progress() / (double) machine.totalTicks();
        return "<gray>进度 " + com.koole.higherRedStoneFun.core.Text.bar(
                ratio, 10, com.koole.higherRedStoneFun.core.Text.colorFor(ratio), "dark_gray");
    }
}
