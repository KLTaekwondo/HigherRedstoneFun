package com.koole.higherRedStoneFun.content;

import com.koole.higherRedStoneFun.core.ItemGroup;
import com.koole.higherRedStoneFun.energy.EnergyNode;
import com.koole.higherRedStoneFun.genetics.GeneticsManager;
import com.koole.higherRedStoneFun.machines.MachineDefinition;
import com.koole.higherRedStoneFun.machines.MachineRegistry;
import com.koole.higherRedStoneFun.machines.logic.GrowthChamberLogic;
import com.koole.higherRedStoneFun.machines.logic.InjectorLogic;
import com.koole.higherRedStoneFun.machines.logic.SequencerLogic;
import com.koole.higherRedStoneFun.machines.logic.SplicerLogic;
import com.koole.higherRedStoneFun.recipes.RecipeType;
import org.bukkit.Material;

/**
 * 模块三：基因机器。
 *
 * <p>设计意图（填补粘液科技在「育种」方向上的空白）：</p>
 *
 * <p>原版粘液科技及其主流附属的农业玩法基本停留在「种下去、等它长、收更多」，
 * 玩家无法真正地「培育」一个优良品种。本模块把真实育种的核心循环做进游戏：</p>
 *
 * <ol>
 *   <li><b>采集</b>：用采样器从成熟作物 / 家畜身上取得生物样本。</li>
 *   <li><b>测序</b>：测序仪把样本解析成基因组（4 个基因位，各 0~5 级）。</li>
 *   <li><b>拼接</b>：拼接机把两个基因组逐位取优并可能突变，逐步逼近满级。</li>
 *   <li><b>注入</b>：注入器把基因组写回种子或胚胎。</li>
 *   <li><b>收获</b>：带基因的作物生长更快、产量更高；带基因的家畜后代更好。</li>
 * </ol>
 *
 * <p>这样就形成了「越育越强、且需要多代选育」的正反馈，
 * 而不是一次性的随机刷取。</p>
 */
public final class GeneticsMachines {

    private GeneticsMachines() {
    }

    public static void register(GeneticsManager genetics) {
        MachineRegistry registry = MachineRegistry.get();

        // ==========================================================
        // 基因测序仪
        // ==========================================================
        registry.register(MachineDefinition.builder(
                        "hrf_dna_sequencer", "基因测序仪", ItemGroup.GENETICS, Material.CALIBRATED_SCULK_SENSOR)
                .recipeType(RecipeType.SEQUENCING)
                .energyRole(EnergyNode.Role.CONSUMER)
                .buffer(20_000L)
                .throughput(12L)
                .inventorySize(27)
                .logic(new SequencerLogic(genetics, 1))
                .lore("<gray>解析生物样本，读出 4 个基因位的等级")
                .lore("<gray>耗电: <red>12 J/t")
                .lore("<gold>需要: 生物样本 + 基因引物")
                .build());

        registry.register(MachineDefinition.builder(
                        "hrf_dna_sequencer_mk2", "基因测序仪 MK-II", ItemGroup.GENETICS, Material.SCULK_SHRIEKER)
                .recipeType(RecipeType.SEQUENCING)
                .energyRole(EnergyNode.Role.CONSUMER)
                .buffer(60_000L)
                .throughput(30L)
                .inventorySize(27)
                .logic(new SequencerLogic(genetics, 2))
                .lore("<gray>二代测序仪，解析出的基因起点更高")
                .lore("<gray>耗电: <red>30 J/t")
                .lore("<green>基因位期望值 +1")
                .glow(true)
                .build());

        // ==========================================================
        // 基因拼接机
        // ==========================================================
        registry.register(MachineDefinition.builder(
                        "hrf_gene_splicer", "基因拼接机", ItemGroup.GENETICS, Material.SMITHING_TABLE)
                .recipeType(RecipeType.SPLICING)
                .energyRole(EnergyNode.Role.CONSUMER)
                .buffer(40_000L)
                .throughput(20L)
                .inventorySize(45)
                .logic(new SplicerLogic(genetics, 0.12D))
                .lore("<gray>把两个基因组逐位取优，合成为一个更好的基因组")
                .lore("<gray>耗电: <red>20 J/t")
                .lore("<gray>突变率: <light_purple>12%")
                .lore("<gold>需要: 基因组 A + 基因组 B + 拼接酶")
                .build());

        registry.register(MachineDefinition.builder(
                        "hrf_gene_splicer_mk2", "基因拼接机 MK-II", ItemGroup.GENETICS, Material.SMITHING_TABLE)
                .recipeType(RecipeType.SPLICING)
                .energyRole(EnergyNode.Role.CONSUMER)
                .buffer(120_000L)
                .throughput(60L)
                .inventorySize(45)
                .logic(new SplicerLogic(genetics, 0.30D))
                .lore("<gray>二代拼接机，突变率大幅提升")
                .lore("<gray>耗电: <red>60 J/t")
                .lore("<gray>突变率: <light_purple>30%")
                .glow(true)
                .build());

        // ==========================================================
        // 基因注入器
        // ==========================================================
        registry.register(MachineDefinition.builder(
                        "hrf_gene_injector", "基因注入器", ItemGroup.GENETICS, Material.BREWING_STAND)
                .recipeType(RecipeType.INJECTION)
                .energyRole(EnergyNode.Role.CONSUMER)
                .buffer(24_000L)
                .throughput(15L)
                .inventorySize(27)
                .logic(new InjectorLogic(genetics, 1))
                .lore("<gray>把基因组注入种子模板或空白胚胎")
                .lore("<gray>耗电: <red>15 J/t")
                .lore("<gold>需要: 空白模板 + 基因组 (+ 稳定剂可选)")
                .build());

        registry.register(MachineDefinition.builder(
                        "hrf_gene_injector_mk2", "基因注入器 MK-II", ItemGroup.GENETICS, Material.BREWING_STAND)
                .recipeType(RecipeType.INJECTION)
                .energyRole(EnergyNode.Role.CONSUMER)
                .buffer(80_000L)
                .throughput(45L)
                .inventorySize(27)
                .logic(new InjectorLogic(genetics, 2))
                .lore("<gray>二代注入器，稳定剂效果更强")
                .lore("<gray>耗电: <red>45 J/t")
                .glow(true)
                .build());

        // ==========================================================
        // 基因培育舱：全自动农业
        // ==========================================================
        registry.register(MachineDefinition.builder(
                        "hrf_growth_chamber", "基因培育舱", ItemGroup.GENETICS, Material.BEEHIVE)
                .energyRole(EnergyNode.Role.CONSUMER)
                .buffer(50_000L)
                .throughput(18L)
                .inventorySize(27)
                .logic(new GrowthChamberLogic(genetics))
                .period(20)
                .lore("<gray>把改良种子的基因「变现」为实际产物")
                .lore("<gray>耗电: <red>18 J/t</gray> <dark_gray>(每次培育)")
                .lore("<gold>放入改良种子，自动产出对应作物")
                .lore("<green>基因等级越高，产出越多、越快")
                .blank()
                .lore("<dark_gray>这是基因工程最终的经济回报")
                .build());
    }
}
