package com.koole.higherRedStoneFun.content;

import com.koole.higherRedStoneFun.items.ItemRegistry;
import com.koole.higherRedStoneFun.recipes.MachineRecipe;
import com.koole.higherRedStoneFun.recipes.RecipeRegistry;
import com.koole.higherRedStoneFun.recipes.RecipeType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * 全部机器配方。
 *
 * <p>配方的组织方式刻意做成「一条条登记」，方便后续迁移到 YAML 配置。
 * 每条配方只声明消耗、产出、耗时与耗电，机器实现完全复用。</p>
 */
public final class Recipes {

    private Recipes() {
    }

    private static ItemStack custom(String id) {
        return ItemRegistry.get().create(id, 1);
    }

    private static ItemStack custom(String id, int amount) {
        return ItemRegistry.get().create(id, amount);
    }

    private static ItemStack vanilla(Material material, int amount) {
        return new ItemStack(material, amount);
    }

    public static void register() {
        registerCraftingTable();
        registerGrinding();
        registerPressing();
        registerSmelting();
        registerCentrifuge();
        registerSawing();
        registerElectric();
        registerGenetics();
    }

    // ==================================================================
    // 增强工作台
    // ==================================================================

    private static void registerCraftingTable() {
        RecipeRegistry registry = RecipeRegistry.get();

        // ---- 基础机器 ----
        registry.register(MachineRecipe.builder("craft_grinder", RecipeType.ENHANCED_CRAFTING)
                .input(vanilla(Material.IRON_INGOT, 4))
                .input(vanilla(Material.STONE, 4))
                .input(custom("hrf_machine_frame"))
                .output(custom("hrf_grinder"))
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("craft_press", RecipeType.ENHANCED_CRAFTING)
                .input(vanilla(Material.IRON_INGOT, 4))
                .input(vanilla(Material.PISTON, 2))
                .input(custom("hrf_machine_frame"))
                .output(custom("hrf_press"))
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("craft_smeltery", RecipeType.ENHANCED_CRAFTING)
                .input(vanilla(Material.BRICKS, 8))
                .input(vanilla(Material.FURNACE, 2))
                .input(custom("hrf_machine_frame"))
                .output(custom("hrf_smeltery"))
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("craft_centrifuge", RecipeType.ENHANCED_CRAFTING)
                .input(vanilla(Material.IRON_INGOT, 6))
                .input(vanilla(Material.CAULDRON, 1))
                .input(custom("hrf_machine_frame"))
                .output(custom("hrf_centrifuge"))
                .ticks(60)
                .build());

        registry.register(MachineRecipe.builder("craft_sawmill", RecipeType.ENHANCED_CRAFTING)
                .input(vanilla(Material.IRON_INGOT, 3))
                .input(vanilla(Material.STONECUTTER, 1))
                .input(custom("hrf_machine_frame"))
                .output(custom("hrf_sawmill"))
                .ticks(40)
                .build());

        // ---- 材料 ----
        registry.register(MachineRecipe.builder("craft_machine_frame", RecipeType.ENHANCED_CRAFTING)
                .input(vanilla(Material.IRON_INGOT, 8))
                .input(vanilla(Material.COPPER_INGOT, 1))
                .output(custom("hrf_machine_frame"))
                .ticks(20)
                .build());

        registry.register(MachineRecipe.builder("craft_copper_wire", RecipeType.ENHANCED_CRAFTING)
                .input(vanilla(Material.COPPER_INGOT, 1))
                .output(custom("hrf_copper_wire", 3))
                .ticks(20)
                .build());

        registry.register(MachineRecipe.builder("craft_battery_cell", RecipeType.ENHANCED_CRAFTING)
                .input(vanilla(Material.AMETHYST_SHARD, 4))
                .input(custom("hrf_copper_wire", 2))
                .input(custom("hrf_iron_plate", 1))
                .output(custom("hrf_battery_cell"))
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("craft_circuit_board", RecipeType.ENHANCED_CRAFTING)
                .input(custom("hrf_copper_wire", 4))
                .input(vanilla(Material.GOLD_INGOT, 1))
                .input(vanilla(Material.REDSTONE, 2))
                .output(custom("hrf_circuit_board"))
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("craft_empty_sample", RecipeType.ENHANCED_CRAFTING)
                .input(vanilla(Material.GLASS, 3))
                .output(custom("hrf_empty_sample", 3))
                .ticks(20)
                .build());

        registry.register(MachineRecipe.builder("craft_gene_primer", RecipeType.ENHANCED_CRAFTING)
                .input(vanilla(Material.BLAZE_POWDER, 2))
                .input(vanilla(Material.GLOWSTONE_DUST, 2))
                .output(custom("hrf_gene_primer", 2))
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("craft_enzyme", RecipeType.ENHANCED_CRAFTING)
                .input(vanilla(Material.FERMENTED_SPIDER_EYE, 2))
                .input(vanilla(Material.SLIME_BALL, 2))
                .input(vanilla(Material.SUGAR, 2))
                .output(custom("hrf_enzyme", 2))
                .ticks(60)
                .build());

        registry.register(MachineRecipe.builder("craft_stabilizer", RecipeType.ENHANCED_CRAFTING)
                .input(vanilla(Material.GHAST_TEAR, 1))
                .input(vanilla(Material.AMETHYST_SHARD, 2))
                .output(custom("hrf_stabilizer", 2))
                .ticks(60)
                .build());

        registry.register(MachineRecipe.builder("craft_seed_template", RecipeType.ENHANCED_CRAFTING)
                .input(vanilla(Material.WHEAT_SEEDS, 4))
                .input(custom("hrf_sterile_water", 1))
                .output(custom("hrf_seed_template", 2))
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("craft_empty_embryo", RecipeType.ENHANCED_CRAFTING)
                .input(vanilla(Material.EGG, 2))
                .input(vanilla(Material.SLIME_BALL, 2))
                .input(custom("hrf_sterile_water", 1))
                .output(custom("hrf_empty_embryo"))
                .ticks(60)
                .build());

        registry.register(MachineRecipe.builder("craft_sterile_water", RecipeType.ENHANCED_CRAFTING)
                .input(vanilla(Material.GLASS_BOTTLE, 1))
                .input(vanilla(Material.SNOWBALL, 2))
                .output(custom("hrf_sterile_water"))
                .ticks(20)
                .build());

        registry.register(MachineRecipe.builder("craft_growth_serum", RecipeType.ENHANCED_CRAFTING)
                .input(custom("hrf_enzyme", 2))
                .input(vanilla(Material.HONEY_BOTTLE, 1))
                .input(vanilla(Material.BONE_MEAL, 4))
                .output(custom("hrf_growth_serum"))
                .ticks(80)
                .build());

        // ---- 电力机器 ----
        registry.register(MachineRecipe.builder("craft_coal_generator", RecipeType.ENHANCED_CRAFTING)
                .input(custom("hrf_machine_frame"))
                .input(custom("hrf_coil"))
                .input(vanilla(Material.FURNACE, 2))
                .input(custom("hrf_copper_wire", 2))
                .output(custom("hrf_coal_generator"))
                .ticks(80)
                .build());

        registry.register(MachineRecipe.builder("craft_coil", RecipeType.ENHANCED_CRAFTING)
                .input(custom("hrf_copper_wire", 4))
                .input(vanilla(Material.IRON_INGOT, 2))
                .output(custom("hrf_coil"))
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("craft_capacitor_basic", RecipeType.ENHANCED_CRAFTING)
                .input(custom("hrf_battery_cell", 2))
                .input(custom("hrf_copper_wire", 4))
                .input(custom("hrf_machine_frame"))
                .output(custom("hrf_capacitor_basic"))
                .ticks(80)
                .build());
    }

