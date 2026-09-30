package com.koole.higherRedStoneFun.commands;

import com.koole.higherRedStoneFun.HigherRedStoneFun;
import com.koole.higherRedStoneFun.core.ItemGroup;
import com.koole.higherRedStoneFun.core.Text;
import com.koole.higherRedStoneFun.items.HrfItem;
import com.koole.higherRedStoneFun.items.ItemRegistry;
import com.koole.higherRedStoneFun.machines.MachineDefinition;
import com.koole.higherRedStoneFun.machines.MachineRegistry;
import com.koole.higherRedStoneFun.recipes.RecipeRegistry;
import com.koole.higherRedStoneFun.recipes.RecipeType;
import com.koole.higherRedStoneFun.ui.GuideMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /hrf 主命令。 */
public final class HrfCommand implements TabExecutor {

    private final HigherRedStoneFun plugin;

    public HrfCommand(HigherRedStoneFun plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> help(sender, label);
            case "guide" -> guide(sender);
            case "give" -> give(sender, args);
            case "energy" -> energy(sender);
            case "stats" -> stats(sender);
            case "genetics", "genes" -> genetics(sender);
            case "recipes" -> recipes(sender, args);
            case "selftest" -> selftest(sender);
            case "reload" -> reload(sender);
            default -> {
                sender.sendMessage(Text.prefixed("<red>未知子命令: <white>" + args[0]));
                help(sender, label);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------

    private void help(CommandSender sender, String label) {
        sender.sendMessage(Text.mm("<dark_gray><st>                                                  "));
        sender.sendMessage(Text.mm("<gradient:#ff6b6b:#ffd93d><bold>HigherRedStoneFun</bold></gradient> <gray>v"
                + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(Text.mm("<gray>三个模块: <white>基础机器</white> · <white>高级电力机器</white> · <white>基因工程"));
        sender.sendMessage(Text.mm(""));
        sender.sendMessage(Text.mm("<yellow>/" + label + " guide <dark_gray>- 打开图鉴，浏览全部机器与配方"));
        sender.sendMessage(Text.mm("<yellow>/" + label + " give <物品ID> [数量] <dark_gray>- 获取物品 (需要权限)"));
        sender.sendMessage(Text.mm("<yellow>/" + label + " energy <dark_gray>- 查看你附近的能源网络状态"));
        sender.sendMessage(Text.mm("<yellow>/" + label + " genetics <dark_gray>- 查看基因工程玩法说明"));
        sender.sendMessage(Text.mm("<yellow>/" + label + " recipes <类型> <dark_gray>- 列出某个配方类型的全部配方"));
        sender.sendMessage(Text.mm("<yellow>/" + label + " stats <dark_gray>- 服务器统计信息"));
        sender.sendMessage(Text.mm("<yellow>/" + label + " selftest <dark_gray>- 核心系统自检 (排障用)"));
        sender.sendMessage(Text.mm("<yellow>/" + label + " reload <dark_gray>- 重载配置 (需要权限)"));
        sender.sendMessage(Text.mm("<dark_gray><st>                                                  "));
    }

    private void guide(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.prefixed("<red>该命令只能由玩家使用"));
            return;
        }
        new GuideMenu().open(player, ItemGroup.BASIC_MACHINES);
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("hrf.command.give")) {
            sender.sendMessage(Text.prefixed("<red>你没有权限使用该命令"));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.prefixed("<red>该命令只能由玩家使用"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.prefixed("<red>用法: /hrf give <物品ID> [数量]"));
            listIds(player);
            return;
        }

        String id = args[1].toLowerCase(Locale.ROOT);
        if (!ItemRegistry.get().exists(id)) {
            player.sendMessage(Text.prefixed("<red>未知物品 ID: <white>" + id));
            listIds(player);
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(2304, Integer.parseInt(args[2])));
            } catch (NumberFormatException ex) {
                player.sendMessage(Text.prefixed("<red>数量必须是数字"));
                return;
            }
        }

