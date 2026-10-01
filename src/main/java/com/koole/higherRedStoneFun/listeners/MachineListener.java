package com.koole.higherRedStoneFun.listeners;

import com.koole.higherRedStoneFun.HigherRedStoneFun;
import com.koole.higherRedStoneFun.core.Text;
import com.koole.higherRedStoneFun.energy.EnergyNode;
import com.koole.higherRedStoneFun.items.ItemRegistry;
import com.koole.higherRedStoneFun.machines.MachineDefinition;
import com.koole.higherRedStoneFun.machines.MachineInstance;
import com.koole.higherRedStoneFun.machines.MachineManager;
import com.koole.higherRedStoneFun.machines.MachineMenu;
import com.koole.higherRedStoneFun.machines.MachineRegistry;
import com.koole.higherRedStoneFun.machines.StructureFormation;
import com.koole.higherRedStoneFun.machines.logic.CraftingTableLogic;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 机器方块的生命周期监听。
 *
 * <p>方块的「身份」通过坐标 + 材质判定，与粘液科技的 BlockStorage 思路一致：
 * 该 API 版本已没有方块级 PersistentDataContainer，而坐标索引还能顺带
 * 免疫「玩家用其他方块替换机器后冒领」的问题。</p>
 */
public final class MachineListener implements Listener {

    private final HigherRedStoneFun plugin;
    private final MachineManager manager;

    public MachineListener(HigherRedStoneFun plugin, MachineManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ------------------------------------------------------------------
    // 放置
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        String id = ItemRegistry.get().idOf(inHand);
        if (id == null) {
            return;
        }

        MachineDefinition definition = MachineRegistry.get().get(id);
        if (definition == null) {
            // 是自定义物品但不是机器：例如基因种子，交给别的监听器处理
            return;
        }

        Location loc = event.getBlockPlaced().getLocation();
        if (manager.isMachine(loc)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Text.prefixed("<red>这里已经有一台机器了"));
            return;
        }

        // 外观必须与定义一致，避免玩家拿同名物品摆出错误外观
        if (event.getBlockPlaced().getType() != definition.icon()) {
            event.getPlayer().sendMessage(Text.prefixed(
                    "<red>机器外观不匹配，请联系管理员检查配置"));
            plugin.getLogger().warning("机器 " + id + " 被放置在 "
                    + event.getBlockPlaced().getType() + " 上，预期 " + definition.icon());
        }

        MachineInstance machine = new MachineInstance(loc, definition);
        machine.setOwner(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        // 走这条路径说明玩家手持自定义机器物品，拆解时应归还自定义物品
        machine.setPlacedAsMachineItem(true);
        definition.logic().onPlace(machine);

        // 多方块：记录组件坐标并检测成型
        if (definition.isMultiblock()) {
            machine.setStructureBlocks(definition.structure().extraBlockLocations(loc));
            machine.recheckStructure();
        }

        manager.add(machine);

        // 接入能源网络
        if (definition.energyRole() != null) {
            plugin.energy().register(machine);
        }

        // 给玩家结构提示
        if (definition.isMultiblock()) {
            if (machine.isStructureComplete()) {
                event.getPlayer().sendMessage(Text.prefixed("<green>结构成型！<white>"
                        + definition.displayName() + " <green>已激活加成"));
            } else {
                event.getPlayer().sendMessage(Text.prefixed("<yellow>结构未完成 <dark_gray>| <gray>还需要: <white>"
                        + definition.structure().describeMissing(loc)));
            }
        } else {
            event.getPlayer().sendMessage(Text.prefixed("<green>已放置 <white>"
                    + definition.displayName() + "</white> <dark_gray>(右键打开界面)"));
        }
    }