    // ==================================================================
    // 研磨
    // ==================================================================

    private static void registerGrinding() {
        RecipeRegistry registry = RecipeRegistry.get();

        registry.register(MachineRecipe.builder("grind_iron", RecipeType.GRINDING)
                .input(vanilla(Material.IRON_INGOT, 1))
                .output(custom("hrf_iron_dust"))
                .ticks(60)
                .build());

        registry.register(MachineRecipe.builder("grind_gold", RecipeType.GRINDING)
                .input(vanilla(Material.GOLD_INGOT, 1))
                .output(custom("hrf_gold_dust"))
                .ticks(60)
                .build());

        registry.register(MachineRecipe.builder("grind_copper", RecipeType.GRINDING)
                .input(vanilla(Material.COPPER_INGOT, 1))
                .output(custom("hrf_copper_dust"))
                .ticks(60)
                .build());

        registry.register(MachineRecipe.builder("grind_quartz", RecipeType.GRINDING)
                .input(vanilla(Material.QUARTZ, 1))
                .output(custom("hrf_quartz_dust"))
                .ticks(60)
                .build());

        registry.register(MachineRecipe.builder("grind_coal", RecipeType.GRINDING)
                .input(vanilla(Material.COAL, 1))
                .output(custom("hrf_carbon"))
                .ticks(60)
                .build());

        registry.register(MachineRecipe.builder("grind_stone", RecipeType.GRINDING)
                .input(vanilla(Material.COBBLESTONE, 1))
                .output(vanilla(Material.SAND, 1))
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("grind_wheat", RecipeType.GRINDING)
                .input(vanilla(Material.WHEAT, 1))
                .output(vanilla(Material.WHEAT_SEEDS, 2), 0.5D)
                .output(vanilla(Material.BONE_MEAL, 1), 0.3D)
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("grind_bone", RecipeType.GRINDING)
                .input(vanilla(Material.BONE, 1))
                .output(vanilla(Material.BONE_MEAL, 4))
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("grind_blaze", RecipeType.GRINDING)
                .input(vanilla(Material.BLAZE_ROD, 1))
                .output(vanilla(Material.BLAZE_POWDER, 3))
                .ticks(60)
                .build());
    }

