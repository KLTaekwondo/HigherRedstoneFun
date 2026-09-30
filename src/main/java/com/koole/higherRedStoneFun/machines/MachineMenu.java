package com.koole.higherRedStoneFun.machines;

import com.koole.higherRedStoneFun.core.Text;
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

    /** 把机器内容镜像到界面。 */
    public void refresh() {
        ItemStack[] contents = machine.contents();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, i < contents.length ? contents[i] : null);
        }
    }

    /** 把界面内容写回机器（正常关闭时调用）。 */
    public void flush() {
        int size = machine.definition().inventorySize();
        ItemStack[] contents = new ItemStack[size];
        for (int i = 0; i < size; i++) {
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
        int output = machine.definition().recipeType().outputSlot();
        // 部分机器（组装/分子重组）输出槽与输入槽有重叠布局，这里以实际输出槽为准
        return slot == output;
    }
}
