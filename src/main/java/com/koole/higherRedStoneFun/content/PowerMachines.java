package com.koole.higherRedStoneFun.content;

import com.koole.higherRedStoneFun.core.ItemGroup;
import com.koole.higherRedStoneFun.energy.EnergyNode;
import com.koole.higherRedStoneFun.machines.MachineDefinition;
import com.koole.higherRedStoneFun.machines.MachineRegistry;
import com.koole.higherRedStoneFun.machines.logic.GeneratorLogic;
import com.koole.higherRedStoneFun.machines.logic.ReactorLogic;
import com.koole.higherRedStoneFun.machines.logic.RecipeMachineLogic;
import com.koole.higherRedStoneFun.machines.logic.SolarGeneratorLogic;
import com.koole.higherRedStoneFun.recipes.RecipeType;
import org.bukkit.Material;

/**
 * 模块二：红石流能机器。
 *
 * <p>设计意图：把「红石流能」做成一整套可管理的系统，而不是单纯的燃料替代品。
 * 基础机器烧红石驱动，红石流能机器则接入能源网络，用 <b>HRE</b>
 * （Higher Redstone Energy，红石流能）计量产能、耗能与储能。</p>
 *
 * <ul>
 *   <li><b>发电端</b>：红石发电机（稳定）→ 太阳能（免费但看天）→ 地热（看环境）→ 核反应堆（最强但会熔毁），
 *       形成清晰的升级曲线。</li>
 *   <li><b>储能端</b>：电容，用于跨昼夜缓冲，决定电网的稳定性。</li>
 *   <li><b>耗能端</b>：红石流能熔炼、红石流能研磨、组装机、分子重组机，速度与耗能挂钩。</li>
 * </ul>
 */
public final class PowerMachines {

    private PowerMachines() {
    }

