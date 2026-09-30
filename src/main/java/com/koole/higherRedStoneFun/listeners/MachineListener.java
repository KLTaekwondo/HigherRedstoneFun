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
        definition.logic().onPlace(machine);
        manager.add(machine);

        // 接入能源网络
        if (definition.energyRole() != null) {
            plugin.energy().register(machine);
        }

        event.getPlayer().sendMessage(Text.prefixed("<green>已放置 <white>"
                + definition.displayName() + "</white> <dark_gray>(右键打开界面)"));
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
        if (machine == null) {
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

        // 不让原版掉落方块本身，我们手动掉落正确物品
        event.setDropItems(false);

        machine.definition().logic().onBreak(machine);

        // 掉落机器本体
        block.getWorld().dropItemNaturally(block.getLocation().clone().add(0.5, 0.5, 0.5),
                MachineRegistry.get().createItem(machine.id(), 1));

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
        if (machine == null) {
            return;
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
        MachineMenu menu = new MachineMenu(machine);

        // 工作台需要先刷新一次预览
        if (machine.definition().logic() instanceof CraftingTableLogic logic) {
            // 把机器当前内容镜像进界面后再取一次预览
            menu.refresh();
        }
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
                + (role == null ? "" : " [" + role + " " + machine.energyStored() + "/" + machine.capacity() + "J]");
    }
}
