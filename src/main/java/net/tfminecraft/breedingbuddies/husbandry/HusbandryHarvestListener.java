package net.tfminecraft.breedingbuddies.husbandry;

import java.util.Optional;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.tfminecraft.cooking.api.CookingFoodApi;

public final class HusbandryHarvestListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMilk(PlayerInteractEntityEvent event) {
        EquipmentSlot slot = event.getHand();
        if (slot != EquipmentSlot.HAND && slot != EquipmentSlot.OFF_HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack used = player.getInventory().getItem(slot);
        if (used == null || used.getType() != Material.BUCKET) {
            return;
        }
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof LivingEntity living)) {
            return;
        }
        HusbandrySpecies species = HusbandryConfig.species(living.getType());
        if (species == null || !species.canMilk()) {
            return;
        }
        event.setCancelled(true);
        if (slot == EquipmentSlot.OFF_HAND) {
            ItemStack main = player.getInventory().getItemInMainHand();
            if (main != null && main.getType() == Material.BUCKET) {
                return;
            }
        }
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        Optional<HusbandryAnimal> stored = HusbandryEntities.lookup(living.getUniqueId());
        if (stored.isEmpty()) {
            return;
        }
        HusbandryAnimal animal = stored.get();
        long now = System.currentTimeMillis();
        if (!HusbandryGrowth.isMature(animal, now)) {
            player.sendMessage("§cThis animal is still growing up.");
            return;
        }
        if (onMilkCooldown(animal, living.getType(), now)) {
            if (shouldMessageCooldown(animal, now)) {
                player.sendMessage("§cThis animal is not ready to be milked.");
            }
            return;
        }
        ItemStack milk = HusbandryHarvest.buildMilk(animal, living.getType());
        if (milk == null) {
            return;
        }
        milk.setAmount(1);
        consumeOneBucket(player, slot);
        giveMilk(player, slot, milk);
        if (living.getWorld() != null) {
            living.getWorld().playSound(living.getLocation(), HusbandryHarvest.milkSound(living.getType()), 1f, 1f);
        }
        animal.setLastMilkAt(now);
        repository.upsertAnimal(animal);
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onVanillaMilkFill(PlayerBucketFillEvent event) {
        if (event.getBucket() == Material.MILK_BUCKET) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShearsInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != Material.SHEARS) {
            return;
        }
        if (HusbandryItems.matches(hand, HusbandryConfig.neuterItem())) {
            return;
        }
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof LivingEntity living)) {
            return;
        }
        if (living.getType() == EntityType.SHEEP) {
            return;
        }
        HusbandrySpecies species = HusbandryConfig.species(living.getType());
        if (species == null || !species.canShear()) {
            return;
        }
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        Optional<HusbandryAnimal> stored = HusbandryEntities.lookup(living.getUniqueId());
        if (stored.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        HusbandryHarvest.tryShear(player, living, stored.get(), species, repository, System.currentTimeMillis());
    }

    @EventHandler(ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        Entity sheared = event.getEntity();
        HusbandrySpecies species = HusbandryConfig.species(sheared.getType());
        if (species == null || !species.canShear()) {
            return;
        }
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        Optional<HusbandryAnimal> stored = HusbandryEntities.lookup(sheared.getUniqueId());
        if (stored.isEmpty()) {
            return;
        }
        if (sheared.getType() == EntityType.SHEEP) {
            HusbandryHarvest.trySheepBonusShear(
                    event.getPlayer(),
                    stored.get(),
                    species,
                    repository,
                    System.currentTimeMillis());
            return;
        }
        event.setCancelled(true);
        if (sheared instanceof LivingEntity living) {
            HusbandryHarvest.tryShear(
                    event.getPlayer(),
                    living,
                    stored.get(),
                    species,
                    repository,
                    System.currentTimeMillis());
        }
    }

    private static boolean onMilkCooldown(HusbandryAnimal animal, EntityType type, long now) {
        Long last = animal.lastMilkAt();
        if (last == null) {
            return false;
        }
        long wait = HusbandryConfig.milkTimerSeconds(type) * 1000L;
        return now - last < wait;
    }

    private static boolean shouldMessageCooldown(HusbandryAnimal animal, long now) {
        Long last = animal.lastMilkAt();
        return last == null || now - last > 1000L;
    }

    private static void consumeOneBucket(Player player, EquipmentSlot slot) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack used = player.getInventory().getItem(slot);
        int amount = used.getAmount();
        if (amount <= 1) {
            player.getInventory().setItem(slot, new ItemStack(Material.AIR));
        } else {
            used.setAmount(amount - 1);
        }
    }

    private static void giveMilk(Player player, EquipmentSlot slot, ItemStack milk) {
        PlayerInventory inv = player.getInventory();
        ItemStack current = inv.getItem(slot);
        if (current == null || current.getType().isAir()) {
            inv.setItem(slot, milk);
            return;
        }
        ItemStack leftover = CookingFoodApi.addToInventory(player, milk);
        if (leftover != null && leftover.getAmount() > 0 && player.getWorld() != null) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDropEgg(EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof Chicken chicken)) {
            return;
        }
        ItemStack stack = event.getItemDrop().getItemStack();
        if (!HusbandryEggs.isVanillaEgg(stack.getType())) {
            return;
        }
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        if (HusbandryEntities.getLoaded(chicken.getUniqueId()).isEmpty()
                && !repository.exists(chicken.getUniqueId())) {
            return;
        }
        HusbandrySpecies species = HusbandryConfig.species(chicken.getType());
        if (species != null && species.hasEgg()) {
            event.setCancelled(true);
        }
    }
}
