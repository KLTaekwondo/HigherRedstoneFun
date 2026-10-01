package com.koole.higherRedStoneFun.ui;

/**
 * 图鉴详情页的标签页。
 *
 * <p>详情页改成「标签栏 + 内容区」：第一行是标签按钮，点击切换整个内容区。
 * 这样每一页只干一件事，不会把所有信息挤在一屏里。</p>
 *
 * <h2>条件显示</h2>
 *
 * <p>没有内容的标签<b>不显示按钮</b>。例如增强工作台是用原版方块搭出来的结构，
 * 没有合成配方，就不显示「合成方式」标签——只留「结构」和「用途」。</p>
 */
public enum GuideTab {

    /** 合成方式：3x3 材料网格 + 产物。 */
    RECIPE("合成方式", "把材料放进机器产出它"),
    /** 结构：多方块机器怎么搭。 */
    STRUCTURE("结构", "多方块机器的搭建方式"),
    /** 用途：机器能做哪些配方 / 材料被用在哪些配方里。 */
    USAGE("用途", "它能做什么，或被用来做什么");

    private final String displayName;
    private final String description;

    GuideTab(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