    /**
     * 放置方块后重新检测附近的机器结构。
     *
     * <p>玩家搭结构时是「一块一块往上放」的，所以每放一块都要让
     * 下方的机器重新判定一次，否则要等到重启才会成型。</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureBlockPlace(BlockPlaceEvent event) {
        Location loc = event.getBlockPlaced().getLocation();

        // 情况 A：玩家放的就是机器物品（普通放置路径），已在 onPlace 处理
        boolean isMachineItem = ItemRegistry.get().idOf(event.getItemInHand()) != null;

        // 情况 B：玩家用普通方块搭出了多方块结构。
        //         这是最常见的操作（创造模式直接拿工作台、铁块、玻璃），
        //         必须能自动成型，否则玩家只会看到「什么都没发生」。
        MachineInstance formed = StructureFormation.tryFormBelow(manager, loc);
        if (formed != null) {
            if (formed.definition().energyRole() != null) {
                plugin.energy().register(formed);
            }
            event.getPlayer().sendMessage(Text.prefixed("<green>结构成型！<white>"
                    + formed.definition().displayName() + " <green>已激活加成"));
            return;
        }

        // 情况 C：已注册机器的结构被补全（例如玻璃被拆后又补上）
        if (isMachineItem) {
            return;
        }
        for (int dy = 1; dy <= 3; dy++) {
            MachineInstance candidate = manager.get(loc.clone().subtract(0, dy, 0));
            if (candidate == null || !candidate.definition().isMultiblock()) {
                continue;
            }
            boolean before = candidate.isStructureComplete();
            candidate.setStructureBlocks(
                    candidate.definition().structure().extraBlockLocations(candidate.location()));
            candidate.recheckStructure();

            if (!before && candidate.isStructureComplete()) {
                event.getPlayer().sendMessage(Text.prefixed("<green>结构成型！<white>"
                        + candidate.definition().displayName() + " <green>已激活加成"));
            }
        }
    }

    // ------------------------------------------------------------------
    // 导线与其它方块
    // ------------------------------------------------------------------

    /**
     * 导线变化时重建受影响的电网。
     *
     * <p>这是「用导线把两个电网连起来」能生效的关键：放置或破坏一根导线后，
     * 它周围 3x3x3 内的网络会被标记为脏，下一次 tick 重建时就会
     * 通过 BFS 发现彼此并把它们合并成一个网络。</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCablePlace(BlockPlaceEvent event) {
        // 机器本身已经在 onPlace 里处理过
        if (ItemRegistry.get().idOf(event.getItemInHand()) != null) {
            return;
        }
        if (com.koole.higherRedStoneFun.energy.EnergyNetwork.isCable(event.getBlockPlaced().getType())) {
            plugin.energy().onBlockChanged(event.getBlockPlaced().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCableBreak(BlockBreakEvent event) {
        if (manager.isMachine(event.getBlock().getLocation())) {
            return;
        }
        if (com.koole.higherRedStoneFun.energy.EnergyNetwork.isCable(event.getBlock().getType())) {
            plugin.energy().onBlockChanged(event.getBlock().getLocation());
        }
    }

    // ------------------------------------------------------------------
    // 破坏
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        MachineInstance machine = manager.get(loc);

        // 不是控制器：检查它是不是某台机器的结构组件
        if (machine == null) {
            handleStructureBlockBreak(event, loc);
            return;
        }

        Player player = event.getPlayer();

        // 权限：只有主人或管理员能拆除
        if (!machine.isOwnedBy(player.getUniqueId()) && !player.hasPermission("hrf.command.reload")) {
            event.setCancelled(true);
            player.sendMessage(Text.prefixed("<red>这不是你的机器"
                    + (machine.ownerName() == null ? "" : " <dark_gray>(属于 " + machine.ownerName() + ")")));
            return;
        }

        // 不让原版掉落方块本身，我们手动决定掉落什么
        event.setDropItems(false);

        machine.definition().logic().onBreak(machine);

        // 掉落机器本体。
        //
        // 关键：必须「拆掉什么就还回什么」。
        //   - 玩家用自定义机器物品放置 -> 还回自定义机器物品（保持身份）
        //   - 玩家用普通方块（原版工作台）搭成 -> 还回那个普通方块
        //
        // 早期版本无条件掉落自定义物品，导致「放一个原版工作台、拆掉、
        // 拿回一个自定义增强工作台」——原版工作台被凭空转换成自定义物品，
        // 是可无限刷取的漏洞。
        ItemStack drop;
        if (machine.isPlacedAsMachineItem()) {
            drop = MachineRegistry.get().createItem(machine.id(), 1);
        } else {
            drop = new ItemStack(block.getType(), 1);
        }
        block.getWorld().dropItemNaturally(block.getLocation().clone().add(0.5, 0.5, 0.5), drop);

        // 掉落内部物品
        for (ItemStack stack : machine.contents()) {
            if (stack != null && !stack.getType().isAir()) {
                block.getWorld().dropItemNaturally(block.getLocation().clone().add(0.5, 0.5, 0.5), stack);
            }
        }

        // 清理注册
        plugin.energy().unregister(loc);
        manager.remove(loc);
    }

    /**
     * 玩家挖掉的不是控制器，而是结构组件方块。
     *
     * <p>处理原则：<b>结构组件是普通方块，挖掉就挖掉</b>——
     * 只是机器因此失去加成，而<b>不是</b>整台机器被摧毁。</p>
     *
     * <p>这一点是刻意设计的。如果把「挖掉玻璃」等同于「拆掉机器」，
     * 玩家就会因为一次误挖而丢掉机器里所有物品——那正是我们要避免的
     * 数据丢失场景。现在损失仅限于那一块方块本身。</p>
     *
     * <p><b>提示必须每次都发。</b>早期版本只在「完整 -> 不完整」的瞬间提示，
     * 于是玩家先挖玻璃（提示了）、再挖铁块时就什么反应都没有，
     * 看起来像「铁块不是结构的一部分」。</p>
     */
    private void handleStructureBlockBreak(BlockBreakEvent event, Location loc) {
        MachineInstance controller = findStructureOwner(loc);
        if (controller == null) {
            return;
        }

        // 重建组件坐标并重新判定（recheckStructure 现在会重建列表）
        controller.recheckStructure();

        if (!controller.isStructureComplete()) {
            String missing = controller.definition().structure()
                    .describeMissing(controller.location());
            event.getPlayer().sendMessage(Text.prefixed(
                    "<yellow>" + controller.definition().displayName()
                            + " <red>结构已损坏，加成失效"
                            + (missing.isEmpty() ? "" : " <dark_gray>| <gray>还需要: <white>" + missing)
                            + " <dark_gray>(机器与内部物品安全)"));
        }
    }

