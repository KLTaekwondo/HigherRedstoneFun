package com.koole.higherRedStoneFun.listeners;

import com.koole.higherRedStoneFun.HigherRedStoneFun;
import com.koole.higherRedStoneFun.core.Text;
import com.koole.higherRedStoneFun.genetics.CropMapping;
import com.koole.higherRedStoneFun.genetics.Genome;
import com.koole.higherRedStoneFun.genetics.GeneticsManager;
import com.koole.higherRedStoneFun.items.ItemRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 作物基因玩法。
 *
 * <p>这一层负责把基因数据真正作用到世界中的作物上：</p>
 *
 * <ul>
 *   <li><b>采样</b>：潜行 + 手上拿空样本瓶，右键成熟作物获得生物样本，
 *       样本会记录该植株当前的基因（野生植株是随机基因）。</li>
 *   <li><b>种植</b>：拿带基因的改良种子右键耕地，基因会绑定到该坐标的作物上。</li>
 *   <li><b>生长加速</b>：带生长基因的作物的随机生长刻会被额外加速。</li>
 *   <li><b>产量加成</b>：收获时按产量基因多掉额外产物。</li>
 *   <li><b>抗逆</b>：抗逆满级时作物不会被踩踏破坏。</li>
 * </ul>
 */
public final class CropListener implements Listener {

    /** 生长基因带来的额外生长尝试次数上限。 */
    private static final int MAX_EXTRA_GROWTH = 5;

    private final HigherRedStoneFun plugin;
    private final GeneticsManager genetics;

    public CropListener(HigherRedStoneFun plugin, GeneticsManager genetics) {
        this.plugin = plugin;
        this.genetics = genetics;
    }

    // ------------------------------------------------------------------
    // 采样
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSample(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        ItemStack hand = event.getItem();
        if (hand == null || !ItemRegistry.get().is(hand, "hrf_empty_sample")) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        CropMapping.Crop crop = CropMapping.byBlock(block.getType());
        if (crop == null) {
            return;
        }

        event.setCancelled(true);

        if (!player.hasPermission("hrf.genetics.use")) {
            player.sendMessage(Text.prefixed("<red>你没有使用基因设备的权限"));
            return;
        }

        // 是否成熟影响采样质量：成熟植株才能采到完整基因
        boolean mature = CropMapping.isMature(block);
        Genome genome = genetics.getCropGenome(block.getLocation());
        if (genome == null) {
            // 野生植株：生成一个随机基因组并绑定，让野生资源也有价值
            double quality = mature ? 0.35D : 0.12D;
            genome = genetics.randomGenome(quality);
            genetics.setCropGenome(block.getLocation(), genome);
        }

        ItemStack sample = ItemRegistry.get().create(crop.sampleItemId(), 1);
        genetics.applyGenome(sample, genome);
        genetics.markSample(sample, GeneticsManager.SampleKind.SEED, crop.sampleItemId());

        // 消耗一个空样本瓶
        ItemStack bottles = hand.clone();
        bottles.setAmount(bottles.getAmount() - 1);
        player.getInventory().setItemInMainHand(bottles.getAmount() <= 0 ? null : bottles);

        // 给玩家采集到的样本
        var leftover = player.getInventory().addItem(sample);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }

