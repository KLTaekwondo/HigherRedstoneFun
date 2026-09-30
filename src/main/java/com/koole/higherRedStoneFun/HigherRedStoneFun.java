package com.koole.higherRedStoneFun;

import com.koole.higherRedStoneFun.commands.HrfCommand;
import com.koole.higherRedStoneFun.content.BasicMachines;
import com.koole.higherRedStoneFun.content.GeneticsMachines;
import com.koole.higherRedStoneFun.content.Materials;
import com.koole.higherRedStoneFun.content.PowerMachines;
import com.koole.higherRedStoneFun.content.Recipes;
import com.koole.higherRedStoneFun.core.Keys;
import com.koole.higherRedStoneFun.core.Text;
import com.koole.higherRedStoneFun.energy.EnergyManager;
import com.koole.higherRedStoneFun.genetics.GeneticsManager;
import com.koole.higherRedStoneFun.items.ItemRegistry;
import com.koole.higherRedStoneFun.listeners.AnimalListener;
import com.koole.higherRedStoneFun.listeners.CropListener;
import com.koole.higherRedStoneFun.listeners.MachineListener;
import com.koole.higherRedStoneFun.listeners.MachineMenuListener;
import com.koole.higherRedStoneFun.machines.MachineInstance;
import com.koole.higherRedStoneFun.machines.MachineManager;
import com.koole.higherRedStoneFun.machines.MachineRegistry;
import com.koole.higherRedStoneFun.machines.MachineStorage;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HigherRedStoneFun 主类。
 *
 * <p>这是一个面向 Minecraft 26.3 / Paper 的全新科技插件，玩法灵感来自
 * 粘液科技（Slimefun），但在三个方面做了针对性的重构与升级：</p>
 *
 * <ol>
 *   <li><b>能源网络</b>：导线不占节点、拓扑带脏标记缓存、按缓冲区分电，
 *       解决原版「导线越多越卡、大机器抢光小机器电」的问题。</li>
 *   <li><b>机器实现</b>：定义 / 状态 / 逻辑三者分离，
 *       同类机器不需要各写一个类，新增一台机器只是一份注册。</li>
 *   <li><b>基因工程</b>：补上原版生态在「育种」上的空白——
 *       可采集、可测序、可拼接、可遗传的 4 基因位系统，
 *       让玩家能真正培育出更高效的作物与家畜。</li>
 * </ol>
 *
 * <p>初始化顺序：Key → 物品 → 配方 → 机器定义 → 存档 → 事件 → 调度。</p>
 */
public final class HigherRedStoneFun extends JavaPlugin {

    private static HigherRedStoneFun instance;

    private EnergyManager energyManager;
    private MachineManager machineManager;
    private MachineStorage machineStorage;
    private GeneticsManager geneticsManager;

    private BukkitTask machineTask;
    private BukkitTask energyTask;
    private BukkitTask autosaveTask;

    /** 种植流程中待绑定基因的临时记录（玩家 UUID -> 待处理种植）。 */
    private final Map<UUID, CropListener.PendingPlant> pendingPlant = new ConcurrentHashMap<>();

    public static HigherRedStoneFun get() {
        return instance;
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();

        // 1. 基础
        saveDefaultConfig();
        Keys.init(this);
        com.koole.higherRedStoneFun.energy.Cables.load(getConfig(), getLogger());
        geneticsManager = new GeneticsManager(Keys.get());
        machineManager = new MachineManager();
        machineStorage = new MachineStorage(this);
        energyManager = new EnergyManager();

        // 2. 内容注册
        //    顺序很重要：先材料、再机器（机器会同时注册自己的物品）、
        //    最后才是配方——因为配方会引用前面两者的 ID。
        Materials.register();
        BasicMachines.register();
        PowerMachines.register();
        GeneticsMachines.register(geneticsManager);
        Recipes.register();

        // 2b. 自检：确保没有引用不存在的物品 ID（配置写错时报错而不是静默丢失）
        validateContent();

        // 3. 恢复机器存档
        int restored = machineStorage.loadAll(machineManager);
        for (MachineInstance machine : machineManager.all()) {
            if (machine.definition().energyRole() != null) {
                energyManager.register(machine);
            }
        }

        // 4. 事件
        registerEvents();

        // 5. 命令
        registerCommand();

        // 6. 调度
        startSchedulers();

        long elapsed = System.currentTimeMillis() - start;
        getLogger().info("========================================");
        getLogger().info(" HigherRedStoneFun v" + getPluginMeta().getVersion() + " 已启用");
        getLogger().info(" 物品: " + ItemRegistry.get().size()
                + " | 机器: " + MachineRegistry.get().size()
                + " | 配方: " + com.koole.higherRedStoneFun.recipes.RecipeRegistry.get().size());
        getLogger().info(" 已恢复机器: " + restored + " 台");
        getLogger().info(" 启动耗时: " + elapsed + " ms");
        getLogger().info("========================================");

        // 提示管理员：本插件与粘液科技的物品体系是独立的
        if (getServer().getPluginManager().getPlugin("Slimefun") != null) {
            getLogger().info("检测到服务器同时安装了 Slimefun，两者互不冲突（使用独立命名空间）。");
        }

        // 启动时自检：通过 -Dhrf.selftest=true 或配置 debug.run-selftest-on-start 触发。
        // 这样服主可以在无人值守的情况下验证核心系统是否正常。
        if (Boolean.getBoolean("hrf.selftest")
                || getConfig().getBoolean("debug.run-selftest-on-start", false)) {
            getServer().getScheduler().runTaskLater(this,
                    () -> new com.koole.higherRedStoneFun.commands.SelfTest(this)
                            .run(getServer().getConsoleSender()), 20L);
        }
    }

