package com.koole.higherRedStoneFun.items;

import com.koole.higherRedStoneFun.core.ItemGroup;
import com.koole.higherRedStoneFun.core.Keys;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局物品注册表。
 *
 * <p>与原版粘液科技最大的不同：本插件用 {@code PersistentDataContainer} 的单个字符串键
 * 作为物品身份标识，而不是依赖显示名或 NBT 库。这样物品可以安全地重命名、翻译，
 * 也不会被铁砧/附魔台破坏身份。</p>
 */
public final class ItemRegistry {

    private static final ItemRegistry INSTANCE = new ItemRegistry();

    private final Map<String, HrfItem> byId = new LinkedHashMap<>();

    private ItemRegistry() {
    }

    public static ItemRegistry get() {
        return INSTANCE;
    }

    /** 注册一个物品定义。ID 重复会抛异常，避免静默覆盖。 */
    public HrfItem register(HrfItem item) {
        if (byId.containsKey(item.id())) {
            throw new IllegalStateException("物品 ID 重复注册: " + item.id());
        }
        byId.put(item.id(), item);
        return item;
    }

    public boolean exists(String id) {
        return byId.containsKey(id);
    }

    @Nullable
    public HrfItem get(String id) {
        return byId.get(id);
    }

    public HrfItem require(String id) {
        HrfItem item = byId.get(id);
        if (item == null) {
            throw new IllegalArgumentException("未知物品 ID: " + id);
        }
        return item;
    }

    public Collection<HrfItem> all() {
        return byId.values();
    }

    public List<HrfItem> byGroup(ItemGroup group) {
        List<HrfItem> out = new ArrayList<>();
        for (HrfItem item : byId.values()) {
            if (item.group() == group) {
                out.add(item);
            }
        }
        return out;
    }

    public int size() {
        return byId.size();
    }

    // ------------------------------------------------------------------
    // 物品创建
    // ------------------------------------------------------------------

    /** 创建一个带标记的自定义物品。 */
    public ItemStack create(String id, int amount) {
        HrfItem item = require(id);
        ItemStack stack = item.toStack(amount);
        mark(stack, id);
        return stack;
    }

    public ItemStack create(String id) {
        return create(id, 1);
    }

    /** 给已有的 ItemStack 打上自定义物品标记。 */
    public void mark(ItemStack stack, String id) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.get().itemId(), PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
    }

    // ------------------------------------------------------------------
    // 物品识别
    // ------------------------------------------------------------------

    /** 读取物品的自定义 ID，非自定义物品返回 null。 */
    @Nullable
    public String idOf(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta.getPersistentDataContainer().get(Keys.get().itemId(), PersistentDataType.STRING);
    }

    public boolean isCustom(@Nullable ItemStack stack) {
        return idOf(stack) != null;
    }

    public boolean is(@Nullable ItemStack stack, String id) {
        return id != null && id.equals(idOf(stack));
    }

    /**
     * 判断两个 ItemStack 是否「同一种物品」。
     *
     * <p>自定义物品只比较 ID；原版物品比较类型与名称，避免把带自定义名字的石头
     * 误判成普通石头。</p>
     */
    public boolean sameItem(@Nullable ItemStack a, @Nullable ItemStack b) {
        if (a == null || b == null || a.getType().isAir() || b.getType().isAir()) {
            return false;
        }
        String idA = idOf(a);
        String idB = idOf(b);
        if (idA != null || idB != null) {
            return idA != null && idA.equals(idB);
        }
        return a.isSimilar(b);
    }

    /**
     * 把任意 ItemStack 归一化成「纯净」的自定义物品副本。
     *
     * <p>用于把玩家放进机器的物品洗掉附魔、自定义名等附加信息，
     * 防止把附魔钻石当作普通钻石在机器里使用。</p>
     */
    public ItemStack normalize(ItemStack stack) {
        String id = idOf(stack);
        if (id != null) {
            return create(id, stack.getAmount());
        }
        ItemStack copy = stack.clone();
        copy.setAmount(1);
        ItemStack plain = new ItemStack(copy.getType());
        plain.setAmount(stack.getAmount());
        return plain;
    }
}