    // ==================================================================
    // 压制
    // ==================================================================

    private static void registerPressing() {
        RecipeRegistry registry = RecipeRegistry.get();

        registry.register(MachineRecipe.builder("press_iron_plate", RecipeType.PRESSING)
                .input(vanilla(Material.IRON_INGOT, 1))
                .output(custom("hrf_iron_plate"))
                .ticks(60)
                .build());

        registry.register(MachineRecipe.builder("press_gold_plate", RecipeType.PRESSING)
                .input(vanilla(Material.GOLD_INGOT, 1))
                .output(custom("hrf_gold_plate"))
                .ticks(60)
                .build());

        registry.register(MachineRecipe.builder("press_copper_plate", RecipeType.PRESSING)
                .input(vanilla(Material.COPPER_INGOT, 1))
                .output(custom("hrf_copper_plate"))
                .ticks(60)
                .build());

        registry.register(MachineRecipe.builder("press_gear", RecipeType.PRESSING)
                .input(custom("hrf_iron_plate", 2))
                .output(custom("hrf_gear"))
                .ticks(80)
                .build());

        registry.register(MachineRecipe.builder("press_copper_wire", RecipeType.PRESSING)
                .input(custom("hrf_copper_plate", 1))
                .output(custom("hrf_copper_wire", 4))
                .ticks(60)
                .build());

        registry.register(MachineRecipe.builder("press_wood_pulp", RecipeType.PRESSING)
                .input(vanilla(Material.OAK_LOG, 1))
                .output(custom("hrf_wood_pulp", 2))
                .ticks(60)
                .build());

        registry.register(MachineRecipe.builder("press_advanced_frame", RecipeType.PRESSING)
                .input(custom("hrf_machine_frame"))
                .input(vanilla(Material.NETHERITE_SCRAP, 1))
                .output(custom("hrf_advanced_frame"))
                .ticks(200)
                .build());
    }

    // ==================================================================
    // 熔炼
    // ==================================================================

    private static void registerSmelting() {
        RecipeRegistry registry = RecipeRegistry.get();

        registry.register(MachineRecipe.builder("smelt_iron", RecipeType.SMELTING)
                .input(vanilla(Material.RAW_IRON, 1))
                .output(vanilla(Material.IRON_INGOT, 1))
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("smelt_gold", RecipeType.SMELTING)
                .input(vanilla(Material.RAW_GOLD, 1))
                .output(vanilla(Material.GOLD_INGOT, 1))
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("smelt_copper", RecipeType.SMELTING)
                .input(vanilla(Material.RAW_COPPER, 1))
                .output(vanilla(Material.COPPER_INGOT, 1))
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("smelt_glass", RecipeType.SMELTING)
                .input(vanilla(Material.SAND, 1))
                .output(vanilla(Material.GLASS, 1))
                .ticks(30)
                .build());

        registry.register(MachineRecipe.builder("smelt_carbon_to_coal", RecipeType.SMELTING)
                .input(custom("hrf_carbon", 4))
                .output(vanilla(Material.COAL, 1))
                .ticks(60)
                .build());

        registry.register(MachineRecipe.builder("smelt_netherite", RecipeType.SMELTING)
                .input(custom("hrf_carbon", 8))
                .input(vanilla(Material.ANCIENT_DEBRIS, 1))
                .output(vanilla(Material.NETHERITE_SCRAP, 1))
                .ticks(300)
                .build());
    }

