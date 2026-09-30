package com.koole.higherRedStoneFun.genetics;

/** 基因位定义。 */
public enum Gene {

    GROWTH("生长", "生", "<green>作物成熟更快、动物成长更快"),
    YIELD("产量", "产", "<yellow>收获数量与掉落物更多"),
    RESILIENCE("抗逆", "抗", "<aqua>抵抗踩踏、干旱与病害"),
    VIGOR("活力", "活", "<light_purple>自动补种、繁殖能力更强");

    private final String displayName;
    private final String shortName;
    private final String description;

    Gene(String displayName, String shortName, String description) {
        this.displayName = displayName;
        this.shortName = shortName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String shortName() {
        return shortName;
    }

    public String description() {
        return description;
    }
}
