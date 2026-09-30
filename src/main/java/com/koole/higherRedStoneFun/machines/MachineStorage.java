package com.koole.higherRedStoneFun.machines;

import com.koole.higherRedStoneFun.HigherRedStoneFun;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

/**
 * 机器持久化。
 *
 * <p>存储方案：每个世界一个 YAML 文件，按区块分节，物品栏用
 * {@link ItemStack#serializeAsBytes()} + Base64 保存。</p>
 *
 * <p>为什么用 Base64 NBT 而不是 YAML 的物品序列化：Paper 的
 * {@code ItemStack.serialize()} 会丢失部分数据组件，而字节序列化是完整的，
 * 并且能安全保存自定义 PDC 标记。</p>
 */
public final class MachineStorage {

    private final HigherRedStoneFun plugin;
    private final File folder;

    public MachineStorage(HigherRedStoneFun plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "machines");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("无法创建机器数据目录: " + folder);
        }
    }

    // ------------------------------------------------------------------
    // 保存
    // ------------------------------------------------------------------

    public void saveAll(java.util.Collection<MachineInstance> machines) {
        // 按世界分组
        Map<String, YamlConfiguration> perWorld = new java.util.HashMap<>();

        for (MachineInstance machine : machines) {
            Location loc = machine.location();
            World world = loc.getWorld();
            if (world == null) {
                continue;
            }
            YamlConfiguration config = perWorld.computeIfAbsent(world.getName(), n -> new YamlConfiguration());
            write(config, machine);
        }

        // 写入磁盘
        for (World world : plugin.getServer().getWorlds()) {
            YamlConfiguration config = perWorld.computeIfAbsent(world.getName(), n -> new YamlConfiguration());
            File file = fileFor(world.getName());
            try {
                config.save(file);
            } catch (IOException ex) {
                plugin.getLogger().severe("保存世界 " + world.getName() + " 的机器数据失败: " + ex.getMessage());
            }
        }
    }

    /** 只保存单台机器（用于节流写盘）。 */
    public void saveOne(MachineInstance machine) {
        Location loc = machine.location();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        File file = fileFor(world.getName());
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        write(config, machine);
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("保存机器 " + loc + " 失败: " + ex.getMessage());
        }
    }

    private void write(YamlConfiguration config, MachineInstance machine) {
        Location loc = machine.location();
        String path = path(loc);

        config.set(path + ".type", machine.id());
        config.set(path + ".energy", machine.energyStored());
        config.set(path + ".fuel", machine.fuelTicks());
        config.set(path + ".progress", machine.progress());
        config.set(path + ".total", machine.totalTicks());
        config.set(path + ".recipe", machine.activeRecipe());
        config.set(path + ".generated", machine.lifetimeGenerated());
        config.set(path + ".consumed", machine.lifetimeConsumed());
        config.set(path + ".completed", machine.completedOperations());
        if (machine.owner() != null) {
            config.set(path + ".owner", machine.owner().toString());
            config.set(path + ".ownerName", machine.ownerName());
        }

        // 机器专用计数（例如核反应堆堆芯温度）
        java.util.Map<String, Integer> counters = machine.counters();
        if (!counters.isEmpty()) {
            ConfigurationSection counterSection = config.createSection(path + ".counters");
            for (java.util.Map.Entry<String, Integer> entry : counters.entrySet()) {
                counterSection.set(entry.getKey(), entry.getValue());
            }
        }

        // 物品栏
        ConfigurationSection inv = config.createSection(path + ".inventory");
        ItemStack[] contents = machine.contents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            try {
                inv.set(String.valueOf(i), Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
            } catch (Exception ex) {
                plugin.getLogger().warning("无法序列化 " + loc + " 第 " + i + " 格物品: " + ex.getMessage());
            }
        }
        machine.clearDirty();
    }

    // ------------------------------------------------------------------
    // 读取
    // ------------------------------------------------------------------

    public int loadAll(MachineManager manager) {
        int loaded = 0;
        for (World world : plugin.getServer().getWorlds()) {
            loaded += loadWorld(world, manager);
        }
        return loaded;
    }

    public int loadWorld(World world, MachineManager manager) {
        File file = fileFor(world.getName());
        if (!file.exists()) {
            return 0;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        int loaded = 0;

        for (String chunkKey : config.getKeys(false)) {
            ConfigurationSection chunkSection = config.getConfigurationSection(chunkKey);
            if (chunkSection == null) {
                continue;
            }
            int chunkX;
            int chunkZ;
            try {
                String[] parts = chunkKey.split("_");
                chunkX = Integer.parseInt(parts[0]);
                chunkZ = Integer.parseInt(parts[1]);
            } catch (Exception ex) {
                plugin.getLogger().warning("跳过无法解析的区块键: " + chunkKey);
                continue;
            }

            for (String blockKey : chunkSection.getKeys(false)) {
                ConfigurationSection section = chunkSection.getConfigurationSection(blockKey);
                if (section == null) {
                    continue;
                }
                MachineInstance machine = read(world, chunkX, chunkZ, blockKey, section);
                if (machine != null) {
                    // 避免重复（同一位置以内存中的为准）
                    if (manager.get(machine.location()) == null) {
                        manager.add(machine);
                    }
                    loaded++;
                }
            }
        }
        return loaded;
    }

    /**
     * 只加载某个区块的机器（区块加载时调用）。
     *
     * <p>相比每次重新读整个 YAML，这里会先把文件读进来再筛区块。
     * 对于一般规模的存档这个开销可以接受，v0.2 会改成按区块分文件。</p>
     */
    public int loadChunkRegion(World world, int chunkX, int chunkZ, MachineManager manager) {
        File file = fileFor(world.getName());
        if (!file.exists()) {
            return 0;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String chunkKey = chunkX + "_" + chunkZ;
        ConfigurationSection chunkSection = config.getConfigurationSection(chunkKey);
        if (chunkSection == null) {
            return 0;
        }

        int loaded = 0;
        for (String blockKey : chunkSection.getKeys(false)) {
            ConfigurationSection section = chunkSection.getConfigurationSection(blockKey);
            if (section == null) {
                continue;
            }
            MachineInstance machine = read(world, chunkX, chunkZ, blockKey, section);
            if (machine != null && manager.get(machine.location()) == null) {
                manager.add(machine);
                loaded++;
            }
        }
        return loaded;
    }

    private MachineInstance read(World world, int chunkX, int chunkZ, String blockKey, ConfigurationSection section) {
        String type = section.getString("type");
        if (type == null) {
            return null;
        }
        MachineDefinition definition = MachineRegistry.get().get(type);
        if (definition == null) {
            plugin.getLogger().warning("未知机器类型 " + type + "，已跳过（可能来自已移除的模块）");
            return null;
        }

        int x;
        int y;
        int z;
        try {
            String[] parts = blockKey.split("_");
            x = Integer.parseInt(parts[0]);
            y = Integer.parseInt(parts[1]);
            z = Integer.parseInt(parts[2]);
        } catch (Exception ex) {
            return null;
        }

        Location loc = new Location(world, x, y, z);

        // 校验方块是否还是机器（材质未被替换）
        if (!isStillMachine(loc, definition)) {
            // 方块已被替换但存档还在：把内部物品掉落到世界上，避免物品凭空消失
            dropOrphan(loc, section);
            return null;
        }

        MachineInstance machine = new MachineInstance(loc, definition);
        machine.restore(
                section.getLong("energy", 0L),
                section.getInt("fuel", 0),
                section.getInt("progress", 0),
                section.getInt("total", 0),
                section.getString("recipe"),
                section.getLong("generated", 0L),
                section.getLong("consumed", 0L),
                section.getInt("completed", 0),
                section.getString("owner"),
                section.getString("ownerName"));

        // 恢复机器专用计数
        ConfigurationSection counters = section.getConfigurationSection("counters");
        if (counters != null) {
            for (String name : counters.getKeys(false)) {
                machine.putCounterRaw(name, counters.getInt(name, 0));
            }
        }

        // 恢复物品栏
        ConfigurationSection inv = section.getConfigurationSection("inventory");
        if (inv != null) {
            ItemStack[] contents = machine.contents();
            for (String slotKey : inv.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(slotKey);
                    if (slot < 0 || slot >= contents.length) {
                        continue;
                    }
                    String encoded = inv.getString(slotKey);
                    if (encoded == null || encoded.isEmpty()) {
                        continue;
                    }
                    contents[slot] = ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
                } catch (Exception ex) {
                    plugin.getLogger().warning("恢复物品失败 " + loc + " 槽位 " + slotKey + ": " + ex.getMessage());
                }
            }
        }
        return machine;
    }

    /**
     * 校验该位置上的方块是否仍然是这台机器。
     *
     * <p>本插件用「坐标」作为机器方块的身份标识（与粘液科技的 BlockStorage 一致），
     * 因此这里只需要确认方块材质没有被玩家替换掉。</p>
     */
    private boolean isStillMachine(Location loc, MachineDefinition definition) {
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }
        if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            // 区块未加载：先信任存档，等玩家靠近时再校验
            return true;
        }
        return world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())
                .getType() == definition.icon();
    }

    /** 方块已被替换时，把存档里的物品归还给世界。 */
    private void dropOrphan(Location loc, ConfigurationSection section) {
        World world = loc.getWorld();
        if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            return;
        }
        ConfigurationSection inv = section.getConfigurationSection("inventory");
        if (inv == null) {
            return;
        }
        for (String slotKey : inv.getKeys(false)) {
            try {
                String encoded = inv.getString(slotKey);
                if (encoded == null || encoded.isEmpty()) {
                    continue;
                }
                ItemStack stack = ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
                if (stack != null && !stack.getType().isAir()) {
                    world.dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), stack);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("归还孤儿机器物品失败 " + loc + " 槽位 " + slotKey + ": " + ex.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // 路径
    // ------------------------------------------------------------------

    /** YAML 路径：{chunkX}_{chunkZ}.{x}_{y}_{z} */
    private static String path(Location loc) {
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        return chunkX + "_" + chunkZ + "." + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    private File fileFor(String worldName) {
        return new File(folder, sanitize(worldName) + ".yml");
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    /** 删除某个世界的存档（/hrf reload 时使用）。 */
    public void delete(String worldName) {
        File file = fileFor(worldName);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("无法删除 " + file);
        }
    }
}
