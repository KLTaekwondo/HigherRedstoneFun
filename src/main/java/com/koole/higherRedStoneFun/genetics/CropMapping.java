package com.koole.higherRedStoneFun.genetics;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 作物映射表：把「种子物品 ID / 方块材质 / 样本物品 ID」互相映射起来。
 *
 * <p>有了这张表，基因模块就不需要为每种作物写一遍逻辑：
 * 采样、注入、种植、收获全部走同一套代码，新增一种作物只是加一行映射。</p>
 */
public final class CropMapping {

    /** 一条作物记录。 */
    public record Crop(
            String seedItemId,
            String sampleItemId,
            Material plantBlock,
            Material seedMaterial,
            Material produce,
            int baseYield) {
    }

    private static final Map<String, Crop> BY_SEED = new HashMap<>();
    private static final Map<String, Crop> BY_SAMPLE = new HashMap<>();
    private static final Map<Material, Crop> BY_BLOCK = new HashMap<>();

    /** 动物映射：样本来源 -> 胚胎物品 ID 与实体类型。 */
    private static final Map<String, Animal> BY_ANIMAL = new HashMap<>();

    public record Animal(String sourceKey, String embryoItemId, EntityType type, Material product) {
    }

    static {
        register(new Crop("hrf_wheat_seeds", "hrf_sample_wheat",
                Material.WHEAT, Material.WHEAT_SEEDS, Material.WHEAT, 1));
        register(new Crop("hrf_carrot_seeds", "hrf_sample_carrot",
                Material.CARROTS, Material.CARROT, Material.CARROT, 2));
        register(new Crop("hrf_potato_seeds", "hrf_sample_potato",
                Material.POTATOES, Material.POTATO, Material.POTATO, 2));
        register(new Crop("hrf_beetroot_seeds", "hrf_sample_beetroot",
                Material.BEETROOTS, Material.BEETROOT_SEEDS, Material.BEETROOT, 1));
        register(new Crop("hrf_nether_wart", "hrf_sample_nether",
                Material.NETHER_WART, Material.NETHER_WART, Material.NETHER_WART, 2));
        register(new Crop("hrf_sweet_berries", "hrf_sample_berry",
                Material.SWEET_BERRY_BUSH, Material.SWEET_BERRIES, Material.SWEET_BERRIES, 2));
        register(new Crop("hrf_melon_seeds", "hrf_sample_melon",
                Material.MELON_STEM, Material.MELON_SEEDS, Material.MELON_SLICE, 3));
        register(new Crop("hrf_pumpkin_seeds", "hrf_sample_pumpkin",
                Material.PUMPKIN_STEM, Material.PUMPKIN_SEEDS, Material.PUMPKIN, 1));

        registerAnimal(new Animal("cow", "hrf_cow_embryo", EntityType.COW, Material.BEEF));
        registerAnimal(new Animal("sheep", "hrf_sheep_embryo", EntityType.SHEEP, Material.WHITE_WOOL));
        registerAnimal(new Animal("chicken", "hrf_chicken_embryo", EntityType.CHICKEN, Material.CHICKEN));
        registerAnimal(new Animal("pig", "hrf_pig_embryo", EntityType.PIG, Material.PORKCHOP));
        registerAnimal(new Animal("rabbit", "hrf_rabbit_embryo", EntityType.RABBIT, Material.RABBIT));
        registerAnimal(new Animal("bee", "hrf_bee_embryo", EntityType.BEE, Material.HONEYCOMB));
    }

    private CropMapping() {
    }

    private static void register(Crop crop) {
        BY_SEED.put(crop.seedItemId(), crop);
        BY_SAMPLE.put(crop.sampleItemId(), crop);
        BY_BLOCK.put(crop.plantBlock(), crop);
    }

    private static void registerAnimal(Animal animal) {
        BY_ANIMAL.put(animal.sourceKey(), animal);
    }

    @Nullable
    public static Crop bySeed(String seedItemId) {
        return BY_SEED.get(seedItemId);
    }

    @Nullable
    public static Crop bySample(String sampleItemId) {
        return BY_SAMPLE.get(sampleItemId);
    }

    @Nullable
    public static Crop byBlock(Material material) {
        return BY_BLOCK.get(material);
    }

    @Nullable
    public static Animal byAnimal(String sourceKey) {
        return BY_ANIMAL.get(sourceKey);
    }

    @Nullable
    public static Animal byEntityType(EntityType type) {
        for (Animal animal : BY_ANIMAL.values()) {
            if (animal.type() == type) {
                return animal;
            }
        }
        return null;
    }

    public static boolean isCropBlock(Material material) {
        return BY_BLOCK.containsKey(material);
    }

    /** 判断方块是否已经成熟可收获。 */
    public static boolean isMature(Block block) {
        Crop crop = byBlock(block.getType());
        if (crop == null) {
            return false;
        }
        if (block.getBlockData() instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return true;
    }
}
