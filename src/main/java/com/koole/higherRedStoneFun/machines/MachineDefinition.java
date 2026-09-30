package com.koole.higherRedStoneFun.machines;

import com.koole.higherRedStoneFun.core.ItemGroup;
import com.koole.higherRedStoneFun.energy.EnergyNode;
import com.koole.higherRedStoneFun.recipes.RecipeType;
import org.bukkit.Material;

/**
 * 机器定义（不可变）：描述一台机器的静态属性。
 *
 * <p>运行时状态全部放在 {@link MachineInstance} 中，因此同一份定义可以被任意多台
 * 机器实例共享，内存占用与原版「每个方块一个对象」相比更低。</p>
 */
public final class MachineDefinition {

    private final String id;
    private final String displayName;
    private final ItemGroup group;
    private final Material icon;
    private final RecipeType recipeType;
    private final EnergyNode.Role energyRole;
    private final long bufferCapacity;
    private final long throughput;
    private final int fuelTicksPerUnit;
    private final int tickPeriod;
    private final int inventorySize;
    private final MachineLogic logic;
    private final boolean glow;
    private final String[] lore;

    private MachineDefinition(Builder b) {
        this.id = b.id;
        this.displayName = b.displayName;
        this.group = b.group;
        this.icon = b.icon;
        this.recipeType = b.recipeType;
        this.energyRole = b.energyRole;
        this.bufferCapacity = b.bufferCapacity;
        this.throughput = b.throughput;
        this.fuelTicksPerUnit = b.fuelTicksPerUnit;
        this.tickPeriod = b.tickPeriod;
        this.inventorySize = b.inventorySize;
        this.logic = b.logic;
        this.glow = b.glow;
        this.lore = b.lore.toArray(new String[0]);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public ItemGroup group() {
        return group;
    }

    public Material icon() {
        return icon;
    }

    public RecipeType recipeType() {
        return recipeType;
    }

    public boolean hasRecipes() {
        return recipeType != null;
    }

    public EnergyNode.Role energyRole() {
        return energyRole;
    }

    public long bufferCapacity() {
        return bufferCapacity;
    }

    /** 发电机：每 tick 产能；用电机：每 tick 基础消耗。 */
    public long throughput() {
        return throughput;
    }

    /** 燃料机器：每单位燃料可运行多少 tick（0 表示不使用燃料）。 */
    public int fuelTicksPerUnit() {
        return fuelTicksPerUnit;
    }

    public boolean usesFuel() {
        return fuelTicksPerUnit > 0;
    }

    public int tickPeriod() {
        return tickPeriod;
    }

    public int inventorySize() {
        return inventorySize;
    }

    public MachineLogic logic() {
        return logic;
    }

    /** 是否是「高阶」机器（物品带附魔光效，便于视觉区分）。 */
    public boolean glow() {
        return glow;
    }

    public String[] lore() {
        return lore.clone();
    }

    public static Builder builder(String id, String displayName, ItemGroup group, Material icon) {
        return new Builder(id, displayName, group, icon);
    }

    public static final class Builder {

        private final String id;
        private final String displayName;
        private final ItemGroup group;
        private final Material icon;
        private RecipeType recipeType;
        private EnergyNode.Role energyRole;
        private long bufferCapacity;
        private long throughput;
        private int fuelTicksPerUnit;
        private int tickPeriod = 1;
        private int inventorySize = -1;
        private MachineLogic logic = MachineLogic.NONE;
        private boolean glow;
        private final java.util.List<String> lore = new java.util.ArrayList<>();

        private Builder(String id, String displayName, ItemGroup group, Material icon) {
            this.id = id;
            this.displayName = displayName;
            this.group = group;
            this.icon = icon;
        }

        public Builder recipeType(RecipeType type) {
            this.recipeType = type;
            if (this.inventorySize < 0) {
                this.inventorySize = type.inventorySize();
            }
            return this;
        }

        public Builder energyRole(EnergyNode.Role role) {
            this.energyRole = role;
            return this;
        }

        public Builder buffer(long capacity) {
            this.bufferCapacity = capacity;
            return this;
        }

        public Builder throughput(long value) {
            this.throughput = value;
            return this;
        }

        public Builder fuel(int ticksPerUnit) {
            this.fuelTicksPerUnit = ticksPerUnit;
            return this;
        }

        /** 调度间隔（tick）。机器越简单可以设得越长以省性能。 */
        public Builder period(int ticks) {
            this.tickPeriod = Math.max(1, ticks);
            return this;
        }

        public Builder inventorySize(int size) {
            this.inventorySize = size;
            return this;
        }

        public Builder logic(MachineLogic logic) {
            this.logic = logic;
            return this;
        }

        /** 标记为高阶机器：生成的物品会带附魔光效。 */
        public Builder glow(boolean value) {
            this.glow = value;
            return this;
        }

        public Builder lore(String... lines) {
            for (String line : lines) {
                this.lore.add(line);
            }
            return this;
        }

        /** 插入一行空 lore，用于视觉分组。 */
        public Builder blank() {
            this.lore.add("");
            return this;
        }

        public MachineDefinition build() {
            if (inventorySize < 0) {
                inventorySize = 27;
            }
            return new MachineDefinition(this);
        }
    }
}
