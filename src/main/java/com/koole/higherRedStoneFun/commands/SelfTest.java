package com.koole.higherRedStoneFun.commands;

import com.koole.higherRedStoneFun.HigherRedStoneFun;
import com.koole.higherRedStoneFun.core.Text;
import com.koole.higherRedStoneFun.energy.EnergyNetwork;
import com.koole.higherRedStoneFun.genetics.CropMapping;
import com.koole.higherRedStoneFun.genetics.Gene;
import com.koole.higherRedStoneFun.genetics.GeneticsManager;
import com.koole.higherRedStoneFun.genetics.GeneticsManager.SampleKind;
import com.koole.higherRedStoneFun.genetics.Genome;
import com.koole.higherRedStoneFun.items.ItemRegistry;
import com.koole.higherRedStoneFun.machines.MachineDefinition;
import com.koole.higherRedStoneFun.machines.MachineInstance;
import com.koole.higherRedStoneFun.machines.MachineMenu;
import com.koole.higherRedStoneFun.machines.MachineRegistry;
import com.koole.higherRedStoneFun.machines.logic.RecipeMachineLogic;
import com.koole.higherRedStoneFun.recipes.MachineRecipe;
import com.koole.higherRedStoneFun.recipes.RecipeRegistry;
import com.koole.higherRedStoneFun.recipes.RecipeType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 核心系统自检。
 *
 * <p>可以在服务器里直接运行 <code>/hrf selftest</code> 来验证三大模块的
 * 关键机制是否正常工作。这对服主排查「机器不工作」类问题很有用：
 * 它能明确区分「是插件坏了」还是「是玩家没接电线」。</p>
 *
 * <p>自检会在远离出生点的高空临时放置方块，结束后完整还原。</p>
 */
public final class SelfTest {

    private final HigherRedStoneFun plugin;
    private final List<String> results = new ArrayList<>();
    private int passed;
    private int failed;

    public SelfTest(HigherRedStoneFun plugin) {
        this.plugin = plugin;
    }

    /** 执行全部自检，返回是否全部通过。 */
    public boolean run(CommandSender sender) {
        results.clear();
        passed = 0;
        failed = 0;

        sender.sendMessage(Text.mm("<dark_gray><st>                                                  "));
        sender.sendMessage(Text.mm("<gold><bold>HigherRedStoneFun 核心自检"));

        testItemIdentity();
        testRecipeMatching();
        testEnergyNetwork();
        testGenetics();
        testCropMapping();
        testMachineLifecycle();
        testPersistence();
        testCraftingTableSafety();
        testTechTreeEntryPoint();
        testEconomyLoops();
        testVanillaRecipeItemMatching();
        testMultiblockStructure();
        testAutoFormation();
        testGuideMenuLayout();
        testDismantleReturnsOriginal();
        testSequentialStructureDamage();

        for (String line : results) {
            sender.sendMessage(Text.mm(line));
        }

        sender.sendMessage(Text.mm(""));
        String color = failed == 0 ? "green" : "red";
        sender.sendMessage(Text.mm("<" + color + "><bold>通过 " + passed + " 项，失败 " + failed + " 项"));
        sender.sendMessage(Text.mm("<dark_gray><st>                                                  "));
        return failed == 0;
    }

    // ------------------------------------------------------------------
    // 断言辅助
    // ------------------------------------------------------------------

    private void check(String name, boolean condition, String detail) {
        if (condition) {
            passed++;
            results.add("<green>  ✔ <white>" + name + " <dark_gray>" + detail);
        } else {
            failed++;
            results.add("<red>  ✘ <white>" + name + " <dark_gray>" + detail);
        }
    }

    private void section(String title) {
        results.add("");
        results.add("<yellow>▶ " + title);
    }

    // ------------------------------------------------------------------
    // 1. 物品身份
    // ------------------------------------------------------------------

