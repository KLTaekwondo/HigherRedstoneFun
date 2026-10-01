package com.koole.higherRedStoneFun.content;

import com.koole.higherRedStoneFun.HigherRedStoneFun;
import com.koole.higherRedStoneFun.items.ItemRegistry;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

/**
 * 引导配方（原版工作台配方）。
 *
 * <h2>为什么需要这个类</h2>
 *
 * <p>v0.1 早期版本有一个致命缺陷：<b>整个科技树的根是断的</b>。
 * 增强工作台没有任何获取途径（全局搜索其 ID 只有定义处一处命中），
 * 而所有机器零件又都必须在增强工作台里合成——包括用来合成增强工作台本身的
 * 机器框架。结果是玩家只能靠 {@code /hrf give} 才能开始游戏。</p>
 *
 * <p>修法：把「增强工作台」和它的两个前置（机器框架、铜导线）放到
 * <b>原版工作台</b>里做。这样科技树就有了真正的起点，
 * 而原版工作台无法制作的东西（齿轮、电路板、机器）仍然需要增强工作台，
 * 升级动机依然成立。</p>
 *
 * <p>这些配方用 {@code hrf_vanilla_} 前缀的命名空间键注册，
 * 避免与其它插件或数据包冲突，也方便在重载时精确移除。</p>
 */
public final class BootstrapRecipes {

    /** 原版配方键前缀，便于识别与清理。 */
    private static final String PREFIX = "vanilla_";

    /**
     * 引导物品的「原版工作台配方」说明。
     *
     * <p>这些物品不在 {@code RecipeRegistry} 里（它们用原版配方），
     * 因此图鉴查不到它们的机器配方。没有这张表的话，图鉴会对它们
     * 显示「没有找到配方」——而玩家其实完全可以在原版工作台做出来，
     * 这会严重误导新手。</p>
     */
    private static final java.util.Map<String, java.util.List<String>> VANILLA_HINTS = java.util.Map.of(
            "hrf_machine_frame", java.util.List.of(
                    "<gray>在原版工作台中合成：",
                    "<white>铁锭　铜锭　铁锭",
                    "<white>铜锭　铁块　铜锭",
                    "<white>铁锭　铜锭　铁锭"),
            "hrf_copper_wire", java.util.List.of(
                    "<gray>在原版工作台中合成（无序）：",
                    "<white>1 铜锭 → 3 铜导线"),
            "hrf_empty_sample", java.util.List.of(
                    "<gray>在原版工作台中合成（无序）：",
                    "<white>3 玻璃 → 3 空样本瓶"),
            "hrf_guide", java.util.List.of(
                    "<gray>在原版工作台中合成（无序）：",
                    "<white>1 书 + 1 铜锭 → 科技图鉴",
                    "",
                    "<gray>右键打开，也可以用 <white>/hrf guide")
    );

    /** 返回该物品的原版工作台配方说明；不是引导物品则返回空列表。 */
    public static java.util.List<String> vanillaHint(String itemId) {
        return VANILLA_HINTS.getOrDefault(itemId, java.util.List.of());
    }

    /** 该物品是否是「只能用原版工作台制作」的引导物品。 */
    public static boolean isVanillaOnly(String itemId) {
        return VANILLA_HINTS.containsKey(itemId);
    }

    private BootstrapRecipes() {
    }

    /** 注册全部引导配方。 */
    public static void register(HigherRedStoneFun plugin) {
        ItemRegistry registry = ItemRegistry.get();

        // ----------------------------------------------------------
        // 1. 机器框架 —— 所有机器的基座
        //    3x3：四角铁锭 + 四边铜锭 + 中心铁块
        // ----------------------------------------------------------
        ShapedRecipe frame = new ShapedRecipe(
                key(plugin, "machine_frame"), registry.create("hrf_machine_frame", 1));
        frame.shape("ICI", "CBC", "ICI");
        frame.setIngredient('I', Material.IRON_INGOT);
        frame.setIngredient('C', Material.COPPER_INGOT);
        frame.setIngredient('B', Material.IRON_BLOCK);
        add(plugin, frame);

        // ----------------------------------------------------------
        // 2. 铜导线 —— 电力线的前置
        //    无序：1 铜锭 -> 3 铜导线
        // ----------------------------------------------------------
        ShapelessRecipe wire = new ShapelessRecipe(
                key(plugin, "copper_wire"), registry.create("hrf_copper_wire", 3));
        wire.addIngredient(Material.COPPER_INGOT);
        add(plugin, wire);

        // ----------------------------------------------------------
        // 3. 增强工作台 —— 科技树的起点
        //
        //     这里<b>故意没有配方</b>。
        //
        //     增强工作台是多方块结构，而且它的结构用的全是原版方块
        //     （工作台 + 铁块 + 玻璃）。既然玩家直接搭就能成型，
        //     再给一个「先合成物品、再放下、再搭结构」的配方就是多余的门槛——
        //     图鉴里也会同时显示「合成配方」和「多方块结构」，
        //     看起来像要求做两件事。
        //
        //     所以它的获取方式就是结构本身：放一个工作台，上面叠铁块与玻璃。
        // ----------------------------------------------------------

        // ----------------------------------------------------------
        // 4. 空样本瓶 —— 基因玩法的起点
        //    3 玻璃 -> 3 空样本瓶（无序）
        // ----------------------------------------------------------
        ShapelessRecipe sample = new ShapelessRecipe(
                key(plugin, "empty_sample"), registry.create("hrf_empty_sample", 3));
        sample.addIngredient(3, Material.GLASS);
        add(plugin, sample);

        // ----------------------------------------------------------
        // 5. 科技图鉴 —— 随身携带的说明书
        //    1 书 + 1 铜锭（无序）
        // ----------------------------------------------------------
        ShapelessRecipe guide = new ShapelessRecipe(
                key(plugin, "guide"), registry.create("hrf_guide", 1));
        guide.addIngredient(Material.BOOK);
        guide.addIngredient(Material.COPPER_INGOT);
        add(plugin, guide);

        plugin.getLogger().info("已注册 " + 5 + " 条引导配方（原版工作台可用）。");
    }

    /** 移除全部引导配方（重载时先清后加，避免重复注册抛异常）。 */
    public static void unregister(HigherRedStoneFun plugin) {
        for (String name : new String[]{"machine_frame", "copper_wire", "empty_sample", "guide"}) {
            plugin.getServer().removeRecipe(key(plugin, name));
        }
    }

    private static NamespacedKey key(HigherRedStoneFun plugin, String name) {
        return new NamespacedKey(plugin, PREFIX + name);
    }

    private static void add(HigherRedStoneFun plugin, org.bukkit.inventory.Recipe recipe) {
        try {
            plugin.getServer().addRecipe(recipe);
        } catch (IllegalStateException ex) {
            // 同名配方已存在（例如热重载）：先移除再登记
            plugin.getServer().removeRecipe(((org.bukkit.Keyed) recipe).getKey());
            plugin.getServer().addRecipe(recipe);
        }
    }

    /** 判断一个物品是否是引导配方产物（供旁路检查使用）。 */
    public static boolean isBootstrapItem(ItemStack stack) {
        String id = ItemRegistry.get().idOf(stack);
        return id != null && (id.equals("hrf_machine_frame")
                || id.equals("hrf_copper_wire")
                || id.equals("hrf_enhanced_crafting_table")
                || id.equals("hrf_empty_sample"));
    }
}
