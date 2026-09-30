package com.koole.higherRedStoneFun.items;

import com.koole.higherRedStoneFun.core.ItemGroup;
import com.koole.higherRedStoneFun.core.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个自定义物品的「定义」（不可变）。
 *
 * <p>它只描述物品长什么样、属于哪个分类，不包含任何游戏逻辑。
 * 逻辑由机器 / 工具 / 基因系统各自实现。</p>
 */
public final class HrfItem {

    private final String id;
    private final Material material;
    private final String name;
    private final List<String> lore;
    private final ItemGroup group;
    private final boolean glow;
    private final boolean hideTooltip;

    private HrfItem(Builder builder) {
        this.id = builder.id;
        this.material = builder.material;
        this.name = builder.name;
        this.lore = List.copyOf(builder.lore);
        this.group = builder.group;
        this.glow = builder.glow;
        this.hideTooltip = builder.hideTooltip;
    }

    public String id() {
        return id;
    }

    public Material material() {
        return material;
    }

    public String name() {
        return name;
    }

    public List<String> lore() {
        return lore;
    }

    public ItemGroup group() {
        return group;
    }

    public boolean glow() {
        return glow;
    }

    public boolean hideTooltip() {
        return hideTooltip;
    }

    /** 名称的纯文本形式（用于消息提示）。 */
    public String plainName() {
        return Text.plain(Text.mm(name));
    }

    public static Builder builder(String id, Material material, String name, ItemGroup group) {
        return new Builder(id, material, name, group);
    }

    public static final class Builder {

        private final String id;
        private final Material material;
        private final String name;
        private final ItemGroup group;
        private final List<String> lore = new ArrayList<>();
        private boolean glow;
        private boolean hideTooltip;

        private Builder(String id, Material material, String name, ItemGroup group) {
            this.id = id;
            this.material = material;
            this.name = name;
            this.group = group;
        }

        public Builder lore(String... lines) {
            for (String line : lines) {
                if (line != null && !line.isEmpty()) {
                    this.lore.add(line);
                }
            }
            return this;
        }

        public Builder lore(List<String> lines) {
            for (String line : lines) {
                if (line != null && !line.isEmpty()) {
                    this.lore.add(line);
                }
            }
            return this;
        }

        public Builder blank() {
            this.lore.add("");
            return this;
        }

        public Builder glow(boolean value) {
            this.glow = value;
            return this;
        }

        public Builder hideTooltip(boolean value) {
            this.hideTooltip = value;
            return this;
        }

        public HrfItem build() {
            return new HrfItem(this);
        }
    }

    /**
     * 生成一个未打标记的基础 ItemStack（不含 PDC）。
     * 标记由 {@link ItemRegistry} 负责写入。
     */
    ItemStack toStack(int amount) {
        ItemStack stack = new ItemStack(material, Math.max(1, Math.min(amount, material.getMaxStackSize())));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.mm(name));
        if (!lore.isEmpty()) {
            List<Component> loreComponents = new ArrayList<>(lore.size());
            for (String line : lore) {
                loreComponents.add(line.isEmpty() ? Component.empty() : Text.mm(line));
            }
            meta.lore(loreComponents);
        }
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (hideTooltip) {
            meta.setHideTooltip(true);
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