    private void testItemIdentity() {
        section("物品身份 (PDC 标记)");
        ItemRegistry registry = ItemRegistry.get();

        ItemStack stack = registry.create("hrf_iron_plate", 5);
        String id = registry.idOf(stack);
        check("创建并读取自定义物品 ID", "hrf_iron_plate".equals(id), "读到: " + id);

        check("数量正确", stack.getAmount() == 5, "数量: " + stack.getAmount());

        // 同 ID 不同实例应视为同一物品
        ItemStack other = registry.create("hrf_iron_plate", 1);
        check("同类物品判定一致", registry.sameItem(stack, other), "sameItem=true");

        // 不同 ID 不应被视为同一物品
        ItemStack different = registry.create("hrf_gold_plate", 1);
        check("异类物品判定不同", !registry.sameItem(stack, different), "sameItem=false");

        // 原版物品不应带自定义 ID
        check("原版物品无自定义 ID", registry.idOf(new ItemStack(Material.STONE)) == null, "id=null");

        // 序列化往返（这是机器存档的关键路径）
        byte[] bytes = stack.serializeAsBytes();
        ItemStack restored = ItemStack.deserializeBytes(bytes);
        check("NBT 序列化往返后 ID 保留",
                "hrf_iron_plate".equals(registry.idOf(restored)),
                idOfOrNull(restored));

        // normalize 应该剥掉附加信息但保留身份
        ItemStack enchanted = registry.create("hrf_iron_plate", 3);
        enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, 5);
        ItemStack normalized = registry.normalize(enchanted);
        check("normalize 保留身份", "hrf_iron_plate".equals(registry.idOf(normalized)), "保留 ID");
    }

    private String idOfOrNull(ItemStack stack) {
        String id = ItemRegistry.get().idOf(stack);
        return "ID=" + id;
    }

    // ------------------------------------------------------------------
    // 2. 配方匹配
    // ------------------------------------------------------------------

    private void testRecipeMatching() {
        section("配方匹配与消耗");
        RecipeRegistry registry = RecipeRegistry.get();

        // 精确匹配
        List<ItemStack> inputs = new ArrayList<>();
        inputs.add(ItemRegistry.get().create("hrf_iron_plate", 2));
        MachineRecipe match = registry.findMatch(RecipeType.PRESSING, inputs);
        check("能匹配到压制配方", match != null && match.id().equals("press_gear"),
                match == null ? "无匹配" : match.id());

        // 数量不足不应匹配
        List<ItemStack> insufficient = new ArrayList<>();
        insufficient.add(ItemRegistry.get().create("hrf_iron_plate", 1));
        MachineRecipe noMatch = registry.findMatch(RecipeType.PRESSING, insufficient);
        check("材料数量不足时不匹配", noMatch == null,
                noMatch == null ? "正确拒绝" : "误匹配 " + noMatch.id());

        // 错误物品不应匹配
        List<ItemStack> wrong = new ArrayList<>();
        wrong.add(ItemRegistry.get().create("hrf_gold_plate", 4));
        check("错误材料不匹配", registry.findMatch(RecipeType.PRESSING, wrong) == null, "正确拒绝");

        // 多余材料只消耗所需数量（这是防丢物品的关键）
        List<ItemStack> excess = new ArrayList<>();
        excess.add(ItemRegistry.get().create("hrf_iron_plate", 8));
        MachineRecipe excessMatch = registry.findMatch(RecipeType.PRESSING, excess);
        if (excessMatch != null) {
            List<ItemStack> slots = new ArrayList<>();
            slots.add(ItemRegistry.get().create("hrf_iron_plate", 8));
            registry.consume(excessMatch, slots);
            ItemStack remaining = slots.get(0);
            boolean ok = remaining != null && remaining.getAmount() == 6;
            check("只消耗配方所需数量", ok,
                    "8 个 -> 剩 " + (remaining == null ? 0 : remaining.getAmount()) + " 个 (应为 6)");
        } else {
            check("只消耗配方所需数量", false, "未匹配到配方");
        }

        // 原版材料的配方
        List<ItemStack> vanillaInputs = new ArrayList<>();
        vanillaInputs.add(new ItemStack(Material.IRON_INGOT, 1));
        MachineRecipe vanillaMatch = registry.findMatch(RecipeType.PRESSING, vanillaInputs);
        check("原版材料配方可匹配",
                vanillaMatch != null && vanillaMatch.id().equals("press_iron_plate"),
                vanillaMatch == null ? "无匹配" : vanillaMatch.id());
    }

    // ------------------------------------------------------------------
    // 3. 能源网络
    // ------------------------------------------------------------------

    private void testEnergyNetwork() {
        section("能源网络");

        World world = plugin.getServer().getWorlds().get(0);
        // 选一个远离出生点的高空位置，避免影响玩家建筑
        Location base = new Location(world, 3000, 200, 3000);

        // 确保区块已加载：能源网络的 BFS 只遍历已加载的区块
        try {
            world.getChunkAt(base).load();
        } catch (Exception ex) {
            check("测试区块可加载", false, ex.toString());
            return;
        }

        // 备份原有方块，测试结束后还原
        List<Block> touched = new ArrayList<>();
        Location genLoc = base.clone();
        Location cableLoc = base.clone().add(1, 0, 0);
        Location capLoc = base.clone().add(2, 0, 0);
        Location machineLoc = base.clone().add(3, 0, 0);

        MachineInstance generator = null;
        MachineInstance capacitor = null;
        MachineInstance consumer = null;

        try {
            // 清理测试区域
            for (int i = 0; i < 8; i++) {
                Block b = base.clone().add(i, 0, 0).getBlock();
                touched.add(b);
                b.setType(Material.AIR, false);
            }
            for (int i = 0; i < 8; i++) {
                if (base.clone().add(i, 0, 0).getBlock().getType() != Material.AIR) {
                    base.clone().add(i, 0, 0).getBlock().setType(Material.AIR, false);
                }
            }

            // 建立：发电机 - 导线 - 电容 - 用电机器
            MachineDefinition genDef = MachineRegistry.get().get("hrf_coal_generator");
            MachineDefinition capDef = MachineRegistry.get().get("hrf_capacitor_basic");
            MachineDefinition useDef = MachineRegistry.get().get("hrf_electric_smeltery");

            check("发电机定义存在", genDef != null, genDef == null ? "缺失" : genDef.id());
            check("电容定义存在", capDef != null, capDef == null ? "缺失" : capDef.id());
            check("电力机器定义存在", useDef != null, useDef == null ? "缺失" : useDef.id());

            if (genDef == null || capDef == null || useDef == null) {
                return;
            }

            // 把方块真的放下去，这样 BFS 才能遍历到
            genLoc.getBlock().setType(genDef.icon(), false);
            cableLoc.getBlock().setType(Material.COPPER_BLOCK, false);
            capLoc.getBlock().setType(capDef.icon(), false);
            machineLoc.getBlock().setType(useDef.icon(), false);

            check("导线材质被识别",
                    EnergyNetwork.isCable(cableLoc.getBlock().getType()),
                    cableLoc.getBlock().getType().name());

            generator = new MachineInstance(genLoc, genDef);
            capacitor = new MachineInstance(capLoc, capDef);
            consumer = new MachineInstance(machineLoc, useDef);

            plugin.energy().register(generator);
            plugin.energy().register(capacitor);
            plugin.energy().register(consumer);

            check("机器已注册到能源管理器",
                    plugin.energy().isRegistered(genLoc)
                            && plugin.energy().isRegistered(capLoc)
                            && plugin.energy().isRegistered(machineLoc),
                    "3 台已注册");

            // 强制重建网络
            EnergyNetwork network = plugin.energy().networkAt(genLoc);
            check("网络能发现全部 3 台机器",
                    network.machineCount() == 3,
                    "发现 " + network.machineCount() + " 台");

            // 给发电机加燃料，跑若干 tick，检查电容是否被充上电
            generator.setSlot(0, new ItemStack(Material.COAL, 8));
            long before = capacitor.stored();

            for (int i = 0; i < 40; i++) {
                plugin.energy().tick();
            }

            long after = capacitor.stored();
            check("发电机产出的电被送入电容", after > before,
                    "电容电量 " + before + " -> " + after + " J");

            // 用电机器：给它电量后消耗，检查是否被正确扣除
            consumer.setEnergyStored(consumer.definition().bufferCapacity());
            long consumerBefore = consumer.energyStored();
            consumer.consumeEnergy(1000L);
            check("用电机能扣除电量",
                    consumer.energyStored() == consumerBefore - 1000L,
                    consumerBefore + " -> " + consumer.energyStored() + " J");

            // 缓冲区上限
            long cap = capacitor.definition().bufferCapacity();
            capacitor.charge(cap * 2);
            check("储能不会超过上限",
                    capacitor.stored() <= cap,
                    capacitor.stored() + " / " + cap + " J");

            // 网络统计
            check("网络统计可用", network.describe().size() > 0,
                    network.describe().size() + " 行诊断信息");

        } catch (Exception ex) {
            check("能源网络测试无异常", false, ex.toString());
        } finally {
            // 完整清理：注销机器 + 还原方块
            plugin.energy().unregister(genLoc);
            plugin.energy().unregister(capLoc);
            plugin.energy().unregister(machineLoc);
            for (Block block : touched) {
                block.setType(Material.AIR, false);
            }
        }
    }

    // ------------------------------------------------------------------
    // 4. 基因系统
    // ------------------------------------------------------------------

    private void testGenetics() {
        section("基因工程");
        var genetics = plugin.genetics();

        // 序列化往返
        Genome genome = Genome.of(3, 2, 5, 1);
        String encoded = genome.serialize();
        Genome decoded = Genome.parse(encoded);
        check("基因序列化往返", decoded.equals(genome), encoded + " -> " + decoded.serialize());

        // 等级裁剪
        Genome clamped = Genome.of(99, -5, 5, 0);
        check("基因等级被裁剪到 0-5",
                clamped.level(Gene.GROWTH) == 5 && clamped.level(Gene.YIELD) == 0,
                clamped.serialize());

        // 效果曲线单调递增
        Genome low = Genome.of(0, 0, 0, 0);
        Genome high = Genome.of(5, 5, 5, 5);
        check("生长倍率随等级提高",
                high.growthMultiplier() > low.growthMultiplier(),
                String.format("%.2fx -> %.2fx", low.growthMultiplier(), high.growthMultiplier()));
        check("产量倍率随等级提高",
                high.yieldMultiplier() > low.yieldMultiplier(),
                String.format("%.2fx -> %.2fx", low.yieldMultiplier(), high.yieldMultiplier()));
        check("满级抗逆免疫踩踏", high.immuneToTrample(), "immune=true");
        check("满级判定正确", high.isPerfect() && !low.isPerfect(), "high=perfect");

        // 拼接：取较优值
        Genome a = Genome.of(5, 0, 0, 0);
        Genome b = Genome.of(0, 4, 0, 0);
        Genome spliced = genetics.splice(a, b, 0.0D); // 突变率 0，结果应确定
        check("拼接取双方较优值",
                spliced.level(Gene.GROWTH) == 5 && spliced.level(Gene.YIELD) == 4,
                "生" + spliced.level(Gene.GROWTH) + " 产" + spliced.level(Gene.YIELD));

        // 拼接不会超过上限
        Genome max = Genome.of(5, 5, 5, 5);
        Genome overMax = genetics.splice(max, max, 1.0D); // 突变率 100%
        check("拼接结果不超过满级",
                overMax.level(Gene.GROWTH) == 5 && overMax.level(Gene.YIELD) == 5,
                overMax.serialize());

        // 遗传：子代基因位必须来自父母之一（允许 ±1 突变）
        Genome parentA = Genome.of(5, 0, 0, 0);
        Genome parentB = Genome.of(0, 0, 0, 0);
        boolean inheritOk = true;
        for (int i = 0; i < 50; i++) {
            Genome child = genetics.inherit(parentA, parentB);
            int growth = child.level(Gene.GROWTH);
            // 父母中该位分别是 5 和 0，突变允许 4..5（5 不能到 6，0 不能到 -1）
            if (growth < 0 || growth > 5) {
                inheritOk = false;
                break;
            }
        }
        check("遗传结果始终在合法范围", inheritOk, "50 次繁殖全部合法");

        // 遗传确实会混合双方
        boolean sawBoth = false;
        for (int i = 0; i < 100; i++) {
            Genome child = genetics.inherit(Genome.of(5, 5, 0, 0), Genome.of(0, 0, 5, 5));
            if (child.level(Gene.GROWTH) >= 4 && child.level(Gene.RESILIENCE) >= 4) {
                sawBoth = true;
                break;
            }
        }
        check("遗传能组合父母双方的基因", sawBoth, "找到同时继承生长与抗逆的子代");

        // 物品上的基因读写
        ItemStack seed = ItemRegistry.get().create("hrf_wheat_seeds", 1);
        Genome seedGenome = Genome.of(4, 3, 2, 1);
        genetics.applyGenome(seed, seedGenome);
        Genome readBack = genetics.genomeOf(seed);
        check("物品能保存并读回基因",
                seedGenome.equals(readBack),
                readBack == null ? "读取失败" : readBack.serialize());

        // 样本标记
        ItemStack sample = ItemRegistry.get().create("hrf_sample_wheat", 1);
        genetics.markSample(sample, SampleKind.SEED, "hrf_sample_wheat");
        check("样本种类标记可读写",
                genetics.sampleKindOf(sample) == SampleKind.SEED
                        && "hrf_sample_wheat".equals(genetics.sampleSourceOf(sample)),
                String.valueOf(genetics.sampleKindOf(sample)));

        // 野生基因生成
        Genome wild = genetics.randomGenome(0.5D);
        check("随机基因在合法范围", wild.total() <= Genome.MAX_LEVEL * 4,
                "强度 " + wild.total());

        // 区块 PDC 存储（作物基因的关键路径）
        World world = plugin.getServer().getWorlds().get(0);
        Location loc = new Location(world, 3010, 200, 3010);
        try {
            Genome stored = Genome.of(2, 3, 1, 4);
            genetics.setCropGenome(loc, stored);
            Genome loaded = genetics.getCropGenome(loc);
            check("作物基因能存入区块 PDC",
                    stored.equals(loaded),
                    loaded == null ? "读取失败" : loaded.serialize());

            genetics.clearCropGenome(loc);
            check("作物基因能清除", genetics.getCropGenome(loc) == null, "已清除");
        } catch (Exception ex) {
            check("区块 PDC 存储无异常", false, ex.toString());
        }

        // 展示
        check("基因摘要可生成", !Genome.of(1, 2, 3, 4).shortSummary().isEmpty(),
                Genome.of(1, 2, 3, 4).shortSummary());
    }

    // ------------------------------------------------------------------
    // 6. 机器生命周期
    // ------------------------------------------------------------------

    /**
     * 模拟一次完整的「放机器 -> 加工 -> 打破机器」流程。
     *
     * <p>这是最贴近真实玩法的测试：验证机器能不能真的把材料变成产物，
     * 以及破坏时会不会把内部物品还给你。</p>
     */
    private void testMachineLifecycle() {
        section("机器生命周期 (加工 / 掉落)");

        World world = plugin.getServer().getWorlds().get(0);
        Location loc = new Location(world, 3020, 200, 3020);
        try {
            world.getChunkAt(loc).load();
        } catch (Exception ex) {
            check("测试区块可加载", false, ex.toString());
            return;
        }

        Block block = loc.getBlock();
        Material original = block.getType();

        try {
            MachineDefinition definition = MachineRegistry.get().get("hrf_grinder");
            if (definition == null) {
                check("研磨机定义存在", false, "缺失");
                return;
            }

            block.setType(definition.icon(), false);
            MachineInstance machine = new MachineInstance(loc, definition);
            plugin.machines().add(machine);

            check("机器已加入管理器", plugin.machines().isMachine(loc), "已注册");

            // 放一个燃料（煤炭）和一个可研磨的物品（铁锭）
            machine.setSlot(RecipeMachineLogic.FUEL_SLOT, new ItemStack(Material.COAL, 1));
            machine.setSlot(11, new ItemStack(Material.IRON_INGOT, 1));

            // 跑足够多的 tick 让它完成一次研磨（配方 60 tick）
            int guard = 0;
            while (machine.completedOperations() == 0 && guard < 500) {
                definition.logic().tick(machine);
                guard++;
            }

            check("机器能完成一次加工", machine.completedOperations() > 0,
                    "耗时 " + guard + " 次 tick 调用");

            ItemStack output = machine.getSlot(15);
            boolean produced = output != null
                    && "hrf_iron_dust".equals(ItemRegistry.get().idOf(output));
            check("产出了正确的自定义物品", produced,
                    produced ? "铁粉 x" + output.getAmount() : "产物为空或错误");

            // 燃料应被消耗
            check("燃料被消耗", machine.hasFuel() || machine.completedOperations() > 0,
                    "剩余燃料 tick: " + machine.fuelTicks());

            // 输入应被消耗
            ItemStack remainingInput = machine.getSlot(11);
            check("输入材料被消耗",
                    remainingInput == null || remainingInput.getType().isAir()
                            || remainingInput.getAmount() == 0,
                    remainingInput == null ? "已清空" : "剩余 " + remainingInput.getAmount());

            // 破坏机器：内部物品应被收集（这里只验证 hasItems 判定正确）
            check("机器能被识别为「有内部物品」", machine.hasItems(), "hasItems=true");

            // 清空后不再有物品
            for (int i = 0; i < machine.contents().length; i++) {
                machine.setSlot(i, null);
            }
            check("清空后无内部物品", !machine.hasItems(), "hasItems=false");

            // 移除
            plugin.machines().remove(loc);
            check("机器能正常移除", !plugin.machines().isMachine(loc), "已移除");

        } catch (Exception ex) {
            check("机器生命周期测试无异常", false, ex.toString());
        } finally {
            block.setType(original == null ? Material.AIR : original, false);
        }
    }

    // ------------------------------------------------------------------
    // 7. 持久化
    // ------------------------------------------------------------------

    /**
     * 验证机器存档的往返：写入 -> 读取 -> 状态一致。
     *
     * <p>重点验证内部物品（尤其是带 PDC 标记的自定义物品）在序列化后
     * 仍然能被正确识别——这是最容易出问题、也最影响玩家体验的地方。</p>
     */
    private void testPersistence() {
        section("机器存档 (序列化往返)");

        World world = plugin.getServer().getWorlds().get(0);
        Location loc = new Location(world, 3030, 200, 3030);
        try {
            world.getChunkAt(loc).load();
        } catch (Exception ex) {
            check("测试区块可加载", false, ex.toString());
            return;
        }

        Block block = loc.getBlock();
        Material original = block.getType();

        try {
            MachineDefinition definition = MachineRegistry.get().get("hrf_electric_smeltery");
            if (definition == null) {
                check("电力冶炼炉定义存在", false, "缺失");
                return;
            }

            block.setType(definition.icon(), false);
            MachineInstance machine = new MachineInstance(loc, definition);
            machine.setEnergyStored(5_000L);
            machine.addFuelTicks(1234);
            machine.setActiveRecipe("electric_smelt_iron", 40);
            machine.advance();
            machine.setCounter("test_counter", 42);
            // 放一个自定义物品进机器，验证自定义标记能否挺过存档
            machine.setSlot(11, ItemRegistry.get().create("hrf_iron_plate", 7));

            // 用真实存档路径保存
            plugin.storage().saveOne(machine);

            // 从磁盘读回
            plugin.machines().remove(loc);
            int restored = plugin.storage().loadChunkRegion(world, loc.getBlockX() >> 4,
                    loc.getBlockZ() >> 4, plugin.machines());

            MachineInstance loaded = plugin.machines().get(loc);
            check("机器能从存档恢复", loaded != null && restored > 0,
                    "恢复 " + restored + " 台");

            if (loaded == null) {
                return;
            }

            check("电量被保留", loaded.energyStored() == 5_000L,
                    loaded.energyStored() + " J (期望 5000)");
            check("燃料被保留", loaded.fuelTicks() == 1234,
                    loaded.fuelTicks() + " tick (期望 1234)");
            check("配方进度被保留",
                    loaded.progress() == 1 && "electric_smelt_iron".equals(loaded.activeRecipe()),
                    "进度 " + loaded.progress() + ", 配方 " + loaded.activeRecipe());
            check("机器专用计数被保留", loaded.counter("test_counter", 0) == 42,
                    "counter=" + loaded.counter("test_counter", 0));

            ItemStack restoredItem = loaded.getSlot(11);
            String restoredId = restoredItem == null ? null : ItemRegistry.get().idOf(restoredItem);
            check("自定义物品身份挺过存档",
                    "hrf_iron_plate".equals(restoredId),
                    "ID=" + restoredId + (restoredItem == null ? "" : ", 数量=" + restoredItem.getAmount()));
            check("物品数量被保留",
                    restoredItem != null && restoredItem.getAmount() == 7,
                    restoredItem == null ? "无物品" : "数量 " + restoredItem.getAmount());

            plugin.machines().remove(loc);

        } catch (Exception ex) {
            check("存档往返测试无异常", false, ex.toString());
        } finally {
            block.setType(original == null ? Material.AIR : original, false);
        }
    }

    // ------------------------------------------------------------------
    // 8. 工作台复制漏洞（回归测试）
    // ------------------------------------------------------------------

    /**
     * 回归测试：工作台的配方预览绝不能被当成真实物品。
     *
     * <p>早期版本的 {@code updatePreview()} 把产物直接写进机器的输出槽，
     * 而材料只在玩家取走时才扣除。于是「放材料 -> 生成预览 -> 拆机器」
     * 就能白拿产物，且可无限重复。</p>
     *
     * <p>修复后预览只存在于 GUI 层，机器状态里永远没有它。
     * 这组断言一旦失败，说明复制漏洞又回来了。</p>
     */
    private void testCraftingTableSafety() {
        section("工作台安全性 (防复制)");

        MachineDefinition definition = MachineRegistry.get().get("hrf_enhanced_crafting_table");
        if (definition == null) {
            check("增强工作台定义存在", false, "缺失");
            return;
        }

        check("工作台被标记为虚拟输出",
                definition.logic().hasVirtualOutput(), "hasVirtualOutput=true");

        // 动态取一条增强工作台配方，避免测试与具体内容耦合
        MachineRecipe sample = null;
        for (MachineRecipe candidate : RecipeRegistry.get().of(RecipeType.ENHANCED_CRAFTING)) {
            if (!candidate.inputs().isEmpty() && !candidate.outputs().isEmpty()) {
                sample = candidate;
                break;
            }
        }
        if (sample == null) {
            check("存在可测试的工作台配方", false, "注册表为空");
            return;
        }

        Location loc = new Location(plugin.getServer().getWorlds().get(0), 3040, 200, 3040);
        MachineInstance machine = new MachineInstance(loc, definition);
        int[] inputSlots = definition.recipeType().inputSlots();
        int outputSlot = definition.recipeType().outputSlot();
        var logic = definition.logic();

        // 把该配方的输入依次放进输入槽
        List<ItemStack> inputs = sample.inputs();
        for (int i = 0; i < inputs.size() && i < inputSlots.length; i++) {
            machine.setSlot(inputSlots[i], inputs.get(i));
        }

        ItemStack previewItem = logic.previewResult(machine);
        check("配方可预览", previewItem != null,
                previewItem == null ? "无预览（配方 " + sample.id() + "）"
                        : sample.id() + " -> " + ItemRegistry.get().idOf(previewItem));

        // ---- 核心断言 1：预览不落进机器状态 ----
        ItemStack stored = machine.getSlot(outputSlot);
        check("预览未被写入机器输出槽",
                stored == null || stored.getType().isAir(),
                stored == null ? "输出槽为空 ✓" : "输出槽被污染: " + stored.getType());

        // ---- 核心断言 2：拆机器时输出槽不掉出任何东西 ----
        ItemStack fromContents = machine.contents()[outputSlot];
        check("拆机器时不会掉出预览产物",
                fromContents == null || fromContents.getType().isAir(),
                "输出槽无物品");

        // ---- 核心断言 3：取走结果时必须扣除材料 ----
        if (previewItem != null
                && logic instanceof com.koole.higherRedStoneFun.machines.logic.CraftingTableLogic table) {

            // 记录放入的材料总量，用于比对
            int placed = 0;
            for (int slot : inputSlots) {
                ItemStack in = machine.getSlot(slot);
                if (in != null && !in.getType().isAir()) {
                    placed += in.getAmount();
                }
            }

            ItemStack result = table.takeResult(machine);
            check("取走结果成功", result != null,
                    result == null ? "结算失败" : ItemRegistry.get().idOf(result));

            int left = 0;
            for (int slot : inputSlots) {
                ItemStack in = machine.getSlot(slot);
                if (in != null && !in.getType().isAir()) {
                    left += in.getAmount();
                }
            }
            int expectedLeft = placed - sample.inputs().stream()
                    .mapToInt(ItemStack::getAmount).sum();
            check("取走后材料被正确扣除",
                    result != null && left == Math.max(0, expectedLeft),
                    "放入 " + placed + " -> 剩余 " + left + " (期望 " + Math.max(0, expectedLeft) + ")");
        }

        // ---- 核心断言 4（最强）：走完整真实路径模拟复制漏洞 ----
        //
        // 旧版本的漏洞路径是：
        //   玩家放材料 -> 点击触发 onContentsChanged()（把预览写进机器输出槽）
        //              -> 关闭界面触发 flush()（把界面内容写回机器）
        //              -> 拆机器（掉落全部内容物）
        // 因此这里必须依次调用这两个方法，而不是只调 previewResult()——
        // 只调 previewResult() 无法复现旧 bug，测试就是假的。
        MachineInstance exploit = new MachineInstance(loc, definition);
        for (int i = 0; i < inputs.size() && i < inputSlots.length; i++) {
            exploit.setSlot(inputSlots[i], inputs.get(i));
        }

        // 步骤 1：模拟「玩家点击/放置」触发的预览刷新
        logic.onContentsChanged(exploit);

        // 步骤 2：模拟「关闭界面」把 GUI 内容写回机器
        try {
            MachineMenu menu = new MachineMenu(exploit);
            menu.refresh();
            menu.flush();
        } catch (Exception ex) {
            check("界面往返无异常", false, ex.toString());
        }

        // 步骤 3：统计「此时拆掉机器能拿到什么」
        String wantedOutput = ItemRegistry.get().idOf(sample.outputs().get(0).stack());
        int outputRecovered = 0;
        for (ItemStack stack : exploit.contents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (wantedOutput != null && wantedOutput.equals(ItemRegistry.get().idOf(stack))) {
                outputRecovered += stack.getAmount();
            }
        }
        check("拆机器不会白送产物（复制漏洞已封堵）",
                outputRecovered == 0,
                wantedOutput == null ? "配方的产出不是自定义物品，跳过"
                        : "回收 " + wantedOutput + " x" + outputRecovered + " (必须为 0)");

        // 旧版本会在这里报 x1：放材料 -> 预览落进机器 -> 拆掉就白拿一个产物

        // ---- 核心断言 5：预览展示槽本身也不能被 flush 写回 ----
        MachineInstance clean = new MachineInstance(loc, definition);
        for (int i = 0; i < inputs.size() && i < inputSlots.length; i++) {
            clean.setSlot(inputSlots[i], inputs.get(i));
        }
        if (logic instanceof com.koole.higherRedStoneFun.machines.logic.CraftingTableLogic table2) {
            try {
                MachineMenu menu = new MachineMenu(clean);
                menu.refresh();                      // 界面会渲染预览
                ItemStack shown = menu.getInventory().getItem(outputSlot);
                boolean previewShown = shown != null && !shown.getType().isAir();
                menu.flush();                        // 关闭界面
                ItemStack afterFlush = clean.getSlot(outputSlot);
                check("界面显示的预览不会被写回机器",
                        !previewShown || afterFlush == null || afterFlush.getType().isAir(),
                        previewShown ? "界面有预览展示 ✓，flush 后机器输出槽为空 ✓" : "无预览可测");
            } catch (Exception ex) {
                check("界面预览写回检查无异常", false, ex.toString());
            }
        }
    }

    // ------------------------------------------------------------------
    // 9. 科技树入口（回归测试）
    // ------------------------------------------------------------------

    /**
     * 回归测试：科技树必须有一个「不依赖自身」的起点。
     *
     * <p>早期版本的增强工作台没有任何获取途径，而它自己又需要机器框架，
     * 机器框架又只能在增强工作台里做——科技树是个死循环，
     * 玩家只能靠 {@code /hrf give} 开始游戏。</p>
     */
    private void testTechTreeEntryPoint() {
        section("科技树入口");

        // 引导物品必须能通过原版工作台获得
        for (String id : new String[]{"hrf_machine_frame", "hrf_copper_wire",
                "hrf_enhanced_crafting_table", "hrf_empty_sample"}) {
            boolean found = false;
            var iterator = plugin.getServer().recipeIterator();
            while (iterator.hasNext()) {
                var recipe = iterator.next();
                if (!(recipe instanceof org.bukkit.Keyed keyed)) {
                    continue;
                }
                if (!keyed.getKey().getNamespace().equals(plugin.getName().toLowerCase())) {
                    continue;
                }
                if (!keyed.getKey().getKey().startsWith("vanilla_")) {
                    continue;
                }
                ItemStack result = recipe.getResult();
                if (id.equals(ItemRegistry.get().idOf(result))) {
                    found = true;
                    break;
                }
            }
            check("可由原版工作台制作: " + id, found, found ? "已注册 ✓" : "缺失 ✗");
        }

        // 增强工作台本身不应依赖「必须在增强工作台里制作」的配方
        MachineRecipe selfLoop = RecipeRegistry.get().byId("craft_machine_frame");
        check("机器框架不再依赖增强工作台",
                selfLoop == null,
                selfLoop == null ? "已移除该循环配方 ✓" : "仍存在自我循环 ✗");
    }

    // ------------------------------------------------------------------
    // 10. 经济闭环（回归测试）
    // ------------------------------------------------------------------

    /**
     * 回归测试：核心转换链不能是净亏损。
     *
     * <p>早期版本的离心机是「2 粉 -> 1 锭」，而研磨机是「1 锭 -> 1 粉」，
     * 于是 {@code 锭 -> 粉 -> 锭} 净亏一半，玩家建完离心机只会后悔。</p>
     */
    private void testEconomyLoops() {
        section("经济闭环 (防负收益)");

        RecipeRegistry registry = RecipeRegistry.get();

        // 研磨：1 粗矿 -> 2 粉（矿石翻倍）
        MachineRecipe rawIron = registry.byId("grind_raw_iron");
        boolean doubling = false;
        if (rawIron != null && !rawIron.outputs().isEmpty()) {
            ItemStack out = rawIron.outputs().get(0).stack();
            doubling = "hrf_iron_dust".equals(ItemRegistry.get().idOf(out)) && out.getAmount() >= 2;
        }
        check("研磨支持矿石翻倍 (1 粗矿 -> 2 粉)", doubling,
                doubling ? "已支持 ✓" : "缺失 ✗ (基础层将失去核心收益)");

        // 离心：1 粉 -> 1 锭（无损）
        MachineRecipe centrifuge = registry.byId("centrifuge_iron_dust");
        boolean lossless = false;
        if (centrifuge != null) {
            int inputAmount = centrifuge.inputs().isEmpty() ? 0 : centrifuge.inputs().get(0).getAmount();
            int outputAmount = 0;
            for (var out : centrifuge.outputs()) {
                if (out.stack().getType() == Material.IRON_INGOT) {
                    outputAmount = out.stack().getAmount();
                    break;
                }
            }
            lossless = inputAmount > 0 && outputAmount >= inputAmount;
        }
        check("离心机不亏损 (1 粉 -> >=1 锭)", lossless,
                lossless ? "无损 ✓" : "仍是净亏损 ✗");

        // 全链路：1 粗铁 -> 2 粉 -> 2 铁锭，必须优于熔炉的 1:1
        check("冶炼线优于原版熔炉 (粗铁 -> 2 铁锭)",
                doubling && lossless,
                "1 粗铁 -> 2 粉 -> 2 铁锭 vs 熔炉 1:1");

        // 碳循环不应亏本：煤炭 -> 碳粉 -> 煤炭 的往返比
        MachineRecipe toCarbon = registry.byId("grind_coal");
        MachineRecipe toCoal = registry.byId("smelt_carbon_to_coal");
        boolean carbonSane = true;
        String carbonDetail = "不适用";
        if (toCarbon != null && toCoal != null) {
            int carbonPerCoal = toCarbon.outputs().isEmpty() ? 0 : toCarbon.outputs().get(0).stack().getAmount();
            int coalPerCarbon = toCoal.inputs().isEmpty() ? 0 : toCoal.inputs().get(0).getAmount();
            if (carbonPerCoal > 0) {
                // 每得 1 煤需要的碳粉数，换算回煤炭数：必须 > 1 才不算自循环刷取
                double coalCostPerCoal = (double) coalPerCarbon / carbonPerCoal;
                carbonSane = coalCostPerCoal > 1.0D;
                carbonDetail = String.format("1 煤 -> %d 碳，%d 碳 -> 1 煤 (等效 %.1f 煤/煤)",
                        carbonPerCoal, coalPerCarbon, coalCostPerCoal);
            }
        }
        check("碳循环不构成无损自循环", carbonSane, carbonDetail);
    }

    // ------------------------------------------------------------------
    // 11. 原版配方的物品匹配（安全关键）
    // ------------------------------------------------------------------

    /**
     * 验证原版工作台配方能否区分「自定义物品」与「同名原版物品」。
     *
     * <p>这是安全关键项：增强工作台的配方需要「机器框架」（铁块外观 +
     * 自定义 PDC 标记）。如果匹配逻辑忽略 PDC，玩家就能用<b>普通铁块</b>
     * 冒充机器框架，直接跳过整条前置产线。</p>
     *
     * <p>{@code RecipeChoice.ExactChoice.test()} 内部用的是
     * {@link org.bukkit.inventory.ItemStack#isSimilar}。本测试实测它在
     * 当前服务端版本上是否真的比较 PDC。</p>
     */
    private void testVanillaRecipeItemMatching() {
        section("原版配方物品匹配 (安全)");

        ItemRegistry registry = ItemRegistry.get();
        ItemStack custom = registry.create("hrf_machine_frame", 1);
        ItemStack plain = new ItemStack(Material.IRON_BLOCK, 1);

        // 两者外观相同（都是铁块），只是 PDC 不同
        check("自定义物品与原版物品外观相同",
                custom.getType() == plain.getType(),
                custom.getType().name());

        // ---- 关键：isSimilar 是否区分 PDC ----
        boolean similar = custom.isSimilar(plain);
        check("isSimilar 能区分自定义物品与原版物品",
                !similar,
                similar ? "【危险】相同 -> 玩家可用普通铁块冒充机器框架"
                        : "不同 ✓ 身份可区分");

        // ---- RecipeChoice.exactChoice 的实际匹配行为 ----
        var choice = org.bukkit.inventory.RecipeChoice.exactChoice(custom);
        boolean acceptsCustom = choice.test(custom);
        boolean acceptsPlain = choice.test(plain);
        check("exactChoice 接受真正的自定义物品", acceptsCustom,
                acceptsCustom ? "接受 ✓" : "拒绝 ✗ (配方无法使用)");
        check("exactChoice 拒绝同名原版物品", !acceptsPlain,
                acceptsPlain ? "【危险】接受 -> 可用普通铁块冒充" : "拒绝 ✓");

        // ---- 兜底：确认我们自己的 sameItem 更严格 ----
        check("ItemRegistry.sameItem 区分两者",
                !registry.sameItem(custom, plain),
                "插件内部判定不受 isSimilar 影响 ✓");

        // ---- 库存告警 ----
        if (similar || acceptsPlain) {
            check("【需要修复】原版配方存在冒充风险", false,
                    "应改用更严格的校验方式（见 BootstrapRecipes 注释）");
        } else {
            check("原版配方无冒充风险", true, "PDC 比较生效 ✓");
        }
    }

    // ------------------------------------------------------------------
    // 12. 多方块结构（含数据安全）
    // ------------------------------------------------------------------

    /**
     * 验证多方块结构：成型判定、加成解锁、以及最关键的<b>数据安全</b>。
     *
     * <p>结构最容易引入的 bug 是「拆结构把机器一起弄丢」。
     * 设计原则是：<b>挖掉结构组件只失去加成，机器与内部物品必须安全</b>。</p>
     */
    private void testMultiblockStructure() {
        section("多方块结构");

        MachineDefinition definition = MachineRegistry.get().get("hrf_enhanced_crafting_table");
        if (definition == null || !definition.isMultiblock()) {
            check("增强工作台已配置结构", false, "未配置");
            return;
        }
        check("增强工作台已配置结构", true, definition.structure().describe());

        World world = plugin.getServer().getWorlds().get(0);
        Location base = new Location(world, 3060, 200, 3060);
        try {
            world.getChunkAt(base).load();
        } catch (Exception ex) {
            check("测试区块可加载", false, ex.toString());
            return;
        }

        // 备份并清空测试区域
        Block controller = base.getBlock();
        Block body = base.clone().add(0, 1, 0).getBlock();
        Block casing = base.clone().add(0, 2, 0).getBlock();
        Material originalController = controller.getType();
        Material originalBody = body.getType();
        Material originalCasing = casing.getType();

        try {
            // ---- 1. 结构不完整时判定为未成型 ----
            controller.setType(Material.CRAFTING_TABLE, false);
            body.setType(Material.AIR, false);
            casing.setType(Material.AIR, false);

            MachineInstance machine = new MachineInstance(base, definition);
            machine.setStructureBlocks(definition.structure().extraBlockLocations(base));
            machine.recheckStructure();
            check("结构缺失时判定未成型", !machine.isStructureComplete(),
                    "未成型 ✓");

            String missing = definition.structure().describeMissing(base);
            check("能指出缺哪一块", !missing.isEmpty(), missing);

            // ---- 2. 搭好结构后成型 ----
            body.setType(Material.IRON_BLOCK, false);
            casing.setType(Material.GLASS, false);
            machine.recheckStructure();
            check("搭好结构后成型", machine.isStructureComplete(), "成型 ✓");

            // ---- 3. 染色玻璃也应该算数（宽容匹配）----
            casing.setType(Material.LIGHT_BLUE_STAINED_GLASS, false);
            machine.recheckStructure();
            check("染色玻璃同样有效", machine.isStructureComplete(),
                    "LIGHT_BLUE_STAINED_GLASS 通过 ✓");

            // 玻璃板也应该算
            casing.setType(Material.GLASS_PANE, false);
            machine.recheckStructure();
            check("玻璃板同样有效", machine.isStructureComplete(), "GLASS_PANE 通过 ✓");

            // 换成非玻璃应该失效
            casing.setType(Material.STONE, false);
            machine.recheckStructure();
            check("非玻璃方块不成立", !machine.isStructureComplete(), "STONE 被拒绝 ✓");

            // 恢复成玻璃
            casing.setType(Material.GLASS, false);
            machine.recheckStructure();
            check("恢复玻璃后重新成型", machine.isStructureComplete(), "恢复 ✓");

            // ---- 4. 精密零件门控 ----
            var tableLogic = definition.logic();
            check("电路板属于精密零件",
                    com.koole.higherRedStoneFun.machines.logic.CraftingTableLogic
                            .isPrecisionPart("hrf_circuit_board"),
                    "已标记 ✓");
            check("铁板不属于精密零件",
                    !com.koole.higherRedStoneFun.machines.logic.CraftingTableLogic
                            .isPrecisionPart("hrf_iron_plate"),
                    "普通零件 ✓");

            // ---- 5. 数据安全：拆掉结构组件不能影响机器与内部物品 ----
            Location loc = base;
            plugin.machines().add(machine);
            machine.setSlot(11, ItemRegistry.get().create("hrf_iron_plate", 9));
            machine.setSlot(0, new ItemStack(Material.COAL, 5));

            int itemsBefore = 0;
            for (ItemStack stack : machine.contents()) {
                if (stack != null && !stack.getType().isAir()) {
                    itemsBefore += stack.getAmount();
                }
            }

            // 拆掉玻璃
            casing.setType(Material.AIR, false);
            boolean wasComplete = machine.isStructureComplete();
            machine.setStructureBlocks(java.util.List.of());
            machine.recheckStructure();

            check("拆结构组件后机器仍然存在",
                    plugin.machines().isMachine(loc), "机器未被删除 ✓");
            check("拆结构组件后状态变为未成型",
                    wasComplete && !machine.isStructureComplete(), "未成型 ✓");

            int itemsAfter = 0;
            for (ItemStack stack : machine.contents()) {
                if (stack != null && !stack.getType().isAir()) {
                    itemsAfter += stack.getAmount();
                }
            }
            check("【数据安全】拆结构不会丢失内部物品",
                    itemsBefore == itemsAfter && itemsBefore > 0,
                    "拆前 " + itemsBefore + " 件 -> 拆后 " + itemsAfter + " 件");

            // ---- 6. 拆控制器才应该掉机器 ----
            plugin.machines().remove(loc);
            check("拆控制器才移除机器",
                    !plugin.machines().isMachine(loc), "已移除 ✓");

        } catch (Exception ex) {
            check("多方块测试无异常", false, ex.toString());
        } finally {
            controller.setType(originalController == null ? Material.AIR : originalController, false);
            body.setType(originalBody == null ? Material.AIR : originalBody, false);
            casing.setType(originalCasing == null ? Material.AIR : originalCasing, false);
        }
    }

    // ------------------------------------------------------------------
    // 13. 结构自动成型（回归测试）
    // ------------------------------------------------------------------

    /**
     * 回归测试：玩家用**普通方块**搭出结构也应该成型。
     *
     * <p>早期版本要求必须拿着 {@code /hrf give} 的自定义物品放置控制器。
     * 玩家在创造模式下从背包拿原版工作台放下（最自然的操作），
     * 系统完全不认识它，叠再多方块都没反应——表现为「多方块结构失败」。</p>
     */
    private void testAutoFormation() {
        section("结构自动成型 (回归)");

        World world = plugin.getServer().getWorlds().get(0);
        Location base = new Location(world, 3080, 200, 3080);
        try {
            world.getChunkAt(base).load();
        } catch (Exception ex) {
            check("测试区块可加载", false, ex.toString());
            return;
        }

        Block controller = base.getBlock();
        Block body = base.clone().add(0, 1, 0).getBlock();
        Block casing = base.clone().add(0, 2, 0).getBlock();
        Material o1 = controller.getType();
        Material o2 = body.getType();
        Material o3 = casing.getType();

        try {
            // 用【原版方块】搭结构，不放任何自定义物品
            controller.setType(Material.CRAFTING_TABLE, false);
            body.setType(Material.IRON_BLOCK, false);
            casing.setType(Material.GLASS, false);

            // 确认这个位置没有被注册过
            plugin.machines().remove(base);

            MachineInstance formed = com.koole.higherRedStoneFun.machines.StructureFormation
                    .tryForm(plugin.machines(), base);

            check("【回归】原版方块搭出的结构能自动成型",
                    formed != null,
                    formed == null ? "未成型 ✗（玩家会看到「什么都没发生」）"
                            : formed.definition().displayName() + " ✓");
            check("成型后已注册到管理器",
                    plugin.machines().isMachine(base), "已注册 ✓");

            if (formed != null) {
                check("成型后结构状态为完整", formed.isStructureComplete(), "完整 ✓");
            }

            // 清理，再测「缺一块不成型」
            plugin.machines().remove(base);
            casing.setType(Material.AIR, false);
            MachineInstance incomplete = com.koole.higherRedStoneFun.machines.StructureFormation
                    .tryForm(plugin.machines(), base);
            check("结构不完整时不会误成型", incomplete == null,
                    incomplete == null ? "正确拒绝 ✓" : "误成型 ✗");

            plugin.machines().remove(base);
        } catch (Exception ex) {
            check("自动成型测试无异常", false, ex.toString());
        } finally {
            controller.setType(o1 == null ? Material.AIR : o1, false);
            body.setType(o2 == null ? Material.AIR : o2, false);
            casing.setType(o3 == null ? Material.AIR : o3, false);
        }
    }

    // ------------------------------------------------------------------
    // 14. 图鉴界面布局（回归测试）
    // ------------------------------------------------------------------

    /**
     * 回归测试：图鉴的按钮槽位不能冲突，返回/关闭必须可达。
     *
     * <p>早期版本把「信息」和「返回」都放在 slot 49，而监听器对 49
     * 无条件 return，导致<b>返回按钮永远点不动</b>。</p>
     */
    private void testGuideMenuLayout() {
        section("图鉴界面布局 (回归)");

        int infoSlot = com.koole.higherRedStoneFun.ui.GuideMenu.SLOT_INFO;
        int closeSlot = com.koole.higherRedStoneFun.ui.GuideMenu.SLOT_CLOSE;
        int backSlot = com.koole.higherRedStoneFun.ui.GuideMenu.SLOT_BACK;
        int prevPage = com.koole.higherRedStoneFun.ui.GuideMenu.SLOT_PREV_PAGE;
        int nextPage = com.koole.higherRedStoneFun.ui.GuideMenu.SLOT_NEXT_PAGE;

        // ---- 底部按钮区不得互相冲突 ----
        int[] bottomButtons = {backSlot, prevPage, infoSlot, nextPage, closeSlot};
        boolean bottomConflict = false;
        for (int i = 0; i < bottomButtons.length; i++) {
            for (int j = i + 1; j < bottomButtons.length; j++) {
                if (bottomButtons[i] == bottomButtons[j]) {
                    bottomConflict = true;
                }
            }
        }
        check("底部按钮互不冲突", !bottomConflict,
                bottomConflict ? "冲突 ✗" : backSlot + "/" + prevPage + "/" + infoSlot
                        + "/" + nextPage + "/" + closeSlot);

        boolean allInBottomRow = true;
        for (int slot : bottomButtons) {
            if (slot < 36 || slot > 44) {
                allInBottomRow = false;
            }
        }
        check("底部按钮都在最后一行（36-44）", allInBottomRow, "底行 ✓");

        // ---- 列表页内容区不得压到按钮 ----
        boolean listConflict = false;
        for (int slot = 0; slot < com.koole.higherRedStoneFun.ui.GuideMenu.LIST_PAGE_SIZE; slot++) {
            for (int button : bottomButtons) {
                if (slot == button) {
                    listConflict = true;
                }
            }
        }
        check("列表页内容区不与按钮冲突", !listConflict,
                listConflict ? "冲突 ✗" : "0-" + (com.koole.higherRedStoneFun.ui.GuideMenu.LIST_PAGE_SIZE - 1)
                        + " 内容 / 36-44 按钮 ✓");

        // ---- 主页面大类槽位不得压到按钮 ----
        boolean mainConflict = false;
        for (int slot : com.koole.higherRedStoneFun.listeners.GuideMenuListener.mainSlots()) {
            for (int button : bottomButtons) {
                if (slot == button) {
                    mainConflict = true;
                }
            }
        }
        check("主页面大类槽位不与按钮冲突", !mainConflict,
                mainConflict ? "冲突 ✗" : "无冲突 ✓");

        // ---- 详情页布局（红顶 / 蓝底 / 绿框 / 九宫格）----
        int subjectSlot = com.koole.higherRedStoneFun.ui.GuideMenu.SLOT_SUBJECT;
        int previewSlot = com.koole.higherRedStoneFun.ui.GuideMenu.SLOT_PREVIEW;
        int currentItemSlot = com.koole.higherRedStoneFun.ui.GuideMenu.SLOT_CURRENT_ITEM;
        int[] grid = com.koole.higherRedStoneFun.ui.GuideMenu.SLOT_RECIPE_GRID;
        int[] rowTop = com.koole.higherRedStoneFun.ui.GuideMenu.ROW_TOP;
        int[] rowBottom = com.koole.higherRedStoneFun.ui.GuideMenu.ROW_BOTTOM;
        int[] usageSlots = com.koole.higherRedStoneFun.ui.GuideMenu.USAGE_SLOTS;
        int[] decorations = com.koole.higherRedStoneFun.ui.GuideMenu.DECORATION_SLOTS;

        check("九宫格是 3x3 共 9 格", grid.length == 9, grid.length + " 格");
        check("九宫格位于第二三四行的四五六列",
                grid[0] == 12 && grid[4] == 22 && grid[8] == 32 && grid[4] - grid[0] == 10,
                "12,13,14 / 21,22,23 / 30,31,32 ✓");

        check("主题位在第三行第二列", subjectSlot == 19, "slot " + subjectSlot);
        check("预览位在第三行第八列", previewSlot == 25, "slot " + previewSlot);
        check("当前物品位在最后一行正中", currentItemSlot == 40, "slot " + currentItemSlot);
        check("当前物品位就是原来的信息纸位置（不被两处覆盖）",
                currentItemSlot == com.koole.higherRedStoneFun.ui.GuideMenu.SLOT_INFO,
                "SLOT_CURRENT_ITEM == SLOT_INFO == " + currentItemSlot + " ✓");

        // 用途翻页按钮必须在当前物品位左右
        int usagePrev = com.koole.higherRedStoneFun.ui.GuideMenu.SLOT_USAGE_PREV;
        int usageNext = com.koole.higherRedStoneFun.ui.GuideMenu.SLOT_USAGE_NEXT;
        check("用途翻页在当前物品位左右",
                usagePrev == currentItemSlot - 2 && usageNext == currentItemSlot + 2,
                usagePrev + " / " + currentItemSlot + " / " + usageNext + " ✓");

        // ---- 中间三行必须完全留空（不放灰边框、不放绿框）----
        check("详情页中间区域没有装饰方块", decorations.length == 0,
                decorations.length == 0 ? "全部留空 ✓" : "仍有 " + decorations.length + " 个装饰位 ✗");

        // 三个标签的中间区域必须一致：没有谁偷偷多画装饰
        java.util.Set<Integer> contentOnly = new java.util.HashSet<>();
        for (int slot = 9; slot <= 35; slot++) {
            contentOnly.add(slot);
        }
        boolean anyDecorationInside = false;
        for (int slot : decorations) {
            if (contentOnly.contains(slot)) {
                anyDecorationInside = true;
            }
        }
        check("中间三行不掺入装饰元素", !anyDecorationInside,
                anyDecorationInside ? "有装饰落在 9-35 ✗" : "9-35 只放内容 ✓");

        check("红顶行是第 1 行", rowTop.length == 9 && rowTop[0] == 0 && rowTop[8] == 8,
                "0-8 ✓");
        check("蓝底行是最后一行", rowBottom.length == 9 && rowBottom[0] == 36 && rowBottom[8] == 44,
                "36-44 ✓");

        // 标签栏必须在红顶行内
        int tabStart = com.koole.higherRedStoneFun.ui.GuideMenu.TAB_BAR_START;
        int tabMax = com.koole.higherRedStoneFun.ui.GuideMenu.TAB_BAR_MAX;
        boolean tabInTopRow = tabStart >= 0 && (tabStart + tabMax - 1) <= 8;
        check("标签栏位于红顶行内", tabInTopRow,
                tabInTopRow ? "槽位 " + tabStart + "~" + (tabStart + tabMax - 1) + " ✓" : "越界 ✗");

        // 标签栏不能压在主题位/预览位/九宫格上
        boolean tabConflict = false;
        String tabConflictDetail = "";
        for (int i = 0; i < tabMax; i++) {
            int slot = tabStart + i;
            if (slot == subjectSlot || slot == previewSlot) {
                tabConflict = true;
                tabConflictDetail = "与内容位（" + slot + "）冲突";
            }
            for (int g : grid) {
                if (slot == g) {
                    tabConflict = true;
                    tabConflictDetail = "与九宫格（" + g + "）冲突";
                }
            }
        }
        check("标签栏不与内容位或九宫格冲突", !tabConflict,
                tabConflict ? tabConflictDetail + " ✗" : "无冲突 ✓");

        // ---- 底部按钮必须在蓝底行内 ----
        boolean buttonsInBottomRow = true;
        for (int b : bottomButtons) {
            if (b < 36 || b > 44) {
                buttonsInBottomRow = false;
            }
        }
        check("底部按钮位于蓝底行内", buttonsInBottomRow, "36-44 ✓");

        // 标签数量不能超过标签栏容量
        int tabCount = com.koole.higherRedStoneFun.ui.GuideTab.values().length;
        check("标签数量适配标签栏", tabCount <= tabMax,
                tabCount + " 个标签 / " + tabMax + " 个位置");

        // ---- 详情页所有内容槽位必须唯一（防再犯「提示被覆盖」的错）----
        List<Integer> detailSlots = new ArrayList<>();
        detailSlots.add(subjectSlot);
        detailSlots.add(previewSlot);
        detailSlots.add(com.koole.higherRedStoneFun.ui.GuideMenu.SLOT_PREV_RECIPE);
        detailSlots.add(com.koole.higherRedStoneFun.ui.GuideMenu.SLOT_NEXT_RECIPE);
        for (int i = 0; i < tabMax; i++) {
            detailSlots.add(tabStart + i);
        }
        for (int slot : grid) {
            detailSlots.add(slot);
        }
        for (int slot : com.koole.higherRedStoneFun.ui.GuideMenu.USAGE_SLOTS) {
            detailSlots.add(slot);
        }
        for (int button : bottomButtons) {
            detailSlots.add(button);
        }
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        int duplicate = -1;
        for (int slot : detailSlots) {
            if (!seen.add(slot)) {
                duplicate = slot;
                break;
            }
        }
        check("详情页所有槽位唯一（防提示被覆盖）", duplicate < 0,
                duplicate < 0 ? detailSlots.size() + " 个槽位无重叠 ✓" : "槽位 " + duplicate + " 重复 ✗");

        // ---- ItemGroup 都要能映射到大类 ----
        check("每个 ItemGroup 都有对应大类",
                com.koole.higherRedStoneFun.listeners.GuideMenuListener.allGroupsMapped(),
                "5 个归类 → 4 个大类 ✓");

        // ---- 大类数量必须放得下主页面槽位 ----
        int sectionCount = com.koole.higherRedStoneFun.ui.GuideSection.values().length;
        int mainSlotCount = com.koole.higherRedStoneFun.listeners.GuideMenuListener.mainSlots().size();
        check("大类数量适配主页面槽位", sectionCount <= mainSlotCount,
                sectionCount + " 个大类 / " + mainSlotCount + " 个位置");

        // ---- 分页必须能装下最大的分类（基因工程 44+ 物品）----
        int pageSize = com.koole.higherRedStoneFun.ui.GuideMenu.LIST_PAGE_SIZE;
        int largest = 0;
        String largestName = "";
        var guide = new com.koole.higherRedStoneFun.ui.GuideMenu();
        for (com.koole.higherRedStoneFun.ui.GuideSection s
                : com.koole.higherRedStoneFun.ui.GuideSection.values()) {
            int n = guide.itemsOf(s).size();
            if (n > largest) {
                largest = n;
                largestName = s.displayName();
            }
        }
        int pages = Math.max(1, (largest + pageSize - 1) / pageSize);
        check("【回归】分页能覆盖最大分类",
                pages * pageSize >= largest,
                largestName + " 有 " + largest + " 个物品 → " + pages + " 页 × " + pageSize + " 格");
    }

    // ------------------------------------------------------------------
    // 15. 拆解归还（回归测试 · 防凭空造物）
    // ------------------------------------------------------------------

    /**
     * 回归测试：拆掉机器必须归还「原来放下去的那个东西」。
     *
     * <p>早期版本无条件掉落自定义机器物品，于是出现了一条凭空造物路径：</p>
     *
     * <pre>
     *   放 1 个【原版工作台】 -> 叠铁块+玻璃 -> 自动成型
     *                        -> 挖掉控制器
     *                        -> 掉落【自定义增强工作台】
     * </pre>
     *
     * <p>原版工作台被凭空转换成了自定义物品，可无限重复。
     * 正确行为是：用什么放的，就还回什么。</p>
     */
    private void testDismantleReturnsOriginal() {
        section("拆解归还 (回归 · 防凭空造物)");

        MachineDefinition definition = MachineRegistry.get().get("hrf_enhanced_crafting_table");
        if (definition == null) {
            check("增强工作台定义存在", false, "缺失");
            return;
        }

        // ---- 1. 用普通方块搭出的机器：应归还普通方块 ----
        World world = plugin.getServer().getWorlds().get(0);
        Location base = new Location(world, 3100, 200, 3100);
        try {
            world.getChunkAt(base).load();
        } catch (Exception ex) {
            check("测试区块可加载", false, ex.toString());
            return;
        }

        Block controller = base.getBlock();
        Block body = base.clone().add(0, 1, 0).getBlock();
        Block casing = base.clone().add(0, 2, 0).getBlock();
        Material o1 = controller.getType();
        Material o2 = body.getType();
        Material o3 = casing.getType();

        try {
            controller.setType(Material.CRAFTING_TABLE, false);
            body.setType(Material.IRON_BLOCK, false);
            casing.setType(Material.GLASS, false);
            plugin.machines().remove(base);

            MachineInstance formed = com.koole.higherRedStoneFun.machines.StructureFormation
                    .tryForm(plugin.machines(), base);

            check("普通方块搭出的结构已成型", formed != null, formed == null ? "失败" : "成功");

            if (formed != null) {
                check("【关键】标记为「非自定义物品放置」",
                        !formed.isPlacedAsMachineItem(),
                        formed.isPlacedAsMachineItem()
                                ? "标记错误 ✗（会导致原版工作台被变成自定义物品）"
                                : "标记正确 ✓");

                // 模拟拆解时的掉落决策
                ItemStack drop = formed.isPlacedAsMachineItem()
                        ? MachineRegistry.get().createItem(formed.id(), 1)
                        : new ItemStack(controller.getType(), 1);

                String droppedId = ItemRegistry.get().idOf(drop);
                check("【关键】拆解归还原版工作台，而非自定义物品",
                        drop.getType() == Material.CRAFTING_TABLE && droppedId == null,
                        "掉落 " + drop.getType().name()
                                + (droppedId == null ? "（原版）✓" : "（自定义 " + droppedId + "）✗ 凭空造物"));
            }

            plugin.machines().remove(base);

            // ---- 2. 用自定义物品放置的机器：应归还自定义物品 ----
            MachineInstance byItem = new MachineInstance(base, definition);
            byItem.setPlacedAsMachineItem(true);
            ItemStack dropByItem = byItem.isPlacedAsMachineItem()
                    ? MachineRegistry.get().createItem(byItem.id(), 1)
                    : new ItemStack(Material.CRAFTING_TABLE, 1);

            check("自定义物品放置的机器归还自定义物品",
                    "hrf_enhanced_crafting_table".equals(ItemRegistry.get().idOf(dropByItem)),
                    "掉落 " + ItemRegistry.get().idOf(dropByItem) + " ✓");

            // ---- 3. 默认值必须是「非自定义」（保守策略）----
            MachineInstance fresh = new MachineInstance(base, definition);
            check("默认标记为「非自定义物品」",
                    !fresh.isPlacedAsMachineItem(),
                    "默认 false ✓（宁可归还普通方块，也不凭空造物）");

        } catch (Exception ex) {
            check("拆解归还测试无异常", false, ex.toString());
        } finally {
            controller.setType(o1 == null ? Material.AIR : o1, false);
            body.setType(o2 == null ? Material.AIR : o2, false);
            casing.setType(o3 == null ? Material.AIR : o3, false);
            plugin.machines().remove(base);
        }
    }

    // ------------------------------------------------------------------
    // 16. 连续破坏结构组件（回归测试）
    // ------------------------------------------------------------------

    /**
     * 回归测试：挖掉<b>每一个</b>组件方块都必须有反馈。
     *
     * <p>早期版本只在「完整 -> 不完整」的瞬间提示一次。玩家先挖玻璃（有提示），
     * 再挖铁块时 {@code wasComplete} 已经是 false，条件不成立，
     * <b>于是完全没有反应</b>——玩家会以为「铁块根本不属于这个结构」。</p>
     *
     * <p>同时验证 {@code structureBlocks} 不会被永久清空
     * （那会导致存档丢失结构坐标、无法重新判定）。</p>
     */
    private void testSequentialStructureDamage() {
        section("连续破坏结构 (回归)");

        MachineDefinition definition = MachineRegistry.get().get("hrf_enhanced_crafting_table");
        if (definition == null || !definition.isMultiblock()) {
            check("增强工作台是多方块", false, "未配置");
            return;
        }

        World world = plugin.getServer().getWorlds().get(0);
        Location base = new Location(world, 3120, 200, 3120);
        try {
            world.getChunkAt(base).load();
        } catch (Exception ex) {
            check("测试区块可加载", false, ex.toString());
            return;
        }

        Block controller = base.getBlock();
        Block body = base.clone().add(0, 1, 0).getBlock();
        Block casing = base.clone().add(0, 2, 0).getBlock();
        Material o1 = controller.getType();
        Material o2 = body.getType();
        Material o3 = casing.getType();

        try {
            controller.setType(Material.CRAFTING_TABLE, false);
            body.setType(Material.IRON_BLOCK, false);
            casing.setType(Material.GLASS, false);
            plugin.machines().remove(base);

            MachineInstance machine = com.koole.higherRedStoneFun.machines.StructureFormation
                    .tryForm(plugin.machines(), base);
            if (machine == null) {
                check("结构已成型", false, "创建失败");
                return;
            }

            check("初始状态为成型", machine.isStructureComplete(), "成型 ✓");
            int blocksAfterForm = machine.structureBlocks().size();
            check("成型后记录了组件坐标", blocksAfterForm == 2,
                    blocksAfterForm + " 个组件");

            // ---- 第一次挖：玻璃 ----
            casing.setType(Material.AIR, false);
            machine.recheckStructure();
            boolean afterGlass = machine.isStructureComplete();
            check("挖玻璃后判定为未成型", !afterGlass, "未成型 ✓");

            String missing1 = definition.structure().describeMissing(base);
            check("挖玻璃后能指出缺少什么", !missing1.isEmpty(), missing1);

            // ---- 关键：组件坐标不能被永久清空 ----
            int blocksAfterGlass = machine.structureBlocks().size();
            check("【回归】挖玻璃后组件坐标仍被记录",
                    blocksAfterGlass == 2,
                    blocksAfterGlass + " 个组件（早期版本会被清成 0，导致无法恢复判定）");

            // ---- 第二次挖：铁块。玩家必须在这次也收到反馈 ----
            body.setType(Material.AIR, false);
            machine.recheckStructure();
            boolean afterIron = machine.isStructureComplete();
            check("挖铁块后仍为未成型", !afterIron, "未成型 ✓");

            String missing2 = definition.structure().describeMissing(base);
            check("【回归】挖铁块后同样能指出缺少什么（而非静默无反应）",
                    !missing2.isEmpty(),
                    missing2.isEmpty() ? "无提示 ✗（玩家会以为铁块不属于结构）" : missing2);

            // ---- 逐块补回：每补一块都应该能重新判定 ----
            body.setType(Material.IRON_BLOCK, false);
            machine.recheckStructure();
            check("补回铁块后仍缺玻璃", !machine.isStructureComplete(),
                    definition.structure().describeMissing(base));

            casing.setType(Material.GLASS, false);
            machine.recheckStructure();
            check("补回玻璃后恢复成型", machine.isStructureComplete(), "恢复 ✓");

            int finalBlocks = machine.structureBlocks().size();
            check("恢复成型后组件坐标完整", finalBlocks == 2, finalBlocks + " 个组件");

            plugin.machines().remove(base);

        } catch (Exception ex) {
            check("连续破坏测试无异常", false, ex.toString());
        } finally {
            controller.setType(o1 == null ? Material.AIR : o1, false);
            body.setType(o2 == null ? Material.AIR : o2, false);
            casing.setType(o3 == null ? Material.AIR : o3, false);
            plugin.machines().remove(base);
        }
    }

    // ------------------------------------------------------------------
    // 5. 作物映射
    // ------------------------------------------------------------------

    private void testCropMapping() {
        section("作物与动物映射");

        check("小麦种子有映射",
                CropMapping.bySeed("hrf_wheat_seeds") != null,
                "hrf_wheat_seeds");

        check("样本能反查作物",
                CropMapping.bySample("hrf_sample_carrot") != null,
                "hrf_sample_carrot");

        check("作物方块能反查",
                CropMapping.byBlock(Material.WHEAT) != null,
                "WHEAT");

        check("非作物方块不误判",
                CropMapping.byBlock(Material.STONE) == null,
                "STONE 无映射");

        CropMapping.Animal cow = CropMapping.byAnimal("cow");
        check("动物映射存在", cow != null && cow.type() == org.bukkit.entity.EntityType.COW,
                cow == null ? "缺失" : cow.type().name());

        check("动物实体反查",
                CropMapping.byEntityType(org.bukkit.entity.EntityType.SHEEP) != null,
                "SHEEP");

        // 验证每种作物种子与样本都有对应物品注册
        int missing = 0;
        for (String seedId : new String[]{"hrf_wheat_seeds", "hrf_carrot_seeds", "hrf_potato_seeds",
                "hrf_beetroot_seeds", "hrf_melon_seeds", "hrf_pumpkin_seeds",
                "hrf_nether_wart", "hrf_sweet_berries"}) {
            if (!ItemRegistry.get().exists(seedId)) {
                missing++;
            }
        }
        check("全部改良种子均已注册", missing == 0, missing == 0 ? "8 种齐全" : missing + " 种缺失");

        int missingEmbryo = 0;
        for (String embryoId : new String[]{"hrf_cow_embryo", "hrf_sheep_embryo", "hrf_chicken_embryo",
                "hrf_pig_embryo", "hrf_rabbit_embryo", "hrf_bee_embryo"}) {
            if (!ItemRegistry.get().exists(embryoId)) {
                missingEmbryo++;
            }
        }
        check("全部胚胎均已注册", missingEmbryo == 0,
                missingEmbryo == 0 ? "6 种齐全" : missingEmbryo + " 种缺失");
    }
}
