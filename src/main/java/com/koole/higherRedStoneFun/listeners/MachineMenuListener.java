package com.koole.higherRedStoneFun.listeners;

import com.koole.higherRedStoneFun.HigherRedStoneFun;
import com.koole.higherRedStoneFun.core.Text;
import com.koole.higherRedStoneFun.items.ItemRegistry;
import com.koole.higherRedStoneFun.machines.MachineInstance;
import com.koole.higherRedStoneFun.machines.MachineMenu;
import com.koole.higherRedStoneFun.machines.logic.CraftingTableLogic;
import com.koole.higherRedStoneFun.machines.logic.FuelValues;
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

import java.util.ArrayList;
import java.util.List;
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
 *   <li>禁止数字键交换、双击收集等绕过写入流程的操作；shift 一键<b>放入</b>是我们
 *       自己实现的（{@link #quickInsert}），因为原版会往预览格里乱塞物品。</li>
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
            // 点击玩家自己的背包
            if (event.isShiftClick()) {
                if (!machine.definition().hasRecipes()) {
                    // 无配方机器（发电机 / 电容）整机都是容器，交给原版最省事
                    return;
                }
                // 自己实现「一键放入」，见 quickInsert 的说明
                event.setCancelled(true);
                quickInsert(event, menu, machine);
                return;
            }
            if (BLOCKED_CLICKS.contains(event.getClick())) {
                event.setCancelled(true);
            }
            return;
        }

        // shift 一键取出：自己实现，理由见 quickExtract
        if (event.isShiftClick() || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (!machine.definition().hasRecipes()) {
                // 无配方机器（发电机 / 电容）整机都是容器，交给原版
                return;
            }
            event.setCancelled(true);
            quickExtract(event, menu, machine);
            return;
        }

        if (BLOCKED_CLICKS.contains(event.getClick()) || BLOCKED_ACTIONS.contains(event.getAction())) {
            event.setCancelled(true);
            feedback(event, "<red>不支持数字键交换 / 双击收集，请手动拖放物品");
            return;
        }

        if (!machine.definition().hasRecipes()) {
            // 无配方机器（发电机 / 电容）：当作普通容器，放行
            return;
        }

        RecipeType type = machine.definition().recipeType();

        // ---- 预览格：点击即合成，产物进右侧成品区 ----
        //
        // 必须放在最前面：预览格是功能格但不是输出格，若落到最后的「放行」
        // 分支，原版就会把预览物品直接交给玩家——那是不存在的物品。
        if (menu.isPreviewSlot(raw)) {
            event.setCancelled(true);
            if (isEmpty(event.getCursor())) {
                craftFromPreview((Player) event.getWhoClicked(), menu, machine);
            } else {
                feedback(event, "<red>请先把光标上的物品放下");
            }
            return;
        }

        // ---- 成品区：真实存储，放行原版取物逻辑（但不许往里放）----
        if (menu.isOutputSlot(raw)) {
            if (!isEmpty(event.getCursor())) {
                event.setCancelled(true);
                feedback(event, "<red>成品区只能取走产物，不能放入物品");
                return;
            }
            refreshLater(menu, machine);
            return;
        }

        // ---- 装饰格：一律不让动 ----
        //
        // 早期这里直接放行，于是有两个后果：
        //   1. 玩家能把边框玻璃板 / 燃料条抠下来；
        //   2. 玩家能把红石之类的东西塞进装饰格——那些格子界面上会被玻璃板
        //      盖住、也不会被 flush 保存显示，表现为「东西放进去就没了」。
        if (!menu.isFunctionalSlot(raw)) {
            event.setCancelled(true);
            if (!isEmpty(event.getCursor())) {
                feedback(event, "<red>这一格是装饰，不能放东西");
            }
            return;
        }

        // ---- 输入槽：加工中不允许抽走材料 ----
        if (type.isInputSlot(raw)) {
            boolean processing = machine.activeRecipe() != null;
            boolean removing = isEmpty(event.getCursor()) && !isEmpty(event.getCurrentItem());
            if (processing && removing) {
                // 抽走材料会让进度与材料不一致，直接拒绝
                feedback(event, "<red>机器正在加工，请等这一批完成后再取材料");
                event.setCancelled(true);
                return;
            }
            refreshLater(menu, machine);
            return;
        }

        // ---- 其他槽位（燃料 / 副产物）：放行 ----
        refreshLater(menu, machine);
    }

    /** 给玩家一句反馈——静默拦截是最让人困惑的交互。 */
    private static void feedback(InventoryClickEvent event, String message) {
        if (event.getWhoClicked() instanceof Player player) {
            player.sendMessage(Text.prefixed(message));
        }
    }

    /**
     * shift 一键放入：把背包里的物品按「原料槽 → 燃料槽」的顺序塞进机器。
     *
     * <p>为什么不直接放行 {@code MOVE_TO_OTHER_INVENTORY}：原版只认「列表里第一个
     * 能放下的格子」，在这台机器上会先把物品塞进<b>预览格 22</b> 和燃料格——
     * 而预览格是虚拟格（{@code flush()} 会保留机器原值），塞进去的物品等于凭空消失。
     * 所以我们自己定顺序、自己堆叠，只往真正接受的槽位里放。</p>
     *
     * <p>燃料类物品（红石 / 红石块）优先补燃料槽，其余按原料区顺序放。</p>
     */
    private void quickInsert(InventoryClickEvent event, MachineMenu menu, MachineInstance machine) {
        ItemStack source = event.getCurrentItem();
        if (isEmpty(source)) {
            return;
        }
        Player player = event.getWhoClicked() instanceof Player p ? p : null;

        // shift 右键只搬一个，shift 左键搬一整叠
        int limit = event.getClick() == ClickType.SHIFT_RIGHT ? 1 : source.getAmount();

        RecipeType type = machine.definition().recipeType();
        List<Integer> targets = new ArrayList<>();
        int fuelSlot = menu.fuelSlot();
        if (fuelSlot >= 0 && FuelValues.isFuel(source.getType())) {
            targets.add(fuelSlot);
        }
        for (int slot : type.inputSlots()) {
            targets.add(slot);
        }

        Inventory target = menu.getInventory();
        ItemRegistry registry = ItemRegistry.get();
        int remaining = limit;

        for (int slot : targets) {
            if (remaining <= 0) {
                break;
            }
            ItemStack existing = target.getItem(slot);
            if (isEmpty(existing)) {
                ItemStack placed = source.clone();
                placed.setAmount(Math.min(remaining, placed.getMaxStackSize()));
                target.setItem(slot, placed);
                remaining -= placed.getAmount();
            } else if (registry.sameItem(existing, source)) {
                int space = existing.getMaxStackSize() - existing.getAmount();
                if (space <= 0) {
                    continue;
                }
                int move = Math.min(space, remaining);
                ItemStack merged = existing.clone();
                merged.setAmount(existing.getAmount() + move);
                target.setItem(slot, merged);
                remaining -= move;
            }
        }

        int moved = limit - remaining;
        if (moved <= 0) {
            if (player != null) {
                player.sendMessage(Text.prefixed("<red>放不下了：这台机器的原料区已满"));
            }
            return;
        }

        // 直接改被点击的格子（事件已取消，不能依赖原版逻辑写回）
        int left = source.getAmount() - moved;
        Inventory clicked = event.getClickedInventory();
        if (clicked != null) {
            if (left <= 0) {
                clicked.setItem(event.getSlot(), null);
            } else {
                ItemStack rest = source.clone();
                rest.setAmount(left);
                clicked.setItem(event.getSlot(), rest);
            }
        }

        refreshLater(menu, machine);
    }

    /**
     * shift 一键取出：把机器里该格的东西搬进玩家背包。
     *
     * <p><b>为什么不交给原版</b>：原版不区分槽位性质。放行
     * {@code MOVE_TO_OTHER_INVENTORY} 会让玩家把<b>预览格里的展示物品</b>和
     * <b>装饰格的边框玻璃板</b>一起 shift 进背包——展示物品根本不属于机器，
     * 拿走就是凭空造物（这正是早期把 shift 全禁掉的原因）。
     * 所以这里按槽位性质逐类判定，只放行真正的功能格。</p>
     */
    private void quickExtract(InventoryClickEvent event, MachineMenu menu, MachineInstance machine) {
        int slot = event.getRawSlot();

        // 预览格：展示物，永远不能拿走
        if (menu.isPreviewSlot(slot)) {
            feedback(event, "<red>预览格只是展示，点它才是真正合成");
            return;
        }
        // 装饰格：边框玻璃板 / 燃料条
        if (!menu.isFunctionalSlot(slot)) {
            return;
        }
        // 原料格：加工中不许抽走（与普通点击同一条规则）
        if (machine.definition().recipeType().isInputSlot(slot) && machine.activeRecipe() != null) {
            feedback(event, "<red>机器正在加工，请等这一批完成后再取材料");
            return;
        }

        ItemStack current = menu.getInventory().getItem(slot);
        if (isEmpty(current)) {
            return;
        }
        Player player = event.getWhoClicked() instanceof Player p ? p : null;
        if (player == null) {
            return;
        }

        // shift 右键只搬一个，shift 左键搬一整叠
        int amount = event.getClick() == ClickType.SHIFT_RIGHT ? 1 : current.getAmount();
        ItemStack moving = current.clone();
        moving.setAmount(amount);
        for (ItemStack drop : player.getInventory().addItem(moving).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }

        int left = current.getAmount() - amount;
        if (left <= 0) {
            menu.getInventory().setItem(slot, null);
        } else {
            ItemStack rest = current.clone();
            rest.setAmount(left);
            menu.getInventory().setItem(slot, rest);
        }

        refreshLater(menu, machine);
    }

    /**
     * 点击预览格：结算一次合成，产物放进右侧成品区。
     *
     * <p>产物<b>不再直接塞给玩家光标</b>，而是进成品区（真实存储）——
     * 这样「点了合成」和「拿走产物」是两步，成品区里堆着什么一目了然，
     * 也随机器存档。</p>
     */
    private void craftFromPreview(Player player, MachineMenu menu, MachineInstance machine) {
        if (!(machine.definition().logic() instanceof CraftingTableLogic logic)) {
            return;
        }

        // 装不下就干脆别合成：否则会先扣材料、产物却无处可放
        ItemStack preview = logic.previewResult(machine);
        if (preview != null && !logic.canStore(machine, preview)) {
            player.sendMessage(Text.prefixed("<red>成品区已满，请先取走产物"));
            return;
        }

        ItemStack result = logic.takeResult(machine);
        if (result == null) {
            // 配方不成立，或结构未成型导致精密零件被锁定
            String reason = logic.failureReason(machine);
            if (reason != null) {
                player.sendMessage(Text.prefixed("<red>" + reason));
            }
            menu.refresh();
            return;
        }

        logic.store(machine, result);

        // 结算完成后必须把【机器 → 界面】整屏回灌：材料已扣除、燃料已减少、
        // 成品区多了产物。若不同步，关闭界面时 flush() 会把旧材料又写回机器——
        // 白拿产物还留着材料，可以无限合成。
        menu.refresh();
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

        for (int slot : event.getRawSlots()) {
            if (slot >= event.getInventory().getSize()) {
                continue;   // 玩家自己的背包，不管
            }
            // 成品区不能拖入；预览格不能拖入（拖进去的物品 flush 时会被丢弃）；
            // 装饰格也不能拖入（否则物品会被玻璃板盖住，形同消失）
            if (menu.isOutputSlot(slot) || menu.isPreviewSlot(slot) || !menu.isFunctionalSlot(slot)) {
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
     * 延迟一个 tick，把界面改动写回机器，并刷新依赖机器状态的画面。
     *
     * <p>不能在当前事件里直接改物品栏，否则会与原版的物品移动逻辑打架，
     * 所以统一放到下一个 tick。</p>
     *
     * <p><b>这里必须 flush（界面 → 机器）</b>：机器的 tick 逻辑、配方匹配、
     * {@code takeResult()} 读的都是<b>机器状态</b>，而玩家的操作只发生在界面上。
     * 早期只在关闭界面时才 flush，后果是：</p>
     *
     * <ul>
     *   <li>开着界面把红石放进燃料槽 → 机器那边没收到 → 点产物回报「燃料不足」，
     *       燃料条和状态位也一直是旧的（玩家觉得「红石填不进去」）；</li>
     *   <li>材料摆好了却连预览都不出——{@code previewResult()} 读的是机器状态，
     *       此时还是一堆 null。</li>
     * </ul>
     */
    private void refreshLater(MachineMenu menu, MachineInstance machine) {
        Bukkit.getScheduler().runTask(HigherRedStoneFun.get(), () -> {
            if (!isStillOpen(menu.getInventory())) {
                return;
            }
            // ① 界面 → 机器（虚拟输出格与装饰格由 flush 自行跳过）
            menu.flush();
            machine.definition().logic().onContentsChanged(machine);

            // ② 机器 → 界面：状态位 / 燃料条 / 成品区
            menu.refreshDynamic();

            if (!machine.definition().hasRecipes()) {
                // 无配方机器（发电机 / 电容）没有配方产物可同步
                return;
            }

            // ③ 真实输出/成品区：把机器里的产物同步到界面
            //    （可能有多格：增强工作台成品区 9 格、离心机 13/14/15、锯木机 14/15）
            for (int output : menu.outputSlots()) {
                ItemStack result = machine.getSlot(output);
                ItemStack shown = menu.getInventory().getItem(output);
                if (isEmpty(shown) || ItemRegistry.get().sameItem(shown, result == null ? shown : result)) {
                    menu.getInventory().setItem(output, result);
                }
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
