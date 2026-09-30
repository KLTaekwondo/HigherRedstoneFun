package com.koole.higherRedStoneFun.energy;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 能源网络。
 *
 * <p>这是对粘液科技 EnergyNet 的一次针对性重构。原版把「导线方块」也当作网络节点，
 * 于是导线越多、网络构建越慢，且每次取电都要重算整张图。这里做了三点改变：</p>
 *
 * <ol>
 *   <li><b>导线不占节点</b>：导线（{@link #isCable}）只是通道，不进入 {@code machines} 列表，
 *       因此一万根导线也不会拖慢机器调度。</li>
 *   <li><b>网络拓扑缓存 + 脏标记</b>：网络在创建时一次性 BFS 出全部成员机器；
 *       只有方块发生变化时才重建，机器 tick 时是 O(机器数) 的简单算术。</li>
 *   <li><b>按需分配</b>：发电机先充满所有电池，剩余电量再按「申请量」分配给用电机，
 *       避免原版「大机器抢光小机器电」的问题。</li>
 * </ol>
 */
public final class EnergyNetwork {

    /** 单次 BFS 允许访问的最大方块数，防止在超大型基地里卡主线程。 */
    private static final int MAX_SCAN_BLOCKS = 4096;

    /** 单个网络允许接入的最大机器数。 */
    private static final int MAX_MACHINES = 512;

    private final EnergyManager manager;
    private final Location origin;
    private final Set<Location> blocks = new HashSet<>();
    private final List<EnergyNode> generators = new ArrayList<>();
    private final List<EnergyNode> consumers = new ArrayList<>();
    private final List<EnergyNode> storages = new ArrayList<>();

    private boolean dirty = true;
    private long totalGenerated;
    private long totalConsumed;
    private long lastNetJoules;

    EnergyNetwork(EnergyManager manager, Location origin) {
        this.manager = manager;
        this.origin = origin.clone();
    }

    public Location origin() {
        return origin.clone();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public int machineCount() {
        return generators.size() + consumers.size() + storages.size();
    }

    public int blockCount() {
        return blocks.size();
    }

    public long storedEnergy() {
        long sum = 0L;
        for (EnergyNode node : storages) {
            sum += node.stored();
        }
        return sum;
    }

    public long capacity() {
        long sum = 0L;
        for (EnergyNode node : storages) {
            sum += node.capacity();
        }
        return sum;
    }

    public long lastNetJoules() {
        return lastNetJoules;
    }

    public long totalGenerated() {
        return totalGenerated;
    }

    public long totalConsumed() {
        return totalConsumed;
    }

    // ------------------------------------------------------------------
    // 拓扑构建
    // ------------------------------------------------------------------

    /**
     * 重建网络拓扑。
     *
     * <p>从网络起点做一次 BFS，跨越导线<b>和机器</b>找出所有接入的机器。</p>
     *
     * <p>注意这里是「机器也能导电」：如果只让导线导电，玩家就必须在每两台
     * 相邻机器之间都塞一根导线，电容也无法充当母线。允许机器导电更符合
     * 玩家对「连在一起就是一个电网」的直觉，也让布线简单得多。</p>
     */
    public void rebuild() {
        blocks.clear();
        generators.clear();
        consumers.clear();
        storages.clear();

        Deque<Location> queue = new ArrayDeque<>();
        Set<Location> visited = new HashSet<>();

        blocks.add(origin);
        queue.add(origin);
        visited.add(origin);

        // 起点自身可能就是机器
        EnergyNode originNode = manager.nodeAt(origin);
        if (originNode != null) {
            addNode(originNode);
        }

        int scanned = 0;
        while (!queue.isEmpty() && scanned < MAX_SCAN_BLOCKS) {
            Location current = queue.poll();
            scanned++;

            for (Location neighbour : neighbours(current)) {
                if (visited.contains(neighbour) || blocks.size() >= MAX_SCAN_BLOCKS) {
                    continue;
                }
                if (!manager.isNetworkBlock(neighbour)) {
                    continue;
                }
                visited.add(neighbour);
                blocks.add(neighbour);

                EnergyNode neighbourNode = manager.nodeAt(neighbour);
                if (neighbourNode != null) {
                    addNode(neighbourNode);
                    // 超过机器上限后不再继续扩张，避免单次 BFS 失控
                    if (machineCount() >= MAX_MACHINES) {
                        continue;
                    }
                }
                queue.add(neighbour);
            }
        }

        dirty = false;
    }

    private void addNode(EnergyNode node) {
        switch (node.role()) {
            case GENERATOR -> {
                if (!generators.contains(node)) {
                    generators.add(node);
                }
            }
            case CONSUMER -> {
                if (!consumers.contains(node)) {
                    consumers.add(node);
                }
            }
            case STORAGE -> {
                if (!storages.contains(node)) {
                    storages.add(node);
                }
            }
        }
        node.setNetwork(this);
    }

    private static List<Location> neighbours(Location loc) {
        List<Location> out = new ArrayList<>(6);
        out.add(loc.clone().add(1, 0, 0));
        out.add(loc.clone().add(-1, 0, 0));
        out.add(loc.clone().add(0, 1, 0));
        out.add(loc.clone().add(0, -1, 0));
        out.add(loc.clone().add(0, 0, 1));
        out.add(loc.clone().add(0, 0, -1));
        return out;
    }

    // ------------------------------------------------------------------
    // 调度
    // ------------------------------------------------------------------

    /**
     * 执行一个 tick 的能源调度。
     *
     * <p>顺序：先让发电机发电并充入电池 -> 电池之间均衡 -> 按申请量供应用电机。</p>
     */
    public void tick() {
        if (dirty) {
            rebuild();
        }

        long generated = 0L;

        // 1. 发电机发电，优先充入本网络的电池
        for (EnergyNode generator : generators) {
            long produced = generator.generate();
            if (produced <= 0L) {
                continue;
            }
            generated += produced;
            long leftover = chargeStorages(produced);
            if (leftover > 0L) {
                // 电池已满：电量直接用于满足用电机
                supplyConsumers(leftover);
            }
        }

        // 2. 电池之间的均衡（把电量从高电量电池转移到低电量电池，避免空转）
        balanceStorages();

        // 3. 用电机取电
        for (EnergyNode consumer : consumers) {
            long demand = consumer.demand();
            if (demand <= 0L) {
                continue;
            }
            long available = drawFromStorages(demand);
            if (available > 0L) {
                consumer.receive(available);
                totalConsumed += available;
            }
        }

        totalGenerated += generated;
        lastNetJoules = generated - Math.min(generated, totalConsumed);
    }

    /** 把电量充入电池，返回未被吸收的剩余量。 */
    private long chargeStorages(long joules) {
        long remaining = joules;
        for (EnergyNode storage : storages) {
            if (remaining <= 0L) {
                break;
            }
            remaining -= storage.charge(remaining);
        }
        return Math.max(0L, remaining);
    }

    /** 从电池抽取最多 {@code joules} 焦耳，返回实际抽到的量。 */
    private long drawFromStorages(long joules) {
        long remaining = joules;
        long drawn = 0L;
        for (EnergyNode storage : storages) {
            if (remaining <= 0L) {
                break;
            }
            long got = storage.discharge(remaining);
            remaining -= got;
            drawn += got;
        }
        return drawn;
    }

    /** 电池均衡：把高于平均值的电量补给低于平均值的电池。 */
    private void balanceStorages() {
        if (storages.size() < 2) {
            return;
        }
        long total = storedEnergy();
        long totalCap = capacity();
        if (totalCap <= 0L) {
            return;
        }
        long average = total / storages.size();

        for (EnergyNode storage : storages) {
            if (storage.stored() > average) {
                long excess = storage.stored() - average;
                long moved = storage.discharge(excess);
                if (moved > 0L) {
                    chargeStorages(moved);
                }
            }
        }
    }

    /** 直接向用电机注入电量（发电机满电池时的直供路径）。 */
    private void supplyConsumers(long joules) {
        long remaining = joules;
        for (EnergyNode consumer : consumers) {
            if (remaining <= 0L) {
                break;
            }
            long demand = consumer.demand();
            if (demand <= 0L) {
                continue;
            }
            long give = Math.min(demand, remaining);
            consumer.receive(give);
            remaining -= give;
            totalConsumed += give;
        }
    }

    /** 网络内所有机器的位置，用于区块卸载时的清理。 */
    public List<EnergyNode> machines() {
        List<EnergyNode> out = new ArrayList<>(machineCount());
        out.addAll(generators);
        out.addAll(consumers);
        out.addAll(storages);
        return Collections.unmodifiableList(out);
    }

    /** 诊断信息，供 /hrf energy 使用。 */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        lines.add("<gray>网络中心: <white>" + fmt(origin));
        lines.add("<gray>节点数: <white>" + machineCount() + " <gray>方块数: <white>" + blocks.size());
        lines.add("<gray>发电机: <white>" + generators.size()
                + " <gray>用电机: <white>" + consumers.size()
                + " <gray>储能: <white>" + storages.size());
        lines.add("<gray>储能: <white>" + com.koole.higherRedStoneFun.core.Text.number(storedEnergy())
                + " <gray>/ <white>" + com.koole.higherRedStoneFun.core.Text.number(capacity()) + " <gray>J");
        lines.add("<gray>累计发电: <white>" + com.koole.higherRedStoneFun.core.Text.number(totalGenerated) + " <gray>J");
        lines.add("<gray>累计耗电: <white>" + com.koole.higherRedStoneFun.core.Text.number(totalConsumed) + " <gray>J");
        return lines;
    }

    private static String fmt(Location loc) {
        return loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    /** 判断一个方块类型是否是本插件的导线（材质集合由 {@link Cables} 统一提供）。 */
    public static boolean isCable(Material material) {
        return Cables.isCable(material);
    }

    /** 供调试：网络包含的方块位置。 */
    public Set<Location> rawBlocks() {
        return Collections.unmodifiableSet(blocks);
    }

    /** 移除一个方块，若影响拓扑则标脏。 */
    public void onBlockRemoved(Location loc) {
        if (blocks.remove(loc)) {
            markDirty();
        }
    }

    /** 检查方块是否属于本网络。 */
    public boolean contains(Location loc) {
        return blocks.contains(loc);
    }

    public Block blockAt(Location loc) {
        return loc.getBlock();
    }
}
