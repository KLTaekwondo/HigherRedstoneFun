package com.koole.higherRedStoneFun.machines;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * 结构成型：让多方块机器「搭出来就成型」，而不是「必须放置特定物品」。
 *
 * <h2>为什么需要这个类</h2>
 *
 * <p>早期实现要求玩家必须拿着 {@code /hrf give} 给的自定义物品去放置控制器。
 * 如果玩家在创造模式下从背包里拿了<b>原版工作台</b>放下（这是最自然的操作），
 * 那个方块没有 PDC 标记，系统完全不认识它——叠再多铁块和玻璃也没有任何反应，
 * 玩家只看到「什么都没发生」。</p>
 *
 * <p>这违反了多方块机器的通用直觉：<b>玩家搭出结构，机器就该成型</b>。
 * 至于控制器是怎么被放下的（自定义物品、原版方块、活塞推动、结构方块生成）
 * 不应该成为门槛。</p>
 *
 * <p>因此这里提供一条「自动成型」路径：只要某个坐标上的方块材质
 * 匹配某台多方块机器的控制器外观，且结构完整，就把它注册成机器。</p>
 */
public final class StructureFormation {

    private StructureFormation() {
    }

    /**
     * 尝试把某个坐标上的方块认定为「刚成型的多方块机器」。
     *
     * @param manager       机器管理器（用于判断是否已注册）
     * @param controllerLoc 候选控制器坐标
     * @return 新成型的机器；不满足条件时返回 null
     */
    public static MachineInstance tryForm(MachineManager manager, Location controllerLoc) {
        World world = controllerLoc.getWorld();
        if (world == null) {
            return null;
        }
        // 已注册过的不是「新成型」
        if (manager.isMachine(controllerLoc)) {
            return null;
        }
        if (!world.isChunkLoaded(controllerLoc.getBlockX() >> 4, controllerLoc.getBlockZ() >> 4)) {
            return null;
        }

        Material actual = world.getBlockAt(controllerLoc).getType();
        if (actual.isAir()) {
            return null;
        }

        for (MachineDefinition definition : MachineRegistry.get().all()) {
            if (!definition.isMultiblock()) {
                continue;
            }
            // 控制器外观必须匹配（例如工作台）
            if (definition.icon() != actual) {
                continue;
            }
            // 结构必须完整
            if (!definition.structure().matches(controllerLoc)) {
                continue;
            }

            MachineInstance machine = new MachineInstance(controllerLoc, definition);
            machine.setStructureBlocks(definition.structure().extraBlockLocations(controllerLoc));
            machine.recheckStructure();
            // 这条路是「玩家用普通方块搭出结构」，拆解时必须归还普通方块，
            // 绝不能把原版工作台变成自定义物品（那是凭空造物）。
            // 回归测试见 SelfTest#testDismantleReturnsOriginal。
            machine.setPlacedAsMachineItem(false);
            definition.logic().onPlace(machine);
            manager.add(machine);
            return machine;
        }
        return null;
    }

    /**
     * 在某个坐标下方查找「结构刚被补全」的候选控制器。
     *
     * <p>玩家搭结构是从下往上叠的，因此新放的方块下方 1~3 格内
     * 就可能是控制器。结构恒为竖直，所以只需向下查找。</p>
     *
     * @param placedLoc 刚放置的方块坐标
     * @return 新成型的机器；没有则返回 null（可能返回多个中的一个）
     */
    public static MachineInstance tryFormBelow(MachineManager manager, Location placedLoc) {
        for (int dy = 1; dy <= 3; dy++) {
            Location candidate = placedLoc.clone().subtract(0, dy, 0);
            MachineInstance formed = tryForm(manager, candidate);
            if (formed != null) {
                return formed;
            }
        }
        return null;
    }
}
