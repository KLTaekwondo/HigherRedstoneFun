package com.koole.higherRedStoneFun.machines;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 机器实例管理器：负责机器方块的运行时索引、tick 调度与区块生命周期。
 *
 * <p>性能设计：机器按「区块」分桶存放，tick 时只遍历已加载区块中的机器，
 * 因此即使服务器里存在十万台机器，只要它们不在玩家附近就不会产生开销。</p>
 */
public final class MachineManager {

    private final Map<Location, MachineInstance> machines = new HashMap<>();
    /** 区块键 -> 该区块内的机器，用于卸载时快速清理。 */
    private final Map<Long, List<MachineInstance>> byChunk = new HashMap<>();
    /** 当前 tick 计数器，用于按周期调度。 */
    private long tickCounter;

    private static Location key(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private static long chunkKey(World world, int chunkX, int chunkZ) {
        // 世界名字哈希 + 区块坐标打包，避免额外对象分配
        long h = world.getUID().getMostSignificantBits() ^ world.getUID().getLeastSignificantBits();
        return (h * 31L + chunkX) * 31L + chunkZ;
    }

    // ------------------------------------------------------------------
    // 增删查
    // ------------------------------------------------------------------

    public void add(MachineInstance machine) {
        Location k = key(machine.location());
        machines.put(k, machine);
        long ck = chunkKey(machine.location().getWorld(),
                machine.location().getBlockX() >> 4,
                machine.location().getBlockZ() >> 4);
        byChunk.computeIfAbsent(ck, x -> new ArrayList<>(4)).add(machine);
    }

    public MachineInstance get(Location loc) {
        return machines.get(key(loc));
    }

    public MachineInstance remove(Location loc) {
        MachineInstance removed = machines.remove(key(loc));
        if (removed != null) {
            long ck = chunkKey(loc.getWorld(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
            List<MachineInstance> list = byChunk.get(ck);
            if (list != null) {
                list.remove(removed);
                if (list.isEmpty()) {
                    byChunk.remove(ck);
                }
            }
        }
        return removed;
    }

    public boolean isMachine(Location loc) {
        return machines.containsKey(key(loc));
    }

    public Collection<MachineInstance> all() {
        return machines.values();
    }

    public int size() {
        return machines.size();
    }

    /** 某个世界内的机器数量（用于 /hrf 命令统计）。 */
    public int countIn(World world) {
        int count = 0;
        for (MachineInstance machine : machines.values()) {
            if (machine.location().getWorld() == world) {
                count++;
            }
        }
        return count;
    }

    public void clear() {
        machines.clear();
        byChunk.clear();
    }

    // ------------------------------------------------------------------
    // 调度
    // ------------------------------------------------------------------

    /**
     * 执行一个 tick。
     *
     * <p>性能关键点：这里遍历的是「包含机器的区块」而不是「全部机器」。
     * 服务器里存有十万台机器时，只要它们分散在一万个区块、且当前只有几百个
     * 区块被加载，实际开销就只与已加载区块有关，而不是与机器总数成正比。</p>
     *
     * <p>未加载区块里的机器会被跳过，等玩家靠近后自动继续工作。</p>
     */
    public void tick() {
        tickCounter++;
        for (List<MachineInstance> chunkMachines : byChunk.values()) {
            if (chunkMachines.isEmpty()) {
                continue;
            }
            // 用第一台机器判断该区块是否加载（同区块内所有机器共享加载状态）
            Location probe = chunkMachines.get(0).location();
            World world = probe.getWorld();
            if (world == null || !world.isChunkLoaded(probe.getBlockX() >> 4, probe.getBlockZ() >> 4)) {
                continue;
            }
            // 复制一份遍历，避免机器在 tick 中卸载区块导致并发修改
            MachineInstance[] snapshot = chunkMachines.toArray(new MachineInstance[0]);
            for (MachineInstance machine : snapshot) {
                MachineDefinition definition = machine.definition();
                if (definition.tickPeriod() > 1 && (tickCounter % definition.tickPeriod()) != 0) {
                    continue;
                }
                try {
                    definition.logic().tick(machine);
                } catch (Exception ex) {
                    // 单台机器出错不应该影响其它机器
                    org.bukkit.Bukkit.getLogger().warning(
                            "[HRF] 机器 " + machine.id() + " 在 " + machine.location() + " tick 时出错: " + ex);
                }
            }
        }
    }

    public long tickCounter() {
        return tickCounter;
    }

    /** 区块卸载时调用：把该区块的机器标记为需要保存，并从内存索引移除。 */
    public List<MachineInstance> detachChunk(World world, int chunkX, int chunkZ) {
        long ck = chunkKey(world, chunkX, chunkZ);
        List<MachineInstance> removed = byChunk.remove(ck);
        if (removed == null) {
            return List.of();
        }
        for (MachineInstance machine : removed) {
            machines.remove(key(machine.location()));
        }
        return removed;
    }

    /** 统计当前已加载（会被 tick）的机器数量。 */
    public int loadedCount() {
        int count = 0;
        for (List<MachineInstance> chunkMachines : byChunk.values()) {
            if (chunkMachines.isEmpty()) {
                continue;
            }
            Location probe = chunkMachines.get(0).location();
            World world = probe.getWorld();
            if (world != null && world.isChunkLoaded(probe.getBlockX() >> 4, probe.getBlockZ() >> 4)) {
                count += chunkMachines.size();
            }
        }
        return count;
    }

    /** 包含机器的区块数量。 */
    public int chunkCount() {
        return byChunk.size();
    }
}
