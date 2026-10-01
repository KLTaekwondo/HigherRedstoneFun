package com.koole.higherRedStoneFun.ui;

import com.koole.higherRedStoneFun.content.BootstrapRecipes;
import com.koole.higherRedStoneFun.core.Text;
import com.koole.higherRedStoneFun.items.HrfItem;
import com.koole.higherRedStoneFun.items.ItemRegistry;
import com.koole.higherRedStoneFun.machines.MachineDefinition;
import com.koole.higherRedStoneFun.machines.MachineRegistry;
import com.koole.higherRedStoneFun.machines.StructurePattern;
import com.koole.higherRedStoneFun.recipes.MachineRecipe;
import com.koole.higherRedStoneFun.recipes.RecipeOutput;
import com.koole.higherRedStoneFun.recipes.RecipeRegistry;
import com.koole.higherRedStoneFun.recipes.RecipeType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 图鉴（三层结构）。
 *
 * <pre>
 *   ① 主页面    大类图标：机器 / 基因工程 / 材料与零件 / 工具与仪器
 *   ② 列表页    该类全部物品，带分页（每页 45 格）
 *   ③ 详情页    标签式：第一行切换标签，内容区只显示当前标签
 * </pre>
 *
 * <h2>详情页布局</h2>
 *
 * <pre>
 *   列      0       1    2      3      4      5    6    7    8
 *   行0   [物品]        [合成方式][用途][结构]              ← 标签栏
 *   行1                 ┌──── 当前标签的内容 ────┐
 *   行2                 │                       │
 *   行3                 │                       │
 *   行4                 └───────────────────────┘
 *   行5   [返回]              [上页][信息][下页]        [关闭]
 * </pre>
 *
 * <h2>标签是「按需显示」的</h2>
 *
 * <p>没有内容的标签不显示按钮。例如增强工作台是用原版方块搭出来的结构，
 * 没有合成配方——它就只显示「结构」和「用途」两个标签。</p>
 */
public final class GuideMenu implements InventoryHolder {

    // ---- 底部按钮（监听器与自检依赖）----

    // ---- 详情页布局 ----

    /**
     * 详情页框架（5×9 = 45 格）：
     *
     * <pre>
     *   列      0      1      2      3      4      5      6      7      8
     *   行1   [红][红][红][红][红][红][红][红][红]      ← 红玻璃 + 标签栏
     *   行2   [玻][ ][ ][材][材][材][ ][ ][玻]
     *   行3   [玻][ ][绿][机器][绿]  ... [绿][产物][绿][玻]   ← 绿框围住两端
     *   行4   [玻][ ][ ][材][材][材][ ][ ][玻]
     *   行5   [蓝][蓝][蓝][蓝][蓝][蓝][蓝][蓝][蓝]      ← 蓝玻璃 + 按钮
     * </pre>
     *
     * <p>中间三行<b>不放任何玻璃板</b>，只有第一列与最后一列留玻璃作为
     * 左右边框——九宫格区域因此是「空的」，材料放进去就是 3x3。</p>
     */
    /** 第一行：红色玻璃背景 */
    public static final int[] ROW_TOP = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    /** 最后一行：蓝色玻璃背景 */
    public static final int[] ROW_BOTTOM = {36, 37, 38, 39, 40, 41, 42, 43, 44};

    /** 标签栏起始槽位（叠在红色背景上，按需显示） */
    public static final int TAB_BAR_START = 2;
    /** 标签栏最多占用的格数 */
    public static final int TAB_BAR_MAX = 5;

    /** 主题位：使用的机器（第三行第二列） */
    public static final int SLOT_SUBJECT = 19;
    /** 预览位：产物（第三行第八列） */
    public static final int SLOT_PREVIEW = 25;

    /**
     * 中间三行<b>不放任何装饰</b>。
     *
     * <p>早期版本这里画过灰色左右边框与绿色框，后来统一去掉——
     * 三个标签的内容形状差别太大，任何固定装饰都只对其中一个合适。</p>
     */
    public static final int[] DECORATION_SLOTS = {};

    /** 3x3 九宫格（第二三四行的四五六列） */
    public static final int[] SLOT_RECIPE_GRID = {12, 13, 14, 21, 22, 23, 30, 31, 32};