        HrfItem item = ItemRegistry.get().require(id);
        int remaining = amount;
        int maxStack = item.material().getMaxStackSize();
        while (remaining > 0) {
            int give = Math.min(maxStack, remaining);
            remaining -= give;
            ItemStack stack = ItemRegistry.get().create(id, give);
            var leftover = player.getInventory().addItem(stack);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
        player.sendMessage(Text.prefixed("<green>已给予 <white>" + item.plainName()
                + " <gray>x" + amount));
    }

    private void listIds(Player player) {
        player.sendMessage(Text.mm("<gray>提示: 用 <yellow>/hrf guide</yellow> 打开图鉴查看全部物品"));
    }

    private void energy(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.prefixed("<gray>能源网络统计:"));
            for (String line : plugin.statistics()) {
                sender.sendMessage(Text.mm(line));
            }
            return;
        }

        var network = plugin.energy().networkForQuery(player.getLocation());
        if (network == null) {
            player.sendMessage(Text.prefixed("<red>你所在的位置没有接入能源网络"));
            player.sendMessage(Text.mm("<gray>提示: 用<white>铜块 / 避雷针 / 末地烛</white>作为导线连接机器"));
            return;
        }

        player.sendMessage(Text.mm("<dark_gray><st>                              "));
        player.sendMessage(Text.mm("<gold><bold>能源网络状态"));
        for (String line : network.describe()) {
            player.sendMessage(Text.mm(line));
        }
        player.sendMessage(Text.mm("<dark_gray><st>                              "));
    }

    private void stats(CommandSender sender) {
        sender.sendMessage(Text.mm("<dark_gray><st>                              "));
        sender.sendMessage(Text.mm("<gold><bold>HigherRedStoneFun 统计"));
        for (String line : plugin.statistics()) {
            sender.sendMessage(Text.mm(line));
        }
        sender.sendMessage(Text.mm("<gray>已加载区块中的机器: <white>" + plugin.machines().loadedCount()));
        sender.sendMessage(Text.mm("<dark_gray><st>                              "));
    }

    private void genetics(CommandSender sender) {
        sender.sendMessage(Text.mm("<dark_gray><st>                                                  "));
        sender.sendMessage(Text.mm("<green><bold>基因工程 · 玩法说明"));
        sender.sendMessage(Text.mm(""));
        sender.sendMessage(Text.mm("<yellow>第一步 · 采集"));
        sender.sendMessage(Text.mm("<gray>手持<white>空样本瓶</white>，<white>潜行 + 右键</white>成熟作物或动物"));
        sender.sendMessage(Text.mm("<gray>空样本瓶 = 3 玻璃，在增强工作台合成"));
        sender.sendMessage(Text.mm(""));
        sender.sendMessage(Text.mm("<yellow>第二步 · 测序"));
        sender.sendMessage(Text.mm("<gray>把<white>生物样本 + 基因引物</white>放进<white>基因测序仪</white>，解析出基因组"));
        sender.sendMessage(Text.mm(""));
        sender.sendMessage(Text.mm("<yellow>第三步 · 拼接（核心）"));
        sender.sendMessage(Text.mm("<gray>把<white>两个基因组 + 拼接酶</white>放进<white>基因拼接机</white>"));
        sender.sendMessage(Text.mm("<gray>每个基因位取两者较优值，并有概率突变 +1"));
        sender.sendMessage(Text.mm("<gray>不断重复，直到得到<gold>全满级完美基因组"));
        sender.sendMessage(Text.mm(""));
        sender.sendMessage(Text.mm("<yellow>第四步 · 注入"));
        sender.sendMessage(Text.mm("<gray><white>空白种子模板 + 植物基因组</white> -> 改良种子"));
        sender.sendMessage(Text.mm("<gray><white>空白胚胎 + 动物基因组</white> -> 可孵化的胚胎"));
        sender.sendMessage(Text.mm(""));
        sender.sendMessage(Text.mm("<yellow>第五步 · 收获"));
        sender.sendMessage(Text.mm("<gray>改良种子直接种在地里，基因会随作物保存"));
        sender.sendMessage(Text.mm("<gray>或用<white>基因培育舱</white>做全自动生产"));
        sender.sendMessage(Text.mm(""));
        sender.sendMessage(Text.mm("<aqua>四个基因位:"));
        for (com.koole.higherRedStoneFun.genetics.Gene gene : com.koole.higherRedStoneFun.genetics.Gene.values()) {
            sender.sendMessage(Text.mm("<gray> - <white>" + gene.displayName() + "</white> " + gene.description()));
        }
        sender.sendMessage(Text.mm("<dark_gray><st>                                                  "));
    }

    private void recipes(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Text.prefixed("<gray>可用的配方类型:"));
            for (RecipeType type : RecipeType.ALL) {
                sender.sendMessage(Text.mm("<yellow>" + type.name().toLowerCase(Locale.ROOT)
                        + " <dark_gray>(" + type.displayName() + ", "
                        + RecipeRegistry.get().size(type) + " 条)"));
            }
            return;
        }

        RecipeType type = null;
        String wanted = args[1].toUpperCase(Locale.ROOT);
        for (RecipeType candidate : RecipeType.ALL) {
            if (candidate.name().equals(wanted)) {
                type = candidate;
                break;
            }
        }
        if (type == null) {
            sender.sendMessage(Text.prefixed("<red>未知配方类型: <white>" + args[1]));
            return;
        }

        var recipes = RecipeRegistry.get().of(type);
        sender.sendMessage(Text.mm("<gold><bold>" + type.displayName()
                + " <gray>共 " + recipes.size() + " 条配方"));
        for (var recipe : recipes) {
            StringBuilder sb = new StringBuilder("<gray> - ");
            for (ItemStack input : recipe.inputs()) {
                sb.append(describe(input)).append(" <dark_gray>+ ");
            }
            sb.setLength(sb.length() - " <dark_gray>+ ".length());
            sb.append(" <dark_gray>=> ");
            for (var output : recipe.outputs()) {
                sb.append(describe(output.stack()));
                if (output.chance() < 1.0D) {
                    sb.append("<dark_gray>(").append((int) (output.chance() * 100)).append("%)");
                }
                sb.append(" ");
            }
            if (type.electric()) {
                sb.append("<dark_gray>[").append(Text.number(recipe.energyCost())).append(" J]");
            }
            sender.sendMessage(Text.mm(sb.toString()));
        }
    }

    private static String describe(ItemStack stack) {
        String id = ItemRegistry.get().idOf(stack);
        if (id != null) {
            HrfItem item = ItemRegistry.get().get(id);
            String name = item == null ? id : item.plainName();
            return "<white>" + name + (stack.getAmount() > 1 ? " x" + stack.getAmount() : "");
        }
        return "<white>" + stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + (stack.getAmount() > 1 ? " x" + stack.getAmount() : "");
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("hrf.command.reload")) {
            sender.sendMessage(Text.prefixed("<red>你没有权限使用该命令"));
            return;
        }
        plugin.reloadAll();
        sender.sendMessage(Text.prefixed("<green>配置已重载"));
    }

    /** 运行核心系统自检（服主排障用）。 */
    private void selftest(CommandSender sender) {
        if (!sender.hasPermission("hrf.command.reload")) {
            sender.sendMessage(Text.prefixed("<red>你没有权限使用该命令"));
            return;
        }
        new com.koole.higherRedStoneFun.commands.SelfTest(plugin).run(sender);
    }

    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("help", "guide", "give", "energy", "genetics",
                    "recipes", "stats", "selftest", "reload")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
            return out;
        }

        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (args[0].equalsIgnoreCase("give")) {
                for (HrfItem item : ItemRegistry.get().all()) {
                    if (item.id().startsWith(prefix)) {
                        out.add(item.id());
                    }
                }
            } else if (args[0].equalsIgnoreCase("recipes")) {
                for (RecipeType type : RecipeType.ALL) {
                    String name = type.name().toLowerCase(Locale.ROOT);
                    if (name.startsWith(prefix)) {
                        out.add(name);
                    }
                }
            }
            return out;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return List.of("1", "8", "16", "64");
        }

        return out;
    }

    /** 供图鉴使用：列出某个分类下的机器。 */
    public static List<MachineDefinition> machinesOf(ItemGroup group) {
        return MachineRegistry.get().byGroup(group);
    }
}
