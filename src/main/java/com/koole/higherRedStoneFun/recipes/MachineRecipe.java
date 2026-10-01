package com.koole.higherRedStoneFun.recipes;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条机器配方。
 *
 * <p>配方只描述「消耗什么、产出什么、需要多少能量、需要多少 tick」，
 * 具体执行由机器负责，因此同一份配方可以被燃料机器和红石流能机器共用。</p>
 */
public final class MachineRecipe {

    private final String id;
    private final RecipeType type;
    private final List<ItemStack> inputs;
    private final List<RecipeOutput> outputs;
    private final int energyCost;
    private final int durationTicks;

    private MachineRecipe(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.inputs = List.copyOf(builder.inputs);
        this.outputs = List.copyOf(builder.outputs);
        this.energyCost = builder.energyCost;
        this.durationTicks = builder.durationTicks;
    }

    public String id() {
        return id;
    }

    public RecipeType type() {
        return type;
    }

    public List<ItemStack> inputs() {
        List<ItemStack> copy = new ArrayList<>(inputs.size());
        for (ItemStack stack : inputs) {
            copy.add(stack.clone());
        }
        return copy;
    }

    public List<RecipeOutput> outputs() {
        return List.copyOf(outputs);
    }

    /** 消耗的 HRE 数（燃料机器会忽略此值）。 */
    public int energyCost() {
        return energyCost;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public static Builder builder(String id, RecipeType type) {
        return new Builder(id, type);
    }

    public static final class Builder {

        private final String id;
        private final RecipeType type;
        private final List<ItemStack> inputs = new ArrayList<>();
        private final List<RecipeOutput> outputs = new ArrayList<>();
        private int energyCost = 0;
        private int durationTicks = 100;

        private Builder(String id, RecipeType type) {
            this.id = id;
            this.type = type;
        }

        public Builder input(ItemStack stack) {
            this.inputs.add(stack.clone());
            return this;
        }

        public Builder output(ItemStack stack) {
            this.outputs.add(RecipeOutput.of(stack));
            return this;
        }

        public Builder output(ItemStack stack, double chance) {
            this.outputs.add(RecipeOutput.chance(stack, chance));
            return this;
        }

        public Builder energy(int hre) {
            this.energyCost = hre;
            return this;
        }

        public Builder ticks(int ticks) {
            this.durationTicks = Math.max(1, ticks);
            return this;
        }

        public MachineRecipe build() {
            return new MachineRecipe(this);
        }
    }
}
