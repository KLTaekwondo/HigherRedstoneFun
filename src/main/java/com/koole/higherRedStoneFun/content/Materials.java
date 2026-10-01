package com.koole.higherRedStoneFun.content;

import com.koole.higherRedStoneFun.core.ItemGroup;
import com.koole.higherRedStoneFun.items.HrfItem;
import com.koole.higherRedStoneFun.items.ItemRegistry;
import org.bukkit.Material;

/**
 * 材料与零件的物品定义。
 *
 * <p>这些是三个机器模块共用的「中间产物」，构成完整的生产链：
 * 基础机器把矿石变成零件 -> 高级电力机器把零件变成精密元件 ->
 * 基因机器用精密元件产出改良种子与胚胎。</p>
 */
public final class Materials {

    private Materials() {
    }

    public static void register() {
        ItemRegistry registry = ItemRegistry.get();

        // ---------------- 工具与仪器 ----------------
        // 图鉴说明书：随身携带，右键打开。这也是 TOOLS 分类里唯一的物品
        // （否则那个分类在图鉴里是空的）。
        registry.register(HrfItem.builder("hrf_guide", Material.BOOK,
                        "<gold>科技图鉴", ItemGroup.TOOLS)
                .lore("<gray>随身携带的机器与配方手册")
                .blank()
                .lore("<yellow>右键打开")
                .lore("<dark_gray>也可以使用 /hrf guide")
                .glow(true)
                .build());

        // ---------------- 基础零件 ----------------
        registry.register(HrfItem.builder("hrf_iron_plate", Material.IRON_INGOT,
                        "<white>铁板", ItemGroup.MATERIALS)
                .lore("<gray>由压制机压制成型的基础零件")
                .build());

        registry.register(HrfItem.builder("hrf_gold_plate", Material.GOLD_INGOT,
                        "<gold>金板", ItemGroup.MATERIALS)
                .lore("<gray>导电性极佳的板材")
                .build());

        registry.register(HrfItem.builder("hrf_copper_plate", Material.COPPER_INGOT,
                        "<#E77C56>铜板", ItemGroup.MATERIALS)
                .lore("<gray>导线与电路的基础材料")
                .build());

        registry.register(HrfItem.builder("hrf_iron_dust", Material.GUNPOWDER,
                        "<gray>铁粉", ItemGroup.MATERIALS)
                .lore("<gray>研磨铁锭得到的粉末")
                .build());

        registry.register(HrfItem.builder("hrf_gold_dust", Material.GLOWSTONE_DUST,
                        "<gold>金粉", ItemGroup.MATERIALS)
                .lore("<gray>研磨金锭得到的粉末")
                .build());

        registry.register(HrfItem.builder("hrf_copper_dust", Material.REDSTONE,
                        "<#E77C56>铜粉", ItemGroup.MATERIALS)
                .lore("<gray>研磨铜锭得到的粉末")
                .build());

        registry.register(HrfItem.builder("hrf_quartz_dust", Material.SUGAR,
                        "<white>石英粉", ItemGroup.MATERIALS)
                .lore("<gray>研磨下界石英得到的粉末")
                .build());

        registry.register(HrfItem.builder("hrf_carbon", Material.COAL,
                        "<dark_gray>碳粉", ItemGroup.MATERIALS)
                .lore("<gray>研磨煤炭得到的精细碳粉")
                .build());

        registry.register(HrfItem.builder("hrf_wood_pulp", Material.BROWN_DYE,
                        "<#8B5A2B>木浆", ItemGroup.MATERIALS)
                .lore("<gray>木材研磨后的纤维浆料")
                .build());

        registry.register(HrfItem.builder("hrf_gear", Material.IRON_NUGGET,
                        "<white>齿轮", ItemGroup.MATERIALS)
                .lore("<gray>传动机构的核心部件")
                .build());

        registry.register(HrfItem.builder("hrf_copper_wire", Material.STRING,
                        "<#E77C56>铜导线", ItemGroup.MATERIALS)
                .lore("<gray>由铜板拉制而成")
                .lore("<gray>用于制作电路板与线圈")
                .build());

        registry.register(HrfItem.builder("hrf_circuit_board", Material.OAK_BUTTON,
                        "<green>电路板", ItemGroup.MATERIALS)
                .lore("<gray>承载电子元件的基础板")
                .build());

        registry.register(HrfItem.builder("hrf_advanced_circuit", Material.REPEATER,
                        "<green>高级电路板", ItemGroup.MATERIALS)
                .lore("<gray>集成度更高的电路，高级机器的核心")
                .glow(true)
                .build());

        registry.register(HrfItem.builder("hrf_machine_frame", Material.IRON_BLOCK,
                        "<white>机器框架", ItemGroup.MATERIALS)
                .lore("<gray>所有机器的外壳基座")
                .build());

        registry.register(HrfItem.builder("hrf_advanced_frame", Material.NETHERITE_SCRAP,
                        "<#8B4513>强化框架", ItemGroup.MATERIALS)
                .lore("<gray>可承受高压电流的机器框架")
                .build());

        // ---------------- 能源零件 ----------------
        registry.register(HrfItem.builder("hrf_coil", Material.COPPER_BLOCK,
                        "<#E77C56>电磁线圈", ItemGroup.MATERIALS)
                .lore("<gray>发电机的核心感应部件")
                .build());

        registry.register(HrfItem.builder("hrf_battery_cell", Material.AMETHYST_SHARD,
                        "<light_purple>储能单元", ItemGroup.MATERIALS)
                .lore("<gray>可反复充放电的储能核心")
                .build());

        registry.register(HrfItem.builder("hrf_solar_cell", Material.DAYLIGHT_DETECTOR,
                        "<gold>光伏板", ItemGroup.MATERIALS)
                .lore("<gray>把阳光转化为电能")
                .build());

        registry.register(HrfItem.builder("hrf_reactor_core", Material.NETHER_STAR,
                        "<dark_red>反应堆核心", ItemGroup.MATERIALS)
                .lore("<dark_red>蕴含巨大能量的核心")
                .lore("<red>运行时需要严密冷却")
                .glow(true)
                .build());

        registry.register(HrfItem.builder("hrf_coolant_cell", Material.BLUE_ICE,
                        "<aqua>冷却单元", ItemGroup.MATERIALS)
                .lore("<gray>为反应堆与基因设备降温")
                .build());

        // ---------------- 基因模块专用 ----------------
        registry.register(HrfItem.builder("hrf_empty_sample", Material.GLASS_BOTTLE,
                        "<white>空样本瓶", ItemGroup.GENETICS)
                .lore("<gray>用于采集植物与动物的生物样本")
                .build());

        registry.register(HrfItem.builder("hrf_plant_sample", Material.GLASS_BOTTLE,
                        "<green>植物生物样本", ItemGroup.GENETICS)
                .lore("<gray>从成熟作物上采集的组织样本")
                .lore("<gray>可放入基因测序仪解析")
                .build());

        registry.register(HrfItem.builder("hrf_animal_sample", Material.GLASS_BOTTLE,
                        "<red>动物生物样本", ItemGroup.GENETICS)
                .lore("<gray>从动物身上采集的组织样本")
                .glow(true)
                .build());

        registry.register(HrfItem.builder("hrf_gene_primer", Material.BLAZE_POWDER,
                        "<yellow>基因引物", ItemGroup.GENETICS)
                .lore("<gray>启动测序反应的必要试剂")
                .build());

        registry.register(HrfItem.builder("hrf_enzyme", Material.FERMENTED_SPIDER_EYE,
                        "<green>拼接酶", ItemGroup.GENETICS)
                .lore("<gray>用于把两段基因拼接在一起")
                .build());

        registry.register(HrfItem.builder("hrf_growth_serum", Material.HONEY_BOTTLE,
                        "<green>生长血清", ItemGroup.GENETICS)
                .lore("<gray>注射后可加速作物与幼崽成长")
                .glow(true)
                .build());

        registry.register(HrfItem.builder("hrf_stabilizer", Material.GHAST_TEAR,
                        "<white>稳定剂", ItemGroup.GENETICS)
                .lore("<gray>提高基因注入的成功率")
                .build());

        registry.register(HrfItem.builder("hrf_sterile_water", Material.POTION,
                        "<aqua>无菌水", ItemGroup.GENETICS)
                .lore("<gray>基因实验的清洗与稀释介质")
                .build());

        registry.register(HrfItem.builder("hrf_seed_template", Material.WHEAT_SEEDS,
                        "<yellow>空白种子模板", ItemGroup.GENETICS)
                .lore("<gray>尚未承载基因的种子基体")
                .build());

        // ---------------- 基因片段（携带单条基因） ----------------
        registry.register(HrfItem.builder("hrf_gene_fragment_growth", Material.LIME_DYE,
                        "<green>基因片段 · 生长", ItemGroup.GENETICS)
                .lore("<gray>携带「生长」性状的基因片段")
                .lore("<green>+1 生长等级")
                .build());

        registry.register(HrfItem.builder("hrf_gene_fragment_yield", Material.YELLOW_DYE,
                        "<yellow>基因片段 · 产量", ItemGroup.GENETICS)
                .lore("<gray>携带「产量」性状的基因片段")
                .lore("<yellow>+1 产量等级")
                .build());

        registry.register(HrfItem.builder("hrf_gene_fragment_resilience", Material.LIGHT_BLUE_DYE,
                        "<aqua>基因片段 · 抗逆", ItemGroup.GENETICS)
                .lore("<gray>携带「抗逆」性状的基因片段")
                .lore("<aqua>+1 抗逆等级")
                .build());

        registry.register(HrfItem.builder("hrf_gene_fragment_vigor", Material.PURPLE_DYE,
                        "<light_purple>基因片段 · 活力", ItemGroup.GENETICS)
                .lore("<gray>携带「活力」性状的基因片段")
                .lore("<light_purple>+1 活力等级")
                .build());

        // ---------------- 种子（带基因模板） ----------------
        registry.register(HrfItem.builder("hrf_wheat_seeds", Material.WHEAT_SEEDS,
                        "<green>改良小麦种子", ItemGroup.GENETICS)
                .lore("<gray>经过基因改造的小麦种子")
                .lore("<gray>右键种植，基因信息会随作物保存")
                .build());

        registry.register(HrfItem.builder("hrf_carrot_seeds", Material.CARROT,
                        "<gold>改良胡萝卜种子", ItemGroup.GENETICS)
                .lore("<gray>经过基因改造的胡萝卜")
                .build());

        registry.register(HrfItem.builder("hrf_potato_seeds", Material.POTATO,
                        "<yellow>改良马铃薯种子", ItemGroup.GENETICS)
                .lore("<gray>经过基因改造的马铃薯")
                .build());

        registry.register(HrfItem.builder("hrf_beetroot_seeds", Material.BEETROOT_SEEDS,
                        "<red>改良甜菜种子", ItemGroup.GENETICS)
                .lore("<gray>经过基因改造的甜菜种子")
                .build());

        registry.register(HrfItem.builder("hrf_melon_seeds", Material.MELON_SEEDS,
                        "<green>改良西瓜种子", ItemGroup.GENETICS)
                .lore("<gray>经过基因改造的西瓜种子")
                .build());

        registry.register(HrfItem.builder("hrf_pumpkin_seeds", Material.PUMPKIN_SEEDS,
                        "<gold>改良南瓜种子", ItemGroup.GENETICS)
                .lore("<gray>经过基因改造的南瓜种子")
                .build());

        registry.register(HrfItem.builder("hrf_nether_wart", Material.NETHER_WART,
                        "<dark_red>改良下界疣", ItemGroup.GENETICS)
                .lore("<gray>经过基因改造的下界疣")
                .build());

        registry.register(HrfItem.builder("hrf_sweet_berries", Material.SWEET_BERRIES,
                        "<red>改良甜浆果", ItemGroup.GENETICS)
                .lore("<gray>经过基因改造的甜浆果")
                .build());

        // ---------------- 胚胎（动物） ----------------
        registry.register(HrfItem.builder("hrf_empty_embryo", Material.EGG,
                        "<white>空白胚胎", ItemGroup.GENETICS)
                .lore("<gray>尚未注入基因的动物胚胎")
                .build());

        registry.register(HrfItem.builder("hrf_cow_embryo", Material.COW_SPAWN_EGG,
                        "<white>牛胚胎", ItemGroup.GENETICS)
                .lore("<gray>可孵化出携带指定基因的牛")
                .build());

        registry.register(HrfItem.builder("hrf_sheep_embryo", Material.SHEEP_SPAWN_EGG,
                        "<white>羊胚胎", ItemGroup.GENETICS)
                .lore("<gray>可孵化出携带指定基因的羊")
                .build());

        registry.register(HrfItem.builder("hrf_chicken_embryo", Material.CHICKEN_SPAWN_EGG,
                        "<white>鸡胚胎", ItemGroup.GENETICS)
                .lore("<gray>可孵化出携带指定基因的鸡")
                .build());

        registry.register(HrfItem.builder("hrf_pig_embryo", Material.PIG_SPAWN_EGG,
                        "<white>猪胚胎", ItemGroup.GENETICS)
                .lore("<gray>可孵化出携带指定基因的猪")
                .build());

        registry.register(HrfItem.builder("hrf_rabbit_embryo", Material.RABBIT_SPAWN_EGG,
                        "<white>兔胚胎", ItemGroup.GENETICS)
                .lore("<gray>可孵化出携带指定基因的兔子")
                .build());

        registry.register(HrfItem.builder("hrf_bee_embryo", Material.BEE_SPAWN_EGG,
                        "<white>蜜蜂胚胎", ItemGroup.GENETICS)
                .lore("<gray>可孵化出携带指定基因的蜜蜂")
                .build());

        // ---------------- 生物样本（可直接采集） ----------------
        registry.register(HrfItem.builder("hrf_sample_wheat", Material.GLASS_BOTTLE,
                        "<green>小麦样本", ItemGroup.GENETICS)
                .lore("<gray>采集自小麦植株")
                .build());

        registry.register(HrfItem.builder("hrf_sample_carrot", Material.GLASS_BOTTLE,
                        "<gold>胡萝卜样本", ItemGroup.GENETICS)
                .lore("<gray>采集自胡萝卜植株")
                .build());

        registry.register(HrfItem.builder("hrf_sample_potato", Material.GLASS_BOTTLE,
                        "<yellow>马铃薯样本", ItemGroup.GENETICS)
                .lore("<gray>采集自马铃薯植株")
                .build());

        registry.register(HrfItem.builder("hrf_sample_beetroot", Material.GLASS_BOTTLE,
                        "<red>甜菜样本", ItemGroup.GENETICS)
                .lore("<gray>采集自甜菜植株")
                .build());

        registry.register(HrfItem.builder("hrf_sample_nether", Material.GLASS_BOTTLE,
                        "<dark_red>下界疣样本", ItemGroup.GENETICS)
                .lore("<gray>采集自下界疣")
                .build());

        registry.register(HrfItem.builder("hrf_sample_berry", Material.GLASS_BOTTLE,
                        "<red>甜浆果样本", ItemGroup.GENETICS)
                .lore("<gray>采集自甜浆果丛")
                .build());

        registry.register(HrfItem.builder("hrf_sample_melon", Material.GLASS_BOTTLE,
                        "<green>西瓜样本", ItemGroup.GENETICS)
                .lore("<gray>采集自西瓜")
                .build());

        registry.register(HrfItem.builder("hrf_sample_pumpkin", Material.GLASS_BOTTLE,
                        "<gold>南瓜样本", ItemGroup.GENETICS)
                .lore("<gray>采集自南瓜")
                .build());

        registry.register(HrfItem.builder("hrf_sample_animal", Material.GLASS_BOTTLE,
                        "<red>动物样本", ItemGroup.GENETICS)
                .lore("<gray>采集自家畜")
                .build());

        registry.register(HrfItem.builder("hrf_sample_bee", Material.GLASS_BOTTLE,
                        "<yellow>蜜蜂样本", ItemGroup.GENETICS)
                .lore("<gray>采集自蜜蜂")
                .build());

        // ---------------- 基因组物品（模板） ----------------
        registry.register(HrfItem.builder("hrf_genome_blank", Material.NAUTILUS_SHELL,
                        "<white>空白基因组", ItemGroup.GENETICS)
                .lore("<gray>测序结果的载体")
                .build());

        registry.register(HrfItem.builder("hrf_genome_plant", Material.HEART_OF_THE_SEA,
                        "<green>植物基因组", ItemGroup.GENETICS)
                .lore("<gray>承载植物性状数据的基因组")
                .glow(true)
                .build());

        registry.register(HrfItem.builder("hrf_genome_animal", Material.PHANTOM_MEMBRANE,
                        "<red>动物基因组", ItemGroup.GENETICS)
                .lore("<gray>承载动物性状数据的基因组")
                .glow(true)
                .build());

        // ---------------- 基因机器专用零件 ----------------
        registry.register(HrfItem.builder("hrf_dna_sequencer_part", Material.SCULK_SENSOR,
                        "<dark_aqua>测序芯片", ItemGroup.GENETICS)
                .lore("<gray>基因测序仪的核心元件")
                .build());

        registry.register(HrfItem.builder("hrf_splicing_matrix", Material.SMITHING_TABLE,
                        "<dark_aqua>拼接矩阵", ItemGroup.GENETICS)
                .lore("<gray>基因拼接机的核心元件")
                .build());

        registry.register(HrfItem.builder("hrf_injector_needle", Material.LIGHTNING_ROD,
                        "<dark_aqua>注入针头", ItemGroup.GENETICS)
                .lore("<gray>基因注入器的核心元件")
                .build());
    }
}
