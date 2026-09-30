package com.koole.higherRedStoneFun.listeners;

import com.koole.higherRedStoneFun.core.ItemGroup;
import com.koole.higherRedStoneFun.items.ItemRegistry;
import com.koole.higherRedStoneFun.machines.MachineRegistry;
import com.koole.higherRedStoneFun.ui.GuideMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** 图鉴界面的点击处理。 */
public final class GuideMenuListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuideMenu menu)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        // 底行：返回 / 信息
        if (event.getRawSlot() == 49) {
            // 信息物品，不做动作
            return;
        }

        // 第一列：切换分类
        int raw = event.getRawSlot();
        if (raw >= 0 && raw < 45 && raw % 9 == 0) {
            List<ItemGroup> groups = List.of(ItemGroup.values());
            int index = raw / 9;
            if (index < groups.size()) {
                menu.open(player, groups.get(index));
            }
            return;
        }

        // 返回按钮
        if (clicked.getType() == org.bukkit.Material.ARROW) {
            menu.open(player, ItemGroup.BASIC_MACHINES);
            return;
        }

        // 其他：查看配方
        String id = ItemRegistry.get().idOf(clicked);
        if (id != null) {
            menu.openRecipe(player, id);
        }
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
}
