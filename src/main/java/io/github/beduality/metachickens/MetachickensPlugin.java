package io.github.beduality.metachickens;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class MetachickensPlugin extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Metachickens Plugin Enabled");

        registerSpawnerRecipes();
    }

    private void registerSpawnerRecipes() {
        for (EntityType entityType : EntityType.values()) {
            if (!entityType.isSpawnable()) {
                continue;
            }

            ItemStack spawnerItem = getSpawnerItem(entityType);
            var spawnEggMaterial = Material.getMaterial(entityType.name() + "_SPAWN_EGG");

            if (spawnEggMaterial == null) {
                getLogger().warning("No spawn egg material found for entity type: " + entityType.name());
                continue;
            }

            // Define the recipe
            var key = new NamespacedKey(this, "spawner_" + entityType.name().toLowerCase());
            ShapedRecipe recipe = new ShapedRecipe(key, spawnerItem);
            recipe.shape("III", "IEI", "III");
            recipe.setIngredient('I', Material.IRON_BARS);
            recipe.setIngredient('E', spawnEggMaterial);

            getServer().addRecipe(recipe);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Metachickens Plugin Disabled");
    }

    private ItemStack getSpawnerItem(EntityType entityType) {
        ItemStack itemStack = new ItemStack(Material.SPAWNER, 1);
        BlockStateMeta bsm = (BlockStateMeta) itemStack.getItemMeta();
        CreatureSpawner cs = (CreatureSpawner) bsm.getBlockState();

        cs.setSpawnedType(entityType);
        bsm.setBlockState(cs);
        itemStack.setItemMeta(bsm);

        return itemStack;
    }

    // On breaking a spawner, drop the spawner item instead of nothing
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (block == null || block.getType() != Material.SPAWNER) {
            return;
        }

        if (event.getPlayer().getGameMode().name().equals("CREATIVE")) {
            return;
        }

        Player player = event.getPlayer();
        var hasSilkTouch = player.getInventory()
                .getItemInMainHand()
                .containsEnchantment(Enchantment.SILK_TOUCH);

        if (!hasSilkTouch) {
            return;
        }

        var entityType = ((CreatureSpawner) block.getState()).getSpawnedType();

        // Drop the spawner block
        block.getWorld().dropItem(block.getLocation(), getSpawnerItem(entityType));
        block.setType(Material.AIR);
    }

    // On placing a spawner, set the spawn type based on the item in the player's
    // hand
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        var itemInHand = event.getItemInHand();

        if (itemInHand.getType() != Material.SPAWNER || !itemInHand.hasItemMeta()) {
            return;
        }

        var block = event.getBlockPlaced();

        if (!(block.getState() instanceof CreatureSpawner)) {
            return;
        }

        CreatureSpawner spawner = (CreatureSpawner) block.getState();
        BlockStateMeta bsm = (BlockStateMeta) itemInHand.getItemMeta();
        CreatureSpawner itemSpawner = (CreatureSpawner) bsm.getBlockState();
        EntityType spawnType = itemSpawner.getSpawnedType();
        spawner.setSpawnedType(spawnType);
        spawner.update();
    }

    private Component DNA_BOTTLE = Component.text("DNA Bottle").color(NamedTextColor.GOLD);

    // On right-clicking on a mob with a vial (glass bottle), make this glass into a
    // water bottle with a meta data (DNA collected from the mob)
    @EventHandler
    public void onRightClickEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof LivingEntity entity) {
            Player player = event.getPlayer();
            ItemStack itemInHand = player.getInventory().getItemInMainHand();

            if (entity instanceof Chicken chicken && !chicken.isAdult()) {
                if (!itemInHand.hasItemMeta() || itemInHand.getItemMeta().displayName() == null || !itemInHand.getItemMeta().displayName().equals(DNA_BOTTLE)) {
                    return;
                }

                modifyBabyChicken(player, chicken);
                return;
            }

            if (itemInHand.getType() != Material.GLASS_BOTTLE) {
                return;
            }

            giveDnaBottle(player, entity);
        }
    }

    private void modifyBabyChicken(Player player, Chicken chicken) {
        var itemInHand = player.getInventory().getItemInMainHand();
        var metadata = itemInHand.getItemMeta();
        var entityTypeString = metadata.getPersistentDataContainer().get(new NamespacedKey(this, "entity_type"),
        org.bukkit.persistence.PersistentDataType.STRING);

        if(entityTypeString == null) {
            getLogger().warning("No entity type found in DNA bottle");
            return;
        }

        var entityType = EntityType.valueOf(entityTypeString);

        player.sendMessage(Component.text("The chicken now has the DNA of entity " + entityType.name()).color(NamedTextColor.GREEN));

        if (entityType == EntityType.PLAYER) {
            entityType = EntityType.ZOMBIE;
        }

        // Add metadata to the metachicken
        chicken.getPersistentDataContainer().set(new NamespacedKey(this, "entity_type"),
                org.bukkit.persistence.PersistentDataType.STRING, entityType.name());

        chicken.customName(Component.text("Metachicken").color(NamedTextColor.GOLD));

        damageChicken(chicken);

        ItemStack glassBottle = new ItemStack(Material.GLASS_BOTTLE);
        player.getInventory().setItemInMainHand(glassBottle);
    }

    private boolean damageChicken(Chicken chicken) {
        var random = Math.random();
        if (random < 0.3) {
            chicken.damage(1000);
            return true;
        }

        chicken.damage(1);

        return false;
    }

    private boolean addItemToInventory(Player player, ItemStack item) {
        // Try adding the item to the player's inventory
        var leftoverItems = player.getInventory().addItem(item);
    
        // If there are leftovers, the inventory was full
        return leftoverItems.isEmpty();
    }

    private void giveDnaBottle(Player player, Entity entity) {
        var entityType = entity.getType();

        if(entity instanceof Chicken) {
            entityType = EntityType.valueOf(getChickenEntityTypeName(entity));
        }

        // Create the DNA bottle item
        var dnaBottle = new ItemStack(Material.POTION);
        var meta = dnaBottle.getItemMeta();
        meta.displayName(DNA_BOTTLE);
        meta.lore(java.util.List
                .of(Component.text("DNA collected from " + entityType.name()).color(NamedTextColor.GRAY)));
        dnaBottle.setItemMeta(meta);
        // Define metadata about the DNA type
        var dnaMeta = dnaBottle.getItemMeta();
        dnaMeta.getPersistentDataContainer().set(new NamespacedKey(this, "entity_type"),
                org.bukkit.persistence.PersistentDataType.STRING, entityType.name());
        dnaBottle.setItemMeta(dnaMeta);

        ItemStack glassBottleStack = player.getInventory().getItemInMainHand();

        if (glassBottleStack.getAmount() > 1) {
            glassBottleStack.setAmount(glassBottleStack.getAmount() - 1);

            if (!addItemToInventory(player, dnaBottle)) {
                // Drop the DNA bottle at the player's location if inventory is full
                player.getWorld().dropItem(player.getLocation(), dnaBottle);
            }
        } else {
            // Replace the single glass bottle with the DNA bottle
            player.getInventory().setItemInMainHand(dnaBottle);
        }
    }

    private String getChickenEntityTypeName(Entity entity) {
        return entity.getPersistentDataContainer().get(new NamespacedKey(this, "entity_type"),
                    org.bukkit.persistence.PersistentDataType.STRING);
    }

    // On chicken laying egg, replace the vanilla egg with a spawn egg
    @EventHandler
    public void onChickenLayEgg(EntityDropItemEvent event) {
        if (event.getEntity() instanceof Chicken chicken) {
            var entityTypeName = getChickenEntityTypeName(chicken);

            if (entityTypeName == null) {
                return;
            }

            var spawnEggMaterial = Material.getMaterial(entityTypeName + "_SPAWN_EGG");

            if (spawnEggMaterial == null) {
                return;
            }

            // Create the spawn egg item
            ItemStack spawnEgg = new ItemStack(spawnEggMaterial);

            // Drop the spawn egg at the entity's death location
            event.getEntity().getWorld().dropItemNaturally(event.getEntity().getLocation(), spawnEgg);

            // Remove the old egg
            event.setCancelled(true);
        }
    }
}