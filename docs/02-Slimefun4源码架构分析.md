# Slimefun4 源码架构分析

> **用途**：本文档是「借鉴 Slimefun4 的设计，用 Paper 1.21+ 新 API 编写一个独立插件」的架构依据。
> 所有结论均来自本地检出的真实源码，逐条标注 `文件路径:行号`。**凡是源码里不存在的类名，本文一律标注「不存在」**，避免按旧版文档臆造。

---

## 0. 分析对象

| 项目 | 值 |
|---|---|
| 源码根 | `D:\Projects\HigherRedStoneFun\.research\Slimefun4` |
| Git 提交 | `5374034c8713248909e60e6855c6f745fd7ca676`（2025-05-01，分支 `experimental`） |
| `pom.xml:12` | `<version>4.9-UNOFFICIAL</version>` |
| 主类 | `io.github.thebusybiscuit.slimefun4.implementation.Slimefun` |
| Java 目标 | `pom.xml:26-27` 编译 Java 16；`pom.xml:29-30` 测试 Java 21 |
| Paper 版本 | `pom.xml:32` `<paper.version>1.21.1</paper.version>` |
| 规模 | `src/main/java` 下 665 个 `.java` 文件 |
| 包结构 | `io.github.thebusybiscuit.slimefun4.{api,core,implementation,integrations,storage,utils}` + 遗留包 `me.mrCookieSlime.*` |

**两个遗留包必须理解**：`me.mrCookieSlime.Slimefun.*`（`BlockStorage`、`BlockMenu`、`BlockMenuPreset`、`BlockTicker`、`AContainer`、`AGenerator`）和 `me.mrCookieSlime.CSCoreLibPlugin.*`（`ChestMenu`、`Config`）。它们不是历史残留的死代码，而是**当前核心运行时**，但官方已在多处标注「将被重写」。

---

## 1. 插件主类 `Slimefun.java`：生命周期与初始化顺序

### 1.1 类声明与静态实例

主类自己就是 `SlimefunAddon` 的一个实现（注册官方物品时 `addon == Slimefun.instance()`）：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java:143
public class Slimefun extends JavaPlugin implements SlimefunAddon {
```

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java:154-156
    /**
     * Our static instance of {@link Slimefun}.
     * Make sure to clean this up in {@link #onDisable()}!
     */
    private static Slimefun instance;
```

静态实例通过私有 setter 维护，`onDisable()` 末尾置 `null`（`:460`），其它静态访问器统一走 `validateInstance()` 抛 `IllegalStateException`：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java:730-734
    private static void validateInstance() {
        if (instance == null) {
            throw new IllegalStateException("Cannot invoke static method, Slimefun instance is null.");
        }
    }
```

**设计要点**：这种「静态单例 + 校验」模式让 500+ 个物品类可以随时 `Slimefun.getRegistry()`，代价是**完全无法多实例化、无法在单元测试里隔离**。新插件建议改为构造注入。

### 1.2 构造函数：伪装成单元测试

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java:216-224
    public Slimefun() {
        super();

        // Check that we got loaded by MockBukkit rather than Bukkit's loader
        // TODO: This is very much a hack and we can hopefully move to a more native way in the future
        if (getClassLoader().getClass().getPackageName().startsWith("be.seeseemelk.mockbukkit")) {
            minecraftVersion = MinecraftVersion.UNIT_TEST;
        }
    }
```

### 1.3 所有服务在**字段初始化**阶段就构造

这是最容易被忽略的一点：大量服务是 `final` 字段直接 `new`，构造发生在 `onEnable()` **之前**：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java:168-210
    // Various things we need
    private final SlimefunRegistry registry = new SlimefunRegistry();
    private final SlimefunCommand command = new SlimefunCommand(this);
    private final TickerTask ticker = new TickerTask();

    // Services - Systems that fulfill certain tasks, treat them as a black box
    private final CustomItemDataService itemDataService = new CustomItemDataService(this, "slimefun_item");
    private final BlockDataService blockDataService = new BlockDataService(this, "slimefun_block");
    private final CustomTextureService textureService = new CustomTextureService(new Config(this, "item-models.yml"));
    private final GitHubService gitHubService = new GitHubService("Slimefun/Slimefun4");
    private final UpdaterService updaterService = new UpdaterService(this, getDescription().getVersion(), getFile());
    private final MetricsService metricsService = new MetricsService(this);
    private final AutoSavingService autoSavingService = new AutoSavingService();
    private final BackupService backupService = new BackupService();
    private final PermissionsService permissionsService = new PermissionsService(this);
    private final PerWorldSettingsService worldSettingsService = new PerWorldSettingsService(this);
    private final MinecraftRecipeService recipeService = new MinecraftRecipeService(this);
    private final HologramsService hologramsService = new HologramsService(this);
    private final SoundService soundService = new SoundService(this);
    private final ThreadService threadService = new ThreadService(this);
    private final AnalyticsService analyticsService = new AnalyticsService(this);

    // Some other things we need
    private final IntegrationsManager integrations = new IntegrationsManager(this);
    private final SlimefunProfiler profiler = new SlimefunProfiler();
    private final GPSNetwork gpsNetwork = new GPSNetwork(this);

    // Even more things we need
    private NetworkManager networkManager;
    private LocalizationService local;

    // Important config files for Slimefun
    private final Config config = new Config(this);
    private final Config items = new Config(this, "Items.yml");
    private final Config researches = new Config(this, "Researches.yml");