    @Override
    public void onDisable() {
        // 先停调度，避免关服过程中机器还在改数据
        stopSchedulers();

        if (machineStorage != null && machineManager != null) {
            machineStorage.saveAll(machineManager.all());
            getLogger().info("已保存 " + machineManager.size() + " 台机器。");
        }        pendingPlant.clear();
        instance = null;
    }

    // ------------------------------------------------------------------
    // 注册
    // ------------------------------------------------------------------

    private void registerEvents() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new MachineListener(this, machineManager), this);
        pm.registerEvents(new MachineMenuListener(), this);
        pm.registerEvents(new CropListener(this, geneticsManager), this);
        pm.registerEvents(new AnimalListener(this, geneticsManager), this);
        pm.registerEvents(new com.koole.higherRedStoneFun.listeners.GuideMenuListener(), this);
    }

    private void registerCommand() {
        PluginCommand command = getCommand("hrf");
        if (command == null) {
            getLogger().severe("无法注册 /hrf 命令，请检查 plugin.yml");
            return;
        }
        HrfCommand executor = new HrfCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /**
     * 启动调度器。
     *
     * <p>分为三个独立任务，各自频率不同：</p>
     * <ul>
     *   <li>能源：每 tick 一次（电价波动需要及时响应）。</li>
     *   <li>机器：每 tick 一次，但每台机器有自己的 tickPeriod，实际开销更小。</li>
     *   <li>存档：每 5 分钟一次，只写「脏」的机器。</li>
     * </ul>
     */
    private void startSchedulers() {
        if (getConfig().getBoolean("performance.enable-energy-net", true)) {
            energyTask = getServer().getScheduler().runTaskTimer(this, () -> {
                try {
                    energyManager.tick();
                } catch (Exception ex) {
                    getLogger().warning("能源网络 tick 出错: " + ex);
                }
            }, 1L, 1L);
        }

        if (getConfig().getBoolean("performance.enable-machines", true)) {
            machineTask = getServer().getScheduler().runTaskTimer(this, () -> {
                try {
                    machineManager.tick();
                } catch (Exception ex) {
                    getLogger().warning("机器 tick 出错: " + ex);
                }
            }, 1L, 1L);
        }

        long autosaveInterval = Math.max(20L * 30L,
                getConfig().getLong("performance.autosave-interval-ticks", 20L * 300L));
        autosaveTask = getServer().getScheduler().runTaskTimer(this, this::autosave,
                autosaveInterval, autosaveInterval);
    }

    private void stopSchedulers() {
        if (machineTask != null) {
            machineTask.cancel();
            machineTask = null;
        }
        if (energyTask != null) {
            energyTask.cancel();
            energyTask = null;
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
    }

    /**
     * 内容自检。
     *
     * <p>在注册完成后校验所有配方引用的物品 ID 都存在。这样内容写错时
     * 会在启动阶段给出明确报错，而不是等到玩家合成时才神秘失败。</p>
     */
    private void validateContent() {
        java.util.List<String> problems = new java.util.ArrayList<>();

        for (com.koole.higherRedStoneFun.recipes.RecipeType type
                : com.koole.higherRedStoneFun.recipes.RecipeType.ALL) {
            for (com.koole.higherRedStoneFun.recipes.MachineRecipe recipe
                    : com.koole.higherRedStoneFun.recipes.RecipeRegistry.get().of(type)) {
                for (org.bukkit.inventory.ItemStack input : recipe.inputs()) {
                    checkItem(input, "配方 " + recipe.id() + " 的输入", problems);
                }
                for (com.koole.higherRedStoneFun.recipes.RecipeOutput output : recipe.outputs()) {
                    checkItem(output.stack(), "配方 " + recipe.id() + " 的产出", problems);
                }
            }
        }

        if (!problems.isEmpty()) {
            for (String problem : problems) {
                getLogger().severe("[内容自检] " + problem);
            }
            throw new IllegalStateException("内容注册存在问题，共 " + problems.size() + " 处，详见上方日志");
        }
    }

    private void checkItem(org.bukkit.inventory.ItemStack stack, String where, java.util.List<String> problems) {
        String id = ItemRegistry.get().idOf(stack);
        // 有 PDC 标记但注册表里查不到 = 引用了不存在的自定义物品
        if (id != null && !ItemRegistry.get().exists(id)) {
            problems.add(where + " 引用了未注册的物品 ID: " + id);
        }
    }

    /** 只保存被修改过的机器，避免无谓的磁盘写入。 */
    private void autosave() {
        int count = 0;
        for (MachineInstance machine : machineManager.all()) {
            if (!machine.isDirty()) {
                continue;
            }
            machineStorage.saveOne(machine);
            count++;
        }
        if (count > 0 && getConfig().getBoolean("debug.verbose-autosave", false)) {
            getLogger().info("自动保存了 " + count + " 台机器。");
        }
    }

    // ------------------------------------------------------------------
    // 区块生命周期
    // ------------------------------------------------------------------

    /**
     * 区块加载时把存档里的机器恢复到内存。
     *
     * <p>让「离开基地再回来」的机器能继续工作，而不需要重启服务器。</p>
     */
    public void restoreChunk(Chunk chunk) {
        if (machineStorage == null) {
            return;
        }
        World world = chunk.getWorld();
        int restored = machineStorage.loadChunkRegion(world, chunk.getX(), chunk.getZ(), machineManager);
        if (restored > 0) {
            for (MachineInstance machine : machineManager.all()) {
                if (machine.definition().energyRole() != null
                        && machine.location().getWorld() == world
                        && (machine.location().getBlockX() >> 4) == chunk.getX()
                        && (machine.location().getBlockZ() >> 4) == chunk.getZ()) {
                    energyManager.register(machine);
                }
            }
            if (getConfig().getBoolean("debug.verbose-chunks", false)) {
                getLogger().info("区块 " + chunk.getX() + "," + chunk.getZ() + " 恢复了 " + restored + " 台机器。");
            }
        }
    }

    // ------------------------------------------------------------------
    // 访问器
    // ------------------------------------------------------------------

    public EnergyManager energy() {
        return energyManager;
    }

    public MachineManager machines() {
        return machineManager;
    }

    public MachineStorage storage() {
        return machineStorage;
    }

    public GeneticsManager genetics() {
        return geneticsManager;
    }

    public Map<UUID, CropListener.PendingPlant> pendingPlant() {
        return pendingPlant;
    }

    /** 重载配置与内容（不做完整重启）。 */
    public void reloadAll() {
        reloadConfig();
        stopSchedulers();
        startSchedulers();
        getLogger().info("配置已重载。");
    }

    /** 发送给控制台的统计信息。 */
    public List<String> statistics() {
        return List.of(
                "<gray>物品注册: <white>" + ItemRegistry.get().size(),
                "<gray>机器注册: <white>" + MachineRegistry.get().size(),
                "<gray>配方注册: <white>" + com.koole.higherRedStoneFun.recipes.RecipeRegistry.get().size(),
                "<gray>已加载机器: <white>" + machineManager.loadedCount()
                        + " <gray>/ 总计 <white>" + machineManager.size(),
                "<gray>机器区块: <white>" + machineManager.chunkCount(),
                "<gray>能源节点: <white>" + energyManager.nodeCount()
                        + " <gray>网络 <white>" + energyManager.networkCount()
        );
    }

    /** 玩家可读的简短版本信息。 */
    public String versionLine() {
        return Text.plain(Text.mm("<gradient:#ff6b6b:#ffd93d>HigherRedStoneFun</gradient> <gray>v"
                + getPluginMeta().getVersion()));
    }
}
