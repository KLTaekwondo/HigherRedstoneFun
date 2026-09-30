package com.koole.higherRedStoneFun.ui;

import com.koole.higherRedStoneFun.core.ItemGroup;
import com.koole.higherRedStoneFun.core.Text;
import com.koole.higherRedStoneFun.items.HrfItem;
import com.koole.higherRedStoneFun.items.ItemRegistry;
import com.koole.higherRedStoneFun.machines.MachineDefinition;
import com.koole.higherRedStoneFun.machines.MachineRegistry;
import com.koole.higherRedStoneFun.recipes.MachineRecipe;
import com.koole.higherRedStoneFun.recipes.RecipeOutput;
import com.koole.higherRedStoneFun.recipes.RecipeRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 图鉴界面。
 *
 * <p>对应粘液科技的 Slimefun Guide。这里做了两点体验优化：</p>
 *
 * <ol>
 *   <li><b>不需要研究解锁</b>：所有条目默认可见，玩家可以先规划科技树，
 *       避免「不知道该研究什么」的迷茫期。</li>
 *   <li><b>内置配方页</b>：点开机器直接看到它的全部配方与耗电，
 *       不用去翻 Wiki。</li>
 * </ol>
 */
public final class GuideMenu implements InventoryHolder {

    private Inventory inventory;

    /** 打开图鉴首页（分类列表）。 */
    public void open(Player player, ItemGroup initialGroup) {
        openGroup(player, initialGroup);
    }

    // ------------------------------------------------------------------
    // 分类页
    // ------------------------------------------------------------------

    private void openGroup(Player player, ItemGroup group) {
        inventory = Bukkit.createInventory(this, 54,
                Text.mm("<dark_gray>图鉴 <gray>» <white>" + group.displayName()));
        fillBorder();

        // 左侧：分类切换
        int slot = 0;
        for (ItemGroup candidate : ItemGroup.values()) {
            ItemStack icon = new ItemStack(candidate.icon());
            ItemMeta meta = icon.getItemMeta();
            boolean selected = candidate == group;
            meta.displayName(Text.mm((selected ? "<green>▶ " : "<gray>") + candidate.displayName()));
            meta.lore(List.of(
                    Text.mm(candidate.description()),
                    Text.mm("<dark_gray>物品数: " + ItemRegistry.get().byGroup(candidate).size())
            ));
            if (selected) {
                meta.setEnchantmentGlintOverride(true);
            }
            icon.setItemMeta(meta);
            inventory.setItem(slot, icon);
            slot += 9;
            if (slot > 45) {
                break;
            }
        }
        // 放回原位：第一列 slot 0,9,18,27,36
        inventory.setItem(0, null);
        int index = 0;
        for (ItemGroup candidate : ItemGroup.values()) {
            ItemStack icon = new ItemStack(candidate.icon());
            ItemMeta meta = icon.getItemMeta();
            boolean selected = candidate == group;
            meta.displayName(Text.mm((selected ? "<green>▶ " : "<gray>") + candidate.displayName()));
            meta.lore(List.of(
                    Text.mm(candidate.description()),
                    Text.mm("<dark_gray>物品数: " + ItemRegistry.get().byGroup(candidate).size()),
                    Text.mm(selected ? "<green>当前分类" : "<yellow>点击切换")
            ));
            if (selected) {
                meta.setEnchantmentGlintOverride(true);
            }
            icon.setItemMeta(meta);
            inventory.setItem(index * 9, icon);
            index++;
        }

        // 主体：该分类下的全部物品
        List<HrfItem> items = ItemRegistry.get().byGroup(group);
        int[] contentSlots = contentSlots();
        for (int i = 0; i < items.size() && i < contentSlots.length; i++) {
            inventory.setItem(contentSlots[i], buildEntry(items.get(i)));
        }

        // 底部信息栏
        inventory.setItem(49, infoItem());

        player.openInventory(inventory);
    }

    private ItemStack buildEntry(HrfItem item) {
        ItemStack stack = ItemRegistry.get().create(item.id(), 1);
        ItemMeta meta = stack.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Text.mm("<dark_gray>ID: " + item.id()));
        lore.add(Text.mm(""));
        for (String line : item.lore()) {
            lore.add(line.isEmpty() ? net.kyori.adventure.text.Component.empty() : Text.mm(line));
        }

        MachineDefinition definition = MachineRegistry.get().get(item.id());
        if (definition != null && definition.hasRecipes()) {
            int count = RecipeRegistry.get().size(definition.recipeType());
            lore.add(Text.mm(""));
            lore.add(Text.mm("<yellow>配方面板: <white>" + definition.recipeType().displayName()
                    + " <dark_gray>(" + count + " 条)"));
        }
        lore.add(Text.mm(""));
        lore.add(Text.mm("<yellow>左键查看配方"));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    // ------------------------------------------------------------------
    // 配方页
    // ------------------------------------------------------------------

