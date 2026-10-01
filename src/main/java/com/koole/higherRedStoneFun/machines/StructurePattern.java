package com.koole.higherRedStoneFun.machines;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 多方块结构模式。
 *
 * <p>描述「一台机器成型需要哪些额外的方块」。控制器方块本身（偏移 0,0,0）
 * 不包含在 {@link #parts()} 里。</p>
 *
 * <h2>为什么结构是竖直的</h2>
 *
 * <p>本插件的机器按区块索引（{@code MachineManager.chunkKey} 只用 X/Z），
 * 而区块也是按 X/Z 划分的。因此：</p>
 *
 * <ul>
 *   <li><b>竖直结构</b>：X/Z 不变，永远落在同一个区块里，
 *       索引、区块卸载、恢复逻辑全部照旧。</li>
 *   <li><b>水平结构</b>：会横跨 2~4 个区块，导致「一台机器被劈成两半」
 *       ——一个区块卸载了、另一个还在，这是数据丢失的经典来源。</li>
 * </ul>
 *
 * <p>所以本插件只支持竖直方向的结构。这不是为了省地方，
 * 而是为了让结构系统和区块生命周期天然不冲突。</p>
 */
public final class StructurePattern {

    /**
     * 结构中的一个方块。
     *
     * @param dx      X 偏移（当前恒为 0，保留以便将来扩展）
     * @param dy      Y 偏移（1 = 控制器上方一格）
     * @param dz      Z 偏移（当前恒为 0）
     * @param matcher 材质判定（用 Tag 而不是具体 Material，兼容染色变体）
     * @param label   人类可读的名称，用于错误提示
     */
    public record Part(int dx, int dy, int dz, Predicate<Material> matcher,
                       Material display, String label) {

        /** 用单个材质匹配。 */
        public static Part of(int dy, Material material, String label) {
            return new Part(0, dy, 0, m -> m == material, material, label);
        }

        /** 用自定义判定匹配（用于「玻璃族」这类没有官方 Tag 的集合）。 */
        public static Part custom(int dy, Predicate<Material> matcher, Material display, String label) {
            return new Part(0, dy, 0, matcher, display, label);
        }

        /** 用标签匹配（能自动兼容同族方块）。 */
        public static Part tagged(int dy, Tag<Material> tag, Material display, String label) {
            return new Part(0, dy, 0, tag::isTagged, display, label);
        }

        /** 该方块相对控制器的偏移，用于诊断输出。 */
        public String offsetText() {
            return dy > 0 ? "上方 " + dy + " 格" : (dy < 0 ? "下方 " + (-dy) + " 格" : "控制器所在格");
        }
    }

    /** 竖直向上的 3 格结构：控制器 -> 机身 -> 罩子。 */
    public static StructurePattern vertical(Material body, String bodyLabel,
                                            Predicate<Material> casing, Material casingDisplay,
                                            String casingLabel) {
        return new StructurePattern(List.of(
                Part.of(1, body, bodyLabel),
                Part.custom(2, casing, casingDisplay, casingLabel)
        ));
    }

    private final List<Part> parts;

    private StructurePattern(List<Part> parts) {
        this.parts = List.copyOf(parts);
    }

    public List<Part> parts() {
        return parts;
    }

    /** 结构占据的全部方块（含控制器自身）。 */
    public List<Location> blockLocations(Location controller) {
        List<Location> out = new ArrayList<>(parts.size() + 1);
        out.add(controller.clone());
        for (Part part : parts) {
            out.add(controller.clone().add(part.dx(), part.dy(), part.dz()));
        }
        return out;
    }

    /** 除控制器外的结构方块。 */
    public List<Location> extraBlockLocations(Location controller) {
        List<Location> out = new ArrayList<>(parts.size());
        for (Part part : parts) {
            out.add(controller.clone().add(part.dx(), part.dy(), part.dz()));
        }
        return out;
    }

    /**
     * 检查结构是否成型。
     *
     * <p>未加载的区块一律视为「不匹配」——宁可判定为未成型，
     * 也不要在无法读取方块时给出错误的成型状态。</p>
     */
    public boolean matches(Location controller) {
        World world = controller.getWorld();
        if (world == null) {
            return false;
        }
        for (Part part : parts) {
            Location loc = controller.clone().add(part.dx(), part.dy(), part.dz());
            if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                return false;
            }
            Block block = world.getBlockAt(loc);
            if (!part.matcher().test(block.getType())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 返回第一个不匹配的结构部件，全部匹配则返回 null。
     *
     * <p>用于给玩家精确的提示：「上方 2 格需要玻璃」比
     * 「结构不完整」有用得多。</p>
     */
    public Part firstMissing(Location controller) {
        World world = controller.getWorld();
        if (world == null) {
            return parts.isEmpty() ? null : parts.get(0);
        }
        for (Part part : parts) {
            Location loc = controller.clone().add(part.dx(), part.dy(), part.dz());
            if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                return part;
            }
            if (!part.matcher().test(world.getBlockAt(loc).getType())) {
                return part;
            }
        }
        return null;
    }

    /** 结构缺失时的提示文本。 */
    public String describeMissing(Location controller) {
        Part missing = firstMissing(controller);
        if (missing == null) {
            return "";
        }
        return missing.offsetText() + "需要" + missing.label();
    }

    /** 结构需求的简短描述，用于物品 lore 与图鉴。 */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (Part part : parts) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(part.label());
        }
        return sb.toString();
    }

    public int size() {
        return parts.size() + 1;
    }
}
