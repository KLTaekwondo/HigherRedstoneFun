package com.koole.higherRedStoneFun.listeners;

import com.koole.higherRedStoneFun.HigherRedStoneFun;
import com.koole.higherRedStoneFun.core.Text;
import com.koole.higherRedStoneFun.genetics.CropMapping;
import com.koole.higherRedStoneFun.genetics.Genome;
import com.koole.higherRedStoneFun.genetics.GeneticsManager;
import com.koole.higherRedStoneFun.items.ItemRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 动物基因玩法。
 *
 * <p>设计要点：动物的基因不是「加个 buff 就完事」，而是<b>可遗传</b>的。</p>
 *
 * <ul>
 *   <li><b>采样</b>：潜行 + 空样本瓶右键动物，采集动物生物样本。</li>
 *   <li><b>孵化</b>：用带动物基因组的胚胎右键方块，生成携带该基因的动物。</li>
 *   <li><b>遗传</b>：两只带基因的动物繁殖时，子代会随机继承父母的基因位，
 *       并有小概率突变——这就是「一代代选育出高产牛群」的玩法。</li>
 *   <li><b>产出</b>：产量基因提高掉落数量，活力基因提高繁殖频率。</li>
 * </ul>
 */
public final class AnimalListener implements Listener {

    private final HigherRedStoneFun plugin;
    private final GeneticsManager genetics;

    public AnimalListener(HigherRedStoneFun plugin, GeneticsManager genetics) {
        this.plugin = plugin;
        this.genetics = genetics;
    }

    // ------------------------------------------------------------------
    // 采样
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSample(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!ItemRegistry.get().is(hand, "hrf_empty_sample")) {
            return;
        }

        Entity target = event.getRightClicked();
        CropMapping.Animal animal = CropMapping.byEntityType(target.getType());
        if (animal == null) {
            return;
        }

        if (!player.hasPermission("hrf.genetics.use")) {
            player.sendMessage(Text.prefixed("<red>你没有使用基因设备的权限"));
            return;
        }

        event.setCancelled(true);

        // 读取或初始化该动物的基因
        Genome genome = genetics.getEntityGenome(target);
        if (genome == null) {
            // 幼年动物基因不完整
            boolean baby = target instanceof Ageable ageable && !ageable.isAdult();
            genome = genetics.randomGenome(baby ? 0.15D : 0.40D);
            genetics.setEntityGenome(target, genome);
        }

        ItemStack sample = ItemRegistry.get().create("hrf_sample_animal", 1);
        genetics.applyGenome(sample, genome);
        genetics.markSample(sample, GeneticsManager.SampleKind.ANIMAL, animal.sourceKey());

        // 消耗空瓶
        ItemStack bottles = hand.clone();
        bottles.setAmount(bottles.getAmount() - 1);
        player.getInventory().setItemInMainHand(bottles.getAmount() <= 0 ? null : bottles);

