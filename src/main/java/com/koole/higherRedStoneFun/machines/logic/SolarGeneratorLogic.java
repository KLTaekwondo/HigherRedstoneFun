package com.koole.higherRedStoneFun.machines.logic;

import com.koole.higherRedStoneFun.machines.MachineInstance;
import com.koole.higherRedStoneFun.machines.MachineLogic;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * 太阳能发电机。
 *
 * <p>发电量由机器上方的实际天空光照决定，因此玩家必须把机器露天放置，
 * 并且需要注意「光照等级 = 发电效率」这一点，而不是简单地看白天黑夜。</p>
 */
public final class SolarGeneratorLogic implements MachineLogic {

    private final long peakOutput;

    public SolarGeneratorLogic(long peakOutput) {
        this.peakOutput = Math.max(1L, peakOutput);
    }

    @Override
    public boolean energyAware() {
        return true;
    }

    @Override
    public long generate(MachineInstance machine) {
        Block block = machine.location().getBlock();
        World world = machine.location().getWorld();
        if (world == null) {
            return 0L;
        }

        // 天空光照：0-15，只有正上方露天才有 15
        int skyLight = block.getRelative(0, 1, 0).getLightFromSky();
        if (skyLight <= 0) {
            return 0L;
        }

        double efficiency = skyLight / 15.0D;

        // 天气惩罚
        if (world.isThundering()) {
            efficiency *= 0.25D;
        } else if (world.hasStorm()) {
            efficiency *= 0.5D;
        }

        // 夜晚没有阳光（天空光照为 0 时已经在上面返回）
        if (!world.isDayTime()) {
            efficiency *= 0.0D;
        }

        long produced = Math.round(peakOutput * efficiency);
        if (produced <= 0L) {
            return 0L;
        }
        machine.addGenerated(produced);
        return produced;
    }

    @Override
    public String statusLine(MachineInstance machine) {
        Block block = machine.location().getBlock();
        int skyLight = block.getRelative(0, 1, 0).getLightFromSky();
        World world = machine.location().getWorld();
        if (world == null || !world.isDayTime() || skyLight <= 0) {
            return "<dark_gray>夜间 / 无光照，停机";
        }
        String weather = world.isThundering() ? "<dark_red>雷暴" : (world.hasStorm() ? "<blue>降雨" : "<yellow>晴朗");
        return weather + " <dark_gray>| <gray>光照 <white>" + skyLight + "/15";
    }

    public long peakOutput() {
        return peakOutput;
    }
}
