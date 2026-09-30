package com.koole.higherRedStoneFun.machines.logic;

import com.koole.higherRedStoneFun.machines.MachineInstance;
import com.koole.higherRedStoneFun.recipes.MachineRecipe;
import com.koole.higherRedStoneFun.recipes.RecipeRegistry;
import com.koole.higherRedStoneFun.recipes.RecipeType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 手动工作台逻辑（增强工作台）。
 *
 * <p>与自动机器的区别：工作台不消耗燃料也不推进进度，玩家把材料摆进去后
 * 结果格会立即显示预览；玩家取走结果时才真正扣除材料。</p>
 *
 * <p>相比粘液科技的同类工作台，这里增加了一个体验优化：
 * <b>结果格是「实时预览」</b>，玩家摆放过程中就能看到能做出什么，
 * 不需要反复试错。</p>
 */
public final class CraftingTableLogic extends RecipeMachineLogic {

    public CraftingTableLogic() {
        super(false, 0L);
    }

    @Override
    public boolean tick(MachineInstance machine) {
        // 工作台不自动生产，仅刷新预览
        return false;
    }

    /** 计算当前输入对应的配方，没有则返回 null。 */
    public MachineRecipe preview(MachineInstance machine) {
        RecipeType type = machine.definition().recipeType();
        List<ItemStack> inputs = new ArrayList<>();
        for (int slot : type.inputSlots()) {
            ItemStack stack = machine.getSlot(slot);
            if (stack != null && !stack.getType().isAir()) {
                inputs.add(stack);
            }
        }
        if (inputs.isEmpty()) {
            return null;
        }
        return RecipeRegistry.get().findMatch(type, inputs);
    }

    @Override
    public void onContentsChanged(MachineInstance machine) {
        updatePreview(machine);
    }

    /** 把配方预览写入结果格。 */
    public void updatePreview(MachineInstance machine) {
        int output = machine.definition().recipeType().outputSlot();
        MachineRecipe recipe = preview(machine);

        if (recipe == null || recipe.outputs().isEmpty()) {
            // 配方不成立：清掉预览，避免玩家白拿一个成品
            machine.setSlot(output, null);
            return;
        }
        machine.setSlot(output, recipe.outputs().get(0).stack());
    }

    /**
     * 玩家取走结果时调用：确认配方仍然成立，然后扣除材料。
     *
     * @return 实际给出的产物，null 表示配方不成立
     */
    public ItemStack takeResult(MachineInstance machine) {
        MachineRecipe recipe = preview(machine);
        if (recipe == null || recipe.outputs().isEmpty()) {
            return null;
        }
        RecipeType type = machine.definition().recipeType();

        // 扣除输入
        List<ItemStack> slots = new ArrayList<>();
        int[] inputSlots = type.inputSlots();
        for (int slot : inputSlots) {
            slots.add(machine.getSlot(slot));
        }
        RecipeRegistry.get().consume(recipe, slots);
        for (int i = 0; i < inputSlots.length; i++) {
            machine.setSlot(inputSlots[i], slots.get(i));
        }

        ItemStack result = recipe.outputs().get(0).stack();
        machine.incrementCompleted();
        return result;
    }

    @Override
    public String statusLine(MachineInstance machine) {
        MachineRecipe recipe = preview(machine);
        if (recipe == null) {
            return "<gray>请摆放材料";
        }
        return "<green>可制作 <dark_gray>| <white>" + recipe.inputs().size() + " <gray>种材料";
    }
}
