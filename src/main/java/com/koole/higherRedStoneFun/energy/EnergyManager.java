package com.koole.higherRedStoneFun.energy;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 能源网络管理器：负责节点的注册、网络的建立/合并/拆除，以及每个 tick 的调度。
 *
 * <p>关键设计：<b>导线方块不是节点，只是通道</b>。因此放置一万根导线不会增加
 * 调度开销，只有真正的机器才进入 tick 循环。</p>
 *
 * <p>网络在「有方块变化」时才重建（脏标记），稳态下每 tick 只做算术运算。</p>
 */
public final class EnergyManager {

    private final Map<Location, EnergyNode> nodes = new HashMap<>();
    private final Map<Location, EnergyNetwork> blockOwner = new HashMap<>();
    private final List<EnergyNetwork> networks = new ArrayList<>();

    private static Location key(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    // ------------------------------------------------------------------
    // 节点注册
    // ------------------------------------------------------------------

    /**
     * 注册一台接入电网的机器。
     *
     * <p>注册时会立刻为它建立（或加入）一个网络，这样即使服务器重启、
     * 播放器还没靠近，机器也已经在网络列表中等待被调度。</p>
     */
    public void register(EnergyNode node) {
        Location k = key(node.location());
        nodes.put(k, node);
        getOrCreate(k).markDirty();
        onBlockChanged(k);
    }

    public void unregister(Location loc) {
        Location k = key(loc);
        EnergyNode removed = nodes.remove(k);
        if (removed != null) {
            removed.setNetwork(null);
        }
        blockOwner.remove(k);
        // 机器消失后，原网络需要重建（可能断裂成两个网络）
        markAffectedDirty(k);
    }

    public EnergyNode nodeAt(Location loc) {
        return nodes.get(key(loc));
    }

    public boolean isRegistered(Location loc) {
        return nodes.containsKey(key(loc));
    }

    // ------------------------------------------------------------------
    // 网络
    // ------------------------------------------------------------------

    /** 取（或创建并重建）某个位置所属的网络。 */
    public EnergyNetwork networkAt(Location loc) {
        Location k = key(loc);
        EnergyNetwork existing = blockOwner.get(k);
        if (existing != null && !existing.isDirty()) {
            return existing;
        }
        EnergyNetwork network = getOrCreate(k);
        rebuild(network);
        return network;
    }

    private EnergyNetwork getOrCreate(Location k) {
        EnergyNetwork existing = blockOwner.get(k);
        if (existing != null) {
            return existing;
        }
        EnergyNetwork network = new EnergyNetwork(this, k);
        networks.add(network);
        // 先认领起点，避免下一个节点又创建一个新网络
        blockOwner.put(k, network);
        return network;
    }

    /** 重建网络并从其它网络手中接管它覆盖的方块（实现自动合并）。 */
    private void rebuild(EnergyNetwork network) {
        network.rebuild();
        for (Location block : network.rawBlocks()) {
            EnergyNetwork owner = blockOwner.get(block);
            if (owner != null && owner != network && networks.contains(owner)) {
                dissolve(owner, network);
            }
            blockOwner.put(block, network);
        }
    }

    /** 拆除一个被合并掉的网络，防止同一批机器被两个网络重复 tick。 */
    private void dissolve(EnergyNetwork dead, EnergyNetwork alive) {
        networks.remove(dead);
        blockOwner.values().removeIf(n -> n == dead);
        for (EnergyNode node : dead.machines()) {
            node.setNetwork(alive);
        }
    }

    // ------------------------------------------------------------------
    // 方块变化
    // ------------------------------------------------------------------

    /**
     * 某个方块发生变化（放置/破坏）时调用。
     *
     * <p>如果它本身是导线，需要让它周围已有的网络重建，从而可能把两个
     * 独立的电网连成一个；如果它周边什么都没有，就为它建立一个新网络。</p>
     */
    public void onBlockChanged(Location loc) {
        markAffectedDirty(loc);

        // 新位置无人认领：若它本身能构成网络（导线或机器），建立新网络
        Location k = key(loc);
        if (!blockOwner.containsKey(k) && isNetworkBlock(loc)) {
            EnergyNetwork network = new EnergyNetwork(this, k);
            networks.add(network);
            blockOwner.put(k, network);
            network.markDirty();
        }
    }

    /** 把该位置周围 3x3x3 内的网络标记为脏，触发下次 tick 重建。 */
    private void markAffectedDirty(Location loc) {
        Set<EnergyNetwork> affected = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    EnergyNetwork network = blockOwner.get(key(loc.clone().add(dx, dy, dz)));
                    if (network != null) {
                        affected.add(network);
                    }
                }
            }
        }
        for (EnergyNetwork network : affected) {
            network.markDirty();
        }
    }

    // ------------------------------------------------------------------
    // BFS 回调
    // ------------------------------------------------------------------

    /** 该位置是否可以成为网络的一部分（导线或已注册节点）。 */
    public boolean isNetworkBlock(Location loc) {
        if (!isLoaded(loc)) {
            return false;
        }
        if (nodes.containsKey(key(loc))) {
            return true;
        }
        return EnergyNetwork.isCable(loc.getBlock().getType());
    }

    /** 该位置是否是导线。 */
    public boolean isCable(Location loc) {
        if (!isLoaded(loc)) {
            return false;
        }
        return EnergyNetwork.isCable(loc.getBlock().getType());
    }

    private static boolean isLoaded(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }
        return world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    /** 每 tick 调度全部网络。 */
    public void tick() {
        // 1. 重建所有被标记为脏的网络。
        //    这里遍历的是快照：rebuild() 可能因网络合并而修改 networks 列表，
        //    直接按索引遍历会漏掉元素。
        for (EnergyNetwork network : new ArrayList<>(networks)) {
            if (!network.isDirty()) {
                continue;
            }
            if (!networks.contains(network)) {
                // 已被别的网络吸收
                continue;
            }
            rebuild(network);
        }

        // 2. 调度有机器接入的网络
        for (EnergyNetwork network : new ArrayList<>(networks)) {
            if (network.machineCount() == 0) {
                continue;
            }
            try {
                network.tick();
            } catch (Exception ex) {
                org.bukkit.Bukkit.getLogger().warning("[HRF] 能源网络 tick 出错: " + ex);
            }
        }

        // 3. 清理空网络，防止玩家反复放置/破坏导致列表无限增长
        if (networks.size() > 32) {
            networks.removeIf(n -> n.machineCount() == 0 && n.blockCount() == 0);
        }
    }

    public Collection<EnergyNetwork> networks() {
        return java.util.Collections.unmodifiableList(networks);
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int networkCount() {
        return networks.size();
    }

    /** 查询某位置所属网络的诊断信息。 */
    public EnergyNetwork networkForQuery(Location loc) {
        EnergyNetwork network = blockOwner.get(key(loc));
        if (network != null) {
            return network;
        }
        // 玩家可能站在机器旁边而不是机器上，扩大一次搜索范围
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -6; dy <= 6; dy++) {
                for (int dz = -6; dz <= 6; dz++) {
                    EnergyNetwork found = blockOwner.get(key(loc.clone().add(dx, dy, dz)));
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 区块卸载时清理该区块内的节点与网络引用，
     * 避免长期运行后内存中堆积大量已卸载区域的引用。
     */
    public void forgetChunk(Chunk chunk) {
        List<Location> toRemove = new ArrayList<>();
        for (Location loc : nodes.keySet()) {
            if (loc.getWorld() == chunk.getWorld()
                    && (loc.getBlockX() >> 4) == chunk.getX()
                    && (loc.getBlockZ() >> 4) == chunk.getZ()) {
                toRemove.add(loc);
            }
        }
        if (toRemove.isEmpty()) {
            return;
        }
        for (Location loc : toRemove) {
            EnergyNode node = nodes.remove(loc);
            if (node != null) {
                node.setNetwork(null);
            }
        }
        blockOwner.keySet().removeIf(loc -> loc.getWorld() == chunk.getWorld()
                && (loc.getBlockX() >> 4) == chunk.getX()
                && (loc.getBlockZ() >> 4) == chunk.getZ());
        networks.removeIf(n -> n.blockCount() == 0 && n.machineCount() == 0);
    }

    /** 检查该位置是否可以接入网络（用于机器放置提示）。 */
    public boolean isConnected(Location loc) {
        return blockOwner.containsKey(key(loc));
    }

    /** 调试用：某个 Material 是否被当作导线。 */
    public static boolean isCableMaterial(org.bukkit.Material material) {
        return EnergyNetwork.isCable(material);
    }
}
