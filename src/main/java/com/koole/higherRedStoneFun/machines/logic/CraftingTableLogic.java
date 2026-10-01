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

    /**
     * 每次合成消耗的燃料 tick 数。
     *
     * <p>1 个红石 = {@link FuelValues#REDSTONE_TICKS} tick，因此 1 红石
     * 可以合成 16 次，1 红石块可以合成 144 次。手动机器不该烧得太凶，
     * 但也不能完全免费——否则它就不像一台「机器」了。</p>
     */
    public static final int FUEL_COST_PER_CRAFT = 100;

    public CraftingTableLogic() {
        super(false, 0L, RecipeType.CRAFTING_FUEL_SLOT);
    }

    /**
     * 确保燃料足够完成一次合成；不足则从燃料槽烧一个红石。
     *
     * @return 燃料是否已足够
     */
    public boolean ensureFuel(MachineInstance machine) {
        if (machine.fuelTicks() < FUEL_COST_PER_CRAFT) {
            consumeFuelItem(machine);
        }
        return machine.fuelTicks() >= FUEL_COST_PER_CRAFT;
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
        // 结构未成型且该产物是精密零件时，不显示预览（避免误导玩家）
        if (blockedByStructure(machine) != null) {
            return null;
        }
        return recipe.outputs().get(0).stack();
    }

    /**
     * 需要「真空环境」（结构成型）才能制造的零件。
     *
     * <p>这是多方块结构存在的理由：单方块工作台能做出全部基础零件，
     * 但精密零件必须在玻璃罩下完成——玻璃罩提供无尘/真空环境，
     * 这在直觉上也说得通。</p>
     */
    private static final java.util.Set<String> PRECISION_PARTS = java.util.Set.of(
            "hrf_circuit_board",
            "hrf_advanced_circuit",
            "hrf_battery_cell",
            "hrf_dna_sequencer_part",
            "hrf_splicing_matrix",
            "hrf_injector_needle"
    );

    /** 该物品是否属于需要真空环境的精密零件。 */
    public static boolean isPrecisionPart(String itemId) {
        return itemId != null && PRECISION_PARTS.contains(itemId);
    }

    /**
     * 判断当前配方是否因为结构未成型而被禁止。
     *
     * @return 被禁止时返回该零件的中文名，否则返回 null
     */
    private String blockedByStructure(MachineInstance machine) {
        if (machine.isStructureComplete()) {
            return null;
        }
        MachineRecipe recipe = preview(machine);
        if (recipe == null || recipe.outputs().isEmpty()) {
            return null;
        }
        ItemStack out = recipe.outputs().get(0).stack();
        String id = com.koole.higherRedStoneFun.items.ItemRegistry.get().idOf(out);
        if (!isPrecisionPart(id)) {
            return null;
        }
        com.koole.higherRedStoneFun.items.HrfItem item =
                com.koole.higherRedStoneFun.items.ItemRegistry.get().get(id);
        return item == null ? id : item.plainName();
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
     * <p>另外这里也<b>重新校验结构</b>：精密零件必须在结构成型状态下才能取走，
     * 防止玩家「先摆好结构做出预览，再拆掉玻璃来拿产物」。</p>
     *
     * @return 实际给出的产物，null 表示配方不成立或结构不足（调用方应取消这次操作）
     */
    public ItemStack takeResult(MachineInstance machine) {
        MachineRecipe recipe = preview(machine);
        if (recipe == null || recipe.outputs().isEmpty()) {
            return null;
        }

        // 结构校验：未成型时禁止取走精密零件
        if (blockedByStructure(machine) != null) {
            return null;
        }

        // 燃料校验：每次合成要烧掉一点红石
        if (!ensureFuel(machine)) {
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

        // 扣除燃料
        machine.consumeFuel(FUEL_COST_PER_CRAFT);

        machine.incrementCompleted();
        return recipe.outputs().get(0).stack();
    }

    /**
     * 结算失败的原因（用于给玩家提示），成功则返回 null。
     */
    public String failureReason(MachineInstance machine) {
        MachineRecipe recipe = preview(machine);
        if (recipe == null || recipe.outputs().isEmpty()) {
            return "配方不成立";
        }
        String blocked = blockedByStructure(machine);
        if (blocked != null) {
            return "「" + blocked + "」需要真空环境，请先搭好结构";
        }
        if (machine.fuelTicks() < FUEL_COST_PER_CRAFT
                && !FuelValues.isFuel(fuelMaterial(machine))) {
            return "燃料不足，请在右侧放入红石";
        }
        return null;
    }

    /** 燃料槽里的物品材质，用于判断「能不能烧」。 */
    private static org.bukkit.Material fuelMaterial(MachineInstance machine) {
        ItemStack fuel = machine.getSlot(RecipeType.CRAFTING_FUEL_SLOT);
        return fuel == null ? null : fuel.getType();
    }

    @Override
    public String statusLine(MachineInstance machine) {
        if (machine.definition().isMultiblock()) {
            if (!machine.isStructureComplete()) {
                String missing = machine.definition().structure().describeMissing(machine.location());
                return "<red>结构未成型 <dark_gray>| <gray>还需要: <white>" + missing;
            }
        }
        MachineRecipe recipe = preview(machine);
        if (recipe == null) {
            return "<gray>请摆放材料";
        }
        String blocked = blockedByStructure(machine);
        if (blocked != null) {
            return "<yellow>需要真空环境 <dark_gray>(<white>" + blocked + "<dark_gray>)";
        }
        // 燃料提示：够用就报剩余次数，不够就提示放红石
        int crafts = machine.fuelTicks() / FUEL_COST_PER_CRAFT;
        if (crafts <= 0 && !FuelValues.isFuel(fuelMaterial(machine))) {
            return "<red>燃料不足 <dark_gray>| <gray>请在右侧放入<white>红石";
        }
        return "<green>可制作 <dark_gray>| <white>" + recipe.inputs().size()
                + " <gray>种材料 <dark_gray>| <gold>还可合成 " + crafts + " 次";
    }
}
