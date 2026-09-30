package com.koole.higherRedStoneFun.energy;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * 导线材质注册表。
 *
 * <p>这是「哪种方块可以当电线用」的唯一事实来源。它从配置读取，
 * 因此服主可以自由更换导线外观，而不需要改代码。</p>
 *
 * <p>之所以单独抽出一个类：早期版本把材质集合硬编码在 {@link EnergyNetwork} 里，
 * 而配置文件和玩家文档写的是另一套材质，导致「按文档接了线却不导电」。
 * 集中到一处后，文档、配置、代码三者不会再漂移。</p>
 */
public final class Cables {

    /** 默认导线：铜块（廉价易得）、避雷针（细长好看）、末地烛（发光装饰）。 */
    private static final Set<Material> DEFAULT = EnumSet.of(
            Material.COPPER_BLOCK,
            Material.LIGHTNING_ROD,
            Material.END_ROD
    );

    private static Set<Material> materials = EnumSet.copyOf(DEFAULT);

    private Cables() {
    }

    /** 从配置加载导线的材质集合。无法识别的材质会被忽略并记录警告。 */
    public static void load(FileConfiguration config, java.util.logging.Logger logger) {
        java.util.List<String> names = config.getStringList("energy.cable-materials");
        if (names.isEmpty()) {
            materials = EnumSet.copyOf(DEFAULT);
            return;
        }

        Set<Material> parsed = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (material == null) {
                logger.warning("[HRF] 配置中的导线材质无法识别，已忽略: " + name);
                continue;
            }
            if (!material.isBlock()) {
                logger.warning("[HRF] 导线材质必须是方块，已忽略: " + name);
                continue;
            }
            parsed.add(material);
        }

        materials = parsed.isEmpty() ? EnumSet.copyOf(DEFAULT) : parsed;
    }

    /** 该材质是否是导线。 */
    public static boolean isCable(Material material) {
        return material != null && materials.contains(material);
    }

    /** 当前生效的导线材质（供文档/提示使用）。 */
    public static Set<Material> all() {
        return java.util.Collections.unmodifiableSet(materials);
    }

    /** 人类可读的导线名称列表，例如「铜块 / 避雷针 / 末地烛」。 */
    public static String describe() {
        StringBuilder sb = new StringBuilder();
        for (Material material : materials) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append(displayName(material));
        }
        return sb.toString();
    }

    private static String displayName(Material material) {
        return switch (material) {
            case COPPER_BLOCK -> "铜块";
            case LIGHTNING_ROD -> "避雷针";
            case END_ROD -> "末地烛";
            case REDSTONE_BLOCK -> "红石块";
            case IRON_BLOCK -> "铁块";
            case GOLD_BLOCK -> "金块";
            default -> material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }
}
