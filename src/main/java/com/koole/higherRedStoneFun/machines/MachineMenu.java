package com.koole.higherRedStoneFun.machines;

import com.koole.higherRedStoneFun.core.Text;
import com.koole.higherRedStoneFun.machines.logic.CraftingTableLogic;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

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
        ItemStack[] contents = machine.contents();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, i < contents.length ? contents[i] : null);
        }
        renderPreview();
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