        var leftover = player.getInventory().addItem(sample);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }

        player.sendMessage(Text.prefixed("<green>已采集动物样本 <dark_gray>| <gray>物种 <white>"
                + animal.sourceKey() + " <gray>基因 <white>" + genome.shortSummary()));
    }

    // ------------------------------------------------------------------
    // 孵化胚胎
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHatch(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack hand = event.getItem();
        String id = ItemRegistry.get().idOf(hand);
        if (id == null || !id.endsWith("_embryo")) {
            return;
        }

        CropMapping.Animal animal = switch (id) {
            case "hrf_cow_embryo" -> CropMapping.byAnimal("cow");
            case "hrf_sheep_embryo" -> CropMapping.byAnimal("sheep");
            case "hrf_chicken_embryo" -> CropMapping.byAnimal("chicken");
            case "hrf_pig_embryo" -> CropMapping.byAnimal("pig");
            case "hrf_rabbit_embryo" -> CropMapping.byAnimal("rabbit");
            case "hrf_bee_embryo" -> CropMapping.byAnimal("bee");
            default -> null;
        };
        if (animal == null) {
            return;
        }

        org.bukkit.block.Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission("hrf.genetics.use")) {
            player.sendMessage(Text.prefixed("<red>你没有使用基因设备的权限"));
            return;
        }

        Genome genome = genetics.genomeOf(hand);
        if (genome == null) {
            player.sendMessage(Text.prefixed("<red>这个胚胎没有携带基因数据"));
            return;
        }

        Location spawnAt = clicked.getLocation().add(0.5, 1.0, 0.5);
        spawnAt.setYaw(player.getLocation().getYaw() + 180.0F);

        Entity spawned;
        try {
            spawned = player.getWorld().spawnEntity(spawnAt, animal.type());
        } catch (Exception ex) {
            player.sendMessage(Text.prefixed("<red>无法在此处孵化胚胎"));
            return;
        }

        genetics.setEntityGenome(spawned, genome);

        // 消耗胚胎
        ItemStack embryos = hand.clone();
        embryos.setAmount(embryos.getAmount() - 1);
        player.getInventory().setItemInMainHand(embryos.getAmount() <= 0 ? null : embryos);

        player.sendMessage(Text.prefixed("<green>已孵化 <white>" + animal.sourceKey()
                + " <dark_gray>| <gray>基因 <white>" + genome.shortSummary()));
    }

    // ------------------------------------------------------------------
    // 遗传
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        LivingEntity mother = event.getMother();
        LivingEntity father = event.getFather();
        LivingEntity child = event.getEntity();

        Genome a = genetics.getEntityGenome(mother);
        Genome b = genetics.getEntityGenome(father);

        if (a == null && b == null) {
            return;
        }

        // 一方没有基因时，按野生处理
        Genome parentA = a == null ? Genome.wild() : a;
        Genome parentB = b == null ? Genome.wild() : b;
        Genome childGenome = genetics.inherit(parentA, parentB);
        genetics.setEntityGenome(child, childGenome);

        // 活力基因提高繁殖意愿：让幼崽更快成年
        if (child instanceof Ageable ageable && !ageable.isAdult()) {
            int vigor = childGenome.level(com.koole.higherRedStoneFun.genetics.Gene.VIGOR);
            if (vigor > 0) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (ageable.isValid() && !ageable.isAdult()
                            && ThreadLocalRandom.current().nextDouble() < 0.15D * vigor) {
                        ageable.setAdult();
                    }
                }, 100L);
            }
        }

        // 通知繁殖者
        if (event.getBreeder() instanceof Player player) {
            player.sendMessage(Text.prefixed("<green>子代基因 <dark_gray>| <white>"
                    + childGenome.shortSummary() + " <dark_gray>(强度 " + childGenome.total() + ")"));
        }
    }

    // ------------------------------------------------------------------
    // 产出加成
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Genome genome = genetics.getEntityGenome(event.getEntity());
        if (genome == null) {
            return;
        }
        double multiplier = genome.yieldMultiplier();
        if (multiplier <= 1.0D) {
            return;
        }

        // 按产量基因提高掉落数量
        for (ItemStack drop : event.getDrops()) {
            if (drop == null || drop.getType().isAir()) {
                continue;
            }
            int bonus = (int) Math.round(drop.getAmount() * (multiplier - 1.0D));
            if (bonus > 0) {
                drop.setAmount(Math.min(drop.getMaxStackSize(), drop.getAmount() + bonus));
            }
        }
    }

    /**
     * 挤奶 / 剪羊毛等交互产出。
     *
     * <p>用 Paper 的 {@code PlayerShearEntityEvent} 与 {@code PlayerInteractEntityEvent}
     * 覆盖常见产出，让基因加成在「非致死」获取方式上也生效。</p>
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onShear(org.bukkit.event.player.PlayerShearEntityEvent event) {
        Genome genome = genetics.getEntityGenome(event.getEntity());
        if (genome == null) {
            return;
        }
        double multiplier = genome.yieldMultiplier();
        if (multiplier <= 1.0D) {
            return;
        }
        int extra = (int) Math.round(multiplier - 1.0D);
        if (extra <= 0) {
            return;
        }
        Location loc = event.getEntity().getLocation();
        event.getEntity().getWorld().dropItemNaturally(loc, new ItemStack(org.bukkit.Material.WHITE_WOOL, extra));
        if (event.getPlayer() instanceof Player player) {
            player.sendMessage(Text.prefixed("<green>剪毛加成 <dark_gray>| <gray>额外 <white>+" + extra + " 羊毛"));
        }
    }
}
