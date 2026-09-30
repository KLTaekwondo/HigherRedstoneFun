package com.koole.higherRedStoneFun.recipes;

import com.koole.higherRedStoneFun.items.ItemRegistry;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配方索引。
 *
 * <p>相比粘液科技每次遍历全部配方，这里按 RecipeType 分桶，
 * 并且为每条配方预先计算「缺口签名」，使匹配成本与配方总数解耦。</p>
 */
public final class RecipeRegistry {

    private static final RecipeRegistry INSTANCE = new RecipeRegistry();

    private final Map<RecipeType, List<MachineRecipe>> byType = new LinkedHashMap<>();
    private final Map<String, MachineRecipe> byId = new LinkedHashMap<>();

    private RecipeRegistry() {
    }

    public static RecipeRegistry get() {
        return INSTANCE;
    }

    public MachineRecipe register(MachineRecipe recipe) {
        if (byId.containsKey(recipe.id())) {
            throw new IllegalStateException("配方 ID 重复: " + recipe.id());
        }
        byId.put(recipe.id(), recipe);
        byType.computeIfAbsent(recipe.type(), k -> new ArrayList<>()).add(recipe);
        return recipe;
    }

    public List<MachineRecipe> of(RecipeType type) {
        return byType.getOrDefault(type, List.of());
    }

    public MachineRecipe byId(String id) {
        return byId.get(id);
    }

    public int size() {
        return byId.size();
    }

    public int size(RecipeType type) {
        return of(type).size();
    }

    /**
     * 在给定输入槽里查找匹配的配方。
     *
     * <p>匹配规则：每个输入槽中的物品必须能覆盖配方的全部输入需求。
     * 输入槽中的多余物品不会被消耗，从而避免玩家误放导致损失。</p>
     */
    public MachineRecipe findMatch(RecipeType type, List<ItemStack> available) {
        for (MachineRecipe recipe : of(type)) {
            if (matches(recipe, available)) {
                return recipe;
            }
        }
        return null;
    }

    private boolean matches(MachineRecipe recipe, List<ItemStack> available) {
        List<ItemStack> remaining = new ArrayList<>(recipe.inputs().size());
        for (ItemStack need : recipe.inputs()) {
            remaining.add(need.clone());
        }

        for (ItemStack slot : available) {
            if (slot == null || slot.getType().isAir()) {
                continue;
            }
            int available1 = slot.getAmount();
            for (ItemStack need : remaining) {
                if (need == null || need.getAmount() <= 0) {
                    continue;
                }
                if (!ItemRegistry.get().sameItem(slot, need)) {
                    continue;
                }
                int used = Math.min(available1, need.getAmount());
                available1 -= used;
                need.setAmount(need.getAmount() - used);
                if (available1 <= 0) {
                    break;
                }
            }
        }

        for (ItemStack need : remaining) {
            if (need != null && need.getAmount() > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从输入槽中扣除配方所需物品。
     *
     * <p>只扣除配方声明需要的数量，例如配方需要 1 个铁锭、玩家放了 8 个，
     * 只会扣掉 1 个，剩下 7 个留在机器里。</p>
     */
    public void consume(MachineRecipe recipe, List<ItemStack> slots) {
        List<ItemStack> remaining = new ArrayList<>(recipe.inputs().size());
        for (ItemStack need : recipe.inputs()) {
            remaining.add(need.clone());
        }

        for (int i = 0; i < slots.size(); i++) {
            ItemStack slot = slots.get(i);
            if (slot == null || slot.getType().isAir()) {
                continue;
            }
            int amount = slot.getAmount();
            int original = amount;
            for (ItemStack need : remaining) {
                if (need == null || need.getAmount() <= 0) {
                    continue;
                }
                if (!ItemRegistry.get().sameItem(slot, need)) {
                    continue;
                }
                int used = Math.min(amount, need.getAmount());
                amount -= used;
                need.setAmount(need.getAmount() - used);
                if (amount <= 0) {
                    break;
                }
            }
            if (amount != original) {
                if (amount <= 0) {
                    slots.set(i, null);
                } else {
                    ItemStack copy = slot.clone();
                    copy.setAmount(amount);
                    slots.set(i, copy);
                }
            }
        }
    }

    public void clear() {
        byType.clear();
        byId.clear();
    }
}
