package com.koole.higherRedStoneFun.ui;

import com.koole.higherRedStoneFun.core.ItemGroup;
import org.bukkit.Material;

import java.util.List;

/**
 * 图鉴的「大类」——主页面上显示的那一层。
 *
 * <h2>为什么单独抽一层，而不直接改 ItemGroup</h2>
 *
 * <p>{@link ItemGroup} 是<b>物品的内部归类</b>，除了图鉴之外还被别处使用：
 * 机器界面的标题会显示它（{@code [基础机器] 增强工作台}）、
 * 注册物品时也会写进去。如果为了图鉴的显示需要把「基础机器」和
 * 「高级电力机器」合并成一个枚举值，那些地方就会一并丢失区分度。</p>
 *
 * <p>所以这里只加一层<b>显示分组</b>：一个大类可以包含多个
 * {@link ItemGroup}，物品自身的归类保持不变。</p>
 *
 * <pre>
 *   主页面            列表页                    详情页
 *   ┌──────────┐     ┌────────────────┐      ┌──────────────┐
 *   │ 机器      │ ──▶ │ 研磨机 压制机…  │ ──▶ │ 3x3 配方网格  │
 *   │ 基因工程  │     │ (基础+高级都在这)│      │ + 产物 + 信息 │
 *   │ 材料与零件│     └────────────────┘      └──────────────┘
 *   │ 工具与仪器│
 *   └──────────┘
 * </pre>
 */
public enum GuideSection {

    MACHINES("机器", "<gray>基础机器与高级电力机器",
            Material.COPPER_BLOCK, List.of(ItemGroup.BASIC_MACHINES, ItemGroup.POWER_MACHINES)),

    GENETICS("基因工程", "<green>作物与动物的遗传改造",
            Material.WHEAT_SEEDS, List.of(ItemGroup.GENETICS)),

    MATERIALS("材料与零件", "<yellow>各类中间产物与零件",
            Material.IRON_INGOT, List.of(ItemGroup.MATERIALS)),

    TOOLS("工具与仪器", "<aqua>辅助玩家操作的器具",
            Material.DIAMOND_PICKAXE, List.of(ItemGroup.TOOLS));

    private final String displayName;
    private final String description;
    private final Material icon;
    private final List<ItemGroup> groups;

    GuideSection(String displayName, String description, Material icon, List<ItemGroup> groups) {
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.groups = groups;
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

    public List<ItemGroup> groups() {
        return groups;
    }

    /** 该大类是否包含某个内部归类。 */
    public boolean contains(ItemGroup group) {
        return groups.contains(group);
    }

    /** 从内部归类反查它属于哪个大类，找不到时归入材料类。 */
    public static GuideSection of(ItemGroup group) {
        for (GuideSection section : values()) {
            if (section.contains(group)) {
                return section;
            }
        }
        return MATERIALS;
    }
}
