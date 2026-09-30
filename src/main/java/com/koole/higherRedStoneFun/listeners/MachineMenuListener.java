package com.koole.higherRedStoneFun.listeners;

import com.koole.higherRedStoneFun.HigherRedStoneFun;
import com.koole.higherRedStoneFun.core.Text;
import com.koole.higherRedStoneFun.machines.MachineInstance;
import com.koole.higherRedStoneFun.machines.MachineMenu;
import com.koole.higherRedStoneFun.machines.logic.CraftingTableLogic;
import com.koole.higherRedStoneFun.recipes.RecipeType;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * 机器界面交互。
 *
 * <p>核心原则：<b>玩家永远不会丢失物品</b>。</p>
 *
 * <p>粘液科技里最常见的物品纠纷是「把东西放进机器后界面卡住 / 机器被拆，东西就没了」。
 * 这里用三条规则保证安全：</p>
 *
 * <ol>
 *   <li>界面直接绑定机器实例（{@link MachineMenu} 自己就是 {@link org.bukkit.inventory.InventoryHolder}），
 *       没有中间映射表，不会出现「界面与机器不同步」。</li>
 *   <li>关闭界面时把界面内容原样写回机器；机器被拆时会把内容物全部掉出。</li>
 *   <li>禁止 shift 转移、数字键交换、双击收集等绕过写入流程的操作。</li>
 * </ol>
 */
public final class MachineMenuListener implements Listener {

    /** 会绕过一致性保证的操作。 */
    private static final Set<InventoryAction> BLOCKED_ACTIONS = Set.of(
            InventoryAction.MOVE_TO_OTHER_INVENTORY,
            InventoryAction.COLLECT_TO_CURSOR,
            InventoryAction.HOTBAR_SWAP,
            InventoryAction.CLONE_STACK,
            InventoryAction.UNKNOWN
    );

    private static final Set<ClickType> BLOCKED_CLICKS = Set.of(
            ClickType.NUMBER_KEY,
            ClickType.DOUBLE_CLICK,
            ClickType.SWAP_OFFHAND,
            ClickType.CONTROL_DROP
    );

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MachineMenu menu)) {
            return;
        }
        MachineInstance machine = menu.machine();

        int raw = event.getRawSlot();
        int machineSize = event.getInventory().getSize();
        boolean inMachine = raw >= 0 && raw < machineSize;

        if (!inMachine) {
            // 点击玩家自己的背包：只禁止 shift 转移，其余放行
            if (event.isShiftClick() || BLOCKED_CLICKS.contains(event.getClick())) {
                event.setCancelled(true);
            }
            return;
        }

        if (BLOCKED_CLICKS.contains(event.getClick()) || BLOCKED_ACTIONS.contains(event.getAction())) {
            event.setCancelled(true);
            return;
        }

        if (!machine.definition().hasRecipes()) {
            // 无配方机器（发电机 / 电容）：当作普通容器，放行
            return;
        }

        RecipeType type = machine.definition().recipeType();

        // ---- 输出槽：只允许空手取走 ----
        if (type.isOutputSlot(raw)) {
            if (!isEmpty(event.getCursor())) {
                // 光标上有东西：禁止往输出槽放
                event.setCancelled(true);
                return;
            }
            if (machine.definition().logic() instanceof CraftingTableLogic logic) {
                // 工作台需要在此刻结算材料
                ItemStack taken = logic.takeResult(machine);
                if (taken == null) {
                    event.setCancelled(true);
                    return;
                }
                // 允许原版把结果格内容移入光标，随后刷新预览
                refreshLater(menu, machine);
            }
            return;
        }

        // ---- 输入槽：加工中不允许抽走材料 ----
        if (type.isInputSlot(raw)) {
            boolean processing = machine.activeRecipe() != null;
            boolean removing = isEmpty(event.getCursor()) && !isEmpty(event.getCurrentItem());
            if (processing && removing) {
                // 抽走材料会让进度与材料不一致，直接拒绝
                Player player = (Player) event.getWhoClicked();
                player.sendMessage(Text.prefixed("<red>机器正在加工，请等这一批完成后再取材料"));
                event.setCancelled(true);
                return;
            }
            if (!isEmpty(event.getCursor())) {
                refreshLater(menu, machine);
            }
            return;
        }

        // ---- 其他槽位（燃料 / 副产物）：放行 ----
        refreshLater(menu, machine);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof MachineMenu menu)) {
            return;
        }
        MachineInstance machine = menu.machine();
        if (!machine.definition().hasRecipes()) {
            return;
        }
        RecipeType type = machine.definition().recipeType();

        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize() && type.isOutputSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }
        refreshLater(menu, machine);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MachineMenu menu)) {
            return;
        }
        // 把界面内容原样写回机器，保证「关掉界面 = 内容保存在机器里」
        menu.flush();
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }

    /**
     * 延迟一个 tick 刷新配方预览。
     *
     * <p>不能在当前事件里直接改物品栏，否则会与原版的物品移动逻辑打架。</p>
     */
    private void refreshLater(MachineMenu menu, MachineInstance machine) {
        if (!machine.definition().hasRecipes()) {
            return;
        }
        Bukkit.getScheduler().runTask(HigherRedStoneFun.get(), () -> {
            if (!isStillOpen(menu.getInventory())) {
                return;
            }
            machine.definition().logic().onContentsChanged(machine);

            // 把最新的预览同步到结果槽
            int output = machine.definition().recipeType().outputSlot();
            ItemStack result = machine.getSlot(output);
            ItemStack shown = menu.getInventory().getItem(output);

            // 只有当玩家没有在操作结果槽时才覆盖显示
            if (isEmpty(shown) || com.koole.higherRedStoneFun.items.ItemRegistry.get()
                    .sameItem(shown, result == null ? shown : result)) {
                menu.getInventory().setItem(output, result);
            }
        });
    }

    private static boolean isStillOpen(Inventory inventory) {
        for (HumanEntity viewer : inventory.getViewers()) {
            if (viewer.getOpenInventory().getTopInventory().getHolder()
                    instanceof MachineMenu m && m.getInventory() == inventory) {
                return true;
            }
        }
        return false;
    }
}
