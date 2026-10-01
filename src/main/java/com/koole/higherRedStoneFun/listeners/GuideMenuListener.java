package com.koole.higherRedStoneFun.listeners;

import com.koole.higherRedStoneFun.core.ItemGroup;
import com.koole.higherRedStoneFun.items.ItemRegistry;
import com.koole.higherRedStoneFun.machines.MachineRegistry;
import com.koole.higherRedStoneFun.ui.GuideMenu;
import com.koole.higherRedStoneFun.ui.GuideSection;
import com.koole.higherRedStoneFun.ui.GuideTab;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 图鉴界面的点击处理，以及「图鉴说明书」物品的使用。
 *
 * <h2>三层导航</h2>
 *
 * <pre>
 *   主页面  section == null                     → 点大类图标进列表
 *   列表页  section != null, itemId == null     → 点物品进详情
 *   详情页  itemId != null                      → 点材料可继续跳转
 * </pre>
 *
 * <p>页面类型通过 {@link GuideMenu} 的导航状态判断，不需要额外维护
 * 「当前在哪一页」的映射表，也就不会有映射没清导致的泄漏。</p>
 */
public final class GuideMenuListener implements Listener {

    /** 图鉴说明书物品的 ID。 */
    public static final String GUIDE_ITEM_ID = "hrf_guide";

    /** 主页面大类图标所在槽位（必须与 {@link GuideMenu} 主页面布局一致）。 */
    private static final int[] MAIN_SLOTS = {19, 21, 23, 25};

    // ------------------------------------------------------------------
    // 点击
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuideMenu menu)) {
            return;
        }
        // 图鉴是只读界面：一律取消原版行为，避免展示物品被拿走
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getInventory().getSize()) {
            return;   // 点在玩家自己背包上
        }

        // ---- 固定按钮 ----
        if (raw == GuideMenu.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }
        if (raw == GuideMenu.SLOT_BACK) {
            goBack(player, menu);
            return;
        }

        // 「用途」翻页只在该标签下生效。
        // 这些槽位在别的标签里只是蓝玻璃背景，不加判断的话点空白也会翻页。
        if (raw == GuideMenu.SLOT_USAGE_PREV || raw == GuideMenu.SLOT_USAGE_NEXT) {
            if (menu.currentItemId() != null && menu.currentTab() == GuideTab.USAGE) {
                menu.turnUsagePage(player, raw == GuideMenu.SLOT_USAGE_NEXT);
            }
            return;
        }

        // 列表页翻页只在列表页生效（否则点详情页的空白背景会跳回列表）
        if (raw == GuideMenu.SLOT_PREV_PAGE || raw == GuideMenu.SLOT_NEXT_PAGE) {
            if (menu.currentItemId() == null) {
                changePage(player, menu, raw == GuideMenu.SLOT_NEXT_PAGE);
            }
            return;
        }

        // 配方翻页只在「合成方式」标签下生效。
        //
        // 注意这里不匹配时是「放行」而不是「return」——用途列表用到第 2~5 行
        // 整块，其中 28/34 恰好是配方翻页的槽位。如果在别的标签下也把点击
        // 吞掉，用途列表里那两项就点不动了。
        if ((raw == GuideMenu.SLOT_PREV_RECIPE || raw == GuideMenu.SLOT_NEXT_RECIPE)
                && menu.currentItemId() != null && menu.currentTab() == GuideTab.RECIPE) {
            changeRecipe(player, menu, raw == GuideMenu.SLOT_NEXT_RECIPE);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        // ---- 详情页：标签栏（第一行）----
        GuideTab tab = menu.tabAt(raw);
        if (tab != null) {
            menu.openItem(player, menu.currentSection(), menu.currentPage(),
                    menu.currentItemId(), tab, 0);
            return;
        }

        // ---- 按页面类型分发 ----

        // 主页面：点大类图标
        if (menu.currentSection() == null) {
            GuideSection target = sectionAt(raw);
            if (target != null) {
                menu.openList(player, target, 0);
            }
            return;
        }

        // 列表页：点物品进详情
        if (menu.currentItemId() == null) {
            String id = ItemRegistry.get().idOf(clicked);
            if (id != null) {
                menu.openItem(player, menu.currentSection(), menu.currentPage(), id, null, 0);
            }
            return;
        }

        // 详情页：点材料 / 产物可以继续追踪它的详情
        String id = ItemRegistry.get().idOf(clicked);
        if (id != null) {
            menu.openItem(player, menu.currentSection(), menu.currentPage(), id, null, 0);
        }
    }

    private GuideSection sectionAt(int rawSlot) {
        GuideSection[] sections = GuideSection.values();
        for (int i = 0; i < MAIN_SLOTS.length && i < sections.length; i++) {
            if (MAIN_SLOTS[i] == rawSlot) {
                return sections[i];
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 导航
    // ------------------------------------------------------------------

    /** 返回上一级：详情页 -> 列表页 -> 主页面。 */
    private void goBack(Player player, GuideMenu menu) {
        if (menu.currentItemId() != null) {
            GuideSection section = menu.currentSection();
            if (section == null) {
                menu.openMain(player);
            } else {
                menu.openList(player, section, menu.currentPage());
            }
        } else if (menu.currentSection() != null) {            menu.openMain(player);
        } else {
            player.closeInventory();
        }
    }

    /** 翻页（列表页专用）。 */
    private void changePage(Player player, GuideMenu menu, boolean forward) {
        GuideSection section = menu.currentSection();
        if (section == null) {
            return;
        }
        int target = menu.currentPage() + (forward ? 1 : -1);
        if (target < 0) {
            return;
        }
        menu.openList(player, section, target);
    }

    /** 切换配方（「合成方式」标签里一个物品有多条配方时使用）。 */
    private void changeRecipe(Player player, GuideMenu menu, boolean forward) {
        String itemId = menu.currentItemId();
        if (itemId == null) {
            return;
        }
        int target = menu.currentRecipeIndex() + (forward ? 1 : -1);
        if (target < 0) {
            return;
        }
        // 切换配方时保持停留在「合成方式」标签
        menu.openItem(player, menu.currentSection(), menu.currentPage(),
                itemId, GuideTab.RECIPE, target);
    }

    // ------------------------------------------------------------------
    // 图鉴说明书
    // ------------------------------------------------------------------

    /**
     * 手持「图鉴说明书」右键打开图鉴。
     *
     * <p>粘液科技的图鉴是一件随身物品，比记命令更符合直觉。
     * 这也是 {@code TOOLS} 分类里唯一的内容物——否则那个分类是空的。</p>
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onUseGuideItem(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!ItemRegistry.get().is(event.getItem(), GUIDE_ITEM_ID)) {
            return;
        }
        event.setCancelled(true);
        new GuideMenu().openMain(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof GuideMenu) {
            event.setCancelled(true);
        }
    }

    /** 判断物品是否是本插件的机器（便于外部使用）。 */
    public static boolean isMachineItem(ItemStack stack) {
        String id = ItemRegistry.get().idOf(stack);
        return id != null && MachineRegistry.get().isMachine(id);
    }

    /** 供自检使用：主页面大类槽位。 */
    public static List<Integer> mainSlots() {
        List<Integer> out = new ArrayList<>();
        for (int slot : MAIN_SLOTS) {
            out.add(slot);
        }
        return out;
    }

    /** 供自检使用：每个 ItemGroup 都能映射到一个大类。 */
    public static boolean allGroupsMapped() {
        for (ItemGroup group : ItemGroup.values()) {
            if (GuideSection.of(group) == null) {
                return false;
            }
        }
        return true;
    }
}
