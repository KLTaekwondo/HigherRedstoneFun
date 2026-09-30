package com.koole.higherRedStoneFun.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/** 文本工具：统一使用 MiniMessage 解析，并默认关闭斜体。 */
public final class Text {

    public static final String PREFIX = "<gradient:#ff6b6b:#ffd93d><bold>HRF</bold></gradient> <dark_gray>»</dark_gray> ";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    /** 解析 MiniMessage 文本并强制关闭默认斜体。 */
    public static Component mm(String raw) {
        if (raw == null) {
            return Component.empty();
        }
        return MM.deserialize(raw).decoration(TextDecoration.ITALIC, false);
    }

    /** 解析带插件前缀的消息。 */
    public static Component prefixed(String raw) {
        return mm(PREFIX + raw);
    }

    /** 解析一组 lore 行。 */
    public static List<Component> lore(List<String> raw) {
        List<Component> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(mm(line));
        }
        return out;
    }

    /** 去掉所有样式，得到纯文本。 */
    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** 数字千分位格式化，便于展示能量数值。 */
    public static String number(long value) {
        return String.format("%,d", value);
    }

    /** 把 0..1 的比例渲染成进度条。 */
    public static String bar(double ratio, int length, String filledColor, String emptyColor) {
        double clamped = Math.max(0.0D, Math.min(1.0D, ratio));
        int filled = (int) Math.round(clamped * length);
        StringBuilder sb = new StringBuilder("<").append(filledColor).append(">");
        for (int i = 0; i < filled; i++) {
            sb.append('|');
        }
        sb.append("</").append(filledColor).append("><").append(emptyColor).append(">");
        for (int i = filled; i < length; i++) {
            sb.append('|');
        }
        sb.append("</").append(emptyColor).append(">");
        return sb.toString();
    }

    /** 根据比例返回颜色名。 */
    public static String colorFor(double ratio) {
        if (ratio >= 0.75D) {
            return "green";
        }
        if (ratio >= 0.4D) {
            return "yellow";
        }
        if (ratio >= 0.15D) {
            return "gold";
        }
        return "red";
    }

    public static Component gray(String raw) {
        return mm("<gray>" + raw + "</gray>");
    }

    public static Component aqua(String raw) {
        return mm("<aqua>" + raw + "</aqua>");
    }

    public static Component error(String raw) {
        return mm("<red>" + raw + "</red>");
    }

    public static Component success(String raw) {
        return mm("<green>" + raw + "</green>");
    }

    public static Component plainText(String raw) {
        return Component.text(raw).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
    }
}
