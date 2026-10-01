package com.koole.higherRedStoneFun.content;

import com.koole.higherRedStoneFun.core.ItemGroup;
import com.koole.higherRedStoneFun.machines.MachineDefinition;
import com.koole.higherRedStoneFun.machines.MachineRegistry;
import com.koole.higherRedStoneFun.machines.StructurePattern;
import com.koole.higherRedStoneFun.machines.logic.CraftingTableLogic;
import com.koole.higherRedStoneFun.machines.logic.RecipeMachineLogic;
import com.koole.higherRedStoneFun.recipes.RecipeType;
import org.bukkit.Material;

/**
 * 模块一：基础机器。
 *
 * <p>设计意图：让玩家在完全没有电力的阶段就能建立起一条完整的生产线。
 * 所有机器都靠固体燃料驱动，因此不需要理解能源网络；它们也是后续
 * 电力机器的「前置科技」——没有基础机器就做不出电路板。</p>
 */
public final class BasicMachines {

    private BasicMachines() {
    }

    public static void register() {
        MachineRegistry registry = MachineRegistry.get();

        // ----------------------------------------------------------
        // 增强工作台：入口机器（原版工作台可制作）
        //
        // 多方块结构（竖直 3 格，不跨区块）：
        //      [玻璃]    <- 真空罩
        //      [铁块]    <- 机身
        //      [工作台]  <- 控制器（点它开界面）
        //
        // 单方块即可使用基础配方；结构成型后解锁「精密零件」
        // （电路板、电池单元、基因机器零件等）。
        // ----------------------------------------------------------
        registry.register(MachineDefinition.builder(
                        "hrf_enhanced_crafting_table", "增强工作台", ItemGroup.BASIC_MACHINES, Material.CRAFTING_TABLE)
                .recipeType(RecipeType.ENHANCED_CRAFTING)
                // 54 格：左侧 3x3 输入、右侧 3x3 结果区、最右列红石燃料 + 能量条。
                // 早期是 27 格，但 3x3 输入需要 9 个格子（10,11,12/19,20,21/28,29,30），
                // 28/29/30 在 27 格里越界被静默丢弃 —— 网格实际只有 2x3。
                .inventorySize(54)
                .logic(new CraftingTableLogic())
                .period(20)
                .structure(StructurePattern.vertical(
                        Material.IRON_BLOCK, "铁块",
                        com.koole.higherRedStoneFun.core.Materials.GLASS_LIKE,
                        Material.GLASS,
                        com.koole.higherRedStoneFun.core.Materials.glassLabel()))
                // 第一行是「物品摘要」，会显示在物品 tooltip 上——只留一句。
                // 其余各行是图鉴里的详细说明。
                .lore("<gray>把材料摆进 3x3 网格，显示产物预览")
                .lore("<gray>不需要燃料，也是最基础的加工台")
                .lore("<gray>所有零件与机器都从这里开始")
                .blank()
                .lore("<green>结构成型后解锁「精密零件」")
                .lore("<gray>电路板 / 电池单元 / 基因仪器零件")
                .blank()
                .lore("<dark_gray>结构：在控制器上方依次叠放铁块与玻璃")
                .build());

        // ----------------------------------------------------------
        // 研磨机
        // ----------------------------------------------------------
        registry.register(MachineDefinition.builder(
                        "hrf_grinder", "研磨机", ItemGroup.BASIC_MACHINES, Material.GRINDSTONE)
                .recipeType(RecipeType.GRINDING)
                .inventorySize(27)
                .fuel(1)
                .logic(RecipeMachineLogic.fuel())
                .lore("<gray>把矿石与金属磨成粉末")
                .lore("<gray>粉末是离心机与合金的原料")
                .build());

        // ----------------------------------------------------------
        // 压制机
        // ----------------------------------------------------------
        registry.register(MachineDefinition.builder(
                        "hrf_press", "压制机", ItemGroup.BASIC_MACHINES, Material.PISTON)
                .recipeType(RecipeType.PRESSING)
                .inventorySize(27)
                .fuel(1)
                .logic(RecipeMachineLogic.fuel())
                .lore("<gray>把锭压成板材、把板材冲压成零件")
                .build());

        // ----------------------------------------------------------
        // 冶炼炉（比原版熔炉快 4 倍）
        // ----------------------------------------------------------
        registry.register(MachineDefinition.builder(
                        "hrf_smeltery", "冶炼炉", ItemGroup.BASIC_MACHINES, Material.BLAST_FURNACE)
                .recipeType(RecipeType.SMELTING)
                .inventorySize(27)
                .fuel(1)
                .logic(RecipeMachineLogic.fuel())
                .lore("<gray>比普通熔炉快得多的冶炼设备")
                .lore("<gold>速度: 原版熔炉的 4 倍")
                .build());

        // ----------------------------------------------------------
        // 离心机
        // ----------------------------------------------------------
        registry.register(MachineDefinition.builder(
                        "hrf_centrifuge", "离心机", ItemGroup.BASIC_MACHINES, Material.CAULDRON)
                .recipeType(RecipeType.CENTRIFUGE)
                .inventorySize(27)
                .fuel(1)
                .logic(RecipeMachineLogic.fuel())
                .lore("<gray>利用高速旋转分离混合物")
                .lore("<gray>是获取稀有副产物的重要途径")
                .build());

        // ----------------------------------------------------------
        // 锯木机
        // ----------------------------------------------------------
        registry.register(MachineDefinition.builder(
                        "hrf_sawmill", "锯木机", ItemGroup.BASIC_MACHINES, Material.STONECUTTER)
                .recipeType(RecipeType.SAWING)
                .inventorySize(27)
                .fuel(1)
                .logic(RecipeMachineLogic.fuel())
                .lore("<gray>把原木高效切割成木板与木浆")
                .lore("<gray>1 原木 = 6 木板，远高于手工")
                .build());
    }
}
