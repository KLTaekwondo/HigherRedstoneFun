package com.koole.higherRedStoneFun.listeners;

import com.koole.higherRedStoneFun.HigherRedStoneFun;
import com.koole.higherRedStoneFun.core.Text;
import com.koole.higherRedStoneFun.items.ItemRegistry;
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

        // ---- 输出槽 ----
        if (menu.isOutputSlot(raw)) {
            // 光标上有东西：禁止往输出槽放
            if (!isEmpty(event.getCursor())) {
                event.setCancelled(true);
                return;
            }

            // 虚拟输出槽（工作台）：需要手动结算，不能让原版逻辑直接把格子里的
            // 预览物品交给玩家——那份预览并不属于机器。
            if (menu.isVirtualOutputSlot(raw)) {
                event.setCancelled(true);
                takeVirtualResult((Player) event.getWhoClicked(), menu, machine);
                return;
            }

            // 真实输出槽（自动机器）：放行原版取物逻辑
            refreshLater(menu, machine);
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
            refreshLater(menu, machine);
            return;
        }

        // ---- 其他槽位（燃料 / 副产物）：放行 ----
        refreshLater(menu, machine);
    }

    /**
     * 结算虚拟输出：重新校验配方 -> 扣材料 -> 把产物交给玩家。
     *
     * <p>产物是<b>现场重新计算</b>出来的新物品，而不是界面上那个展示用的预览，
     * 因此即使玩家在界面上看到过预览，也必须材料成立才会真正拿到东西。</p>
     */
    private void takeVirtualResult(Player player, MachineMenu menu, MachineInstance machine) {
        if (!(machine.definition().logic() instanceof CraftingTableLogic logic)) {
            return;
        }
        ItemStack result = logic.takeResult(machine);
        if (result == null) {
            // 配方不成立，或结构未成型导致精密零件被锁定
            String reason = logic.failureReason(machine);
            if (reason != null) {
                player.sendMessage(Text.prefixed("<red>" + reason));
            }
            menu.renderPreview();
            return;
        }

        // 先叠加到光标上已有的同类物品，再退化为放入背包，最后才掉在地上
        ItemStack cursor = player.getItemOnCursor();
        if (!isEmpty(cursor) && ItemRegistry.get().sameItem(cursor, result)
                && cursor.getAmount() + result.getAmount() <= cursor.getMaxStackSize()) {
            ItemStack merged = cursor.clone();
            merged.setAmount(cursor.getAmount() + result.getAmount());
            player.setItemOnCursor(merged);
        } else if (isEmpty(cursor)) {
            player.setItemOnCursor(result);
        } else {
            var leftover = player.getInventory().addItem(result);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }

        // 刷新预览，让玩家能连续合成
        menu.renderPreview();
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

    /**
     * 关闭界面：把内容写回机器。
     *
     * <p>{@link MachineMenu#flush()} 会跳过虚拟输出槽，因此工作台的预览
     * 不会被当成真实物品存进机器，也就无法通过拆机器被带走。</p>
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MachineMenu menu)) {
            return;
        }
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

            if (menu.usesVirtualOutput()) {
                // 虚拟输出：只重新渲染预览，绝不写进机器状态
                menu.renderPreview();
                return;
            }

            // 真实输出：把机器里的产物同步到界面
            int output = machine.definition().recipeType().outputSlot();
            ItemStack result = machine.getSlot(output);
            ItemStack shown = menu.getInventory().getItem(output);
            if (isEmpty(shown) || ItemRegistry.get().sameItem(shown, result == null ? shown : result)) {
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