        player.sendMessage(Text.prefixed("<green>已采集样本 <dark_gray>| <gray>基因: <white>"
                + genome.shortSummary() + (mature ? "" : " <dark_gray>(未成熟，基因不完整)")));
    }

    // ------------------------------------------------------------------
    // 种植
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlant(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack hand = event.getItem();
        String id = ItemRegistry.get().idOf(hand);
        if (id == null) {
            return;
        }

        CropMapping.Crop crop = CropMapping.bySeed(id);
        if (crop == null) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        // 判断是否点在可种植的方块上
        if (!isPlantable(clicked, crop)) {
            return;
        }

        Genome genome = genetics.genomeOf(hand);
        if (genome == null) {
            return;
        }

        Location target = clicked.getLocation().add(0, 1, 0);
        if (crop.plantBlock() == Material.NETHER_WART) {
            target = clicked.getLocation().add(0, 1, 0);
        } else if (crop.plantBlock() == Material.SWEET_BERRY_BUSH) {
            target = clicked.getLocation().add(0, 1, 0);
        }

        // 记录目标位置，等方块真正放下去后再绑定基因
        plugin.pendingPlant().put(event.getPlayer().getUniqueId(), new PendingPlant(target, genome, crop));
    }

    private static boolean isPlantable(Block clicked, CropMapping.Crop crop) {
        Material type = clicked.getType();
        return switch (crop.plantBlock()) {
            case NETHER_WART -> type == Material.SOUL_SAND;
            case SWEET_BERRY_BUSH -> type == Material.GRASS_BLOCK
                    || type == Material.DIRT || type == Material.COARSE_DIRT
                    || type == Material.PODZOL || type == Material.ROOTED_DIRT
                    || type == Material.MOSS_BLOCK || type == Material.FARMLAND;
            default -> type == Material.FARMLAND;
        };
    }

    /** 待绑定基因的种植记录。 */
    public record PendingPlant(Location location, Genome genome, CropMapping.Crop crop) {
    }

    /**
     * 方块真正被放置后绑定基因。
     *
     * <p>原版在 PlayerInteractEvent 之后才真正放置方块，因此这里用
     * BlockPlaceEvent 确认落地位置再写基因，避免「种在错误坐标」。</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaced(org.bukkit.event.block.BlockPlaceEvent event) {
        PendingPlant pending = plugin.pendingPlant().remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        Block placed = event.getBlockPlaced();
        // 原版作物可能把方块放在相邻格（例如西瓜/南瓜茎），这里做一次容错
        Location loc = placed.getLocation();
        if (CropMapping.byBlock(placed.getType()) == null) {
            loc = pending.location();
        }
        genetics.setCropGenome(loc, pending.genome());
    }

    // ------------------------------------------------------------------
    // 生长加速
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        CropMapping.Crop crop = CropMapping.byBlock(block.getType());
        if (crop == null) {
            return;
        }
        Genome genome = genetics.getCropGenome(block.getLocation());
        if (genome == null) {
            return;
        }

        int growthLevel = genome.level(com.koole.higherRedStoneFun.genetics.Gene.GROWTH);
        if (growthLevel <= 0) {
            return;
        }

        // 额外推进生长阶段（等价于「长得更快」）
        int extra = Math.min(MAX_EXTRA_GROWTH, growthLevel);
        if (!(block.getBlockData() instanceof Ageable ageable)) {
            return;
        }
        int next = Math.min(ageable.getMaximumAge(), ageable.getAge() + extra);
        if (next != ageable.getAge()) {
            ageable.setAge(next);
            block.setBlockData(ageable, true);
        }
    }

    // ------------------------------------------------------------------
    // 收获
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHarvest(BlockBreakEvent event) {
        Block block = event.getBlock();
        CropMapping.Crop crop = CropMapping.byBlock(block.getType());
        if (crop == null) {
            return;
        }

        Location loc = block.getLocation();
        Genome genome = genetics.getCropGenome(loc);
        if (genome == null) {
            return;
        }

        // 先清掉基因记录，避免同一位置重复触发
        genetics.clearCropGenome(loc);

        // 只有成熟收获才有产量加成
        boolean mature = CropMapping.isMature(block);
        if (!mature) {
            return;
        }

        double multiplier = genome.yieldMultiplier();
        if (multiplier <= 1.0D) {
            return;
        }

        int baseAmount = crop.baseYield();
        int bonus = (int) Math.round(baseAmount * (multiplier - 1.0D));
        if (bonus <= 0) {
            return;
        }

        // 用玩家手持工具模拟原版掉落，然后额外补上基因加成的部分
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        List<ItemStack> drops = new java.util.ArrayList<>(block.getDrops(tool, event.getPlayer()));

        ItemStack extra = new ItemStack(crop.produce(), bonus);
        boolean merged = false;
        for (int i = 0; i < drops.size(); i++) {
            ItemStack drop = drops.get(i);
            if (drop.getType() == extra.getType()) {
                ItemStack combined = drop.clone();
                combined.setAmount(drop.getAmount() + bonus);
                drops.set(i, combined);
                merged = true;
                break;
            }
        }
        if (!merged) {
            drops.add(extra);
        }

        // 取消原版掉落，改由我们掉落计算后的结果
        event.setDropItems(false);
        for (ItemStack drop : drops) {
            if (drop != null && !drop.getType().isAir()) {
                block.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), drop);
            }
        }

        // 有活力的作物有概率自动补种
        double regrow = genome.regrowChance();
        if (regrow > 0.0D && ThreadLocalRandom.current().nextDouble() < regrow) {
            replant(block, crop, genome);
        }

        Player player = event.getPlayer();
        player.sendMessage(Text.prefixed("<green>收获加成 <dark_gray>| <gray>基因 <white>"
                + genome.shortSummary() + " <gray>额外 <white>+" + bonus + " " + crop.produce()));
    }

    /** 自动补种：把作物重新种回原位。 */
    private void replant(Block block, CropMapping.Crop crop, Genome genome) {
        block.setType(crop.plantBlock(), true);
        if (block.getBlockData() instanceof Ageable ageable) {
            ageable.setAge(0);
            block.setBlockData(ageable, true);
        }
        genetics.setCropGenome(block.getLocation(), genome);
    }

    // ------------------------------------------------------------------
    // 抗逆：防踩踏
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.FARMLAND) {
            return;
        }
        // 检查上方作物是否免疫踩踏
        Block above = block.getRelative(0, 1, 0);
        Genome genome = genetics.getCropGenome(above.getLocation());
        if (genome != null && genome.immuneToTrample()) {
            event.setCancelled(true);
        }
    }

    /** 给玩家显示某个位置的作物基因（预留：/hrf genes 查询）。 */
    public String describeCrop(Location loc) {
        Genome genome = genetics.getCropGenome(loc);
        if (genome == null) {
            return "<gray>该位置没有基因记录";
        }
        return "<green>" + genome.shortSummary() + " <dark_gray>(" + genome.total() + " 点强度)";
    }

    /** 把基因组写到物品 lore 上（供外部调用）。 */
    public void stamp(ItemStack stack, Genome genome) {
        genetics.applyGenome(stack, genome);
    }

    /** 读取物品上的基因组。 */
    public Genome read(ItemStack stack) {
        return genetics.genomeOf(stack);
    }

    /** 工具方法：判断物品是否是基因相关物品。 */
    public static boolean isGeneticItem(ItemStack stack) {
        String id = ItemRegistry.get().idOf(stack);
        if (id == null) {
            return false;
        }
        return id.startsWith("hrf_genome") || id.startsWith("hrf_gene")
                || id.startsWith("hrf_sample") || id.contains("_embryo")
                || id.endsWith("_seeds") || id.equals("hrf_seed_template");
    }

    /** 供调试：清掉某物品的基因 lore。 */
    public static void stripLore(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        meta.lore(null);
        stack.setItemMeta(meta);
    }
}
