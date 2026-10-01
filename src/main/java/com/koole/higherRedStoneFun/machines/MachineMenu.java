package com.koole.higherRedStoneFun.machines;

import com.koole.higherRedStoneFun.core.Text;
import com.koole.higherRedStoneFun.machines.logic.CraftingTableLogic;
import com.koole.higherRedStoneFun.machines.logic.FuelValues;
import com.koole.higherRedStoneFun.recipes.RecipeType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 机器界面。
 *
 * <p>实现 {@link InventoryHolder}，因此可以通过 {@code getHolder()} 直接把界面
 * 绑定到机器实例上——不需要像粘液科技那样额外维护一份打开界面的映射表，
 * 也就不会出现「界面关了但映射没清」导致的内存泄漏。</p>
 */
public final class MachineMenu implements InventoryHolder {

    private final MachineInstance machine;
    private final Inventory inventory;

    public MachineMenu(MachineInstance machine) {
        this.machine = machine;
        this.inventory = Bukkit.createInventory(this, machine.definition().inventorySize(), title(machine));
    }

    private static net.kyori.adventure.text.Component title(MachineInstance machine) {
        return Text.mm("<dark_gray>[" + machine.definition().group().displayName() + "] <white>"
                + machine.definition().displayName());
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public MachineInstance machine() {
        return machine;
    }

    /**
     * 把机器内容镜像到界面，并渲染虚拟输出预览。
     *
     * <p>虚拟输出（工作台的结果格）只存在于界面层，永远不会写回机器状态，
     * 因此玩家拆掉机器时不可能把它带走。</p>
     */
    public void refresh() {
        renderFrame();
        // 机器内容写在功能槽上（覆盖边框）
        ItemStack[] contents = machine.contents();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (isFunctionalSlot(i)) {
                inventory.setItem(i, i < contents.length ? contents[i] : null);
            }
        }
        renderEnergyColumn();
        renderStatus();
        renderPreview();
    }