    /** 打开某个物品的配方详情。 */
    public void openRecipe(Player player, String itemId) {
        HrfItem item = ItemRegistry.get().get(itemId);
        MachineDefinition definition = MachineRegistry.get().get(itemId);

        String title = item == null ? itemId : item.plainName();
        inventory = Bukkit.createInventory(this, 54, Text.mm("<dark_gray>配方 <gray>» <white>" + title));
        fillBorder();

        // 顶部：目标物品
        ItemStack header = ItemRegistry.get().create(itemId, 1);
        inventory.setItem(4, header);

        List<MachineRecipe> recipes;
        if (definition != null && definition.hasRecipes()) {
            recipes = RecipeRegistry.get().of(definition.recipeType());
        } else {
            recipes = findRecipesProducing(itemId);
        }

        if (recipes.isEmpty()) {
            inventory.setItem(22, simple(Material.BARRIER, "<red>没有找到配方",
                    "<gray>该物品可能只能通过其他方式获得"));
        } else {
            int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
            for (int i = 0; i < recipes.size() && i < slots.length; i++) {
                inventory.setItem(slots[i], recipeIcon(i + 1, recipes.get(i)));
            }
            if (recipes.size() > slots.length) {
                inventory.setItem(40, simple(Material.PAPER,
                        "<yellow>还有 " + (recipes.size() - slots.length) + " 条配方",
                        "<gray>使用 <white>/hrf recipes "
                                + (definition != null ? definition.recipeType().name().toLowerCase(Locale.ROOT) : "")
                                + "</white> 查看全部"));
            }
        }

        inventory.setItem(49, backItem());
        player.openInventory(inventory);
    }

    /** 反查：哪些配方会产出这个物品。 */
    private List<MachineRecipe> findRecipesProducing(String itemId) {
        List<MachineRecipe> out = new ArrayList<>();
        for (com.koole.higherRedStoneFun.recipes.RecipeType type
                : com.koole.higherRedStoneFun.recipes.RecipeType.ALL) {
            for (MachineRecipe recipe : RecipeRegistry.get().of(type)) {
                for (RecipeOutput output : recipe.outputs()) {
                    if (ItemRegistry.get().is(output.stack(), itemId)) {
                        out.add(recipe);
                        break;
                    }
                }
            }
        }
        return out;
    }

    private ItemStack recipeIcon(int index, MachineRecipe recipe) {
        ItemStack stack = new ItemStack(Material.PAPER, Math.min(64, index));
        ItemMeta meta = stack.getItemMeta();
        RecipeOutput first = recipe.outputs().isEmpty() ? null : recipe.outputs().get(0);
        String name = first == null ? "空配方" : describe(first.stack());
        meta.displayName(Text.mm("<yellow>配方 #" + index + " <dark_gray>» <white>" + name));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Text.mm("<gray>机器类型: <white>" + recipe.type().displayName()));
        lore.add(Text.mm(""));
        lore.add(Text.mm("<yellow>需要材料:"));
        for (ItemStack input : recipe.inputs()) {
            lore.add(Text.mm("<gray> - <white>" + describe(input)));
        }
        lore.add(Text.mm(""));
        lore.add(Text.mm("<yellow>产出:"));
        for (RecipeOutput output : recipe.outputs()) {
            String chance = output.chance() < 1.0D
                    ? " <dark_gray>(" + (int) (output.chance() * 100) + "% 概率)" : "";
            lore.add(Text.mm("<gray> - <white>" + describe(output.stack()) + chance));
        }
        lore.add(Text.mm(""));
        lore.add(Text.mm("<gray>耗时: <white>" + String.format("%.1f", recipe.durationTicks() / 20.0D) + " 秒"));
        if (recipe.type().electric()) {
            lore.add(Text.mm("<gray>耗电: <red>" + Text.number(recipe.energyCost()) + " J"));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static String describe(ItemStack stack) {
        String id = ItemRegistry.get().idOf(stack);
        String name;
        if (id != null) {
            HrfItem item = ItemRegistry.get().get(id);
            name = item == null ? id : item.plainName();
        } else {
            name = stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
        return name + (stack.getAmount() > 1 ? " x" + stack.getAmount() : "");
    }

    // ------------------------------------------------------------------
    // 通用
    // ------------------------------------------------------------------

    private void fillBorder() {
        ItemStack filler = simple(Material.GRAY_STAINED_GLASS_PANE, " ", "");
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, null);
            }
        }
        // 只铺底行作为视觉分隔
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }
        inventory.setItem(49, infoItem());
    }

    private ItemStack infoItem() {
        return simple(Material.BOOK, "<gold>HigherRedStoneFun 图鉴",
                "<gray>基础机器 · 高级电力机器 · 基因工程",
                "",
                "<yellow>左键 <gray>物品查看配方",
                "<yellow>点击左侧分类 <gray>切换模块",
                "",
                "<dark_gray>导线: 铜块 / 避雷针 / 末地烛");
    }

    private ItemStack backItem() {
        return simple(Material.ARROW, "<yellow>返回图鉴",
                "<gray>点击回到分类列表");
    }

    private static ItemStack simple(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.mm(name));
        if (lore.length > 0) {
            List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(line.isEmpty() ? net.kyori.adventure.text.Component.empty() : Text.mm(line));
            }
            meta.lore(lines);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static int[] contentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 0; row < 5; row++) {
            for (int col = 1; col < 9; col++) {
                slots.add(row * 9 + col);
            }
        }
        int[] out = new int[slots.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = slots.get(i);
        }
        return out;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
