package net.tfminecraft.breedingbuddies.husbandry;

import java.util.List;
import java.util.UUID;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class HusbandryOwnershipService {

    public enum Result {
        OK,
        NO_REPOSITORY,
        AT_CAP,
        ALREADY_OWNER,
        NOT_OWNER,
        TARGET_AT_CAP,
        ALREADY_TARGET_OWNER,
        CHARACTER_REQUIRED,
        HELD_OWNER
    }

    private HusbandryOwnershipService() {}

    public static boolean isStaff(Player player) {
        return player != null && player.hasPermission("breedingbuddies.admin");
    }

    public static boolean canAccept(Player player) {
        if (player == null || HusbandryIdentity.session(player).isEmpty()) {
            return false;
        }
        if (isStaff(player)) {
            return true;
        }
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return false;
        }
        return repository.countForPlayer(player.getUniqueId()) < HusbandryConfig.maxAnimals();
    }

    private static boolean canAccept(Player player, UUID animal) {
        HusbandryRepository repository = HusbandryEntities.repository();
        return repository != null && (repository.accountHasAnimal(player.getUniqueId(), animal) || canAccept(player));
    }

    public static boolean isOwner(Player player, UUID animalUuid) {
        return HusbandryIdentity.session(player).map(session -> isOwner(session.ref(), animalUuid)).orElse(false);
    }

    /** Account-only callers cannot acquire a character's permission. */
    @Deprecated
    public static boolean isOwner(UUID account, UUID animal) { return false; }

    public static boolean isOwner(net.tfminecraft.tlibs.character.CharacterRef ref, UUID animalUuid) {
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null || ref == null || animalUuid == null) return false;
        return repository.listOwners(animalUuid).stream().anyMatch(owner -> ref.equals(owner.ref()));
    }

    public static boolean hasAnyOwner(UUID animalUuid) {
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null || animalUuid == null) {
            return false;
        }
        return repository.hasAnyOwner(animalUuid);
    }

    public static List<HusbandryOwner> listOwners(UUID animalUuid) {
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null) {
            return List.of();
        }
        return repository.listOwners(animalUuid);
    }

    public static Result claimOwner(Player player, HusbandryAnimal animal, LivingEntity entity, String name) {
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null || player == null || animal == null) {
            return Result.NO_REPOSITORY;
        }
        var session = HusbandryIdentity.session(player).orElse(null);
        if (session == null) return Result.CHARACTER_REQUIRED;
        if (repository.isEstateHeld(animal.uuid())) return Result.HELD_OWNER;
        if (isOwner(player, animal.uuid())) {
            applyName(animal, entity, name);
            repository.upsertAnimal(animal);
            return Result.OK;
        }
        if (hasAnyOwner(animal.uuid())) {
            return Result.NOT_OWNER;
        }
        if (!canAccept(player)) {
            return Result.AT_CAP;
        }
        applyName(animal, entity, name);
        animal.setState(HusbandryAnimalState.OWNED);
        repository.saveAnimalAndOwner(animal,
                new HusbandryOwner(animal.uuid(), player.getUniqueId(), session.ref().characterId(), "owner"));
        return Result.OK;
    }

    public static Result addCoOwner(Player actor, Player target, HusbandryAnimal animal) {
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null || actor == null || target == null || animal == null) {
            return Result.NO_REPOSITORY;
        }
        var actorSession = HusbandryIdentity.session(actor).orElse(null);
        var targetSession = HusbandryIdentity.session(target).orElse(null);
        if (actorSession == null || targetSession == null) return Result.CHARACTER_REQUIRED;
        if (repository.isEstateHeld(animal.uuid())) return Result.HELD_OWNER;
        if (!hasAnyOwner(animal.uuid())) {
            return Result.NOT_OWNER;
        }
        boolean selfClaim = actorSession.ref().equals(targetSession.ref());
        if (selfClaim && repository.hasHeldOwner(animal.uuid())) return Result.HELD_OWNER;
        if (!selfClaim && !isOwner(actor, animal.uuid())) {
            return Result.NOT_OWNER;
        }
        if (isOwner(target, animal.uuid())) {
            return Result.ALREADY_TARGET_OWNER;
        }
        if (!canAccept(target, animal.uuid())) {
            return Result.TARGET_AT_CAP;
        }
        animal.setState(HusbandryAnimalState.OWNED);
        repository.saveAnimalAndOwner(animal,
                new HusbandryOwner(animal.uuid(), target.getUniqueId(), targetSession.ref().characterId(), "coowner"));
        return Result.OK;
    }

    public static Result removeSelf(Player player, HusbandryAnimal animal) {
        HusbandryRepository repository = HusbandryEntities.repository();
        if (repository == null || player == null || animal == null) {
            return Result.NO_REPOSITORY;
        }
        if (repository.isEstateHeld(animal.uuid())) return Result.HELD_OWNER;
        if (!isOwner(player, animal.uuid())) {
            return Result.NOT_OWNER;
        }
        repository.deleteCharacterOwner(animal.uuid(), HusbandryIdentity.session(player).orElseThrow().ref().characterId());
        if (!hasAnyOwner(animal.uuid())) {
            animal.setState(HusbandryAnimalState.UNTAMED);
            repository.upsertAnimal(animal);
        }
        return Result.OK;
    }

    public static void applyName(HusbandryAnimal animal, LivingEntity entity, String name) {
        String sanitized = sanitizeName(name, animal, entity);
        animal.setName(sanitized);
        if (entity != null && entity.isValid()) {
            HusbandryStateDisplay.sync(entity, animal);
        }
    }

    private static String sanitizeName(String name, HusbandryAnimal animal, LivingEntity entity) {
        if (name != null && !name.isBlank() && !"???".equals(name.trim())) {
            return name.trim();
        }
        if (animal != null && animal.name() != null && !animal.name().isBlank()
                && !"???".equals(animal.name().trim())) {
            return animal.name();
        }
        if (entity != null) {
            return HusbandryEntities.displayName(entity.getType());
        }
        return "Animal";
    }
}