    public static void register() {
        MachineRegistry registry = MachineRegistry.get();

        // ==========================================================
        // 发电端
        // ==========================================================

        // id 保留 hrf_coal_generator：改名不改 id，避免破坏已有存档与配方引用
        registry.register(MachineDefinition.builder(
                        "hrf_coal_generator", "红石发电机", ItemGroup.POWER_MACHINES, Material.FURNACE)
                .energyRole(EnergyNode.Role.GENERATOR)
                .throughput(8L)
                .buffer(2_000L)
                .inventorySize(9)
                .logic(new GeneratorLogic(8L))
                .lore("<gray>最基础的发电机，烧红石产出红石流能")
                .lore("<gray>产能: <red>8 HRE/t</red> <dark_gray>(≈160 HRE/s)")
                .lore("<gray>内部缓冲: <red>2,000 HRE")
                .build());

        registry.register(MachineDefinition.builder(
                        "hrf_solar_generator", "太阳能发电机", ItemGroup.POWER_MACHINES, Material.DAYLIGHT_DETECTOR)
                .energyRole(EnergyNode.Role.GENERATOR)
                .throughput(12L)
                .buffer(4_000L)
                .inventorySize(9)
                .logic(new SolarGeneratorLogic(12L))
                .lore("<gray>白天免费产能，但完全依赖天气与光照")
                .lore("<gray>峰值产能: <red>12 HRE/t</red> <dark_gray>(满光照、晴天)")
                .lore("<gold>必须露天放置，上方不能有遮挡")
                .lore("<gray>夜间 / 降雨 / 雷暴会大幅降低效率")
                .build());

        registry.register(MachineDefinition.builder(
                        "hrf_geothermal_generator", "地热发电机", ItemGroup.POWER_MACHINES, Material.MAGMA_BLOCK)
                .energyRole(EnergyNode.Role.GENERATOR)
                .throughput(20L)
                .buffer(8_000L)
                .inventorySize(9)
                .logic(new GeothermalLogic(20L))
                .lore("<gray>利用岩浆的热量持续产出红石流能")
                .lore("<gray>产能: <red>20 HRE/t</red> <dark_gray>(≈400 HRE/s)")
                .lore("<gold>下方 3 格内需要有岩浆")
                .build());

        registry.register(MachineDefinition.builder(
                        "hrf_nuclear_reactor", "核反应堆", ItemGroup.POWER_MACHINES, Material.RESPAWN_ANCHOR)
                .energyRole(EnergyNode.Role.GENERATOR)
                .throughput(400L)
                .buffer(120_000L)
                .inventorySize(9)
                .logic(new ReactorLogic(400L))
                .lore("<gray>全插件最强的电源，但会熔毁爆炸")
                .lore("<dark_red><bold>终局发电机</bold>")
                .lore("<gray>产能: <red>400 HRE/t</red> <dark_gray>(≈8,000 HRE/s)")
                .lore("<gray>燃料槽: 下界合金碎片 / 远古残骸 / 烈焰棒")
                .lore("<gray>冷却槽: 蓝冰 / 浮冰 / 冰 / 雪球")
                .lore("<red>失去冷却会持续升温，满值后熔毁爆炸！")
                .glow(true)
                .build());

        // ==========================================================
        // 储能端
        // ==========================================================

        registry.register(MachineDefinition.builder(
                        "hrf_capacitor_basic", "基础电容", ItemGroup.POWER_MACHINES, Material.COPPER_BLOCK)
                .energyRole(EnergyNode.Role.STORAGE)
                .buffer(50_000L)
                .inventorySize(9)
                .lore("<gray>存储红石流能，用于跨昼夜缓冲")
                .lore("<gray>容量: <red>50,000 HRE")
                .build());

        registry.register(MachineDefinition.builder(
                        "hrf_capacitor_advanced", "高级电容", ItemGroup.POWER_MACHINES, Material.AMETHYST_BLOCK)
                .energyRole(EnergyNode.Role.STORAGE)
                .buffer(500_000L)
                .inventorySize(9)
                .lore("<gray>大容量储能，阵列化后可支撑整座基地")
                .lore("<gray>容量: <red>500,000 HRE")
                .glow(true)
                .build());

        registry.register(MachineDefinition.builder(
                        "hrf_energy_regulator", "能源调节器", ItemGroup.POWER_MACHINES, Material.TARGET)
                .energyRole(EnergyNode.Role.STORAGE)
                .buffer(1_000_000L)
                .inventorySize(9)
                .lore("<gray>超大型储能与稳压设备")
                .lore("<gray>容量: <red>1,000,000 HRE")
                .lore("<gold>建议与核反应堆配套使用")
                .build());

        // ==========================================================
        // 耗能端
        // ==========================================================

        registry.register(MachineDefinition.builder(
                        "hrf_electric_smeltery", "红石流能冶炼炉", ItemGroup.POWER_MACHINES, Material.BLAST_FURNACE)
                .recipeType(RecipeType.ELECTRIC_SMELTING)
                .energyRole(EnergyNode.Role.CONSUMER)
                .buffer(12_000L)
                .throughput(6L)
                .inventorySize(27)
                .logic(RecipeMachineLogic.electric(6L))
                .lore("<gray>由红石流能驱动的冶炼炉，速度是原版熔炉的 8 倍")
                .lore("<gray>耗能: <red>6 HRE/t")
                .build());

        registry.register(MachineDefinition.builder(
                        "hrf_electric_grinder", "红石流能研磨机", ItemGroup.POWER_MACHINES, Material.GRINDSTONE)
                .recipeType(RecipeType.ELECTRIC_GRINDING)
                .energyRole(EnergyNode.Role.CONSUMER)
                .buffer(10_000L)
                .throughput(5L)
                .inventorySize(27)
                .logic(RecipeMachineLogic.electric(5L))
                .lore("<gray>红石流能驱动的高速研磨，效率高于燃料版")
                .lore("<gray>耗能: <red>5 HRE/t")
                .build());

        registry.register(MachineDefinition.builder(
                        "hrf_assembler", "组装机", ItemGroup.POWER_MACHINES, Material.CRAFTER)
                .recipeType(RecipeType.ASSEMBLING)
                .energyRole(EnergyNode.Role.CONSUMER)
                .buffer(30_000L)
                .throughput(25L)
                .inventorySize(45)
                .logic(RecipeMachineLogic.electric(25L))
                .lore("<gray>自动组装复杂机械与电路")
                .lore("<gray>耗能: <red>25 HRE/t")
                .lore("<gold>高级机器的必经之路")
                .build());

        registry.register(MachineDefinition.builder(
                        "hrf_molecular_recombinator", "分子重组机", ItemGroup.POWER_MACHINES, Material.BEACON)
                .recipeType(RecipeType.MOLECULAR)
                .energyRole(EnergyNode.Role.CONSUMER)
                .buffer(120_000L)
                .throughput(120L)
                .inventorySize(45)
                .logic(RecipeMachineLogic.electric(120L))
                .lore("<gray>在分子层面重组物质，产出稀有材料")
                .lore("<dark_purple><bold>终局合成设备</bold>")
                .lore("<gray>耗能: <red>120 HRE/t")
                .glow(true)
                .build());
    }

