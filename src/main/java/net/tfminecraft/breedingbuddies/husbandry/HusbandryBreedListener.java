package net.tfminecraft.breedingbuddies.husbandry;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class HusbandryBreedListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof LivingEntity living)) {
            return;
        }
        if (!HusbandryConfig.isHusbandryType(living.getType())) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = event.getHand() == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        HusbandryFeedQuality.offer(
                living.getUniqueId(),
                HusbandryFeedQuality.scaleOf(hand),
                System.currentTimeMillis());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnterLove(EntityEnterLoveModeEvent event) {
        Entity entity = event.getEntity();
        BlockReason reason = cannotBreed(entity);
        if (reason == BlockReason.NONE) {
            HusbandryFeedQuality.commitOffer(entity.getUniqueId(), System.currentTimeMillis());
            return;
        }
        HusbandryFeedQuality.discardOffer(entity.getUniqueId());
        event.setCancelled(true);
        clearLove(entity);
        Player feeder = event.getHumanEntity() instanceof Player player ? player : null;
        if (feeder != null) {
            feeder.sendMessage(reason.message);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        LivingEntity mother = event.getMother();
        LivingEntity father = event.getFather();
        BlockReason motherReason = cannotBreed(mother);
        BlockReason fatherReason = cannotBreed(father);
        if (motherReason != BlockReason.NONE || fatherReason != BlockReason.NONE) {
            event.setCancelled(true);
            clearLove(mother);
            clearLove(father);
            messageBreeder(event, firstBlocked(motherReason, fatherReason));
            return;
        }

        LivingEntity child = event.getEntity();
        if (!HusbandryConfig.isHusbandryType(child.getType())
                && !HusbandryConfig.isHusbandryType(mother.getType())
                && !HusbandryConfig.isHusbandryType(father.getType())) {
            return;
        }

        if (!ownershipAllows(event.getBreeder(), mother, father)) {
            event.setCancelled(true);
            clearLove(mother);
            clearLove(father);
            messageBreeder(event, BlockReason.NOT_OWNER);
            return;
        }

        persistBaby(child, mother, father, event.getBredWith());
        scheduleMountStats(child);
    }

    private static void scheduleMountStats(LivingEntity child) {
        if (!HusbandryMounts.isMount(child)) {
            return;
        }
        org.bukkit.Bukkit.getScheduler().runTaskLater(net.tfminecraft.breedingbuddies.BreedingBuddies.plugin, () -> {
            if (child == null || !child.isValid()) {
                return;
            }
            HusbandryEntities.lookup(child.getUniqueId()).ifPresent(baby ->
                    HusbandryMounts.applyStats(child, baby));
        }, 1L);
    }

    private static BlockReason firstBlocked(BlockReason mother, BlockReason father) {
        return mother != BlockReason.NONE ? mother : father;
    }

    private static void messageBreeder(EntityBreedEvent event, BlockReason reason) {
        if (event.getBreeder() instanceof Player player) {
            player.sendMessage(reason.message);
        }
    }

    private static boolean ownershipAllows(LivingEntity breeder, LivingEntity mother, LivingEntity father) {
        boolean owned = HusbandryOwnershipService.hasAnyOwner(mother.getUniqueId())
                || HusbandryOwnershipService.hasAnyOwner(father.getUniqueId());
        if (!owned) {
            return true;
        }
        if (!(breeder instanceof Player player)) {
            return false;
        }
        if (HusbandryOwnershipService.isStaff(player)) {
            return true;
        }
        return HusbandryOwnershipService.isOwner(player, mother.getUniqueId())
                && HusbandryOwnershipService.isOwner(player, father.getUniqueId());
    }

    private static BlockReason cannotBreed(Entity entity) {
        if (entity == null || !HusbandryConfig.isHusbandryType(entity.getType())) {
            return BlockReason.NONE;
        }
        if (HusbandryEntities.repository() == null) {
            return BlockReason.UNAVAILABLE;
        }
        Optional<HusbandryAnimal> stored = HusbandryEntities.lookup(entity.getUniqueId());
        if (stored.isEmpty()) {
            return BlockReason.NO_RECORD;
        }
        HusbandryAnimal animal = stored.get();
        if (animal.neutered()) {
            return BlockReason.NEUTERED;
        }
        if (animal.hungrySince() != null || animal.dirtySince() != null) {
            return BlockReason.UNWELL;
        }
        if (!HusbandryGrowth.isMature(animal, System.currentTimeMillis())) {
            return BlockReason.IMMATURE;
        }
        return BlockReason.NONE;
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private static void persistBaby(
            LivingEntity child,
            LivingEntity mother,
            LivingEntity father,
            ItemStack bredWith) {
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        UUID uuid = child.getUniqueId();
        if (repository.exists(uuid)) {
            return;
        }
        HusbandryAnimal motherAnimal = HusbandryEntities.lookup(mother.getUniqueId()).orElse(null);
        HusbandryAnimal fatherAnimal = HusbandryEntities.lookup(father.getUniqueId()).orElse(null);
        int motherGenetics = motherAnimal == null ? 0 : motherAnimal.genetics();
        int fatherGenetics = fatherAnimal == null ? 0 : fatherAnimal.genetics();
        int motherCare = motherAnimal == null ? 0 : motherAnimal.care();
        int fatherCare = fatherAnimal == null ? 0 : fatherAnimal.care();
        long now = System.currentTimeMillis();
        double feedScale = HusbandryFeedQuality.consumeBreedingScale(
                mother.getUniqueId(), father.getUniqueId(), bredWith, now);
        int genetics = HusbandryGenetics.roll(
                motherGenetics, fatherGenetics, motherCare, fatherCare, ThreadLocalRandom.current(), feedScale);
        String name = HusbandryEntities.displayName(child.getType());
        child.setCustomName(name);
        child.setCustomNameVisible(false);
        HusbandryEntities.applyPersistFlags(child);
        HusbandryEntities.stampManaged(child);

        HusbandryAnimal baby = new HusbandryAnimal(uuid, child.getType().name(), name);
        baby.setState(HusbandryAnimalState.UNTAMED);
        baby.setGenetics(genetics);
        if (HusbandryMounts.isMount(child) && motherAnimal != null && fatherAnimal != null) {
            HusbandryMountGenes.inherit(baby, motherAnimal, fatherAnimal, feedScale, ThreadLocalRandom.current());
        }
        baby.setCare(0);
        baby.setStatsRevision(HusbandryConfig.statsRevision());
        baby.setLastProcessedAt(now);
        baby.setLoadedVisitStart(now);
        baby.setUnloadedAt(null);
        baby.setMatureAt(HusbandryGrowth.computeMatureAt(child.getType(), now));
        HusbandryHarvest.prepareNewAnimal(baby, child.getType());
        repository.upsertAnimal(baby);
        HusbandryEntities.putLoaded(baby);
        HusbandryGrowth.applyMaturity(child, baby, now);
    }

    private static void clearLove(Entity entity) {
        if (entity instanceof Animals animals) {
            animals.setLoveModeTicks(0);
        }
    }

    private enum BlockReason {
        NONE(""),
        UNAVAILABLE("§cCould not breed this animal."),
        NO_RECORD("§cThis animal cannot breed."),
        UNWELL("§cHungry or dirty animals cannot breed."),
        NEUTERED("§cNeutered animals cannot breed."),
        IMMATURE("§cThis animal is still growing up."),
        NOT_OWNER("§cYou must own both parents.");

        final String message;

        BlockReason(String message) {
            this.message = message;
        }
    }
}
