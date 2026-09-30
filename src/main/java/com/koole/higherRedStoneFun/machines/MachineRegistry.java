package com.koole.higherRedStoneFun.machines;

import com.koole.higherRedStoneFun.core.ItemGroup;
import com.koole.higherRedStoneFun.items.ItemRegistry;
import com.koole.higherRedStoneFun.items.HrfItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 机器定义注册表。
 *
 * <p>注册一台机器 = 注册一个物品 + 一个定义 + 一个逻辑。三者通过相同的 ID 关联。</p>
 */
public final class MachineRegistry {

    private static final MachineRegistry INSTANCE = new MachineRegistry();

    private final Map<String, MachineDefinition> definitions = new LinkedHashMap<>();

    private MachineRegistry() {
    }

    public static MachineRegistry get() {
        return INSTANCE;
    }

    public MachineDefinition register(MachineDefinition definition) {
        if (definitions.containsKey(definition.id())) {
            throw new IllegalStateException("机器 ID 重复: " + definition.id());
        }
        definitions.put(definition.id(), definition);

        // 同时把它注册成一个可获得的自定义物品
        if (!ItemRegistry.get().exists(definition.id())) {
            HrfItem.Builder builder = HrfItem.builder(
                            definition.id(), definition.icon(), "<white>" + definition.displayName(), definition.group())
                    .lore("<dark_gray>机器方块")
                    .blank();

            for (String line : definition.lore()) {
                builder.lore(line);
            }

            // 自动附加机器通用信息
            if (definition.energyRole() != null) {
                switch (definition.energyRole()) {
                    case GENERATOR -> builder.lore("<gray>产能: <red>" + definition.throughput() + " J/t");
                    case CONSUMER -> builder.lore("<gray>耗电: <red>" + definition.throughput() + " J/t");
                    case STORAGE -> builder.lore("<gray>储电: <red>" + definition.bufferCapacity() + " J");
                }
            }
            if (definition.usesFuel()) {
                builder.lore("<gray>燃料: <gold>可燃物</gold> <dark_gray>(" + definition.fuelTicksPerUnit() + " tick/单位)");
            }
            if (definition.hasRecipes()) {
                builder.lore("<gray>配方类型: <yellow>" + definition.recipeType().displayName());
            }
            builder.blank().lore("<yellow>右键放置 / 打开界面");

            if (definition.glow()) {
                builder.glow(true);
            }

            ItemRegistry.get().register(builder.build());
        }
        return definition;
    }

    public MachineDefinition get(String id) {
        return definitions.get(id);
    }

    public boolean isMachine(String id) {
        return definitions.containsKey(id);
    }

    public Collection<MachineDefinition> all() {
        return definitions.values();
    }

    public List<MachineDefinition> byGroup(ItemGroup group) {
        List<MachineDefinition> out = new ArrayList<>();
        for (MachineDefinition definition : definitions.values()) {
            if (definition.group() == group) {
                out.add(definition);
            }
        }
        return out;
    }

    public int size() {
        return definitions.size();
    }

    /** 创建该机器的物品形态。 */
    public ItemStack createItem(String id, int amount) {
        return ItemRegistry.get().create(id, amount);
    }

    public void clear() {
        definitions.clear();
    }

    /** 判断一个 Material 是否被用作机器方块的外观（用于防误破坏提示）。 */
    public boolean isMachineMaterial(Material material) {
        for (MachineDefinition definition : definitions.values()) {
            if (definition.icon() == material) {
                return true;
            }
        }
        return false;
    }
}