    // ==================================================================
    // 离心
    // ==================================================================

    private static void registerCentrifuge() {
        RecipeRegistry registry = RecipeRegistry.get();

        registry.register(MachineRecipe.builder("centrifuge_iron_dust", RecipeType.CENTRIFUGE)
                .input(custom("hrf_iron_dust", 2))
                .output(vanilla(Material.IRON_INGOT, 1))
                .output(vanilla(Material.GOLD_NUGGET, 1), 0.15D)
                .output(vanilla(Material.REDSTONE, 1), 0.10D)
                .ticks(120)
                .build());

        registry.register(MachineRecipe.builder("centrifuge_gold_dust", RecipeType.CENTRIFUGE)
                .input(custom("hrf_gold_dust", 2))
                .output(vanilla(Material.GOLD_INGOT, 1))
                .output(vanilla(Material.GLOWSTONE_DUST, 1), 0.20D)
                .ticks(120)
                .build());

        registry.register(MachineRecipe.builder("centrifuge_quartz_dust", RecipeType.CENTRIFUGE)
                .input(custom("hrf_quartz_dust", 2))
                .output(vanilla(Material.QUARTZ, 1))
                .output(vanilla(Material.AMETHYST_SHARD, 1), 0.12D)
                .output(vanilla(Material.LAPIS_LAZULI, 1), 0.18D)
                .ticks(120)
                .build());

        registry.register(MachineRecipe.builder("centrifuge_soul_sand", RecipeType.CENTRIFUGE)
                .input(vanilla(Material.SOUL_SAND, 4))
                .output(vanilla(Material.GLOWSTONE_DUST, 1))
                .output(vanilla(Material.GOLD_NUGGET, 1), 0.35D)
                .output(vanilla(Material.QUARTZ, 1), 0.25D)
                .ticks(160)
                .build());

        registry.register(MachineRecipe.builder("centrifuge_ender_pearl", RecipeType.CENTRIFUGE)
                .input(vanilla(Material.ENDER_PEARL, 1))
                .output(vanilla(Material.ENDER_EYE, 1), 0.10D)
                .output(vanilla(Material.CHORUS_FRUIT, 1), 0.30D)
                .ticks(200)
                .build());

        registry.register(MachineRecipe.builder("centrifuge_biomass", RecipeType.CENTRIFUGE)
                .input(custom("hrf_wood_pulp", 4))
                .output(vanilla(Material.SUGAR, 1))
                .output(vanilla(Material.BONE_MEAL, 2), 0.40D)
                .output(vanilla(Material.SLIME_BALL, 1), 0.08D)
                .ticks(140)
                .build());
    }

    // ==================================================================
    // 锯木
    // ==================================================================

