package com.koole.higherRedStoneFun.machines;

import com.koole.higherRedStoneFun.energy.EnergyNetwork;
import com.koole.higherRedStoneFun.energy.EnergyNode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * 一台机器方块的运行时状态。
 *
 * <p>状态与定义分离，因此一台「电力研磨机」和一台「电力熔炉」共享同一份
 * 调度器代码，只有 {@link MachineDefinition} 不同。</p>
 */
public final class MachineInstance implements EnergyNode {

    private final Location location;
    private final MachineDefinition definition;

    /** 机器内部物品栏（长度 = definition.inventorySize()）。 */
    private ItemStack[] contents;

    /** 当前配方进度（tick）。 */
    private int progress;
    /** 当前配方需要的总 tick 数。 */
    private int totalTicks;
    /** 当前正在处理的配方 ID，用于存档恢复。 */
    private String activeRecipe;

    /** 剩余燃料 tick（燃料机器使用）。 */
    private int fuelTicks;

    /** 发电机的储能缓冲 / 电池的储能。 */
    private long energyStored;

    /** 用电机本 tick 是否在申请电力。 */
    private boolean requestingPower;

    /** 累计统计数据。 */
    private long lifetimeGenerated;
    private long lifetimeConsumed;
    private int completedOperations;

    /**
     * 机器专用计数字段。
     *
     * <p>用于存放不该污染通用模型的少量状态（例如核反应堆的堆芯温度与冷却余额）。
     * 它会随机器一起持久化，因此服务器重启后反应堆不会「自动冷却」。</p>
     */
    private final java.util.Map<String, Integer> counters = new java.util.HashMap<>();

    private UUID owner;
    private String ownerName;

    private EnergyNetwork network;
    private boolean dirty;

    public MachineInstance(Location location, MachineDefinition definition) {
        this.location = location.clone();
        this.definition = definition;
        this.contents = new ItemStack[definition.inventorySize()];
    }

    // ------------------------------------------------------------------
    // 基础访问
    // ------------------------------------------------------------------

    public Location location() {
        return location.clone();
    }

    public MachineDefinition definition() {
        return definition;
    }

    public String id() {
        return definition.id();
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName;
        markDirty();
    }

    public boolean isOwnedBy(UUID uuid) {
        return owner == null || owner.equals(uuid);
    }

    // ------------------------------------------------------------------
    // 物品栏
    // ------------------------------------------------------------------

    public ItemStack[] contents() {
        return contents;
    }

    public ItemStack getSlot(int slot) {
        if (slot < 0 || slot >= contents.length) {
            return null;
        }
        return contents[slot];
    }

