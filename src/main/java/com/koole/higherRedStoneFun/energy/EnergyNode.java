package com.koole.higherRedStoneFun.energy;

import org.bukkit.Location;

/**
 * 接入能源网络的一个节点。
 *
 * <p>实现类通常是机器方块本身。节点不关心网络拓扑，只负责回答三个问题：
 * 我能发多少电、我需要多少电、我有多少储能。</p>
 */
public interface EnergyNode {

    /** 节点在网络中扮演的角色。 */
    enum Role {
        /** 发电机：{@link #generate()} 返回本 tick 发出的焦耳。 */
        GENERATOR,
        /** 用电机：{@link #demand()} 返回本 tick 想要的焦耳。 */
        CONSUMER,
        /** 储能：电池 / 电容。 */
        STORAGE
    }

    /** 节点所在位置（必须与注册时一致）。 */
    Location location();

    /** 节点角色。 */
    Role role();

    /**
     * 发电机调用：产出能量。
     *
     * @return 本 tick 产生的焦耳数，0 表示未发电
     */
    default long generate() {
        return 0L;
    }

    /**
     * 用电机调用：本 tick 希望获得的焦耳数。
     *
     * @return 期望值，0 表示当前不需要电
     */
    default long demand() {
        return 0L;
    }

    /**
     * 用电机调用：领取能量。
     *
     * @param joules 实际分配给它的焦耳（可能小于 demand）
     */
    default void receive(long joules) {
    }

    /**
     * 储能调用：充能。
     *
     * @param joules 试图充入的焦耳
     * @return 实际接受的焦耳
     */
    default long charge(long joules) {
        return 0L;
    }

    /**
     * 储能调用：放电。
     *
     * @param joules 试图抽取的焦耳
     * @return 实际给出的焦耳
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
