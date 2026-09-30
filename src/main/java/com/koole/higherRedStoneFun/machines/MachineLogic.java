package com.koole.higherRedStoneFun.machines;

/**
 * 机器行为接口。
 *
 * <p>把「行为」从「定义」和「状态」中分离出来，是本插件与粘液科技最大的架构差异：
 * 原版每个机器类型都是一个继承层级很深的类，新增一台机器往往需要新写一个类；
 * 这里绝大多数机器只需要注册一份 {@link MachineDefinition} 加一个逻辑实现即可。</p>
 */
public interface MachineLogic {

    /** 空逻辑：机器只作为储能或纯装饰存在。 */
    MachineLogic NONE = new MachineLogic() {
    };

    /**
     * 每 tick 调用一次（受 {@link MachineDefinition#tickPeriod()} 控制频率）。
     *
     * @param machine 机器实例
     * @return 本 tick 是否产生了变化（用于跳过不必要的存档）
     */
    default boolean tick(MachineInstance machine) {
        return false;
    }

    /** 机器被放置时调用。 */
    default void onPlace(MachineInstance machine) {
    }

    /** 机器被破坏时调用，用于掉落内部物品。 */
    default void onBreak(MachineInstance machine) {
    }

    /** 玩家打开界面时调用，用于刷新动态 lore。 */
    default void onOpen(MachineInstance machine) {
        onContentsChanged(machine);
    }

    /**
     * 机器内部物品发生变化时调用。
     *
     * <p>主要用于工作台刷新结果预览——玩家每放一个材料都要立刻看到能否合成。</p>
     */
    default void onContentsChanged(MachineInstance machine) {
    }

    /** 机器界面的标题附加信息。 */
    default String statusLine(MachineInstance machine) {
        return "";
    }

    /**
     * 作为发电机时，本 tick 向网络输出的焦耳数。
     *
     * <p>实现方应当自行扣减燃料并把产物计入 {@link MachineInstance#addGenerated(long)}。</p>
     */
    default long generate(MachineInstance machine) {
        return 0L;
    }

    /**
     * 作为用电机时，本 tick 希望从网络领取的焦耳数。
     *
     * <p>推荐实现「缓冲区补满」模型：只有当机器确实有活要干（
     * {@link MachineInstance#requestPower(boolean)} 为真）时才返回需求，
     * 否则返回 0，从而避免机器空转偷电。</p>
     */
    default long demand(MachineInstance machine) {
        return 0L;
    }

    /** 该机器是否参与能源网络。 */
    default boolean energyAware() {
        return false;
    }
}