    /**
     * 地热发电机：需要下方有岩浆。
     *
     * <p>它比太阳能更稳定（不受昼夜影响），但需要玩家寻找合适的地形，
     * 形成「选址」这一层玩法。</p>
     */
    private static final class GeothermalLogic implements com.koole.higherRedStoneFun.machines.MachineLogic {

        private final long output;
        /** 每个岩浆桶提供的运行 tick。 */
        private static final int LAVA_BUCKET_TICKS = 20_000;

        GeothermalLogic(long output) {
            this.output = output;
        }

        @Override
        public boolean energyAware() {
            return true;
        }

        @Override
        public long generate(com.koole.higherRedStoneFun.machines.MachineInstance machine) {
            boolean heated = hasLavaBelow(machine) || machine.hasFuel() || refuelLava(machine);
            if (!heated) {
                return 0L;
            }
            if (!hasLavaBelow(machine)) {
                machine.consumeFuelTick();
            }
            machine.addGenerated(output);
            return output;
        }

        /** 检查机器下方 3 格内是否有岩浆。 */
        private boolean hasLavaBelow(com.koole.higherRedStoneFun.machines.MachineInstance machine) {
            org.bukkit.block.Block below = machine.location().getBlock().getRelative(0, -1, 0);
            for (int i = 0; i < 3; i++) {
                if (below.getType() == Material.LAVA) {
                    return true;
                }
                below = below.getRelative(0, -1, 0);
            }
            return false;
        }

        /** 允许用岩浆桶作为备用燃料。 */
        private boolean refuelLava(com.koole.higherRedStoneFun.machines.MachineInstance machine) {
            org.bukkit.inventory.ItemStack fuel = machine.getSlot(0);
            if (fuel == null || fuel.getType() != Material.LAVA_BUCKET) {
                return false;
            }
            org.bukkit.inventory.ItemStack copy = fuel.clone();
            copy.setAmount(copy.getAmount() - 1);
            machine.setSlot(0, copy.getAmount() <= 0 ? null : copy);
            machine.addFuelTicks(LAVA_BUCKET_TICKS);

            // 返还空桶
            org.bukkit.inventory.ItemStack bucket = new org.bukkit.inventory.ItemStack(Material.BUCKET);
            org.bukkit.inventory.ItemStack existing = machine.getSlot(1);
            if (existing == null || existing.getType().isAir()) {
                machine.setSlot(1, bucket);
            } else if (existing.getType() == Material.BUCKET
                    && existing.getAmount() < existing.getMaxStackSize()) {
                org.bukkit.inventory.ItemStack merged = existing.clone();
                merged.setAmount(existing.getAmount() + 1);
                machine.setSlot(1, merged);
            } else {
                machine.location().getWorld()
                        .dropItemNaturally(machine.location().clone().add(0.5, 1.0, 0.5), bucket);
            }
            return true;
        }

        @Override
        public String statusLine(com.koole.higherRedStoneFun.machines.MachineInstance machine) {
            if (hasLavaBelow(machine)) {
                return "<green>地热充足 <dark_gray>| <gray>下方检测到岩浆";
            }
            if (machine.hasFuel()) {
                return "<gold>使用备用岩浆桶 <dark_gray>| <gray>余量 <white>" + machine.fuelTicks();
            }
            return "<red>缺少热源 <dark_gray>(下方 3 格内需要岩浆)";
        }
    }
}
