package com.koole.higherRedStoneFun.core;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * 物品图鉴分类（对应粘液科技里的 Category / ItemGroup）。
 *
 * <p>本插件保留「图鉴」作为玩家了解机器用途的入口，但不再强制研究解锁，
 * 而是用「研究点」作为可选加速手段（见设计文档中的优化说明）。</p>
 */
public enum ItemGroup {

    BASIC_MACHINES("基础机器", "<gray>入门级机械，无需电力即可运转", Material.COPPER_BLOCK),
    POWER_MACHINES("高级电力机器", "<red>以焦耳(J)为能量单位的高速产线", Material.REDSTONE_BLOCK),
    GENETICS("基因工程", "<green>作物与动物的遗传改造", Material.WHEAT_SEEDS),
    MATERIALS("材料与零件", "<yellow>各类中间产物与零件", Material.IRON_INGOT),
    TOOLS("工具与仪器", "<aqua>辅助玩家操作的器具", Material.DIAMOND_PICKAXE);

    private final String displayName;
    private final String description;
    private final Material icon;

    ItemGroup(String displayName, String description, Material icon) {
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public Material icon() {
        return icon;
    }

    /** 图鉴 GUI 中使用的图标物品。 */
    public ItemStack iconStack() {
        ItemStack stack = new ItemStack(icon);
        var meta = stack.getItemMeta();
        meta.displayName(Text.mm("<bold>" + displayName + "</bold>"));
        meta.lore(java.util.List.of(Text.mm(description)));
        stack.setItemMeta(meta);
        return stack;
    }
}