    /**
     * 在控制器附近的竖直方向上查找结构归属。
     *
     * <p>因为结构恒为竖直，只需向下检查 1~2 格即可，无需全房间搜索。</p>
     */
    private MachineInstance findStructureOwner(Location loc) {
        for (int dy = 1; dy <= 3; dy++) {
            MachineInstance candidate = manager.get(loc.clone().subtract(0, dy, 0));
            if (candidate != null && candidate.definition().isMultiblock()) {
                return candidate;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        MachineInstance machine = manager.get(block.getLocation());

        // 兜底：玩家可能用原版方块搭出了结构，但放置事件发生在插件启用之前
        // （例如服务器重启后重新加载了区块）。这里再尝试一次自动成型。
        if (machine == null) {
            machine = StructureFormation.tryForm(manager, block.getLocation());
            if (machine == null) {
                return;
            }
            if (machine.definition().energyRole() != null) {
                plugin.energy().register(machine);
            }
            event.getPlayer().sendMessage(Text.prefixed("<green>检测到成型结构 <dark_gray>| <white>"
                    + machine.definition().displayName()));
        }

        Player player = event.getPlayer();

        // 潜行 + 手持机器物品 = 放置，交给 BlockPlaceEvent
        if (player.isSneaking() && ItemRegistry.get().idOf(event.getItem()) != null) {
            return;
        }

        event.setCancelled(true);

        if (!player.hasPermission("hrf.machine.use")) {
            player.sendMessage(Text.prefixed("<red>你没有使用机器的权限"));
            return;
        }

        // 主人校验（机器的 owner 为 null 表示公共机器）
        if (!machine.isOwnedBy(player.getUniqueId()) && !player.hasPermission("hrf.command.reload")) {
            player.sendMessage(Text.prefixed("<red>这不是你的机器"
                    + (machine.ownerName() == null ? "" : " <dark_gray>(属于 " + machine.ownerName() + ")")));
            return;
        }

        openMenu(player, machine);
    }

    private void openMenu(Player player, MachineInstance machine) {
        // 每次打开都重新判定结构：玩家可能刚挖掉或补上了组件方块。
        // 这样界面里显示的预览与提示永远和世界现状一致。
        if (machine.definition().isMultiblock()) {
            boolean before = machine.isStructureComplete();
            machine.recheckStructure();
            if (before != machine.isStructureComplete()) {
                player.sendMessage(Text.prefixed(machine.isStructureComplete()
                        ? "<green>结构已补全 <dark_gray>| <white>" + machine.definition().displayName()
                                + " <green>加成已恢复"
                        : "<yellow>结构未成型 <dark_gray>| <gray>加成失效，还需要: <white>"
                                + machine.definition().structure().describeMissing(machine.location())));
            }
        }

        MachineMenu menu = new MachineMenu(machine);
        menu.open(player);

        // 发送一条状态提示
        String status = machine.definition().logic().statusLine(machine);
        if (status != null && !status.isEmpty()) {
            player.sendMessage(Text.prefixed(status));
        }
    }

    // ------------------------------------------------------------------
    // 区块生命周期
    // ------------------------------------------------------------------

    @EventHandler
    public void onChunkUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        org.bukkit.World world = event.getWorld();

        List<MachineInstance> detached = manager.detachChunk(world, chunkX, chunkZ);
        for (MachineInstance machine : detached) {
            // 卸下时保存一次，防止服务器异常关闭丢数据
            plugin.storage().saveOne(machine);
            if (machine.definition().energyRole() != null) {
                plugin.energy().unregister(machine.location());
            }
        }
        plugin.energy().forgetChunk(event.getChunk());
    }

    /**
     * 区块重新加载时，把存档里的机器恢复到内存索引。
     *
     * <p>这一步让「离开基地再回来」的机器能继续工作，而不需要重启服务器。</p>
     */
    @EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        plugin.restoreChunk(event.getChunk());
    }

    /** 判断某个方块面是否朝向可用容器（预留给 v0.2 的物流模块）。 */
    public static boolean isContainerFacing(BlockFace face) {
        return face == BlockFace.UP || face == BlockFace.DOWN
                || face == BlockFace.NORTH || face == BlockFace.SOUTH
                || face == BlockFace.EAST || face == BlockFace.WEST;
    }

    /** 供调试：打印机器信息。 */
    public static String describe(MachineInstance machine) {
        EnergyNode.Role role = machine.definition().energyRole();
        return machine.id() + " @" + machine.location()
                + (role == null ? "" : " [" + role + " " + machine.energyStored() + "/" + machine.capacity() + "HRE]");
    }
}