    /**
     * 所有非功能槽铺上边框玻璃。
     *
     * <p>功能槽 = 输入槽 ∪ 输出槽 ∪ 燃料槽。这样玩家一眼就能看出
     * 哪几个格子是可用的，其余都是装饰——早期版本整个界面就是一堆
     * 一样的空格子，玩家根本不知道材料该放哪。</p>
     */
    private void renderFrame() {
        ItemStack pane = pane();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, isFunctionalSlot(i) ? null : pane);
        }
    }

    private ItemStack pane() {
        ItemStack stack = new ItemStack(org.bukkit.Material.GRAY_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.mm(" "));
        stack.setItemMeta(meta);
        return stack;
    }

    /** 该槽位是否属于功能槽（输入/输出/燃料）。 */
    public boolean isFunctionalSlot(int slot) {
        if (!machine.definition().hasRecipes()) {
            return true;
        }
        RecipeType type = machine.definition().recipeType();
        for (int input : type.inputSlots()) {
            if (input == slot) {
                return true;
            }
        }
        for (int output : type.outputSlots()) {
            if (output == slot) {
                return true;
            }
        }
        return slot == fuelSlot();
    }

    /** 本机器的燃料槽位置。 */
    public int fuelSlot() {
        MachineLogic logic = machine.definition().logic();
        if (logic instanceof com.koole.higherRedStoneFun.machines.logic.RecipeMachineLogic rml) {
            return rml.fuelSlot();
        }
        return -1;
    }

    /**
     * 渲染最右侧的竖直能量条（增强工作台的燃料余量）。
     *
     * <p>用一列格子做成"电池柱"：下面的格子亮起表示燃料充足。
     * 这比一行文字直观——玩家扫一眼就知道还能撑多久。</p>
     */
    private void renderEnergyColumn() {
        int[] column = RecipeType.CRAFTING_ENERGY_COLUMN;
        if (inventory.getSize() <= 45
                || machine.definition().recipeType() != RecipeType.ENHANCED_CRAFTING) {
            return;
        }
        int fuel = machine.fuelTicks();
        double ratio = Math.min(1.0D, fuel / (double) FuelValues.REDSTONE_TICKS);
        int filled = (int) Math.ceil(ratio * column.length);

        for (int i = 0; i < column.length; i++) {
            // 从下往上填：底部的格子先亮
            boolean on = i < filled;
            org.bukkit.Material mat = on
                    ? org.bukkit.Material.RED_STAINED_GLASS_PANE
                    : org.bukkit.Material.BLACK_STAINED_GLASS_PANE;
            ItemStack stack = new ItemStack(mat);
            org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
            meta.displayName(Text.mm(on ? "<red>▌" : "<dark_gray>▌"));
            stack.setItemMeta(meta);
            inventory.setItem(column[column.length - 1 - i], stack);
        }
    }

    /** 状态位：显示进度 / 能量 / 燃料。 */
    private void renderStatus() {
        int slot = statusSlot();
        if (slot < 0) {
            return;
        }
        ItemStack stack = new ItemStack(org.bukkit.Material.REDSTONE_TORCH);
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.mm("<gold>机器状态"));

        List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        lore.add(Text.mm(machine.definition().logic().statusLine(machine)));
        lore.add(net.kyori.adventure.text.Component.empty());

        if (fuelSlot() >= 0) {
            int fuel = machine.fuelTicks();
            lore.add(Text.mm("<gray>燃料: <red>" + FuelValues.fuelName()
                    + " <dark_gray>(" + fuel + " tick)"));
            if (machine.definition().recipeType() == RecipeType.ENHANCED_CRAFTING) {
                lore.add(Text.mm("<gray>还可合成: <white>"
                        + (fuel / CraftingTableLogic.FUEL_COST_PER_CRAFT) + " <gray>次"));
            }
            lore.add(Text.mm("<dark_gray>把红石放进右上角燃料槽"));
        }
        if (machine.definition().energyRole() != null) {
            lore.add(Text.mm("<gray>红石流能: <red>"
                    + Text.number(machine.energyStored()) + " <gray>HRE"));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        inventory.setItem(slot, stack);
    }

    /**
     * 状态位的位置。
     *
     * <p>优先用机器布局里留好的位置（增强工作台是第五行第三列），
     * 否则退回到第一个空闲槽。</p>
     */
    private int statusSlot() {
        int preferred = RecipeType.CRAFTING_STATUS_SLOT;
        if (inventory.getSize() > preferred && !isFunctionalSlot(preferred)) {
            return preferred;
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!isFunctionalSlot(i)) {
                return i;
            }
        }
        return -1;
    }

    /** 单独刷新虚拟输出槽（玩家改动材料后调用）。 */
    public void renderPreview() {
        MachineLogic logic = machine.definition().logic();
        if (!logic.hasVirtualOutput()) {
            return;
        }
        int output = virtualOutputSlot();
        if (output < 0) {
            return;
        }
        // 预览是界面层的展示物，不落进 machine
        inventory.setItem(output, logic.previewResult(machine));
    }

    /** 虚拟输出槽的下标，没有则为 -1。 */
    public int virtualOutputSlot() {
        if (!machine.definition().hasRecipes() || !machine.definition().logic().hasVirtualOutput()) {
            return -1;
        }
        return machine.definition().recipeType().outputSlot();
    }

    /**
     * 把界面内容写回机器（关闭界面时调用）。
     *
     * <p><b>必须跳过虚拟输出槽</b>：那个格子里的东西只是预览，
     * 若一并写回就会变成可被拆走的真实物品，正是复制漏洞的来源。</p>
     */
    public void flush() {
        int size = machine.definition().inventorySize();
        int virtualSlot = virtualOutputSlot();
        ItemStack[] contents = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            if (i == virtualSlot) {
                // 虚拟槽：保持机器原本的值（工作台恒为 null）
                contents[i] = machine.getSlot(i);
                continue;
            }
            contents[i] = inventory.getItem(i);
        }
        machine.setContents(contents);
    }

    public void open(Player player) {
        refresh();
        player.openInventory(inventory);
        machine.definition().logic().onOpen(machine);
    }

    /** 判断点击的槽位是否属于本机器的输入区（用于防误操作）。 */
    public boolean isInputSlot(int slot) {
        if (!machine.definition().hasRecipes()) {
            return false;
        }
        for (int input : machine.definition().recipeType().inputSlots()) {
            if (input == slot) {
                return true;
            }
        }
        return false;
    }

    public boolean isOutputSlot(int slot) {
        if (!machine.definition().hasRecipes()) {
            return false;
        }
        return slot == machine.definition().recipeType().outputSlot();
    }

    /** 该槽位是否是虚拟输出（玩家取走时需要走结算流程）。 */
    public boolean isVirtualOutputSlot(int slot) {
        return slot >= 0 && slot == virtualOutputSlot();
    }

    /** 该界面是否使用虚拟输出模型。 */
    public boolean usesVirtualOutput() {
        return machine.definition().logic() instanceof CraftingTableLogic;
    }
}
