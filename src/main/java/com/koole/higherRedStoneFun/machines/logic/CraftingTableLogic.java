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
 * <h2>为什么预览不能存进机器状态</h2>
 *
 * <p>早期实现把预览产物<b>直接写进机器的输出槽</b>，而材料只在玩家点击取走时
 * 才扣除。这留下了一条无限复制路径：</p>
 *
 * <pre>
 *   放入 8 铁 + 1 铜  ->  预览把「机器框架」写进输出槽（材料还没扣）
 *                     ->  直接破坏机器
 *                     ->  掉落：8 铁 + 1 铜 + 1 机器框架
 *   净赚一个机器框架，可无限重复。
 * </pre>
 *
 * <p>根因是模型问题而非笔误：<b>预览是「界面上的一个提示」，不是「机器持有的一件物品」</b>。
 * 因此现在预览完全由 {@link com.koole.higherRedStoneFun.machines.MachineMenu}
 * 渲染到 GUI 层，机器状态里永远不存在这个物品，破坏机器自然也掉不出来。</p>
 */
public final class CraftingTableLogic extends RecipeMachineLogic {

    public CraftingTableLogic() {
        super(false, 0L);
    }

    @Override
    public boolean tick(MachineInstance machine) {
        // 工作台不自动生产
        return false;
    }

    @Override
    public boolean hasVirtualOutput() {
        return true;
    }

    /**
     * 计算当前输入对应的配方，没有则返回 null。
     *
     * <p>输入槽里放着的物品本身可能是合成原料，也可能包含玩家主动放进去的成品，
     * 这里一律按「输入」处理；输出槽永远不参与匹配。</p>
     */
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

    /** 供 GUI 渲染的预览产物。永远不写回机器状态。 */
    @Override
    public ItemStack previewResult(MachineInstance machine) {
        MachineRecipe recipe = preview(machine);
        if (recipe == null || recipe.outputs().isEmpty()) {
            return null;
        }
        return recipe.outputs().get(0).stack();
    }

    @Override
    public void onContentsChanged(MachineInstance machine) {
        // 预览由界面负责渲染，机器侧无需任何动作。
        //
        // 这里刻意留空：早期版本在此把预览写进机器的输出槽，
        // 导致「放材料 -> 拆机器」可以白拿产物（无限复制）。
        // 回归测试见 SelfTest#testCraftingTableSafety。
        // 如需验证该测试确实有效，可临时恢复下面两行，测试应报
        // 「拆机器不会白送产物 … 回收 xxx x1 (必须为 0)」。
        //
        //   MachineRecipe recipe = preview(machine);
        //   machine.setSlot(machine.definition().recipeType().outputSlot(),
        //                   recipe == null ? null : recipe.outputs().get(0).stack());
    }

    /**
     * 玩家取走结果时调用：重新校验配方，成立才扣除材料并返回产物。
     *
     * <p>注意这里<b>重新匹配一次配方</b>，而不是信任界面上显示的预览——
     * 玩家可能在界面里改动过材料，只有当下这一刻成立才算数。</p>
     *
     * @return 实际给出的产物，null 表示配方不成立（调用方应取消这次操作）
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

        machine.incrementCompleted();
        return recipe.outputs().get(0).stack();
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