    private static void registerSawing() {
        RecipeRegistry registry = RecipeRegistry.get();

        registry.register(MachineRecipe.builder("saw_oak", RecipeType.SAWING)
                .input(vanilla(Material.OAK_LOG, 1))
                .output(vanilla(Material.OAK_PLANKS, 6))
                .output(vanilla(Material.STICK, 2), 0.5D)
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("saw_spruce", RecipeType.SAWING)
                .input(vanilla(Material.SPRUCE_LOG, 1))
                .output(vanilla(Material.SPRUCE_PLANKS, 6))
                .output(vanilla(Material.STICK, 2), 0.5D)
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("saw_birch", RecipeType.SAWING)
                .input(vanilla(Material.BIRCH_LOG, 1))
                .output(vanilla(Material.BIRCH_PLANKS, 6))
                .output(vanilla(Material.STICK, 2), 0.5D)
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("saw_jungle", RecipeType.SAWING)
                .input(vanilla(Material.JUNGLE_LOG, 1))
                .output(vanilla(Material.JUNGLE_PLANKS, 6))
                .output(vanilla(Material.STICK, 2), 0.5D)
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("saw_acacia", RecipeType.SAWING)
                .input(vanilla(Material.ACACIA_LOG, 1))
                .output(vanilla(Material.ACACIA_PLANKS, 6))
                .output(vanilla(Material.STICK, 2), 0.5D)
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("saw_dark_oak", RecipeType.SAWING)
                .input(vanilla(Material.DARK_OAK_LOG, 1))
                .output(vanilla(Material.DARK_OAK_PLANKS, 6))
                .output(vanilla(Material.STICK, 2), 0.5D)
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("saw_mangrove", RecipeType.SAWING)
                .input(vanilla(Material.MANGROVE_LOG, 1))
                .output(vanilla(Material.MANGROVE_PLANKS, 6))
                .output(vanilla(Material.STICK, 2), 0.5D)
                .ticks(40)
                .build());

        registry.register(MachineRecipe.builder("saw_cherry", RecipeType.SAWING)
                .input(vanilla(Material.CHERRY_LOG, 1))
                .output(vanilla(Material.CHERRY_PLANKS, 6))
                .output(vanilla(Material.STICK, 2), 0.5D)
                .ticks(40)
                .build());
    }

    // ==================================================================
    // 电力机器
    // ==================================================================

