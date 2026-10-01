package com.koole.higherRedStoneFun.energy;

import org.bukkit.Location;

/**
 * 接入能源网络的一个节点。
 *
 * <p>实现类通常是机器方块本身。节点不关心网络拓扑，只负责回答三个问题：
 * 我能产出多少 HRE、我需要多少 HRE、我有多少储能。</p>
 */
public interface EnergyNode {

    /** 节点在网络中扮演的角色。 */
    enum Role {
        /** 发电机：{@link #generate()} 返回本 tick 产出的 HRE。 */
        GENERATOR,
        /** 用能机器：{@link #demand()} 返回本 tick 想要的 HRE。 */
        CONSUMER,
        /** 储能：电池 / 电容。 */
        STORAGE
    }

    /** 节点所在位置（必须与注册时一致）。 */
    Location location();

    /** 节点角色。 */
    Role role();

    /**
     * 发电机调用：产出红石流能。
     *
     * @return 本 tick 产生的 HRE 数，0 表示未产能
     */
    default long generate() {
        return 0L;
    }

    /**
     * 用能机器调用：本 tick 希望获得的 HRE 数。
     *
     * @return 期望值，0 表示当前不需要红石流能
     */
    default long demand() {
        return 0L;
    }

    /**
     * 用能机器调用：领取红石流能。
     *
     * @param joules 实际分配给它的 HRE（可能小于 demand）
     */
    default void receive(long joules) {
    }

    /**
     * 储能调用：充能。
     *
     * @param joules 试图充入的 HRE
     * @return 实际接受的 HRE
     */
    default long charge(long joules) {
        return 0L;
    }

    /**
     * 储能调用：放能。
     *
     * @param joules 试图抽取的 HRE
     * @return 实际给出的 HRE
     */
    default long discharge(long joules) {
        return 0L;
    }

    /** 当前储能（非储能节点返回 0）。 */
    default long stored() {
        return 0L;
    }

    /** 储能上限（非储能节点返回 0）。 */
    default long capacity() {
        return 0L;
    }

    /** 由网络在重建拓扑时调用，告知它归属于哪个网络。 */
    default void setNetwork(EnergyNetwork network) {
    }

    /** 节点所属网络，未接入时为 null。 */
    default EnergyNetwork network() {
        return null;
    }
}
