package net.tfminecraft.breedingbuddies.husbandry;

/** A serialized copy of an owned animal's entity, taken while it was loaded. */
public record HusbandrySnapshot(byte[] data, long savedAt) {}