    private static void registerElectric() {
        RecipeRegistry registry = RecipeRegistry.get();

        // ---- 组装机 ----
        registry.register(MachineRecipe.builder("assemble_solar", RecipeType.ASSEMBLING)
                .input(custom("hrf_advanced_frame"))
                .input(custom("hrf_solar_cell", 4))
                .input(custom("hrf_circuit_board", 2))
                .input(custom("hrf_copper_wire", 4))
                .output(custom("hrf_solar_generator"))
                .energy(20_000)
                .ticks(400)
                .build());

        registry.register(MachineRecipe.builder("assemble_geothermal", RecipeType.ASSEMBLING)
                .input(custom("hrf_advanced_frame"))
                .input(custom("hrf_coil", 4))
                .input(custom("hrf_circuit_board", 3))
                .input(vanilla(Material.MAGMA_BLOCK, 4))
                .output(custom("hrf_geothermal_generator"))
                .energy(30_000)
                .ticks(500)
                .build());

        registry.register(MachineRecipe.builder("assemble_advanced_circuit", RecipeType.ASSEMBLING)
                .input(custom("hrf_circuit_board", 2))
                .input(custom("hrf_gold_plate"))
                .input(vanilla(Material.DIAMOND, 1))
                .output(custom("hrf_advanced_circuit"))
                .energy(12_000)
                .ticks(300)
                .build());

        registry.register(MachineRecipe.builder("assemble_capacitor_advanced", RecipeType.ASSEMBLING)
                .input(custom("hrf_capacitor_basic", 4))
                .input(custom("hrf_advanced_circuit", 2))
                .input(custom("hrf_battery_cell", 8))
                .input(custom("hrf_advanced_frame"))
                .output(custom("hrf_capacitor_advanced"))
                .energy(40_000)
                .ticks(600)
                .build());

        registry.register(MachineRecipe.builder("assemble_regulator", RecipeType.ASSEMBLING)
                .input(custom("hrf_capacitor_advanced", 2))
                .input(custom("hrf_advanced_circuit", 4))
                .input(vanilla(Material.NETHERITE_INGOT, 1))
                .output(custom("hrf_energy_regulator"))
                .energy(120_000)
                .ticks(900)
                .build());

        registry.register(MachineRecipe.builder("assemble_reactor_core", RecipeType.ASSEMBLING)
                .input(vanilla(Material.NETHER_STAR, 1))
                .input(custom("hrf_advanced_circuit", 8))
                .input(custom("hrf_energy_regulator"))
                .input(vanilla(Material.NETHERITE_BLOCK, 1))
                .output(custom("hrf_reactor_core"))
                .energy(500_000)
                .ticks(2_000)
                .build());

        registry.register(MachineRecipe.builder("assemble_reactor", RecipeType.ASSEMBLING)
                .input(custom("hrf_advanced_frame", 8))
                .input(custom("hrf_reactor_core"))
                .input(custom("hrf_coolant_cell", 4))
                .input(custom("hrf_advanced_circuit", 4))
                .output(custom("hrf_nuclear_reactor"))
                .energy(800_000)
                .ticks(3_000)
                .build());

        registry.register(MachineRecipe.builder("assemble_electric_smeltery", RecipeType.ASSEMBLING)
                .input(custom("hrf_advanced_frame"))
                .input(custom("hrf_coil", 2))
                .input(custom("hrf_circuit_board", 2))
                .input(custom("hrf_smeltery"))
                .output(custom("hrf_electric_smeltery"))
                .energy(15_000)
                .ticks(400)
                .build());

        registry.register(MachineRecipe.builder("assemble_electric_grinder", RecipeType.ASSEMBLING)
                .input(custom("hrf_advanced_frame"))
                .input(custom("hrf_gear", 4))
                .input(custom("hrf_circuit_board", 2))
                .input(custom("hrf_grinder"))
                .output(custom("hrf_electric_grinder"))
                .energy(15_000)
                .ticks(400)
                .build());

        registry.register(MachineRecipe.builder("assemble_assembler", RecipeType.ASSEMBLING)
                .input(custom("hrf_advanced_frame", 4))
                .input(custom("hrf_advanced_circuit", 2))
                .input(custom("hrf_battery_cell", 4))
                .input(vanilla(Material.CRAFTER, 1))
                .output(custom("hrf_assembler"))
                .energy(60_000)
                .ticks(800)
                .build());

        registry.register(MachineRecipe.builder("assemble_coolant", RecipeType.ASSEMBLING)
                .input(vanilla(Material.BLUE_ICE, 4))
                .input(custom("hrf_quartz_dust", 2))
                .output(custom("hrf_coolant_cell"))
                .energy(2_400)
                .ticks(120)
                .build());

        // ---- 分子重组机 ----
        registry.register(MachineRecipe.builder("molecular_solar_cell", RecipeType.MOLECULAR)
                .input(vanilla(Material.QUARTZ, 8))
                .input(custom("hrf_quartz_dust", 4))
                .input(custom("hrf_advanced_circuit"))
                .output(custom("hrf_solar_cell", 4))
                .energy(50_000)
                .ticks(600)
                .build());

        registry.register(MachineRecipe.builder("molecular_diamond", RecipeType.MOLECULAR)
                .input(custom("hrf_carbon", 16))
                .input(vanilla(Material.COAL_BLOCK, 4))
                .output(vanilla(Material.DIAMOND, 2))
                .energy(100_000)
                .ticks(1_200)
                .build());

        registry.register(MachineRecipe.builder("molecular_netherite", RecipeType.MOLECULAR)
                .input(vanilla(Material.NETHERITE_SCRAP, 4))
                .input(vanilla(Material.GOLD_INGOT, 4))
                .output(vanilla(Material.NETHERITE_INGOT, 1))
                .energy(150_000)
                .ticks(1_500)
                .build());

        registry.register(MachineRecipe.builder("molecular_recombinator", RecipeType.MOLECULAR)
                .input(custom("hrf_assembler", 2))
                .input(custom("hrf_reactor_core"))
                .input(vanilla(Material.BEACON, 1))
                .input(custom("hrf_energy_regulator"))
                .output(custom("hrf_molecular_recombinator"))
                .energy(1_000_000)
                .ticks(3_600)
                .build());

        registry.register(MachineRecipe.builder("molecular_echo_shard", RecipeType.MOLECULAR)
                .input(vanilla(Material.AMETHYST_SHARD, 16))
                .input(vanilla(Material.SCULK, 8))
                .output(vanilla(Material.ECHO_SHARD, 2))
                .energy(80_000)
                .ticks(900)
                .build());

        // ---- 电力机器的直接配方 ----
        registry.register(MachineRecipe.builder("electric_smelt_iron", RecipeType.ELECTRIC_SMELTING)
                .input(vanilla(Material.RAW_IRON, 1))
                .output(vanilla(Material.IRON_INGOT, 1))
                .energy(400)
                .ticks(20)
                .build());

        registry.register(MachineRecipe.builder("electric_smelt_gold", RecipeType.ELECTRIC_SMELTING)
                .input(vanilla(Material.RAW_GOLD, 1))
                .output(vanilla(Material.GOLD_INGOT, 1))
                .energy(400)
                .ticks(20)
                .build());

        registry.register(MachineRecipe.builder("electric_smelt_copper", RecipeType.ELECTRIC_SMELTING)
                .input(vanilla(Material.RAW_COPPER, 1))
                .output(vanilla(Material.COPPER_INGOT, 1))
                .energy(400)
                .ticks(20)
                .build());

        registry.register(MachineRecipe.builder("electric_smelt_sand", RecipeType.ELECTRIC_SMELTING)
                .input(vanilla(Material.SAND, 1))
                .output(vanilla(Material.GLASS, 1))
                .energy(300)
                .ticks(15)
                .build());

        registry.register(MachineRecipe.builder("electric_grind_iron", RecipeType.ELECTRIC_GRINDING)
                .input(vanilla(Material.IRON_INGOT, 1))
                .output(custom("hrf_iron_dust"))
                .energy(500)
                .ticks(20)
                .build());

        registry.register(MachineRecipe.builder("electric_grind_gold", RecipeType.ELECTRIC_GRINDING)
                .input(vanilla(Material.GOLD_INGOT, 1))
                .output(custom("hrf_gold_dust"))
                .energy(500)
                .ticks(20)
                .build());

        registry.register(MachineRecipe.builder("electric_grind_ancient_debris", RecipeType.ELECTRIC_GRINDING)
                .input(vanilla(Material.ANCIENT_DEBRIS, 1))
                .output(vanilla(Material.NETHERITE_SCRAP, 1), 0.5D)
                .output(custom("hrf_carbon", 2), 0.75D)
                .energy(2_000)
                .ticks(80)
                .build());
    }

