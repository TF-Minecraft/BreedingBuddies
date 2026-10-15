package net.tfminecraft.breedingbuddies.husbandry;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;

public final class HusbandryTamingListener implements Listener {

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null || !HusbandryItems.matches(result, HusbandryConfig.tameItem())) {
            return;
        }
        AnvilView anvil = event.getView();
        String rename = anvil.getRenameText();
        if (rename == null || rename.isBlank()) {
            return;
        }
        HusbandryItems.setTameName(result, rename);
        event.setResult(result);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        Entity clicked = event.getRightClicked();

        if (HusbandryItems.matches(hand, HusbandryConfig.coOwnItem())) {
            event.setCancelled(true);
            if (acceptsLinkedToken(player, hand)) {
                handleCoOwnOnPlayer(player, player, hand);
            } else if (clicked instanceof Player target) {
                handleCoOwnOnPlayer(player, target, hand);
            } else if (clicked instanceof LivingEntity living) {
                handleCoOwnLink(player, living, hand);
            }
            return;
        }

        if (!HusbandryItems.matches(hand, HusbandryConfig.tameItem())) {
            return;
        }
        event.setCancelled(true);
        if (!(clicked instanceof LivingEntity living)) {
            return;
        }
        handleTame(player, living, hand);
    }

    @EventHandler
    public void onInteractUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack hand = event.getItem();
        if (!HusbandryItems.matches(hand, HusbandryConfig.coOwnItem())) {
            return;
        }
        if (!acceptsLinkedToken(event.getPlayer(), hand)) {
            return;
        }
        event.setCancelled(true);
        handleCoOwnOnPlayer(event.getPlayer(), event.getPlayer(), hand);
    }

    private static boolean acceptsLinkedToken(Player player, ItemStack hand) {
        UUID linked = HusbandryCoOwnToken.parse(HusbandryItems.linkedAnimal(hand));
        return HusbandryCoOwnToken.accepts(
                linked,
                HusbandryOwnershipService.hasAnyOwner(linked),
                HusbandryOwnershipService.isOwner(player, linked));
    }

    private static void handleTame(Player player, LivingEntity entity, ItemStack hand) {
        String name = HusbandryItems.tameName(hand);
        if (name == null) {
            player.sendMessage("§cName this item on an anvil first.");
            return;
        }
        if (entity instanceof Tameable tameable && !tameable.isTamed()) {
            player.sendMessage("§cTame this animal first.");
            return;
        }
        if (HusbandryConfig.species(entity.getType()) == null) {
            player.sendMessage("§cThis animal cannot be tamed.");
            return;
        }
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        Optional<HusbandryAnimal> stored = HusbandryEntities.lookup(entity.getUniqueId());
        HusbandryAnimal animal;
        if (stored.isEmpty()) {
            if (!HusbandryOwnershipService.canAccept(player)) {
                player.sendMessage("§cYou already own too many animals.");
                return;
            }
            animal = HusbandrySpawner.createWildRecord(entity);
        } else {
            animal = stored.get();
        }
        boolean alreadyMine = HusbandryOwnershipService.isOwner(player, animal.uuid());
        HusbandryOwnershipService.Result result =
                HusbandryOwnershipService.claimOwner(player, animal, entity, name);
        switch (result) {
            case HELD_OWNER -> player.sendMessage("§cThis animal has an unresolved estate; ask staff.");
            case CHARACTER_REQUIRED -> player.sendMessage("§cChoose a character before owning animals.");
            case AT_CAP -> player.sendMessage("§cYou already own too many animals.");
            case NOT_OWNER -> player.sendMessage("§cThis animal already has an owner.");
            case OK -> {
                HusbandryEntities.applyPersistFlags(entity);
                HusbandryItems.useFromMainHand(player);
                if (alreadyMine) {
                    player.sendMessage("§aRenamed to " + name + ".");
                } else {
                    HusbandryFx.playTame(entity);
                    player.sendMessage("§aTamed " + name + ".");
                }
            }
        }
    }

    // Keep the existing legacy text representation, formatting, and exact-string comparisons.
    @SuppressWarnings("deprecation")
    private static void handleCoOwnLink(Player player, LivingEntity entity, ItemStack hand) {
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        Optional<HusbandryAnimal> stored = HusbandryEntities.lookup(entity.getUniqueId());
        if (stored.isEmpty()) {
            player.sendMessage("§cThis animal cannot be shared.");
            return;
        }
        HusbandryAnimal animal = stored.get();
        if (!HusbandryOwnershipService.isOwner(player, animal.uuid())) {
            player.sendMessage("§cThis is not your animal.");
            return;
        }
        String label = animal.name() != null && !animal.name().isBlank()
                ? animal.name()
                : HusbandryEntities.displayName(entity.getType());
        HusbandryItems.setLinkedAnimal(
                hand,
                animal.uuid().toString(),
                ChatColor.GRAY + "Linked to " + label);
        player.sendMessage("§aLinked to " + label + ".");
        HusbandryFx.playBond(entity);
    }

    private static void handleCoOwnOnPlayer(Player actor, Player target, ItemStack hand) {
        String linked = HusbandryItems.linkedAnimal(hand);
        if (linked == null || linked.isBlank()) {
            actor.sendMessage("§cLink this token to your animal first.");
            return;
        }
        UUID animalUuid;
        try {
            animalUuid = UUID.fromString(linked);
        } catch (IllegalArgumentException ignored) {
            actor.sendMessage("§cThis token does not name any animal.");
            return;
        }
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return;
        }
        Optional<HusbandryAnimal> stored = HusbandryEntities.lookup(animalUuid);
        if (stored.isEmpty()) {
            actor.sendMessage("§cThat animal can no longer be found.");
            return;
        }
        HusbandryAnimal animal = stored.get();
        HusbandryOwnershipService.Result result =
                HusbandryOwnershipService.addCoOwner(actor, target, animal);
        switch (result) {
            case CHARACTER_REQUIRED -> actor.sendMessage("§cBoth players must choose a character first.");
            case HELD_OWNER -> actor.sendMessage("§cThis animal has an unresolved owner; ask staff.");
            case NOT_OWNER -> actor.sendMessage("§cThis is not your animal.");
            case ALREADY_TARGET_OWNER -> actor.sendMessage("§eAlready an owner of this animal.");
            case TARGET_AT_CAP -> actor.sendMessage("§cThat player already owns too many animals.");
            case OK -> {
                HusbandryItems.consumeOne(hand);
                HusbandryFx.playBond(target);
                String label = animal.name() != null && !animal.name().isBlank() ? animal.name() : "animal";
                if (actor.equals(target)) {
                    actor.sendMessage("§aYou are now a co-owner of " + label + ".");
                } else {
                    actor.sendMessage("§aAdded " + target.getName() + " as a co-owner.");
                    target.sendMessage("§aYou are now a co-owner of " + label + ".");
                }
            }
        }
    }
}