    /** 「结构」标签：竖直示意列（九宫格的中间一列，从下到上） */
    public static final int[] SLOT_STRUCTURE_COLUMN = {31, 22, 13};

    /** 配方翻页（主题位与预览位的正下方） */
    public static final int SLOT_PREV_RECIPE = 28;
    public static final int SLOT_NEXT_RECIPE = 34;

    /** 最后一行：返回 / 关闭 */
    public static final int SLOT_BACK = 36;
    public static final int SLOT_CLOSE = 44;
    /** 列表页：上一页 / 页码 / 下一页 */
    public static final int SLOT_PREV_PAGE = 38;
    public static final int SLOT_INFO = 40;
    public static final int SLOT_NEXT_PAGE = 42;

    /**
     * 最后一行的「当前物品」位。
     *
     * <p>直接放物品本身，而不是一行 ID 文字——玩家扫一眼就知道在看什么。</p>
     */
    public static final int SLOT_CURRENT_ITEM = 40;
    /** 「用途」标签的翻页按钮（当前物品位的左右） */
    public static final int SLOT_USAGE_PREV = 38;
    public static final int SLOT_USAGE_NEXT = 42;

    /**
     * 「用途」列表可用的槽位：中间三行整块（27 格）。
     *
     * <p>用途页不需要九宫格，也不需要绿框与主题位/预览位——那些是
     * 「合成方式」标签的元素。所以中间三行全部拿来展示，只在第一行
     * （标签）与最后一行（按钮 + 当前物品）让位。</p>
     */
    public static final int[] USAGE_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };

    /** 「用途」每页显示多少条 */
    public static final int USAGE_PAGE_SIZE = USAGE_SLOTS.length;

    /** 列表页内容区（中间三行 = 27 格） */
    public static final int LIST_PAGE_SIZE = 27;
    /** 列表页内容起始槽位（第二行第一个） */
    public static final int LIST_START = 9;

    /** 主页面大类图标的槽位（左右各留一列，视觉居中） */
    private static final int[] MAIN_SLOTS = {19, 21, 23, 25};

    private static final int INVENTORY_SIZE = 45;

    private Inventory inventory;

    // ---- 导航状态 ----
    private GuideSection section;
    private int page;
    private String itemId;
    private GuideTab tab;
    private int recipeIndex;
    /** 「用途」标签的当前页。 */
    private int usagePage;

    /** 当前物品实际可用的标签（按顺序）。 */
    private final List<GuideTab> availableTabs = new ArrayList<>();

    // ==================================================================
    // ① 主页面
    // ==================================================================

    public void openMain(Player player) {
        this.section = null;
        this.itemId = null;
        this.page = 0;
        this.recipeIndex = 0;
        this.tab = null;
        availableTabs.clear();

        inventory = create(Text.mm("<dark_gray>图鉴 <gray>» <white>HigherRedStoneFun"));
        fillBackground();

        GuideSection[] sections = GuideSection.values();
        for (int i = 0; i < sections.length && i < MAIN_SLOTS.length; i++) {
            GuideSection s = sections[i];
            inventory.setItem(MAIN_SLOTS[i], simple(s.icon(),
                    "<white>" + s.displayName(),
                    s.description(),
                    "",
                    "<dark_gray>物品数: <white>" + itemsOf(s).size(),
                    "<yellow>点击查看"));
        }

        inventory.setItem(SLOT_INFO, simple(Material.BOOK,
                "<gold>HigherRedStoneFun 图鉴",
                "<gray>基础机器 · 高级电力机器 · 基因工程",
                "",
                "<yellow>左键 <gray>物品查看详情"));
        inventory.setItem(SLOT_CLOSE, closeItem());
        player.openInventory(inventory);
    }

    // ==================================================================
    // ② 列表页
    // ==================================================================

    public void openList(Player player, GuideSection section, int page) {
        this.section = section;
        this.itemId = null;
        this.recipeIndex = 0;
        this.tab = null;
        availableTabs.clear();

        List<HrfItem> items = itemsOf(section);
        int pages = Math.max(1, (items.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE);
        this.page = Math.max(0, Math.min(page, pages - 1));

        inventory = create(Text.mm("<dark_gray>图鉴 <gray>» <white>" + section.displayName()));
        fillBackground();

        int start = this.page * LIST_PAGE_SIZE;
        for (int i = 0; i < LIST_PAGE_SIZE; i++) {
            int index = start + i;
            if (index >= items.size()) {
                break;
            }
            // 内容从第二行开始（第一行是红色标题栏）
            inventory.setItem(LIST_START + i, buildEntry(items.get(index)));
        }

        inventory.setItem(SLOT_BACK, simple(Material.ARROW, "<yellow>返回大类", "<gray>回到图鉴首页"));
        inventory.setItem(SLOT_INFO, simple(Material.PAPER,
                "<gold>" + section.displayName(),
                "<dark_gray>第 <white>" + (this.page + 1) + "<dark_gray> / <white>" + pages
                        + " <dark_gray>页",
                "<dark_gray>共 <white>" + items.size() + " <dark_gray>个物品"));
        if (pages > 1) {
            if (this.page > 0) {
                inventory.setItem(SLOT_PREV_PAGE, simple(Material.SPECTRAL_ARROW,
                        "<yellow>← 上一页", "<gray>第 " + this.page + " 页"));
            }
            if (this.page < pages - 1) {
                inventory.setItem(SLOT_NEXT_PAGE, simple(Material.SPECTRAL_ARROW,
                        "<yellow>下一页 →", "<gray>第 " + (this.page + 2) + " 页"));
            }
        }
        inventory.setItem(SLOT_CLOSE, closeItem());
        player.openInventory(inventory);
    }

    // ==================================================================
    // ③ 详情页（标签式）
    // ==================================================================

    public void openItem(Player player, GuideSection section, int page, String itemId,
                         @Nullable GuideTab wanted, int recipeIndex) {
        // 换物品时重置「用途」翻页，避免带着上一件物品的页码
        if (!itemId.equals(this.itemId)) {
            this.usagePage = 0;
        }
        this.section = section;
        this.page = Math.max(0, page);
        this.itemId = itemId;
        this.recipeIndex = Math.max(0, recipeIndex);

        HrfItem item = ItemRegistry.get().get(itemId);
        String title = item == null ? itemId : item.plainName();
        inventory = create(Text.mm("<dark_gray>详情 <gray>» <white>" + title));
        fillDetailFrame();

        // ---- 计算可用的标签（按需显示）----
        //
        // 顺序遵循「拿到 → 搭好 → 使用」：
        //   合成方式（怎么获得）→ 结构（怎么搭起来）→ 用途（能做什么）
        // 结构是前提、用途是结果，所以结构排在用途前面。
        availableTabs.clear();
        if (hasRecipeTab(itemId)) {
            availableTabs.add(GuideTab.RECIPE);
        }
        if (hasStructureTab(itemId)) {
            availableTabs.add(GuideTab.STRUCTURE);
        }
        if (hasUsageTab(itemId)) {
            availableTabs.add(GuideTab.USAGE);
        }
        if (availableTabs.isEmpty()) {
            availableTabs.add(GuideTab.USAGE);   // 兜底：至少一个标签，避免空标签栏
        }
        this.tab = (wanted != null && availableTabs.contains(wanted)) ? wanted : availableTabs.get(0);

        // ---- 标签栏（叠在红色背景上）----
        renderTabBar();

        // ---- 内容区 ----
        switch (this.tab) {
            case RECIPE -> renderRecipeTab(itemId);
            case USAGE -> renderUsageTab(itemId);
            case STRUCTURE -> renderStructureTab(itemId);
        }

        // ---- 最后一行：返回 / 翻页 / 当前物品 / 关闭 ----
        inventory.setItem(SLOT_BACK, simple(Material.ARROW, "<yellow>返回列表",
                "<gray>回到 " + (section == null ? "图鉴" : section.displayName())));
        renderCurrentItem(itemId);
        inventory.setItem(SLOT_CLOSE, closeItem());
        player.openInventory(inventory);
    }

    /**
     * 最后一行的「当前物品」位。
     *
     * <p>直接显示物品本身而不是一行 ID 文字——玩家扫一眼就知道在看什么。
     * ID 与当前标签写进它的 lore。</p>
     */
    private void renderCurrentItem(String itemId) {
        ItemStack stack = ItemRegistry.get().create(itemId, 1);
        HrfItem item = ItemRegistry.get().get(itemId);
        List<String> lore = new ArrayList<>();
        if (item != null) {
            lore.addAll(item.lore());
        }
        lore.add("");
        lore.add("<dark_gray>ID: <white>" + itemId);
        lore.add("<dark_gray>当前标签: <white>" + this.tab.displayName());
        appendLore(stack, lore);
        inventory.setItem(SLOT_CURRENT_ITEM, stack);
    }

    // ------------------------------------------------------------------
    // 详情页 · 标签栏
    // ------------------------------------------------------------------

    /** 标签栏：按需显示，当前标签高亮。 */
    private void renderTabBar() {
        for (int i = 0; i < availableTabs.size() && i < TAB_BAR_MAX; i++) {
            GuideTab t = availableTabs.get(i);
            boolean current = t == this.tab;
            ItemStack icon = simple(tabIcon(t),
                    (current ? "<green>▶ " : "<gray>") + t.displayName(),
                    t.description(),
                    "",
                    current ? "<green>当前标签" : "<yellow>点击切换");
            if (current) {
                ItemMeta meta = icon.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                icon.setItemMeta(meta);
            }
            inventory.setItem(TAB_BAR_START + i, icon);
        }
    }

    private static Material tabIcon(GuideTab tab) {
        return switch (tab) {
            case RECIPE -> Material.CRAFTING_TABLE;
            case USAGE -> Material.OAK_SIGN;
            case STRUCTURE -> Material.IRON_BLOCK;
        };
    }

    // ------------------------------------------------------------------
    // 详情页 · 标签内容
    // ------------------------------------------------------------------

    /**
     * 九宫格区域保持<b>空</b>。
     *
     * <p>早期版本这里铺了一层白色玻璃当底，看起来像「9 个被占用的格子」。
     * 实际上九宫格应该是留空的——材料放进去就是 3x3，空着就是空着。</p>
     */
    private void renderEmptyGrid() {
        for (int slot : SLOT_RECIPE_GRID) {
            inventory.setItem(slot, null);
        }
    }

    /** 「合成方式」：机器配方画成 3x3 九宫格；原版配方显示文字说明。 */
    private void renderRecipeTab(String itemId) {
        renderEmptyGrid();
        List<MachineRecipe> recipes = recipesProducing(itemId);

        if (recipes.isEmpty()) {
            // 引导物品：用原版工作台制作，没有机器网格
            List<String> hints = BootstrapRecipes.vanillaHint(itemId);
            inventory.setItem(SLOT_SUBJECT, simple(Material.CRAFTING_TABLE,
                    "<white>原版工作台",
                    "<dark_gray>不需要任何机器"));
            renderPreview(ItemRegistry.get().create(itemId, 1), null);

            List<String> lore = new ArrayList<>(hints);
            lore.add("");
            lore.add("<green>这是科技树的起点");
            lore.add("<dark_gray>结构里全是原版方块，所以要写原版名字");
            inventory.setItem(SLOT_SUBJECT, simple(Material.OAK_SIGN, "<gold>获取方式",
                    lore.toArray(new String[0])));
            return;
        }

        this.recipeIndex = Math.min(this.recipeIndex, recipes.size() - 1);
        renderRecipe(recipes.get(this.recipeIndex), this.recipeIndex + 1, recipes.size());
    }

    /** 把产物放到预览位（绿色框内）。 */
    private void renderPreview(ItemStack display, @Nullable RecipeOutput output) {
        List<String> lore = new ArrayList<>();
        if (output != null) {
            if (output.chance() < 1.0D) {
                lore.add("<yellow>概率产出 <white>" + Math.round(output.chance() * 100) + "%");
            } else {
                lore.add("<green>必定产出");
            }
        }
        lore.add("<dark_gray>产物预览");
        if (ItemRegistry.get().isCustom(display)) {
            lore.add("<yellow>左键查看该产物");
        }
        appendLore(display, lore);
        inventory.setItem(SLOT_PREVIEW, display);
    }

    private void renderRecipe(MachineRecipe recipe, int position, int total) {
        RecipeType type = recipe.type();
        MachineDefinition machine = machineOfType(type);
        ItemStack machineIcon = machine != null
                ? MachineRegistry.get().createItem(machine.id(), 1)
                : simple(type.icon(), "<white>" + type.displayName(), "<dark_gray>机器类型");
        List<String> machineLore = new ArrayList<>();
        machineLore.add("<dark_gray>使用机器: <white>" + type.displayName());
        machineLore.add(type.electric() ? "<dark_gray>需要电力" : "<dark_gray>燃料驱动");
        machineLore.add("<dark_gray>耗时 <white>"
                + String.format("%.1f", recipe.durationTicks() / 20.0D) + " <dark_gray>秒");
        if (type.electric()) {
            machineLore.add("<dark_gray>耗电 <red>"
                    + Text.number(recipe.energyCost()) + " <dark_gray>J");
        }
        if (total > 1) {
            machineLore.add("");
            machineLore.add("<gray>该物品有 <white>" + total + " <gray>种合成方式");
            machineLore.add("<dark_gray>用左右箭头切换");
        }
        appendLore(machineIcon, machineLore);
        inventory.setItem(SLOT_SUBJECT, machineIcon);

        // 3x3 九宫格（材料按顺序填入，位置不代表必须摆放的位置）
        List<ItemStack> inputs = recipe.inputs();
        for (int i = 0; i < SLOT_RECIPE_GRID.length && i < inputs.size(); i++) {
            ItemStack stack = inputs.get(i).clone();
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>需要 <white>x" + stack.getAmount());
            lore.add("<gray>放哪个槽位都可以");
            if (ItemRegistry.get().isCustom(stack)) {
                lore.add("<yellow>左键查看该材料");
            }
            appendLore(stack, lore);
            inventory.setItem(SLOT_RECIPE_GRID[i], stack);
        }

        // 产物预览（主产物放在绿色框内）
        List<RecipeOutput> outputs = recipe.outputs();
        if (!outputs.isEmpty()) {
            renderPreview(outputs.get(0).stack(), outputs.get(0));
        }
        // 副产物（离心机这类多产物机器）放九宫格右侧的空白位
        if (outputs.size() > 1) {
            int[] extraSlots = {15, 16, 17};
            for (int i = 1; i < outputs.size() && i - 1 < extraSlots.length; i++) {
                RecipeOutput out = outputs.get(i);
                ItemStack display = out.stack();
                List<String> lore = new ArrayList<>();
                lore.add("<dark_gray>副产物");
                lore.add(out.chance() < 1.0D
                        ? "<yellow>概率 <white>" + Math.round(out.chance() * 100) + "%"
                        : "<green>必定产出");
                appendLore(display, lore);
                inventory.setItem(extraSlots[i - 1], display);
            }
        }

        if (total > 1) {
            if (position > 1) {
                inventory.setItem(SLOT_PREV_RECIPE, simple(Material.SPECTRAL_ARROW,
                        "<yellow>← 上一种方式", "<gray>" + (position - 1) + " / " + total));
            }
            if (position < total) {
                inventory.setItem(SLOT_NEXT_RECIPE, simple(Material.SPECTRAL_ARROW,
                        "<yellow>下一种方式 →", "<gray>" + (position + 1) + " / " + total));
            }
        }
    }

    /**
     * 「用途」标签。
     *
     * <p>对<b>机器</b>而言是「它能做哪些配方」；对<b>材料</b>而言是
     * 「哪些配方会消耗它」。两种情况展示的都是产物图标——玩家想知道的是
     * 「我能得到什么」，点一下就能跳到那个产物的详情。</p>
     */
    private void renderUsageTab(String itemId) {
        List<ItemStack> entries = usageEntries(itemId);

        if (entries.isEmpty()) {
            inventory.setItem(22, simple(Material.BARRIER, "<red>暂无用途",
                    "<gray>该物品目前没有被任何配方使用"));
            return;
        }

        int pages = Math.max(1, (entries.size() + USAGE_PAGE_SIZE - 1) / USAGE_PAGE_SIZE);
        this.usagePage = Math.max(0, Math.min(this.usagePage, pages - 1));
        int start = this.usagePage * USAGE_PAGE_SIZE;

        // 第 2~5 行整块用于展示，只跳过绿框与主题位/预览位
        for (int i = 0; i < USAGE_SLOTS.length; i++) {
            int index = start + i;
            if (index >= entries.size()) {
                break;
            }
            inventory.setItem(USAGE_SLOTS[i], entries.get(index));
        }

        // 翻页按钮放在最后一行的当前物品位左右
        if (pages > 1) {
            if (this.usagePage > 0) {
                inventory.setItem(SLOT_USAGE_PREV, simple(Material.SPECTRAL_ARROW,
                        "<yellow>← 上一页", "<gray>第 " + this.usagePage + " / " + pages + " 页"));
            }
            if (this.usagePage < pages - 1) {
                inventory.setItem(SLOT_USAGE_NEXT, simple(Material.SPECTRAL_ARROW,
                        "<yellow>下一页 →", "<gray>第 " + (this.usagePage + 2) + " / " + pages + " 页"));
            }
        }
    }

    /** 「用途」翻页。由监听器在点击左右箭头时调用。 */
    public void turnUsagePage(Player player, boolean forward) {
        int entries = usageEntries(this.itemId).size();
        int pages = Math.max(1, (entries + USAGE_PAGE_SIZE - 1) / USAGE_PAGE_SIZE);
        int target = this.usagePage + (forward ? 1 : -1);
        if (target < 0 || target >= pages) {
            return;
        }
        this.usagePage = target;
        openItem(player, this.section, this.page, this.itemId, GuideTab.USAGE, 0);
    }

    /** 「结构」标签：把多方块结构竖着画出来。 */
    private void renderStructureTab(String itemId) {
        MachineDefinition definition = MachineRegistry.get().get(itemId);
        if (definition == null || !definition.isMultiblock()) {
            inventory.setItem(22, simple(Material.BARRIER, "<red>不是多方块机器"));
            return;
        }
        StructurePattern pattern = definition.structure();
        renderEmptyGrid();

        // 结构里全是【原版方块】，所以每一格都必须显示原版方块的名字。
        // 早期版本把控制器那一格写成 definition.displayName()（例如
        // 「增强工作台」），但那一格实际就是普通工作台——会误导玩家。
        String controllerBlock = com.koole.higherRedStoneFun.core.Materials
                .blockName(definition.icon());

        List<ItemStack> column = new ArrayList<>();
        column.add(simple(definition.icon(),
                "<white>" + controllerBlock,
                "<yellow>控制器",
                "<gray>这一格就是你放下去的方块",
                "<gray>右键它打开界面"));
        for (StructurePattern.Part part : pattern.parts()) {
            column.add(simple(part.display(),
                    "<white>" + part.label(),
                    "<dark_gray>" + part.offsetText()));
        }
        for (int i = 0; i < SLOT_STRUCTURE_COLUMN.length && i < column.size(); i++) {
            inventory.setItem(SLOT_STRUCTURE_COLUMN[i], column.get(i));
        }

        // 说明牌放在第三行第二列（也就是「合成方式」标签里主题位的位置）
        List<String> lore = new ArrayList<>();
        lore.add("<gray>从下往上依次叠放：");
        lore.add("");
        lore.add("<white>1. " + controllerBlock + " <dark_gray>(控制器)");
        int step = 2;
        for (StructurePattern.Part part : pattern.parts()) {
            lore.add("<white>" + step + ". " + part.label()
                    + " <dark_gray>(" + part.offsetText() + ")");
            step++;
        }
        lore.add("");
        lore.add("<green>搭好后自动成型，无需合成");
        inventory.setItem(SLOT_SUBJECT, simple(Material.OAK_SIGN, "<gold>搭建步骤",
                lore.toArray(new String[0])));

        // 预览位显示当前机器（悬挂展示）
        renderPreview(ItemRegistry.get().create(itemId, 1), null);
    }

    // ------------------------------------------------------------------
    // 标签可用性
    // ------------------------------------------------------------------

    private boolean hasRecipeTab(String itemId) {
        return !recipesProducing(itemId).isEmpty() || BootstrapRecipes.isVanillaOnly(itemId);
    }

    private boolean hasUsageTab(String itemId) {
        MachineDefinition definition = MachineRegistry.get().get(itemId);
        if (definition != null && definition.hasRecipes()) {
            return RecipeRegistry.get().size(definition.recipeType()) > 0;
        }
        return !usedInRecipes(itemId).isEmpty();
    }

    private boolean hasStructureTab(String itemId) {
        MachineDefinition definition = MachineRegistry.get().get(itemId);
        return definition != null && definition.isMultiblock();
    }

    /** 供监听器使用：某个槽位对应哪个标签，不是标签槽则返回 null。 */
    @Nullable
    public GuideTab tabAt(int slot) {
        int index = slot - TAB_BAR_START;
        if (index < 0 || index >= availableTabs.size() || index >= TAB_BAR_MAX) {
            return null;
        }
        return availableTabs.get(index);
    }

    // ------------------------------------------------------------------
    // 数据查询
    // ------------------------------------------------------------------

    /** 某个大类下的全部物品（按注册顺序，机器在前）。 */
    public List<HrfItem> itemsOf(GuideSection section) {
        List<HrfItem> out = new ArrayList<>();
        for (com.koole.higherRedStoneFun.core.ItemGroup group : section.groups()) {
            out.addAll(ItemRegistry.get().byGroup(group));
        }
        return out;
    }

    /**
     * 找出所有「产出该物品」的配方。
     *
     * <p>注意这里查的是<b>产物</b>，而不是「该物品所属机器的全部配方」。</p>
     */
    public static List<MachineRecipe> recipesProducing(String itemId) {
        List<MachineRecipe> out = new ArrayList<>();
        for (RecipeType type : RecipeType.ALL) {
            for (MachineRecipe recipe : RecipeRegistry.get().of(type)) {
                for (RecipeOutput output : recipe.outputs()) {
                    if (ItemRegistry.get().is(output.stack(), itemId)) {
                        out.add(recipe);
                        break;
                    }
                }
            }
        }
        return out;
    }

    /** 找出所有「消耗该物品」的配方。 */
    public static List<MachineRecipe> usedInRecipes(String itemId) {
        List<MachineRecipe> out = new ArrayList<>();
        for (RecipeType type : RecipeType.ALL) {
            for (MachineRecipe recipe : RecipeRegistry.get().of(type)) {
                for (ItemStack input : recipe.inputs()) {
                    if (ItemRegistry.get().is(input, itemId)) {
                        out.add(recipe);
                        break;
                    }
                }
            }
        }
        return out;
    }

    /**
     * 「用途」标签要展示的条目。
     *
     * <p>机器 -> 它能处理的配方产物；材料 -> 用到它的配方产物。</p>
     */
    private List<ItemStack> usageEntries(String itemId) {
        List<ItemStack> out = new ArrayList<>();
        MachineDefinition definition = MachineRegistry.get().get(itemId);

        if (definition != null && definition.hasRecipes()) {
            // 机器：列出它能做的配方
            for (MachineRecipe recipe : RecipeRegistry.get().of(definition.recipeType())) {
                if (recipe.outputs().isEmpty()) {
                    continue;
                }
                ItemStack display = recipe.outputs().get(0).stack();
                appendLore(display, List.of(
                        "<dark_gray>由 <white>" + definition.displayName() + " <dark_gray>产出",
                        "<gray>耗时 " + String.format("%.1f", recipe.durationTicks() / 20.0D) + " 秒"));
                out.add(display);
            }
            return out;
        }

        // 材料：列出用到它的配方
        for (MachineRecipe recipe : usedInRecipes(itemId)) {
            if (recipe.outputs().isEmpty()) {
                continue;
            }
            ItemStack display = recipe.outputs().get(0).stack();
            appendLore(display, List.of(
                    "<dark_gray>使用机器: <white>" + recipe.type().displayName(),
                    "<gray>点击查看该产物的详情"));
            out.add(display);
        }
        return out;
    }

    /** 找出使用某个配方类型的机器（详情页显示机器图标用）。 */
    private static MachineDefinition machineOfType(RecipeType type) {
        for (MachineDefinition definition : MachineRegistry.get().all()) {
            if (definition.hasRecipes() && definition.recipeType() == type) {
                return definition;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 物品构建
    // ------------------------------------------------------------------

    private ItemStack buildEntry(HrfItem item) {
        ItemStack stack = ItemRegistry.get().create(item.id(), 1);
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>ID: " + item.id());
        lore.add("");

        MachineDefinition definition = MachineRegistry.get().get(item.id());
        if (definition != null) {
            for (String line : definition.lore()) {
                lore.add(line);
            }
            lore.add("");
            if (definition.isMultiblock()) {
                lore.add("<yellow>多方块结构: <white>" + definition.structure().describe());
            }
            if (definition.energyRole() != null) {
                switch (definition.energyRole()) {
                    case GENERATOR -> lore.add("<gray>产能: <red>" + definition.throughput() + " J/t");
                    case CONSUMER -> lore.add("<gray>耗电: <red>" + definition.throughput() + " J/t");
                    case STORAGE -> lore.add("<gray>储电: <red>"
                            + Text.number(definition.bufferCapacity()) + " J");
                }
            }
            if (definition.usesFuel()) {
                lore.add("<gray>燃料: <gold>可燃物");
            }
            if (definition.hasRecipes()) {
                lore.add("<gray>配方面板: <yellow>" + definition.recipeType().displayName());
            }
        } else {
            lore.addAll(item.lore());
        }

        lore.add("");
        lore.add("<yellow>左键查看详情");
        appendLore(stack, lore);
        return stack;
    }

    private void appendLore(ItemStack stack, List<String> lines) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(line.isEmpty() ? net.kyori.adventure.text.Component.empty() : Text.mm(line));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
    }

    private static ItemStack simple(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.mm(name));
        if (lore.length > 0) {
            List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(line.isEmpty() ? net.kyori.adventure.text.Component.empty() : Text.mm(line));
            }
            meta.lore(lines);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack closeItem() {
        return simple(Material.BARRIER, "<red>关闭图鉴", "<gray>点击关闭");
    }

    private Inventory create(net.kyori.adventure.text.Component title) {
        return Bukkit.createInventory(this, INVENTORY_SIZE, title);
    }

    /**
     * 通用背景：清空内容，只画第一行（红）与最后一行（蓝）。
     *
     * <p>中间区域刻意不铺玻璃板——九宫格、物品列表都直接浮在上面，
     * 这样界面干净，也不会让「空槽位」看起来像「被占用的格子」。</p>
     */
    private void fillBackground() {
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inventory.setItem(i, null);
        }
        ItemStack red = pane(Material.RED_STAINED_GLASS_PANE);
        for (int slot : ROW_TOP) {
            inventory.setItem(slot, red);
        }
        ItemStack blue = pane(Material.BLUE_STAINED_GLASS_PANE);
        for (int slot : ROW_BOTTOM) {
            inventory.setItem(slot, blue);
        }
    }

    /**
     * 详情页的通用框架：只有第一行（红）与最后一行（蓝）。
     *
     * <p>中间三行<b>全部留空</b>——不放灰边框、不放绿框、也不铺背景。
     * 三个标签（合成方式 / 用途 / 结构）的中间区域因此完全一致：
     * 空着，只放当前标签自己的内容。</p>
     *
     * <p>这样做的理由：三个标签的内容形状差别很大（3x3 网格 / 物品列表 /
     * 竖直结构），任何固定的装饰框都只对其中一个合适，套到另外两个上
     * 反而显得杂乱。</p>
     */
    private void fillDetailFrame() {
        fillBackground();
    }

    private static ItemStack pane(Material material) {
        return simple(material, " ", "");
    }

    // ------------------------------------------------------------------
    // 访问器（监听器用）
    // ------------------------------------------------------------------

    public GuideSection currentSection() {
        return section;
    }

    public int currentPage() {
        return page;
    }

    public String currentItemId() {
        return itemId;
    }

    @Nullable
    public GuideTab currentTab() {
        return tab;
    }

    public int currentRecipeIndex() {
        return recipeIndex;
    }

    public List<GuideTab> availableTabs() {
        return java.util.Collections.unmodifiableList(availableTabs);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
