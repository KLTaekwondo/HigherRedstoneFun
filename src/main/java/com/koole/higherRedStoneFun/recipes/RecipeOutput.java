package com.koole.higherRedStoneFun.recipes;

import org.bukkit.inventory.ItemStack;

/** 一个带概率的配方产物。 */
public final class RecipeOutput {

    private final ItemStack stack;
    private final double chance;

    private RecipeOutput(ItemStack stack, double chance) {
        this.stack = stack;
        this.chance = Math.max(0.0D, Math.min(1.0D, chance));
    }

    /** 100% 产出的物品。 */
    public static RecipeOutput of(ItemStack stack) {
        return new RecipeOutput(stack, 1.0D);
    }

    /** 按概率产出的副产物。 */
    public static RecipeOutput chance(ItemStack stack, double chance) {
        return new RecipeOutput(stack, chance);
    }

    public ItemStack stack() {
        return stack.clone();
    }

    public double chance() {
        return chance;
    }
}