    public void setSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= contents.length) {
            return;
        }
        contents[slot] = stack;
        markDirty();
    }

    public void setContents(ItemStack[] newContents) {
        this.contents = new ItemStack[definition.inventorySize()];
        for (int i = 0; i < Math.min(newContents.length, contents.length); i++) {
            contents[i] = newContents[i];
        }
        markDirty();
    }

    /** 检查机器内部是否还有物品（破坏时用于掉落）。 */
    public boolean hasItems() {
        for (ItemStack stack : contents) {
            if (stack != null && !stack.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 进度 / 燃料
    // ------------------------------------------------------------------

    public int progress() {
        return progress;
    }

    public int totalTicks() {
        return totalTicks;
    }

    public String activeRecipe() {
        return activeRecipe;
    }

    public void setActiveRecipe(String recipeId, int durationTicks) {
        this.activeRecipe = recipeId;
        this.totalTicks = Math.max(1, durationTicks);
        this.progress = 0;
        markDirty();
    }

    public void clearRecipe() {
        this.activeRecipe = null;
        this.totalTicks = 0;
        this.progress = 0;
        markDirty();
    }

    public void advance() {
        this.progress++;
    }

    public int fuelTicks() {
        return fuelTicks;
    }

    public void setFuelTicks(int ticks) {
        this.fuelTicks = Math.max(0, ticks);
        markDirty();
    }

    public void addFuelTicks(int ticks) {
        this.fuelTicks += ticks;
        markDirty();
    }

    public void consumeFuelTick() {
        if (fuelTicks > 0) {
            fuelTicks--;
        }
    }

    public boolean hasFuel() {
        return fuelTicks > 0;
    }

    public int completedOperations() {
        return completedOperations;
    }

    public void incrementCompleted() {
        completedOperations++;
        markDirty();
    }

    // ------------------------------------------------------------------
    // EnergyNode 实现
    // ------------------------------------------------------------------

    @Override
    public Role role() {
        return definition.energyRole() == null ? Role.CONSUMER : definition.energyRole();
    }

    /** 发电机：产量由 MachineLogic 决定。 */
    @Override
    public long generate() {
        return definition.logic().generate(this);
    }

    /** 用电机：需求量由 MachineLogic 决定。 */
    @Override
    public long demand() {
        return definition.logic().demand(this);
    }

    @Override
    public void receive(long joules) {
        charge(joules);
    }

    @Override
    public long charge(long joules) {
        long cap = definition.bufferCapacity();
        if (cap <= 0L) {
            return 0L;
        }
        long space = cap - energyStored;
        long accepted = Math.min(Math.max(0L, joules), space);
        energyStored += accepted;
        if (accepted > 0L) {
            markDirty();
        }
        return accepted;
    }

    @Override
    public long discharge(long joules) {
        long given = Math.min(Math.max(0L, joules), energyStored);
        energyStored -= given;
        if (given > 0L) {
            markDirty();
        }
        return given;
    }

    @Override
    public long stored() {
        return energyStored;
    }

    @Override
    public long capacity() {
        return definition.bufferCapacity();
    }

    @Override
    public void setNetwork(EnergyNetwork network) {
        this.network = network;
    }

    @Override
    public EnergyNetwork network() {
        return network;
    }

    // ------------------------------------------------------------------
    // 能量操作（供 MachineLogic 使用）
    // ------------------------------------------------------------------

    public long energyStored() {
        return energyStored;
    }

    public void setEnergyStored(long value) {
        this.energyStored = Math.max(0L, Math.min(value, definition.bufferCapacity()));
        markDirty();
    }

    /** 机器是否正在申请电力（有活要干且电量不足）。 */
    public boolean isRequestingPower() {
        return requestingPower;
    }

    /**
     * 设置本 tick 是否申请电力。
     *
     * <p>MachineLogic 在处理配方时调用：有活干就置 true，没活干置 false，
     * 网络会据此决定是否给它充电。</p>
     */
    public void requestPower(boolean value) {
        this.requestingPower = value;
    }

    /** 尝试从自身缓冲区扣除电量，电量不足返回 false 且不扣电。 */
    public boolean consumeEnergy(long joules) {
        if (joules <= 0L) {
            return true;
        }
        if (energyStored < joules) {
            return false;
        }
        energyStored -= joules;
        lifetimeConsumed += joules;
        markDirty();
        return true;
    }

    public long lifetimeGenerated() {
        return lifetimeGenerated;
    }

    public long lifetimeConsumed() {
        return lifetimeConsumed;
    }

    public void addGenerated(long joules) {
        this.lifetimeGenerated += joules;
    }

    public void addConsumed(long joules) {
        this.lifetimeConsumed += joules;
    }

    // ------------------------------------------------------------------
    // 机器专用计数
    // ------------------------------------------------------------------

    public int counter(String name, int defaultValue) {
        return counters.getOrDefault(name, defaultValue);
    }

    public void setCounter(String name, int value) {
        counters.put(name, value);
        markDirty();
    }

    public void removeCounter(String name) {
        if (counters.remove(name) != null) {
            markDirty();
        }
    }

    /** 供存档使用：返回计数字段的只读视图。 */
    public java.util.Map<String, Integer> counters() {
        return java.util.Collections.unmodifiableMap(counters);
    }

    /** 供存档恢复使用。 */
    public void putCounterRaw(String name, int value) {
        counters.put(name, value);
    }

    // ------------------------------------------------------------------
    // 脏标记（用于节流存档）
    // ------------------------------------------------------------------

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    /** 把持久化字段一次性读入。 */
    public void restore(long energy, int fuel, int progress, int total, String recipe,
                        long generated, long consumed, int completed, String ownerId, String ownerLabel) {
        this.energyStored = Math.max(0L, Math.min(energy, definition.bufferCapacity()));
        this.fuelTicks = Math.max(0, fuel);
        this.progress = Math.max(0, progress);
        this.totalTicks = Math.max(0, total);
        this.activeRecipe = recipe;
        this.lifetimeGenerated = Math.max(0L, generated);
        this.lifetimeConsumed = Math.max(0L, consumed);
        this.completedOperations = Math.max(0, completed);
        if (ownerId != null && !ownerId.isEmpty()) {
            try {
                this.owner = UUID.fromString(ownerId);
            } catch (IllegalArgumentException ignored) {
                this.owner = null;
            }
        }
        this.ownerName = ownerLabel;
        this.dirty = false;
    }
}