    // Data storage
    private Storage playerStorage;
```

注意 `CustomItemDataService(this, "slimefun_item")` 与 `BlockDataService(this, "slimefun_block")` 的两个 `NamespacedKey` 就在这里定型 —— 它们是**整个自定义物品体系的识别根**（见 §2.3）。

### 1.4 `onEnable()` 三分支

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java:229-243
    @Override
    public void onEnable() {
        setInstance(this);

        if (isUnitTest()) {
            // We handle Unit Tests seperately.
            onUnitTestStart();
        } else if (isVersionUnsupported()) {
            // We wanna ensure that the Server uses a compatible version of Minecraft.
            getServer().getPluginManager().disablePlugin(this);
        } else {
            // The Environment has been validated.
            onPluginStart();
        }
    }
```

`isVersionUnsupported()`（`:518-559`）用 `PaperLib` 探测版本，遍历 `MinecraftVersion` 枚举匹配，不匹配则打警告并 `return true` → 直接禁用插件。

### 1.5 `onPluginStart()` —— 权威初始化顺序

这是全文最重要的一段。按行号顺序：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java:263-345（节选，保留顺序）
    private void onPluginStart() {
        long timestamp = System.nanoTime();
        Logger logger = getLogger();

        // 1. 服务器软件检查（Paper 检测 / CS-CoreLib 冲突 / Java 版本建议）
        if (PaperLib.isPaper()) { ... } else { PaperLib.suggestPaper(this); }

        // 2. 判断是否全新安装（用于匿名统计）
        isNewlyInstalled = !new File("data-storage/Slimefun").exists();

        // 3. 创建目录
        logger.log(Level.INFO, "Creating directories...");
        createDirectories();

        // 4. 把 config.yml 读入内存缓存（含 guide 实现类的构造）
        registry.load(this, config);

        // 5. 本地化
        logger.log(Level.INFO, "Loading language files...");
        String chatPrefix = config.getString("options.chat-prefix");
        String serverDefaultLanguage = config.getString("options.language");
        local = new LocalizationService(this, chatPrefix, serverDefaultLanguage);

        // 6. 网络管理器（Cargo + Energy 共用）
        int networkSize = config.getInt("networks.max-size");
        if (networkSize < 1) { ...; networkSize = 1; }
        networkManager = new NetworkManager(networkSize, config.getBoolean("networks.enable-visualizer"), config.getBoolean("networks.delete-excess-items"));

        // 7. 玩家数据存储后端（硬编码）
        playerStorage = new LegacyStorage();
        logger.log(Level.INFO, "Using legacy storage for player data");

        // 8. bStats 与自研统计（裸线程 + 自建调度线程）
        new Thread(metricsService::start, "Slimefun Metrics").start();
        analyticsService.start();

        // 9. 自动更新器
        if (config.getBoolean("options.auto-update")) { updaterService.start(); } else { updaterService.disable(); }

        // 10. GEO 资源
        GEOResourcesSetup.setup();

        // 11. 加载 Tag
        loadTags();

        // 12. 加载全部物品（SlimefunItemSetup.setup）
        loadItems();

        // 13. 加载研究
        loadResearches();

        registry.setResearchingEnabled(getResearchCfg().getBoolean("enable-researching"));
        PostSetup.setupWiki();

        // 14. 注册全部监听器
        registerListeners();
```

紧接着是**延迟到服务器启动完成后**的部分，这解释了 addon 为什么必须在 `onEnable` 里注册物品：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java:347-393（节选）
        // 15. 0 tick 延迟的同步任务：纹理/权限/音效 + 原版配方索引
        runSync(new SlimefunStartupTask(this, () -> {
            textureService.register(registry.getAllSlimefunItems(), true);
            permissionsService.register(registry.getAllSlimefunItems(), true);
            soundService.reload(true);
            try {
                recipeService.refresh();
            } catch (Exception | LinkageError x) { ... }
        }), 0);

        // 16. 注册 /slimefun 命令
        command.register();

        // 17. 盔甲/辐射/彩虹/太阳能周期任务（同步或异步见 §7）
        if (config.getBoolean("options.enable-armor-effects")) {
            new SlimefunArmorTask().schedule(this, config.getInt("options.armor-update-interval") * 20L);
            ...
        }

        // 18. 启动自动保存 + 全息 + 方块 Ticker
        autoSavingService.start(this, config.getInt("options.auto-save-delay-in-minutes"));
        hologramsService.start();
        ticker.start(this);

        // 19. 第三方插件集成 + GitHub 数据抓取
        integrations.start();
        gitHubService.start(this);
```

**关键顺序结论**：

1. `registry.load(config)` 必须在 `LocalizationService` 之前（后者要用 config）。
2. **`networkManager` 必须在物品加载之前**，因为 `EnergyConnector` 等物品在构造/注册时会查询网络。
3. `loadItems()` 只是**构造并 register 物品对象**；真正把配方交给机器、把物品塞进 ItemGroup 的 `SlimefunItem.load()` 发生在第 15 步的 `SlimefunStartupTask` 里（见 §2.6）。
4. `ticker.start()` 放在最后，避免世界/方块数据尚未加载就开 tick。

`SlimefunStartupTask` 做的事（世界方块数据的**主线程同步全量加载**）：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/tasks/SlimefunStartupTask.java:44-69（节选）
    @Override
    public void run() {
        runnable.run();

        // Load all items
        PostSetup.loadItems();

        // Load all worlds
        Slimefun.getWorldSettingsService().load(Bukkit.getWorlds());

        for (World world : Bukkit.getWorlds()) {
            try {
                new BlockStorage(world);
            } catch (Exception x) { ... }
        }

        // Load/Unload Worlds, only after all plugins have started up. Fixes #2862
        new WorldListener(this.plugin);

        if (isEnabled("ELEVATOR_PLATE", "GPS_ACTIVATION_DEVICE_SHARED", "GPS_ACTIVATION_DEVICE_PERSONAL")) {
            new TeleporterListener(plugin);
        }
    }
```

### 1.6 `onDisable()` —— 关闭顺序

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java:409-469（节选，保留顺序）
    @Override
    public void onDisable() {
        if (instance() == null || minecraftVersion == MinecraftVersion.UNIT_TEST) {
            return;
        }

        // 1. 立即取消本插件全部任务
        Bukkit.getScheduler().cancelTasks(this);

        // 2. 让 Ticker 把 movingQueue / deletionQueue 排空
        try {
            ticker.halt();
            ticker.run();
        } catch (Exception x) { ... }

        // 3. 杀掉 Profiler 线程
        profiler.kill();

        // 4. 保存内存中所有脏 PlayerProfile
        PlayerProfile.iterator().forEachRemaining(profile -> {
            if (profile.isDirty()) { profile.save(); }
        });

        // 5. 保存所有世界方块数据
        for (Map.Entry<String, BlockStorage> entry : getRegistry().getWorlds().entrySet()) {
            try { entry.getValue().saveAndRemove(); } catch (Exception x) { ... }
        }

        // 6. 保存 universal 容器（末影箱等）
        for (UniversalBlockMenu menu : registry.getUniversalInventories().values()) { menu.save(); }

        // 7. 打备份 zip
        if (config.getBoolean("options.backup-data")) { backupService.run(); }

        metricsService.cleanUp();
        setInstance(null);

        // 8. 关掉所有打开的容器，防止 /reload 造成物品复制
        for (Player p : Bukkit.getOnlinePlayers()) { p.closeInventory(); }
    }
```

**两个陷阱**：
- `cancelTasks` 在保存之前执行，且此后 `Slimefun.runSync` 会因 `isEnabled() == false` 静默丢弃任务（`:1050-1052`），所以**关闭流程全部是主线程同步写盘**，大盘会明显卡关服。
- 备份 zip 的压缩也在主线程（`:452-454`）。

### 1.7 目录结构

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java:588-607
    private void createDirectories() {
        String[] storageFolders = { "Players", "blocks", "stored-blocks", "stored-inventories", "stored-chunks", "universal-inventories", "waypoints", "block-backups" };
        String[] pluginFolders = { "scripts", "error-reports", "cache/github", "world-settings" };

        for (String folder : storageFolders) {
            File file = new File("data-storage/Slimefun", folder);
            if (!file.exists()) { file.mkdirs(); }
        }

        for (String folder : pluginFolders) {
            File file = new File("plugins/Slimefun", folder);
            if (!file.exists()) { file.mkdirs(); }
        }
    }
```

注意：数据写在**服务端根目录**的 `data-storage/Slimefun`，不在 `plugins/Slimefun`。这在新插件里应当放弃，改用 `getDataFolder()`。

### 1.8 静态访问器清单（`Slimefun` 暴露的全局 API）

`:719` `instance()`、`:743` `logger()`、`:753` `getVersion()`、`:758` `getCfg()`、`:763` `getResearchCfg()`、`:768` `getItemCfg()`、`:780` `getGPSNetwork()`、`:785` `getTickerTask()`、`:795` `getLocalization()`、`:807` `getMinecraftRecipeService()`、`:812` `getItemDataService()`、`:817` `getItemTextureService()`、`:822` `getPermissionsService()`、`:827` `getBlockDataService()`、`:840` `getWorldSettingsService()`、`:851` `getHologramsService()`、`:862` `getSoundService()`、`:873` `getIntegrations()`、`:884` `getProtectionManager()`、`:894` `getUpdater()`、`:905` `getMetricsService()`、`:916` `getAnalyticsService()`、`:927` `getGitHubService()`、`:939` `getNetworkManager()`、`:944` `getRegistry()`、`:969` `getCommand()`、`:980` `getProfiler()`、`:990` `getMinecraftVersion()`、`:1001` `isNewlyInstalled()`、`:1014` `getInstalledAddons()`、`:1040/:1069` `runSync(...)`、`:1085` `getPlayerStorage()`、`:1095` `getThreadService()`。

`getInstalledAddons()` 的实现很有意思 —— 它**不查任何注册表**，只是把所有 (soft)depend 了 Slimefun 的插件当作 addon：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java:1014-1024
    public static @Nonnull Set<Plugin> getInstalledAddons() {
        validateInstance();
        String pluginName = instance.getName();

        // @formatter:off - Collect any Plugin that (soft)-depends on Slimefun
        return Arrays.stream(instance.getServer().getPluginManager().getPlugins()).filter(plugin -> {
            PluginDescriptionFile description = plugin.getDescription();
            return description.getDepend().contains(pluginName) || description.getSoftDepend().contains(pluginName);
        }).collect(Collectors.toSet());
        // @formatter:on
    }
```

### 1.9 监听器注册（45 个）

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java:612-673（节选）
    private void registerListeners() {
        // Old deprecated CS-CoreLib Listener
        new MenuListener(this);

        new SlimefunBootsListener(this);
        new SlimefunItemInteractListener(this);
        new SlimefunItemConsumeListener(this);
        new BlockPhysicsListener(this);
        new CargoNodeListener(this);
        new MultiBlockListener(this);
        ...
        new BlockListener(this);
        ...
        new NetworkListener(this, networkManager);
        ...
        // Item-specific Listeners
        new CoolerListener(this, (Cooler) SlimefunItems.COOLER.getItem());
        new SeismicAxeListener(this, (SeismicAxe) SlimefunItems.SEISMIC_AXE.getItem());
        new RadioactivityListener(this);
        new AncientAltarListener(this, (AncientAltar) SlimefunItems.ANCIENT_ALTAR.getItem(), (AncientPedestal) SlimefunItems.ANCIENT_PEDESTAL.getItem());
        grapplingHookListener.register(this, (GrapplingHook) SlimefunItems.GRAPPLING_HOOK.getItem());
        ...
        // Handle Slimefun Guide being given on Join
        new SlimefunGuideListener(this, config.getBoolean("guide.receive-on-first-join"));
        new PlayerProfileListener(this);
    }
```

**模式**：每个 `Listener` 的构造函数内部自己 `registerEvents`，主类只负责 `new`。新插件用 `Listener` 实例方法注册或 Paper 的 `LifecycleEventManager` 会更清晰。

---

## 2. 物品与方块抽象体系

### 2.1 `SlimefunItem` 基类

包路径：`io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem`
类签名（`:66`）：`public class SlimefunItem implements Placeable`

核心字段：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItem.java:66-115
public class SlimefunItem implements Placeable {

    /**
     * This is our item id.
     */
    private final String id;

    /**
     * This is the original {@link ItemStack} that represents this item.
     * It is immutable and should always be cloned, never used directly.
     */
    private final ItemStack itemStackTemplate;

    protected SlimefunAddon addon;

    private ItemState state = ItemState.UNREGISTERED;

    private ItemGroup itemGroup;
    private Research research;

    private ItemStack[] recipe;
    private RecipeType recipeType;
    protected ItemStack recipeOutput;

    protected boolean enchantable = true;
    protected boolean disenchantable = true;
    protected boolean hidden = false;
    protected boolean useableInWorkbench = false;

    private Optional<String> wikiURL = Optional.empty();

    private final OptionalMap<Class<? extends ItemHandler>, ItemHandler> itemHandlers = new OptionalMap<>(HashMap::new);
    private final Set<ItemSetting<?>> itemSettings = new HashSet<>();

    private boolean ticking = false;
    private BlockTicker blockTicker;
```

**`itemHandlers` 的数据结构选择是关键设计**：`OptionalMap<Class<? extends ItemHandler>, ItemHandler>` —— **每种 ItemHandler 类型最多只能有一个实例**。这就是为什么 `BlockTicker` 是「特殊案例」而不是列表（见 §2.1.2）。

主构造器（`:149-160`）：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItem.java:148-160
    @ParametersAreNonnullByDefault
    public SlimefunItem(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe, @Nullable ItemStack recipeOutput) {
        Validate.notNull(itemGroup, "'itemGroup' is not allowed to be null!");
        Validate.notNull(item, "'item' is not allowed to be null!");
        Validate.notNull(recipeType, "'recipeType' is not allowed to be null!");

        this.itemGroup = itemGroup;
        this.itemStackTemplate = item.item();
        this.id = item.getItemId();
        this.recipeType = recipeType;
        this.recipe = recipe;
        this.recipeOutput = recipeOutput;
    }
```

注意 `item.item()` 返回的是 **clone**（`SlimefunItemStack.java:336-338`），所以模板不可被外部污染。

`ItemState` 四态（`api/items/ItemState.java:13-34`）：`UNREGISTERED` / `ENABLED` / `DISABLED` / `VANILLA_FALLBACK`。

#### 2.1.1 `register(SlimefunAddon)` —— 唯一注册入口

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItem.java:428-512（节选）
    public void register(@Nonnull SlimefunAddon addon) {
        Validate.notNull(addon, "A SlimefunAddon cannot be null!");
        Validate.notNull(addon.getJavaPlugin(), "SlimefunAddon#getJavaPlugin() is not allowed to return null!");

        this.addon = addon;

        try {
            checkDependencies(addon);
            checkForConflicts();

            preRegister();

            if (recipe == null) {
                recipe = new ItemStack[9];
            } else if (recipe.length < 9) {
                recipe = Arrays.copyOf(recipe, 9);
            }

            Slimefun.getRegistry().getAllSlimefunItems().add(this);
            Slimefun.getRegistry().getSlimefunItemIds().put(id, this);

            // Items that are "not-configurable" cannot be configured.
            if (!(this instanceof NotConfigurable)) {
                Slimefun.getItemCfg().setDefaultValue(id + ".enabled", true);
                Slimefun.getItemCfg().setDefaultValue(id + ".can-be-used-in-workbenches", useableInWorkbench);
                Slimefun.getItemCfg().setDefaultValue(id + ".hide-in-guide", hidden);
                Slimefun.getItemCfg().setDefaultValue(id + ".allow-enchanting", enchantable);
                Slimefun.getItemCfg().setDefaultValue(id + ".allow-disenchanting", disenchantable);

                // Load all item settings
                for (ItemSetting<?> setting : itemSettings) {
                    setting.reload();
                }
            }
```

依赖校验与 id 冲突校验：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItem.java:583-598
    private void checkDependencies(@Nonnull SlimefunAddon addon) {
        if (!addon.hasDependency("Slimefun")) {
            throw new MissingDependencyException(addon, "Slimefun");
        }
    }

    /**
     * This method checks for id conflicts.
     */
    private void checkForConflicts() {
        SlimefunItem conflictingItem = getById(id);

        if (conflictingItem != null) {
            throw new IdConflictException(this, conflictingItem);
        }
    }
```

状态机与异步加载兜底（`:489-511`）：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItem.java:489-511
            // Now we can be certain this item should be enabled
            if (state == ItemState.ENABLED) {
                onEnable();
            } else {
                // Clear item handlers if we are disabled so that calling them isn't possible later on
                for (ItemHandler handler : this.itemHandlers.values()) {
                    if (handler instanceof BlockTicker) {
                        Slimefun.getRegistry().getTickerBlocks().remove(getId());
                    }
                }
                this.itemHandlers.clear();
            }

            postRegister();

            // handle runtime-registrations / auto-loading
            if (Slimefun.getRegistry().isAutoLoadingEnabled() && state == ItemState.ENABLED) {
                info("Item was registered during runtime.");
                load();
            }
        } catch (Exception x) {
            error("Registering " + toString() + " has failed!", x);
        }
    }
```

`onEnable()`（私有，`:520-549`）负责：注册 ItemGroup（若未注册）、检查堆叠数、加入 `enabledItems`、加载 handlers、登记放射性物品。

#### 2.1.2 `addItemHandler` —— 注册后不可再改

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItem.java:785-804
    public final void addItemHandler(ItemHandler... handlers) {
        Validate.notEmpty(handlers, "You cannot add zero handlers...");
        Validate.noNullElements(handlers, "You cannot add any 'null' ItemHandler!");

        // Make sure they are added before the item was registered.
        if (state != ItemState.UNREGISTERED) {
            throw new UnsupportedOperationException("You cannot add an ItemHandler after the SlimefunItem was registered.");
        }

        for (ItemHandler handler : handlers) {
            itemHandlers.put(handler.getIdentifier(), handler);

            // Tickers are a special case (at the moment at least)
            if (handler instanceof BlockTicker ticker) {
                ticking = true;
                Slimefun.getRegistry().getTickerBlocks().add(getId());
                blockTicker = ticker;
            }
        }
    }
```

`addItemHandler` 是 `final`，且**必须在 `register()` 之前调用** —— 这是最常见的 addon 报错来源。`BlockTicker` 被特殊处理：写入全服 `tickerBlocks` 集合，供 `BlockStorage` 在加载方块时决定是否 `enableTicker`。

扩展点：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItem.java:843-856
    public void preRegister() {
        // Override this method to execute code before the Item has been registered
        // Useful for calls to addItemHandler(...)
    }

    public void postRegister() {
        // Override this method to execute code after the Item has been registered
        // Useful for calls to Slimefun.getItemValue(...)
    }
```

`ItemHandler` 接口（`api/items/ItemHandler.java:28-55`）：`@FunctionalInterface`，只有 `getIdentifier()` 是必须实现的，`validate(SlimefunItem)` 默认返回 `Optional.empty()`。全部 handler 位于 `core/handlers/`（15 个）+ 遗留包 `me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker`。

调用 handler 的统一入口：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItem.java:915-930
    @ParametersAreNonnullByDefault
    public <T extends ItemHandler> boolean callItemHandler(Class<T> c, Consumer<T> callable) {
        Optional<ItemHandler> handler = itemHandlers.get(c);

        if (handler.isPresent()) {
            try {
                callable.accept(c.cast(handler.get()));
            } catch (Exception | LinkageError x) {
                error("Could not pass \"" + c.getSimpleName() + "\" for " + toString(), x);
            }

            return true;
        }

        return false;
    }
```

返回值语义：**「是否找到了 handler」**，不是「handler 执行结果」。多处调用者依赖这个语义（如 `SlimefunItemInteractListener` 用它决定是否打开 GUI）。

### 2.2 `SlimefunItemStack`

包路径：`io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack`（575 行）

它不是 `ItemStack` 的子类（`ItemStack` 是 final），而是**装饰器**，内部持有 `delegate`：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItemStack.java:57-63
public class SlimefunItemStack {
    private ItemStack delegate;

    private String id;
    private ItemMetaSnapshot itemMetaSnapshot;

    private String texture = null;
```

**最重要的构造器** —— id 校验 + 写入 PDC + 读取纹理，一步到位：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItemStack.java:65-83
    public SlimefunItemStack(@Nonnull String id, @Nonnull ItemStack item) {
        delegate = new ItemStack(item);

        Validate.notNull(id, "The item id must never be null!");
        Validate.isTrue(id.equals(id.toUpperCase(Locale.ROOT)), "Slimefun Item Ids must be uppercase! (e.g. 'MY_ITEM_ID')");

        if (Slimefun.instance() == null) {
            throw new PrematureCodeException("A SlimefunItemStack must never be be created before your Plugin was enabled.");
        }

        this.id = id;

        ItemMeta meta = delegate.getItemMeta();

        Slimefun.getItemDataService().setItemData(meta, id);
        Slimefun.getItemTextureService().setTexture(meta, id);

        setItemMeta(meta);
    }
```

三个强制约束：
1. **id 必须全大写**（`Validate.isTrue`，违反直接抛异常）。
2. **不能在 `onEnable()` 之前构造**（`PrematureCodeException`）—— 因为要用 `Slimefun.getItemDataService()`。这意味着**任何 `static final SlimefunItemStack` 字段的类都不能被提前类加载**。
3. `setItemMeta` 同时缓存一份 `ItemMetaSnapshot`（`:255-259`），用于快速读显示名而不必反复 `getItemMeta()`：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItemStack.java:255-259
    public boolean setItemMeta(ItemMeta meta) {
        itemMetaSnapshot = new ItemMetaSnapshot(meta);

        return delegate.setItemMeta(meta);
    }
```

纹理支持两种输入：Base64 或纯 hex 字符串（后者被自动包装成 textures.minecraft.net URL 再 Base64）：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItemStack.java:291-303
    private static @Nonnull String getTexture(@Nonnull String id, @Nonnull String texture) {
        Validate.notNull(id, "The id cannot be null");
        Validate.notNull(texture, "The texture cannot be null");

        if (texture.startsWith("ey")) {
            return texture;
        } else if (CommonPatterns.HEXADECIMAL.matcher(texture).matches()) {
            String value = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + texture + "\"}}}";
            return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        } else {
            throw new IllegalArgumentException("The provided texture for Item \"" + id + "\" does not seem to be a valid texture String!");
        }
    }
```

`item()` 返回 clone（`:336-338`），`equals`/`hashCode` 被 `final` 锁死为 `Object` 默认实现（`:318-331`）—— 即**按引用比较**，防止子类改写导致集合行为异常。这是有意的防御性设计。

### 2.3 持久化方案：**Paper PDC（PersistentDataContainer）**，不是原始 NBT

这是「Slimefun4 现在用什么方式标记自定义物品」的准确答案。

#### 2.3.1 `CustomItemDataService` —— 物品标记

包路径：`io.github.thebusybiscuit.slimefun4.core.services.CustomItemDataService`

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/core/services/CustomItemDataService.java:31-55
public class CustomItemDataService implements Keyed {

    /**
     * This is the {@link NamespacedKey} used to store/read data.
     */
    private final NamespacedKey namespacedKey;

    public CustomItemDataService(@Nonnull Plugin plugin, @Nonnull String key) {
        // Null-Validation is performed in the NamespacedKey constructor
        namespacedKey = new NamespacedKey(plugin, key);
    }

    @Override
    public NamespacedKey getKey() {
        return namespacedKey;
    }
```

key 为 `slimefun:slimefun_item`（来自 `Slimefun.java:174`）。写入与读取：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/core/services/CustomItemDataService.java:84-90
    public void setItemData(@Nonnull ItemMeta meta, @Nonnull String id) {
        Validate.notNull(meta, "The ItemMeta cannot be null!");
        Validate.notNull(id, "Cannot store null on an ItemMeta!");

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(namespacedKey, PersistentDataType.STRING, id);
    }
```

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/core/services/CustomItemDataService.java:102-124
    public @Nonnull Optional<String> getItemData(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return Optional.empty();
        }

        return getItemData(item.getItemMeta());
    }

    public @Nonnull Optional<String> getItemData(@Nonnull ItemMeta meta) {
        Validate.notNull(meta, "Cannot read data from null!");

        PersistentDataContainer container = meta.getPersistentDataContainer();
        return Optional.ofNullable(container.get(namespacedKey, PersistentDataType.STRING));
    }
```

配套的身份比较（用于「两个物品是不是同一个自定义物品」）：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/core/services/CustomItemDataService.java:138-159
    public boolean hasEqualItemData(@Nonnull ItemMeta meta1, @Nonnull ItemMeta meta2) {
        Validate.notNull(meta1, "Cannot read data from null (first arg)");
        Validate.notNull(meta2, "Cannot read data from null (second arg)");

        Optional<String> data1 = getItemData(meta1);

        // Check if the first data is present
        if (data1.isPresent()) {
            // Only retrieve the second data where necessary.
            Optional<String> data2 = getItemData(meta2);

            /*
             * Check if both are present and equal.
             * Optional#equals(...) compares their values, so no need
             * to call Optional#get() here.
             */
            return data2.isPresent() && data1.equals(data2);
        } else {
            // No value present, we can return immediately.
            return false;
        }
    }
```

#### 2.3.2 `BlockDataService` —— 方块标记（TileEntity 快路径）

包路径：`io.github.thebusybiscuit.slimefun4.core.services.BlockDataService`

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/core/services/BlockDataService.java:35-51（节选）
public class BlockDataService implements Keyed {

    private final NamespacedKey namespacedKey;

    public BlockDataService(@Nonnull Plugin plugin, @Nonnull String key) {
        namespacedKey = new NamespacedKey(plugin, key);
    }
```

key 为 `slimefun:slimefun_block`（`Slimefun.java:175`）。写入：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/core/services/BlockDataService.java:66-90（节选）
    public void setBlockData(@Nonnull Block b, @Nonnull String value) {
        Validate.notNull(b, "The block cannot be null!");
        Validate.notNull(value, "The value cannot be null!");

        /**
         * Don't use PaperLib here, it seems to be quite buggy in block-placing scenarios
         * and it would be too tedious to check for individual build versions to circumvent this.
         */
        BlockState state = b.getState();

        if (state instanceof TileState tileState) {
            try {
                PersistentDataContainer container = tileState.getPersistentDataContainer();
                container.set(namespacedKey, PersistentDataType.STRING, value);
                state.update();
            } catch (Exception x) { ... }
        }
    }
```

**性能关键点**：`Block#getState()` 很贵，所以作者用 `SlimefunTag.TILE_ENTITIES` 做一次廉价的 Material 判别，先筛掉不可能有 PDC 的方块：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/core/services/BlockDataService.java:122-142
    /**
     * This method checks whether the given {@link Material} is a Tile Entity.
     * ...
     * Due to {@link Block#getState()} being a very expensive call performance-wise though,
     * this simple lookup method is used instead.
     */
    public boolean isTileEntity(@Nullable Material type) {
        if (type == null || type.isAir()) {
            // Cannot store data on air
            return false;
        }

        return SlimefunTag.TILE_ENTITIES.isTagged(type);
    }
```

#### 2.3.3 双写策略：PDC 是快路径，YAML 是真相源

放置时**两处都写**：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/BlockListener.java:124-131
                } else {
                    if (Slimefun.getBlockDataService().isTileEntity(block.getType())) {
                        Slimefun.getBlockDataService().setBlockData(block, sfItem.getId());
                    }

                    BlockStorage.addBlockInfo(block, "id", sfItem.getId(), true);
                    sfItem.callItemHandler(BlockPlaceHandler.class, handler -> handler.onPlayerPlace(e));
                }
```

读取时优先 PDC，但**限定主线程**：

```java
// src/main/java/me/mrCookieSlime/Slimefun/api/BlockStorage.java:763-780
    @Nullable
    public static String checkID(@Nonnull Block b) {
        // Only access the BlockState when on the main thread
        if (Bukkit.isPrimaryThread() && Slimefun.getBlockDataService().isTileEntity(b.getType())) {
            Optional<String> blockData = Slimefun.getBlockDataService().getBlockData(b);

            if (blockData.isPresent()) {
                return blockData.get();
            }
        }

        return checkID(b.getLocation());
    }

    @Nullable
    public static String checkID(@Nonnull Location l) {
        return getLocationInfo(l, "id");
    }
```

**为什么这么做**：异步 Ticker 线程不能碰 `BlockState`（Paper/Spigot 的 `getState()` 在非主线程访问方块会抛异常或造成 chunk 加载），所以异步路径自动退化到内存 `ConcurrentHashMap<Location, Config>` 查表。这个「PDC 快路径只在主线程」的设计是理解整个方块系统的钥匙。

#### 2.3.4 迁移到 Paper 1.21+ 的建议

本地检出的 Paper API（`D:\Projects\HigherRedStoneFun\.research\paper-api-sources`）显示：

- `ItemMeta` 已继承 `PersistentDataHolder`（`org/bukkit/inventory/meta/ItemMeta.java:44`），PDC 方案**完全兼容且仍是推荐做法**。
- 但 `ItemStack` 现在直接提供 PDC 读写（`org/bukkit/inventory/ItemStack.java:73`、`:86`）：

```java
// .research/paper-api-sources/org/bukkit/inventory/ItemStack.java:77-88
    /**
     * Edits the {@link PersistentDataContainer} of this stack. The
     * {@link PersistentDataContainer} instance is only valid inside the
     * consumer.
     *
     * @param consumer the persistent data container consumer
     * @return {@code true} if the edit was successful, {@code false} otherwise.
     */
    public boolean editPersistentDataContainer(@NotNull Consumer<PersistentDataContainer> consumer) {
        return this.craftDelegate.editPersistentDataContainer(consumer);
    }
```

- 同时 Paper 引入了 **DataComponent API**（`io.papermc.paper.datacomponent.DataComponentTypes`，见 `.research/paper-api-sources/io/papermc/paper/datacomponent/DataComponentTypes.java:102-403`），可直接操作 `CUSTOM_MODEL_DATA`、`ITEM_NAME`、`LORE`、`TOOLTIP_DISPLAY` 等。

**新插件的推荐取舍**：
| 需求 | 推荐方案 |
|---|---|
| 标记「这是哪个自定义物品」 | **PDC**（`custom_item_id` 之类），跨版本稳定、可与原版物品共存、NBT 层面不冲突 |
| 自定义显示名 / Lore | **Adventure `Component`**（`ItemMeta#displayName(Component)` / `lore(List<Component>)`），不要用 `setDisplayName(String)`（Paper 已标 `@Deprecated`，`ItemMeta.java:115/139/281/306`） |
| 自定义模型 | **DataComponent `CUSTOM_MODEL_DATA`** 或 `CUSTOM_MODEL_DATA` + 资源包，优于旧的 `CustomModelData` 整数 |
| 隐藏 tooltip 部件 | **DataComponent `TOOLTIP_DISPLAY`**，优于逐个 `ItemFlag` |

### 2.4 `ItemGroup` 与分类注册

包路径：`io.github.thebusybiscuit.slimefun4.api.items.ItemGroup`（388 行），子类在 `api.items.groups`。

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/ItemGroup.java:40-48
public class ItemGroup implements Keyed {

    private SlimefunAddon addon;

    protected final List<SlimefunItem> items = new ArrayList<>();
    protected final NamespacedKey key;
    protected final ItemStack item;
    protected int tier;
    protected boolean crossAddonItemGroup = false;
```

构造器默认 tier=3，并强制隐藏属性/附魔提示：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/ItemGroup.java:78-92（节选）
    @ParametersAreNonnullByDefault
    public ItemGroup(NamespacedKey key, ItemStack item, int tier) {
        Validate.notNull(key, "An item group's NamespacedKey must not be null!");
        Validate.notNull(item, "An item group's ItemStack must not be null!");

        this.item = item;
        this.key = key;

        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(VersionedItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        this.item.setItemMeta(meta);
        this.tier = tier;
    }
```

注册（**没有 ItemGroupRegistry，直接写 registry 的 list 再按 tier 排序**）：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/ItemGroup.java:108-129
    public void register(@Nonnull SlimefunAddon addon) {
        Validate.notNull(addon, "The Addon cannot be null");

        if (isRegistered()) {
            throw new UnsupportedOperationException("This ItemGroup has already been registered!");
        }

        this.addon = addon;

        Slimefun.getRegistry().getAllItemGroups().add(this);
        sortCategoriesByTier();
    }

    public boolean isRegistered() {
        return this.addon != null && Slimefun.getRegistry().getAllItemGroups().contains(this);
    }
```

**跨 addon 写入会告警**（防止 addon 往别人的分类里塞东西）：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/ItemGroup.java:181-194
    public void add(@Nonnull SlimefunItem item) {
        Validate.notNull(item, "Cannot add null Items to an ItemGroup!");

        if (items.contains(item)) {
            // Ignore duplicate entries
            return;
        }

        if (isRegistered() && !isCrossAddonItemGroup() && !item.getAddon().getName().equals(this.addon.getName())) {
            item.warn("This item does not belong into ItemGroup " + this + " as that group belongs to " + this.addon.getName());
        }

        items.add(item);
    }
```

可见性规则 —— 空组或全是禁用物品的组不显示：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/ItemGroup.java:311-328
    public boolean isVisible(@Nonnull Player p) {
        if (items.isEmpty() || !isAccessible(p)) {
            return false;
        }

        for (SlimefunItem slimefunItem : getItems()) {
            /*
             * If any item for this item group is visible,
             * the item group itself is also visible.
             * Empty item groups are not displayed.
             */
            if (!slimefunItem.isHidden() && !slimefunItem.isDisabledIn(p.getWorld())) {
                return true;
            }
        }

        return false;
    }
```

变体族（`api/items/groups/`，共 6 个文件）：

| 类 | 作用 |
|---|---|
| `LockedItemGroup` | 需先解锁父组全部研究才可见（`hasUnlocked`，`LockedItemGroup.java:153-167`） |
| `SeasonalItemGroup` | 按月份过滤（`isAccessible`，`SeasonalItemGroup.java:58-66`） |
| `FlexItemGroup` | 抽象，自定义渲染整页；`isVisible` 被 `final` 锁死返回 `true`，改用 `isVisible(p, profile, layout)` |
| `NestedItemGroup` | `FlexItemGroup` 子类，含 `SubItemGroup`，`GROUP_SIZE = 36` |
| `SubItemGroup` | 永远不可见但可被搜索（`isVisible` 返回 `false`，`isAccessible` 返回 `true`） |

`FlexItemGroup` 禁用了父类的物品操作：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/groups/FlexItemGroup.java:62-97（节选）
    @ParametersAreNonnullByDefault
    public abstract boolean isVisible(Player p, PlayerProfile profile, SlimefunGuideMode layout);

    public abstract void open(Player p, PlayerProfile profile, SlimefunGuideMode layout);

    @Override
    public final void add(@Nonnull SlimefunItem item) {
        throw new UnsupportedOperationException("You cannot add items to a FlexItemGroup!");
    }

    @Override
    public final @Nonnull List<SlimefunItem> getItems() {
        throw new UnsupportedOperationException("A FlexItemGroup has no items!");
    }
```

#### `SlimefunItems` 的注册方式 —— **它只存 ItemStack，不注册**

这是最容易误解的地方。`io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems` **只声明 `SlimefunItemStack` 常量**，没有任何 `register(...)` 或 `createItemGroup(...)`：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/SlimefunItems.java:28-45（节选）
/**
 * This class holds a static references to every {@link SlimefunItemStack}
 * found in Slimefun.
 */
@SuppressWarnings("java:S1192") // Suppress "duplicate string literal" warnings
public final class SlimefunItems {

    private SlimefunItems() {}

    /* Items */
    public static final SlimefunItemStack PORTABLE_CRAFTER = new SlimefunItemStack("PORTABLE_CRAFTER", HeadTexture.PORTABLE_CRAFTER, "&6Portable Crafter", "&a&oA portable Crafting Table", "", LoreBuilder.RIGHT_CLICK_TO_OPEN);
    public static final SlimefunItemStack ENERGY_REGULATOR = new SlimefunItemStack("ENERGY_REGULATOR", HeadTexture.ENERGY_REGULATOR, "&6Energy Regulator", "", "&fCore Component of an Energy Network");
    public static final SlimefunItemStack ENERGY_CONNECTOR = new SlimefunItemStack("ENERGY_CONNECTOR", HeadTexture.ENERGY_CONNECTOR, "&eEnergy Connector", LoreBuilder.range(6), "", "&fPlace this between machines", "&fand generators to connect them", "&fto your regulator.");
```

它用 `static {}` 块做属性后处理：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/SlimefunItems.java:191-198
    static {
        GRANDMAS_WALKING_STICK.addUnsafeEnchantment(Enchantment.KNOCKBACK, 2);
        GRANDPAS_WALKING_STICK.addUnsafeEnchantment(Enchantment.KNOCKBACK, 5);

        BLADE_OF_VAMPIRES.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2);
        BLADE_OF_VAMPIRES.addUnsafeEnchantment(VersionedEnchantment.UNBREAKING, 4);
        BLADE_OF_VAMPIRES.addUnsafeEnchantment(Enchantment.SHARPNESS, 2);
    }
```

真正的注册在 `implementation.setup.SlimefunItemSetup.setup(plugin)`，模式是「一行一个 `new XxxItem(...).register(plugin)`」：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/SlimefunItemSetup.java:227-256（节选）
public final class SlimefunItemSetup {

    private static boolean registeredItems = false;

    private SlimefunItemSetup() {}

    public static void setup(@Nonnull Slimefun plugin) {
        if (registeredItems) {
            throw new UnsupportedOperationException("Slimefun Items can only be registered once!");
        }

        registeredItems = true;
        DefaultItemGroups itemGroups = new DefaultItemGroups();

        // @formatter:off (We will need to refactor this one day)
        new SlimefunItem(itemGroups.weapons, SlimefunItems.GRANDMAS_WALKING_STICK, RecipeType.ENHANCED_CRAFTING_TABLE,
                new ItemStack[] {null, new ItemStack(Material.OAK_LOG), null, null, new ItemStack(Material.OAK_LOG), null, null, new ItemStack(Material.OAK_LOG), null})
                .register(plugin);
        ...
        new PortableCrafter(itemGroups.usefulItems, SlimefunItems.PORTABLE_CRAFTER, RecipeType.ENHANCED_CRAFTING_TABLE,
                new ItemStack[] {new ItemStack(Material.BOOK), new ItemStack(Material.CRAFTING_TABLE), null, null, null, null, null, null, null})
                .register(plugin);
```

带参数链式设置的代表性例子：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/SlimefunItemSetup.java:2707-2712
        new ProduceCollector(itemGroups.electricity, SlimefunItems.PRODUCE_COLLECTOR, RecipeType.ENHANCED_CRAFTING_TABLE,
                new ItemStack[] {null, new ItemStack(Material.HAY_BLOCK), null, new ItemStack(Material.BUCKET), SlimefunItems.MEDIUM_CAPACITOR.item(), new ItemStack(Material.BUCKET), SlimefunItems.ALUMINUM_BRASS_INGOT.item(), SlimefunItems.ELECTRIC_MOTOR.item(), SlimefunItems.ALUMINUM_BRASS_INGOT.item()})
                .setCapacity(256)
                .setProcessingSpeed(1)
                .setEnergyConsumption(16)
                .register(plugin);
```

> 注意 `register()` 返回 `void`，所以 `.setCapacity(...)` 这类返回 `this` 的方法必须在 `register` **之前**调用。这不是 fluent API，只是普通方法链。

内置 ItemGroup 声明在 `implementation.setup.DefaultItemGroups`（**package-private**，addon 不可复用）：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/DefaultItemGroups.java:33-58（节选）
class DefaultItemGroups {

    // Standard Item Groups
    protected final ItemGroup weapons = new ItemGroup(new NamespacedKey(Slimefun.instance(), "weapons"), CustomItemStack.create(SlimefunItems.BLADE_OF_VAMPIRES.item(), "&7Weapons"), 1);
    protected final ItemGroup basicMachines = new ItemGroup(new NamespacedKey(Slimefun.instance(), "basic_machines"), CustomItemStack.create(SlimefunItems.ENHANCED_CRAFTING_TABLE.item(), "&7Basic Machines"), 1);
    ...
    // Locked Item Groups
    protected final LockedItemGroup electricity = new LockedItemGroup(new NamespacedKey(Slimefun.instance(), "electricity"), CustomItemStack.create(SlimefunItems.NUCLEAR_REACTOR.item(), "&bEnergy and Electricity"), 4, basicMachines.getKey());
    protected final LockedItemGroup androids = new LockedItemGroup(new NamespacedKey(Slimefun.instance(), "androids"), CustomItemStack.create(SlimefunItems.PROGRAMMABLE_ANDROID.item(), "&cProgrammable Androids"), 4, basicMachines.getKey());
    protected final ItemGroup cargo = new LockedItemGroup(new NamespacedKey(Slimefun.instance(), "cargo"), CustomItemStack.create(SlimefunItems.CARGO_MANAGER.item(), "&cCargo Management"), 4, basicMachines.getKey());
```

### 2.5 注册表 `SlimefunRegistry` 与识别/反查机制

**`SlimefunItemRegistry` 这个类不存在**。唯一注册表是 `io.github.thebusybiscuit.slimefun4.core.SlimefunRegistry`：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/core/SlimefunRegistry.java:54-94（节选）
public final class SlimefunRegistry {

    private final Map<String, SlimefunItem> slimefunIds = new HashMap<>();
    private final List<SlimefunItem> slimefunItems = new ArrayList<>();
    private final List<SlimefunItem> enabledItems = new ArrayList<>();

    private final List<ItemGroup> categories = new ArrayList<>();
    private final List<MultiBlock> multiblocks = new LinkedList<>();

    private final List<Research> researches = new LinkedList<>();
    private final List<String> researchRanks = new ArrayList<>();
    private final Set<UUID> researchingPlayers = Collections.synchronizedSet(new HashSet<>());
    ...
    private final Set<String> tickers = new HashSet<>();
    private final Set<SlimefunItem> radioactive = new HashSet<>();
    private final Set<ItemStack> barterDrops = new HashSet<>();
    ...
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, BlockStorage> worlds = new ConcurrentHashMap<>();
    private final Map<String, BlockInfoConfig> chunks = new HashMap<>();
    private final Map<SlimefunGuideMode, SlimefunGuideImplementation> guides = new EnumMap<>(SlimefunGuideMode.class);
    private final Map<EntityType, Set<ItemStack>> mobDrops = new HashMap<>();

    private final Map<String, BlockMenuPreset> blockMenuPresets = new HashMap<>();
    private final Map<String, UniversalBlockMenu> universalInventories = new HashMap<>();
    private final Map<Class<? extends ItemHandler>, Set<ItemHandler>> globalItemHandlers = new HashMap<>();
```

**注意线程安全的不一致性**：`profiles`/`worlds` 是 `ConcurrentHashMap`（因为要被异步 Ticker 与自动保存访问），而 `chunks`/`universalInventories`/`blockMenuPresets` 是普通 `HashMap`。这是 §7 要讨论的风险点。

反查走静态方法（**没有 `getItem(String)`，叫 `getById`**）：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItem.java:1140-1184（节选）
    public static @Nullable SlimefunItem getById(@Nonnull String id) {
        return Slimefun.getRegistry().getSlimefunItemIds().get(id);
    }

    public static @Nonnull Optional<SlimefunItem> getOptionalById(@Nonnull String id) {
        return Optional.ofNullable(getById(id));
    }

    public static @Nullable SlimefunItem getByItem(@Nullable SlimefunItemStack slimefunItemStack) {
        if (slimefunItemStack == null) {
            return null;
        }

        var delegate = slimefunItemStack.item();
        if (delegate.getType() == Material.AIR) {
            return null;
        }

        return getById(slimefunItemStack.getItemId());
    }

    /**
     * Retrieve a {@link SlimefunItem} from an {@link ItemStack}.
     */
    public static @Nullable SlimefunItem getByItem(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        Optional<String> itemID = Slimefun.getItemDataService().getItemData(item);

        return itemID.map(SlimefunItem::getById).orElse(null);
    }
```

**完整识别链路**（这是最核心的反查机制）：

```
任意 ItemStack
  → Slimefun.getItemDataService().getItemData(item)   // 读 PDC: slimefun_item
  → Optional<String> id
  → SlimefunItem.getById(id)                          // HashMap 查表
  → SlimefunItem 实例
  → item.getBlockTicker() / callItemHandler(...)
```

`SlimefunItem.isItem(ItemStack)` 提供了实例侧的比较：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItem.java:751-765
    public boolean isItem(@Nullable ItemStack item) {
        if (item == null) {
            return false;
        }

        if (item.hasItemMeta()) {
            Optional<String> itemId = Slimefun.getItemDataService().getItemData(item);

            if (itemId.isPresent()) {
                return getId().equals(itemId.get());
            }
        }

        return false;
    }
```

方块的识别在 `BlockStorage`：

```java
// src/main/java/me/mrCookieSlime/Slimefun/api/BlockStorage.java:746-761
    @Nullable
    public static SlimefunItem check(@Nonnull Block b) {
        String id = checkID(b);
        return id == null ? null : SlimefunItem.getById(id);
    }

    @Nullable
    public static SlimefunItem check(@Nonnull Location l) {
        String id = checkID(l);
        return id == null ? null : SlimefunItem.getById(id);
    }

    public static boolean check(Block block, String slimefunItem) {
        String id = checkID(block);
        return id != null && id.equals(slimefunItem);
    }
```

`SlimefunItem` 的 `equals`/`hashCode` 只按 `id`（`:1120-1131`），所以「同一 id 的物品对象」在集合中会互相覆盖。

### 2.6 配方类型 `RecipeType` 体系

包路径：`io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType` —— **该包只有 2 个文件**（`RecipeType.java` + `package-info.java`）。

**它不是枚举、不是接口，而是普通类**，类头带官方 TODO：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/recipes/RecipeType.java:35-36
// TODO: Remove this class and rewrite the recipe system
public class RecipeType implements Keyed {
```

**grep `extends RecipeType` → 零匹配**。所有配方类型都是本类里的 `public static final` 常量，共 26 个。

字段与关键构造器：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/recipes/RecipeType.java:72-115（节选）
    private final ItemStack item;
    private final NamespacedKey key;
    private final String machine;
    private BiConsumer<ItemStack[], ItemStack> consumer;

    private RecipeType() {
        this.item = null;
        this.machine = "";
        this.key = new NamespacedKey(Slimefun.instance(), "null");
    }

    public RecipeType(NamespacedKey key, ItemStack item, BiConsumer<ItemStack[], ItemStack> callback, String... lore) {
        this.item = CustomItemStack.create(item, null, lore);
        this.key = key;
        this.consumer = callback;

        Optional<String> itemId = Slimefun.getItemDataService().getItemData(item);
        this.machine = itemId.orElse("");
    }

    public RecipeType(NamespacedKey key, ItemStack item) {
        this(key, item, null);
    }
```

**`RecipeType` 一身三职**：
1. Guide 里显示的「机器图标 + 说明文字」（`item` + `lore`）。
2. 注册时把配方转交给真正的机器（`register(...)`）。
3. 作为 `Keyed` 身份用于本地化（`recipes.yml` 里按 `namespace.key` 查）。

#### 全部 26 个常量

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/recipes/RecipeType.java:38-70
    public static final RecipeType MULTIBLOCK = new RecipeType(new NamespacedKey(Slimefun.instance(), "multiblock"), CustomItemStack.create(Material.BRICKS, "&bMultiBlock", "", "&a&oBuild it in the World"));
    public static final RecipeType ARMOR_FORGE = new RecipeType(new NamespacedKey(Slimefun.instance(), "armor_forge"), SlimefunItems.ARMOR_FORGE, "", "&a&oCraft it in an Armor Forge");
    public static final RecipeType GRIND_STONE = new RecipeType(new NamespacedKey(Slimefun.instance(), "grind_stone"), SlimefunItems.GRIND_STONE, "", "&a&oGrind it using the Grind Stone");
    public static final RecipeType SMELTERY = new RecipeType(new NamespacedKey(Slimefun.instance(), "smeltery"), SlimefunItems.SMELTERY, "", "&a&oSmelt it using a Smeltery");
    public static final RecipeType ORE_CRUSHER = new RecipeType(new NamespacedKey(Slimefun.instance(), "ore_crusher"), SlimefunItems.ORE_CRUSHER, "", "&a&oCrush it using the Ore Crusher");
    public static final RecipeType GOLD_PAN = new RecipeType(new NamespacedKey(Slimefun.instance(), "gold_pan"), SlimefunItems.GOLD_PAN, "", "&a&oUse a Gold Pan on Gravel to obtain this Item");
    public static final RecipeType COMPRESSOR = new RecipeType(new NamespacedKey(Slimefun.instance(), "compressor"), SlimefunItems.COMPRESSOR, "", "&a&oCompress it using the Compressor");
    public static final RecipeType PRESSURE_CHAMBER = new RecipeType(new NamespacedKey(Slimefun.instance(), "pressure_chamber"), SlimefunItems.PRESSURE_CHAMBER, "", "&a&oCompress it using the Pressure Chamber");
    public static final RecipeType MAGIC_WORKBENCH = new RecipeType(new NamespacedKey(Slimefun.instance(), "magic_workbench"), SlimefunItems.MAGIC_WORKBENCH, "", "&a&oCraft it in a Magic Workbench");
    public static final RecipeType ORE_WASHER = new RecipeType(new NamespacedKey(Slimefun.instance(), "ore_washer"), SlimefunItems.ORE_WASHER, "", "&a&oWash it in an Ore Washer");
    public static final RecipeType ENHANCED_CRAFTING_TABLE = new RecipeType(new NamespacedKey(Slimefun.instance(), "enhanced_crafting_table"), SlimefunItems.ENHANCED_CRAFTING_TABLE, "", "&a&oA regular Crafting Table cannot", "&a&ohold this massive Amount of Power...");
    public static final RecipeType JUICER = new RecipeType(new NamespacedKey(Slimefun.instance(), "juicer"), SlimefunItems.JUICER, "", "&a&oUsed for Juice Creation");

    public static final RecipeType ANCIENT_ALTAR = new RecipeType(new NamespacedKey(Slimefun.instance(), "ancient_altar"), SlimefunItems.ANCIENT_ALTAR.item(), (recipe, output) -> {
        AltarRecipe altarRecipe = new AltarRecipe(Arrays.asList(recipe), output);
        AncientAltar altar = ((AncientAltar) SlimefunItems.ANCIENT_ALTAR.getItem());
        altar.getRecipes().add(altarRecipe);
    });

    public static final RecipeType MOB_DROP = new RecipeType(new NamespacedKey(Slimefun.instance(), "mob_drop"), CustomItemStack.create(Material.IRON_SWORD, "&bMob Drop"), RecipeType::registerMobDrop, "", "&rKill the specified Mob to obtain this Item");
    public static final RecipeType BARTER_DROP = new RecipeType(new NamespacedKey(Slimefun.instance(), "barter_drop"), CustomItemStack.create(Material.GOLD_INGOT, "&bBarter Drop"), RecipeType::registerBarterDrop, "&aBarter with piglins for a chance", "&ato obtain this item");
    public static final RecipeType INTERACT = new RecipeType(new NamespacedKey(Slimefun.instance(), "interact"), CustomItemStack.create(Material.PLAYER_HEAD, "&bInteract", "", "&a&oRight click with this item"));

    public static final RecipeType HEATED_PRESSURE_CHAMBER = new RecipeType(new NamespacedKey(Slimefun.instance(), "heated_pressure_chamber"), SlimefunItems.HEATED_PRESSURE_CHAMBER);
    public static final RecipeType FOOD_FABRICATOR = new RecipeType(new NamespacedKey(Slimefun.instance(), "food_fabricator"), SlimefunItems.FOOD_FABRICATOR);
    public static final RecipeType FOOD_COMPOSTER = new RecipeType(new NamespacedKey(Slimefun.instance(), "food_composter"), SlimefunItems.FOOD_COMPOSTER);
    public static final RecipeType FREEZER = new RecipeType(new NamespacedKey(Slimefun.instance(), "freezer"), SlimefunItems.FREEZER);
    public static final RecipeType REFINERY = new RecipeType(new NamespacedKey(Slimefun.instance(), "refinery"), SlimefunItems.REFINERY);

    public static final RecipeType GEO_MINER = new RecipeType(new NamespacedKey(Slimefun.instance(), "geo_miner"), SlimefunItems.GEO_MINER);
    public static final RecipeType NUCLEAR_REACTOR = new RecipeType(new NamespacedKey(Slimefun.instance(), "nuclear_reactor"), SlimefunItems.NUCLEAR_REACTOR);

    public static final RecipeType NULL = new RecipeType();
```

**三类语义**：
| 类别 | 示例 | `machine` 字段 | `consumer` |
|---|---|---|---|
| 多块机器工作台 | `ENHANCED_CRAFTING_TABLE`、`GRIND_STONE`、`SMELTERY` | 机器 id（从物品 PDC 反推） | null |
| 自定义回调 | `ANCIENT_ALTAR`、`MOB_DROP`、`BARTER_DROP` | 依 item 而定 | 非 null lambda |
| 无实际配方 | `NULL`、`INTERACT` | `""` | null |

#### 配方如何落到机器上

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/recipes/RecipeType.java:117-139
    public void register(ItemStack[] recipe, ItemStack result) {
        if (consumer != null) {
            consumer.accept(recipe, result);
        } else {
            SlimefunItem slimefunItem = SlimefunItem.getById(this.machine);

            if (slimefunItem instanceof MultiBlockMachine mbm) {
                mbm.addRecipe(recipe, result);
            }
        }
    }

    public @Nullable ItemStack toItem() {
        return this.item;
    }

    public @Nonnull ItemStack getItem(Player p) {
        return Slimefun.getLocalization().getRecipeTypeItem(p, this);
    }

    public SlimefunItem getMachine() {
        return SlimefunItem.getById(machine);
    }
```

调用点在 `SlimefunItem.load()`：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/items/SlimefunItem.java:767-776
    /**
     * This method is used for internal purposes only.
     */
    public void load() {
        if (!hidden) {
            itemGroup.add(this);
        }

        recipeType.register(recipe, getRecipeOutput());
    }
```

`load()` 由 `PostSetup.loadItems()` 在启动任务里统一调用：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/PostSetup.java:63-84（节选）
    public static void loadItems() {
        Iterator<SlimefunItem> iterator = Slimefun.getRegistry().getEnabledSlimefunItems().iterator();

        while (iterator.hasNext()) {
            SlimefunItem item = iterator.next();

            if (item == null) {
                Slimefun.logger().log(Level.WARNING, "Removed bugged Item ('NULL?')");
                iterator.remove();
            } else {
                try {
                    item.load();
                } catch (Exception | LinkageError x) {
                    item.error("Failed to properly load this Item", x);
                }
            }
        }

        Bukkit.getPluginManager().callEvent(new SlimefunItemRegistryFinalizedEvent());
        
        loadOreGrinderRecipes();
        loadSmelteryRecipes();
```

并在最后打开运行时自动加载（这样 `onEnable` 之后注册的 addon 物品也能立刻 `load()`）：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/PostSetup.java:113-115
        Slimefun.getItemCfg().save();
        Slimefun.getResearchCfg().save();
        Slimefun.getRegistry().setAutoLoadingMode(true);
```

**生命周期事件 `SlimefunItemRegistryFinalizedEvent`**（`api/events/SlimefunItemRegistryFinalizedEvent.java:19`）是 addon 做「所有物品都 load 完」后处理的官方钩子。

#### `RecipeType` 不描述 GUI

这是关键认知：**`RecipeType` 完全不声明 GUI 尺寸或槽位**。槽位由机器类自己声明。Guide 里那个 3×3 展示区由指南实现类写死：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/SurvivalSlimefunGuide.java:70-72
    private static final int MAX_ITEM_GROUPS = 36;

    private final int[] recipeSlots = { 3, 4, 5, 12, 13, 14, 21, 22, 23 };
```

#### 真实的配方查找 API（不存在 `findRecipe(BlockMenu, SlimefunItemStack)`）

**(a) 多块机器** —— `RecipeType` 上的 4 个静态工具方法。配方以「输入/输出」成对存放于 `List<ItemStack[]>`，**偶数下标=输入，奇数下标=输出**：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/recipes/RecipeType.java:174-228（节选）
    public static List<ItemStack> getRecipeInputs(MultiBlockMachine machine) {
        if (machine == null) {
            return new ArrayList<>();
        }

        List<ItemStack[]> recipes = machine.getRecipes();
        List<ItemStack> convertible = new ArrayList<>();

        for (int i = 0; i < recipes.size(); i++) {
            if (i % 2 == 0) {
                convertible.add(recipes.get(i)[0]);
            }
        }

        return convertible;
    }

    public static ItemStack getRecipeOutput(MultiBlockMachine machine, ItemStack input) {
        List<ItemStack[]> recipes = machine.getRecipes();
        return recipes.get(((getRecipeInputs(machine).indexOf(input) * 2) + 1))[0].clone();
    }

    public static ItemStack getRecipeOutputList(MultiBlockMachine machine, ItemStack[] input) {
        List<ItemStack[]> recipes = machine.getRecipes();
        return recipes.get((recipes.indexOf(input) + 1))[0];
    }
```

`getRecipeInputList` 还会按「空槽多者优先」排序，让更精确的配方先被匹配：

```java
// src/main/java/io/github/thebusybiscuit/slimefun4/api/recipes/RecipeType.java:191-218（节选）
    public static List<ItemStack[]> getRecipeInputList(MultiBlockMachine machine) {
        if (machine == null) {
            return new ArrayList<>();
        }

        List<ItemStack[]> recipes = machine.getRecipes();
        List<ItemStack[]> convertible = new ArrayList<>();

        for (int i = 0; i < recipes.size(); i++) {
            if (i % 2 == 0) {
                convertible.add(recipes.get(i));
            }
        }

        convertible.sort(Comparator.comparing(recipe -> {
            int emptySlots = 9;

            for (ItemStack ingredient : recipe) {
                if (ingredient != null) {
                    emptySlots--;
                }
            }

            return emptySlots;
        }));

        return convertible;
    }
```

**(b) 电力机器** —— `AContainer.findNextRecipe(BlockMenu)`：

```java
// src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AContainer.java:410-449
    protected MachineRecipe findNextRecipe(BlockMenu inv) {
        Map<Integer, ItemStack> inventory = new HashMap<>();

        for (int slot : getInputSlots()) {
            ItemStack item = inv.getItemInSlot(slot);

            if (item != null) {
                inventory.put(slot, ItemStackWrapper.wrap(item));
            }
        }

        Map<Integer, Integer> found = new HashMap<>();

        for (MachineRecipe recipe : recipes) {
            for (ItemStack input : recipe.getInput()) {
                for (int slot : getInputSlots()) {
                    if (SlimefunUtils.isItemSimilar(inventory.get(slot), input, true)) {
                        found.put(slot, input.getAmount());
                        break;
                    }
                }
            }

            if (found.size() == recipe.getInput().length) {
                if (!InvUtils.fitAll(inv.toInventory(), recipe.getOutput(), getOutputSlots())) {
                    return null;
                }

                for (Map.Entry<Integer, Integer> entry : found.entrySet()) {
                    inv.consumeItem(entry.getKey(), entry.getValue());
                }

                return recipe;
            } else {
                found.clear();
            }
        }

        return null;
    }
```

**三重循环** O(配方数 × 输入数 × 输入槽数)，这是 `AContainer` 的已知性能特征。共 11 处子类覆写（如 `AutoBrewer.java:65`、`ElectricGoldPan.java:88`、`AutoEnchanter.java:58`、`geo/OilPump.java:91`）。

`MachineRecipe` 在**遗留包**而非 `api/recipes`：

```java
// src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/MachineRecipe.java:1-34（节选）
package me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems;

// This class will be rewritten in the "Recipe Rewrite"
public class MachineRecipe {

    private int ticks;
    private final ItemStack[] input;
    private final ItemStack[] output;

    public MachineRecipe(int seconds, ItemStack[] input, ItemStack[] output) {
        this.ticks = seconds * 2;
        this.input = input;
        this.output = output;
    }
```

注意 `seconds * 2` —— 参数名叫 seconds，实际存的是 tick 数的一半。



---

## 3. 机器体系：BlockTicker 与 TickerTask

### 3.1 `BlockTicker` —— 机器行为的接口

`me/mrCookieSlime/Slimefun/Objects/handlers/BlockTicker.java:14`

```java
public abstract class BlockTicker implements ItemHandler {

    protected boolean unique = true;

    public void update() {
        if (unique) {
            uniqueTick();      // 每个 tick 只执行一次（不是每个方块一次）
            unique = false;
        }
    }

    /** 是否必须在主线程执行（涉及世界操作时必须 true） */
    public abstract boolean isSynchronized();

    /** 每个 tick 对每个方块调用 */
    public abstract void tick(Block b, SlimefunItem item, Config data);

    /** 每个 tick 全局只调用一次，用于做「所有机器共享」的准备工作 */
    public void uniqueTick() { }
}
```

**设计要点**：

1. `BlockTicker` 是**接口（抽象类）+ 三个方法**，不是深继承。机器只需实现 `tick` 与 `isSynchronized`。
2. `uniqueTick()` 是一个聪明的优化：遍历上万台机器时，某些「全局计算」不需要每台机器重算一遍。
3. `validate()` 在注册期就拒绝「把 ticker 挂在非方块物品上」，是**早期失败**的好例子。

### 3.2 `TickerTask` —— 调度器

`implementation/tasks/TickerTask.java:43`

```java
public class TickerTask implements Runnable {

    /** 当前活动的 tick 位置，按区块分桶。值必须是线程安全的可变 Set */
    private final Map<ChunkPosition, Set<Location>> tickingLocations = new ConcurrentHashMap<>();

    /** 待删除 / 待移动的队列 */
    private final Map<Location, Location> movingQueue = new ConcurrentHashMap<>();
    private final Map<Location, Boolean> deletionQueue = new ConcurrentHashMap<>();

    /** 记录某位置发生 bug 的次数，过多则删除该位置 */
    private final Map<BlockPosition, Integer> bugs = new ConcurrentHashMap<>();

    public void start(@Nonnull Slimefun plugin) {
        this.tickRate = Slimefun.getCfg().getInt("URID.custom-ticker-delay");
        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        scheduler.runTaskTimerAsynchronously(plugin, this, 100L, tickRate);   // 异步！
    }
}
```

**关键事实（与常见误解不同）**：`TickerTask` 是**异步**运行的（`runTaskTimerAsynchronously`），
但每台机器通过 `isSynchronized()` 决定是否把自己调度回主线程：

```java
// TickerTask.java:150-180
private void tickLocation(@Nonnull Set<BlockTicker> tickers, @Nonnull Location l) {
    Config data = BlockStorage.getLocationInfo(l);
    SlimefunItem item = SlimefunItem.getById(data.getString("id"));

    if (item != null && item.getBlockTicker() != null) {
        try {
            if (item.getBlockTicker().isSynchronized()) {
                Slimefun.getProfiler().scheduleEntries(1);
                item.getBlockTicker().update();
                // 同步动作总是带 50ms 延迟（1 游戏刻）
                Slimefun.runSync(() -> {
                    Block b = l.getBlock();
                    tickBlock(l, b, item, data, System.nanoTime());
                });
            } else {
                long timestamp = Slimefun.getProfiler().newEntry();
                item.getBlockTicker().update();
                Block b = l.getBlock();
                tickBlock(l, b, item, data, timestamp);   // 留在异步线程
            }
            tickers.add(item.getBlockTicker());
        } catch (Exception x) {
            reportErrors(l, item, x);
        }
    }
}
```

**这套设计的代价**：

- 绝大多数机器需要碰世界（读取容器、修改方块），必须 `isSynchronized() == true`，
  于是它们全都被 `runSync` 推回主线程，异步调度并没有真正卸载主线程压力。
- `Slimefun.runSync` 每个 tick、每台机器各提交一次任务，任务队列本身成为开销。
- `bugs` 计数达到阈值会**静默删除玩家的机器**，这在实践中会引起数据丢失投诉。

### 3.3 区块加载判断

```java
// TickerTask.java:137-148
private void tickChunk(ChunkPosition chunk, Set<BlockTicker> tickers, Set<Location> locations) {
    try {
        if (chunk.isLoaded()) {           // 只处理已加载区块
            for (Location l : locations) {
                tickLocation(tickers, l);
            }
        }
    } catch (ArrayIndexOutOfBoundsException | NumberFormatException x) {
        Slimefun.logger().log(Level.SEVERE, x, ...);
    }
}
```

注意这里捕获的是 `ArrayIndexOutOfBoundsException | NumberFormatException` —— 说明历史上确实
因为区块坐标解析问题崩过。**本插件对应做法**：`MachineManager.tick()` 用
`world.isChunkLoaded(x >> 4, z >> 4)` 直接判断，并且以「区块」为外层循环，
避免对每台机器重复判断。

### 3.4 对照表：本插件的机器体系

| 方面 | Slimefun4 | HigherRedStoneFun v0.1 |
| --- | --- | --- |
| 行为抽象 | `BlockTicker` 抽象类 + `isSynchronized()` | `MachineLogic` 接口（纯接口，默认方法） |
| 定义与状态 | 混在 `SlimefunItem` + `Config` 里 | `MachineDefinition`（不可变）/ `MachineInstance`（状态）分离 |
| 调度 | 异步任务 + 逐台 `runSync` 回主线程 | 主线程单任务，按区块分桶遍历 |
| 节流 | 全局统一 `custom-ticker-delay` | 每台机器可声明 `period(N)` |
| 出错处理 | `bugs` 计数，超限删除机器 | try/catch 隔离 + 日志，不删除数据 |
| 加载判断 | 每区块一次 `chunk.isLoaded()` | 每区块一次，用区块内首台机器代表 |

---

## 4. 能源系统 `EnergyNet`

`core/networks/energy/EnergyNet.java:46`

```java
public class EnergyNet extends Network implements HologramOwner {

    private static final int RANGE = 6;      // 注意：Wiki 写的是 7 格，源码是 6

    protected EnergyNet(@Nonnull Location l) { ... }
}
```

### 4.1 硬约束：一个网络只允许一个调节器

```java
// EnergyNet.java:146-153
public void tick(@Nonnull Block b) {
    AtomicLong timestamp = new AtomicLong(Slimefun.getProfiler().newEntry());

    if (!regulator.equals(b.getLocation())) {
        updateHologram(b, "&4Multiple Energy Regulators connected");
        Slimefun.getProfiler().closeEntry(b.getLocation(), SlimefunItems.ENERGY_REGULATOR.getItem(), timestamp.get());
        return;      // ← 整个网络直接停止工作
    }
    ...
}
```

**这是一个设计缺陷而非特性**：玩家在电网里多放一个调节器，整张电网静默停摆，
唯一的反馈是一个悬浮字。排查成本极高。

### 4.2 能源分配：按 Map 遍历顺序

```java
// EnergyNet.java:157-190
if (connectorNodes.isEmpty() && terminusNodes.isEmpty()) {
    updateHologram(b, "&4No Energy Network found");
} else {
    int generatorsSupply = tickAllGenerators(timestamp::getAndAdd);
    int capacitorsSupply = tickAllCapacitors();
    int supply = NumberUtils.flowSafeAddition(generatorsSupply, capacitorsSupply);
    int remainingEnergy = supply;
    int demand = 0;

    for (Map.Entry<Location, EnergyNetComponent> entry : consumers.entrySet()) {
        Location loc = entry.getKey();
        EnergyNetComponent component = entry.getValue();
        int capacity = component.getCapacity();
        int charge = component.getCharge(loc);

        if (charge < capacity) {
            int availableSpace = capacity - charge;
            demand = NumberUtils.flowSafeAddition(demand, availableSpace);

            if (remainingEnergy > 0) {
                if (remainingEnergy > availableSpace) {
                    component.setCharge(loc, capacity);        // 先遍历到的先装满
                    remainingEnergy -= availableSpace;
                } else {
                    component.setCharge(loc, charge + remainingEnergy);
                    remainingEnergy = 0;
                }
            }
        }
    }

    storeRemainingEnergy(remainingEnergy);
    updateHologram(b, supply, demand);
}
```

**问题**：`consumers` 是一个 `Map`，遍历顺序取决于实现（且与插入顺序相关）。
高耗电机器如果恰好排在前面，会把电全部吃光，后面的机器永远缺电。
玩家看到的症状是「有的机器一直在跑，有的永远不动」，且**重启服务器可能改变行为**。

### 4.3 电容在无余量时被强制清零

```java
// EnergyNet.java:196-214
private void storeRemainingEnergy(int remainingEnergy) {
    for (Map.Entry<Location, EnergyNetComponent> entry : capacitors.entrySet()) {
        Location loc = entry.getKey();
        EnergyNetComponent component = entry.getValue();

        if (remainingEnergy > 0) {
            int capacity = component.getCapacity();
            if (remainingEnergy > capacity) {
                component.setCharge(loc, capacity);
                remainingEnergy -= capacity;
            } else {
                component.setCharge(loc, remainingEnergy);
                remainingEnergy = 0;
            }
        } else {
            component.setCharge(loc, 0);     // ← 发电不足时，电容被直接置 0
        }
    }
    ...
}
```

**这是本插件最想修正的一点**：电容的语义应该是「有电就存，缺电就放」。
但这里当 `remainingEnergy <= 0` 时，电容被**直接归零**，
也就是说发电机供不上的时候，电容里存了一个白天的电会凭空消失，而不是拿来救急。

**本插件对应做法**（`energy/EnergyNetwork.java`）：

```java
public void tick() {
    if (dirty) rebuild();

    long generated = 0L;

    // 1. 发电机发电，优先充入本网络的电池
    for (EnergyNode generator : generators) {
        long produced = generator.generate();
        if (produced <= 0L) continue;
        generated += produced;
        long leftover = chargeStorages(produced);
        if (leftover > 0L) {
            supplyConsumers(leftover);
        }
    }

    // 2. 电池之间的均衡
    balanceStorages();

    // 3. 用电机从电池取电
    for (EnergyNode consumer : consumers) {
        long demand = consumer.demand();
        if (demand <= 0L) continue;
        long available = drawFromStorages(demand);
        if (available > 0L) {
            consumer.receive(available);
            totalConsumed += available;
        }
    }
}
```

以及关键的「按需索电」模型（`machines/MachineInstance.java`）：

```java
@Override
public long demand() {
    if (!requestingPower) {
        return 0L;                    // 没活干 = 不要电，不空转偷电
    }
    long cap = definition.bufferCapacity();
    return Math.max(0L, cap - energyStored);   // 只申请补满缓冲区的量
}
```

### 4.4 导线即节点的问题

`EnergyNet` 把导线方块（`connectorNodes`）也纳入网络计算，且每次 tick 全量重算。
**导线越多，重算成本越高**，这是社区「基地一大就卡」的主要来源。

**本插件对应做法**：导线只是 BFS 通道，不进入 `generators/consumers/storages` 列表：

```java
// energy/EnergyNetwork.java
EnergyNode neighbourNode = manager.nodeAt(neighbour);
if (neighbourNode != null) {
    addNode(neighbourNode);
    if (machineCount() >= MAX_MACHINES) continue;
}
queue.add(neighbour);   // 导线的邻居继续扩展
```

一台机器与一根导线都只是 BFS 的一跳，但**只有机器进入调度列表**。

### 4.5 单位不一致

| 来源 | 表示 |
| --- | --- |
| 源码 `getEnergyProduction()` | 每网络 tick（0.5s）的值 |
| 游戏内 tooltip | `×2` 后的 **J/s** |
| 官方 Wiki | 标为 "J/t" |

**三方不一致**。本插件统一使用 **J/t**（每 tick 焦耳），并让配置、lore、
`/hrf energy` 输出共用同一单位。

---

## 5. 物流系统 `CargoNet`

`core/networks/cargo/CargoNet.java`

它与 `EnergyNet` 共享 `Network` 基类，因此继承同样的三个问题：

1. **节点即导线**：货运节点本身构成网络，规模一大就慢。
2. **按 Map 顺序处理**：物品在两个容器之间的移动顺序不确定。
3. **必须有频率控制**：`CargoNet` 依赖 `CargoNode` 的频率设置（每 tick / 每 N tick），
   配置不当会瞬间把 TPS 打满。

**本插件 v0.1 没有实现物流系统**。这是一个有意识的取舍：
与其做一个性能可疑的物流，不如先把「机器能不能正确工作」这条主线做扎实。
v0.2 会参考 `CargoNet` 的节点设计，但会采用「按区块的事件驱动」而非全网络轮询。

---

## 6. 数据存储

### 6.1 `BlockStorage`

`me/mrCookieSlime/Slimefun/api/BlockStorage.java`

以 `Location` 为键存储方块数据。官方 ADR-0001（2023-11）承认用 YAML 存方块数据是错误设计：

> YAML is "good for a config format but **not good for a data store**"

会产生 large files that can get corrupted，而其改造计划 **Phase 2–6 全部 `Not started`**。

### 6.2 其它存储

| 组件 | 位置 | 说明 |
| --- | --- | --- |
| `BlockStorage` | `me.mrCookieSlime.Slimefun.api` | 方块数据，YAML |
| `WaypointStorage` | `core.services` | GPS 传送点 |
| `PlayerProfile` | `core.profiles` | 玩家研究进度、背包数据 |

**本插件对应做法**：

- 机器存档 `machines/<世界>.yml`，**按区块分节**（`MachineStorage.path()`），
  单台机器损坏不污染整个文件。
- 物品栏用 `ItemStack.serializeAsBytes()` + Base64，而非 YAML 物品序列化——
  后者会丢失部分数据组件，且**自定义 PDC 标记会丢**。
- 作物基因直接存**区块 PDC**（`GeneticsManager.setCropGenome`），
  免费、无上限、随区块自动存盘，不需要维护一张全局方块表。

---

## 7. 同步与异步

| 机制 | 位置 | 用途 |
| --- | --- | --- |
| `Slimefun.runSync(Runnable)` | `implementation/Slimefun.java` | 把任务调度回主线程 |
| `TickerTask` | `implementation/tasks` | 异步调度机器，逐台回主线程 |
| `AsyncRecipeChoiceTask` | `implementation/tasks` | 异步计算配方选择 |

**核心问题**：`TickerTask` 虽然是异步的，但绝大多数机器需要碰世界，
必须 `isSynchronized() == true`，于是**每台机器每 tick 提交一个同步任务**。
这反而比「主线程直接遍历」更慢，因为多了任务队列的入队/出队开销。

**本插件对应做法**：不做异步调度，直接在主线程按区块遍历（`MachineManager.tick()`）。
因为：
1. 机器逻辑本身是纯数据操作（进度 + 物品栏），不涉及昂贵的世界查询。
2. 避免 `runSync` 的队列开销。
3. 避免跨线程访问 `ItemStack` 的一致性风险。

代价是单 tick 的机器处理量有上限，但通过 `MAX_SCAN_BLOCKS`、
`period(N)` 节流与区块分桶，这个上限是可控的。

---

## 8. 命令、配置、本地化与 Addon 接入

### 8.1 Addon 接入点

```java
public interface SlimefunAddon extends Plugin {
    JavaPlugin getJavaPlugin();
    String getBugTrackerURL();
    default void setAddonConfiguration(...) { }
}
```

Addon 通过实现 `SlimefunAddon`（或 `@SlimefunAddon` 注解）接入，
用 JitPack 坐标 `com.github.Slimefun:Slimefun4:<tag>` 依赖。

**本插件 v0.1 不提供 Addon API**，但架构已经为它留好了位置：
第三方只需能调用 `MachineRegistry.get().register(MachineDefinition...)` 即可扩展。
v0.2 会把这部分正式开放。

### 8.2 命令

`/sf` 系列命令，包含 `guide`、`search`、`give`、`research`、`stats`、`debug` 等子命令。

### 8.3 本地化

`Localization` 类 + `messages.yml`，支持多语言（社区翻译走 Crowdin）。

**本插件 v0.1 采用硬编码中文 MiniMessage 文本**（`core/Text.java`）。
这是一个取舍：v0.1 优先保证内容完整，本地化留到 API 稳定之后。

---

## 9. 扩展代码骨架对照

### 9.1 写一台自定义用电机

**Slimefun 的写法**（继承 + 重写）：

```java
public class MyMachine extends SimpleSlimefunItem<BlockTicker> {

    public MyMachine(ItemGroup group, SlimefunItemStack item, RecipeType type, ItemStack[] recipe) {
        super(group, item, type, recipe);
    }

    @Override
    public BlockTicker getItemHandler() {
        return new BlockTicker() {
            @Override
            public void tick(Block b, SlimefunItem item, Config data) {
                // 每 tick 对每个方块调用
            }

            @Override
            public boolean isSynchronized() {
                return true;   // 涉及世界操作，必须回主线程
            }
        };
    }
}
```

**本插件的写法**（注册定义 + 复用逻辑，不写新类）：

```java
MachineRegistry.get().register(
    MachineDefinition.builder("my_machine", "我的机器", ItemGroup.POWER_MACHINES, Material.FURNACE)
        .recipeType(RecipeType.ELECTRIC_SMELTING)   // 复用界面布局与配方匹配
        .energyRole(EnergyNode.Role.CONSUMER)
        .buffer(12_000L)
        .throughput(6L)
        .logic(RecipeMachineLogic.electric(6L))     // 复用整套加工逻辑
        .lore("<gray>耗电: <red>6 J/t")
        .build());
```

再在 `Recipes.java` 里加一条配方，这台机器就完整可用了。

### 9.2 写一台自定义发电机

```java
MachineRegistry.get().register(
    MachineDefinition.builder("my_generator", "我的发电机", ItemGroup.POWER_MACHINES, Material.MAGMA_BLOCK)
        .energyRole(EnergyNode.Role.GENERATOR)
        .throughput(20L)
        .buffer(8_000L)
        .logic(new MyGeneratorLogic(20L))    // 实现 MachineLogic.generate()
        .build());
```

其中逻辑只需实现一个方法：

```java
public class MyGeneratorLogic implements MachineLogic {

    private final long output;

    public MyGeneratorLogic(long output) { this.output = output; }

    @Override
    public boolean energyAware() { return true; }

    @Override
    public long generate(MachineInstance machine) {
        if (!machine.hasFuel() && !refuel(machine)) {
            return 0L;                     // 没燃料就不发电
        }
        machine.consumeFuelTick();
        machine.addGenerated(output);
        return output;
    }

    @Override
    public String statusLine(MachineInstance machine) {
        return machine.hasFuel() ? "<green>运行中" : "<red>无燃料";
    }
}
```

### 9.3 核心差异总结

| | Slimefun4 | HigherRedStoneFun |
| --- | --- | --- |
| 新增同类机器 | 写一个新类 | 写一份注册 |
| 定义/状态 | 混在 `SlimefunItem` + `Config` | 分离为两个类 |
| 回调接口 | 抽象类 + 必须实现 | 接口 + 全部有默认实现 |
| 输出槽布局 | 各机器自己处理 | 由 `RecipeType` 统一声明 |

---

## 10. 结论：本插件借鉴了什么、改了什么

### 借鉴（验证过的好设计）

1. **PDC 标记物品身份** —— 已经照搬。
2. **坐标作为方块身份** —— 已经照搬（并加了材质校验防幽灵机器）。
3. **`ItemGroup` 枚举驱动图鉴** —— 已经照搬。
4. **`RecipeType` 抽象输入/输出槽位** —— 已经照搬，且进一步把界面布局也纳入其中。
5. **`BlockTicker` 接口化而非深继承** —— 已经照搬并推进为纯接口 + 默认方法。
6. **机器出错隔离** —— 已经照搬（但不删除玩家数据）。
7. **按区块组织 tick** —— 已经照搬。

### 修改（源码中被证实的问题）

| 问题 | Slimefun 现状 | 本插件做法 |
| --- | --- | --- |
| 一个网络只允许一个调节器 | `EnergyNet.java:149` 直接 return | 取消该概念 |
| 按 Map 顺序分配电量 | `EnergyNet.java:166` | 按缓冲区补满，需求驱动 |
| 电容无余量时被清零 | `EnergyNet.java:211` | 真正的充放电模型 |
| 导线即节点 | `connectorNodes` 参与计算 | 导线只是通道 |
| 全量重算 | 每网络 tick | 脏标记 + 拓扑缓存 |
| YAML 存方块数据 | ADR-0001 自认错误 | 按区块分节 + NBT 字节序列化 |
| 异步调度反而更慢 | 逐台 `runSync` | 主线程按区块遍历 |
| 单位三方不一致 | J/s 与 J/t 混用 | 统一 J/t |
| 研究 XP 时间税 | 258 个研究 / 5431 等级 | 取消研究，改为材料门槛 |

详见 `03-玩法升级与优化设计.md` 的对照表。
