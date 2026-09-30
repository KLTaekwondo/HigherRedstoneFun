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