    // ==================================================================
    // 基因机器
    // ==================================================================

    private static void registerGenetics() {
        RecipeRegistry registry = RecipeRegistry.get();

        // ---- 基因机器本体（用组装机做） ----
        registry.register(MachineRecipe.builder("assemble_sequencer", RecipeType.ASSEMBLING)
                .input(custom("hrf_dna_sequencer_part"))
                .input(custom("hrf_advanced_circuit", 2))
                .input(custom("hrf_circuit_board", 4))
                .input(custom("hrf_advanced_frame"))
                .output(custom("hrf_dna_sequencer"))
                .energy(80_000)
                .ticks(900)
                .build());

        registry.register(MachineRecipe.builder("assemble_sequencer_mk2", RecipeType.ASSEMBLING)
                .input(custom("hrf_dna_sequencer"))
                .input(vanilla(Material.SCULK_SHRIEKER, 2))
                .input(custom("hrf_advanced_circuit", 4))
                .input(vanilla(Material.ECHO_SHARD, 4))
                .output(custom("hrf_dna_sequencer_mk2"))
                .energy(200_000)
                .ticks(1_800)
                .build());

        registry.register(MachineRecipe.builder("assemble_splicer", RecipeType.ASSEMBLING)
                .input(custom("hrf_splicing_matrix"))
                .input(custom("hrf_advanced_circuit", 2))
                .input(custom("hrf_copper_wire", 8))
                .input(custom("hrf_advanced_frame"))
                .output(custom("hrf_gene_splicer"))
                .energy(100_000)
                .ticks(1_200)
                .build());

        registry.register(MachineRecipe.builder("assemble_splicer_mk2", RecipeType.ASSEMBLING)
                .input(custom("hrf_gene_splicer"))
                .input(custom("hrf_advanced_circuit", 6))
                .input(vanilla(Material.NETHER_STAR, 1))
                .input(custom("hrf_reactor_core"))
                .output(custom("hrf_gene_splicer_mk2"))
                .energy(400_000)
                .ticks(2_400)
                .build());

        registry.register(MachineRecipe.builder("assemble_injector", RecipeType.ASSEMBLING)
                .input(custom("hrf_injector_needle"))
                .input(custom("hrf_advanced_circuit", 2))
                .input(vanilla(Material.BREWING_STAND, 2))
                .input(custom("hrf_advanced_frame"))
                .output(custom("hrf_gene_injector"))
                .energy(90_000)
                .ticks(1_000)
                .build());

        registry.register(MachineRecipe.builder("assemble_injector_mk2", RecipeType.ASSEMBLING)
                .input(custom("hrf_gene_injector"))
                .input(custom("hrf_advanced_circuit", 4))
                .input(custom("hrf_coolant_cell", 4))
                .output(custom("hrf_gene_injector_mk2"))
                .energy(250_000)
                .ticks(2_000)
                .build());

        registry.register(MachineRecipe.builder("assemble_growth_chamber", RecipeType.ASSEMBLING)
                .input(custom("hrf_advanced_frame", 2))
                .input(vanilla(Material.BEEHIVE, 2))
                .input(custom("hrf_advanced_circuit", 2))
                .input(custom("hrf_coolant_cell", 2))
                .output(custom("hrf_growth_chamber"))
                .energy(150_000)
                .ticks(1_500)
                .build());

        // ---- 基因机器零件 ----
        registry.register(MachineRecipe.builder("assemble_sequencer_part", RecipeType.ASSEMBLING)
                .input(vanilla(Material.SCULK_SENSOR, 2))
                .input(custom("hrf_advanced_circuit"))
                .input(vanilla(Material.AMETHYST_SHARD, 4))
                .output(custom("hrf_dna_sequencer_part"))
                .energy(20_000)
                .ticks(400)
                .build());

        registry.register(MachineRecipe.builder("assemble_splicing_matrix", RecipeType.ASSEMBLING)
                .input(vanilla(Material.SMITHING_TABLE, 1))
                .input(custom("hrf_advanced_circuit"))
                .input(custom("hrf_gold_plate", 4))
                .output(custom("hrf_splicing_matrix"))
                .energy(20_000)
                .ticks(400)
                .build());

        registry.register(MachineRecipe.builder("assemble_injector_needle", RecipeType.ASSEMBLING)
                .input(vanilla(Material.LIGHTNING_ROD, 2))
                .input(custom("hrf_advanced_circuit"))
                .input(custom("hrf_iron_plate", 2))
                .output(custom("hrf_injector_needle"))
                .energy(20_000)
                .ticks(400)
                .build());

        // ---- 基因片段（分子重组） ----
        registry.register(MachineRecipe.builder("molecular_fragment_growth", RecipeType.MOLECULAR)
                .input(custom("hrf_enzyme", 2))
                .input(vanilla(Material.WHEAT, 8))
                .input(vanilla(Material.BONE_MEAL, 8))
                .output(custom("hrf_gene_fragment_growth"))
                .energy(30_000)
                .ticks(500)
                .build());

        registry.register(MachineRecipe.builder("molecular_fragment_yield", RecipeType.MOLECULAR)
                .input(custom("hrf_enzyme", 2))
                .input(vanilla(Material.GOLDEN_CARROT, 4))
                .input(vanilla(Material.HONEY_BOTTLE, 2))
                .output(custom("hrf_gene_fragment_yield"))
                .energy(30_000)
                .ticks(500)
                .build());

        registry.register(MachineRecipe.builder("molecular_fragment_resilience", RecipeType.MOLECULAR)
                .input(custom("hrf_enzyme", 2))
                .input(vanilla(Material.CACTUS, 8))
                .input(vanilla(Material.BLUE_ICE, 2))
                .output(custom("hrf_gene_fragment_resilience"))
                .energy(30_000)
                .ticks(500)
                .build());

        registry.register(MachineRecipe.builder("molecular_fragment_vigor", RecipeType.MOLECULAR)
                .input(custom("hrf_enzyme", 2))
                .input(vanilla(Material.GHAST_TEAR, 1))
                .input(vanilla(Material.SLIME_BLOCK, 4))
                .output(custom("hrf_gene_fragment_vigor"))
                .energy(30_000)
                .ticks(500)
                .build());

        // ---- 测序 / 注入 的直接配方 ----
        registry.register(MachineRecipe.builder("sequence_plant", RecipeType.SEQUENCING)
                .input(custom("hrf_sample_wheat"))
                .input(custom("hrf_gene_primer"))
                .output(custom("hrf_genome_plant"))
                .energy(2_400)
                .ticks(200)
                .build());

        registry.register(MachineRecipe.builder("inject_seed", RecipeType.INJECTION)
                .input(custom("hrf_seed_template"))
                .input(custom("hrf_genome_plant"))
                .output(custom("hrf_wheat_seeds"))
                .energy(3_600)
                .ticks(240)
                .build());
    }
}
