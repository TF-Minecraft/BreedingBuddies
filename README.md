# BreedingBuddies

> Animal care, breeding, and mount development for TF-Minecraft.

BreedingBuddies gives farm animals individual ownership, care routines, and
inherited traits. Players build relationships with their animals over time,
while breeding connects the next generation to the animals they have raised.

## Features

- **Animal ownership** — track owned animals and share responsibility through
  co-ownership.
- **Daily care** — feeding and tending animals increases friendship, with
  daily records of the attention each animal has received.
- **Inherited traits** — offspring genetics reflect their parents' genetics
  and friendship, with variation between generations.
- **Mount development** — pass health, movement speed, and jump characteristics
  from parent mounts to their offspring.
- **Reward bundles** — collect tiered bundles from happy animals, with better
  tiers for higher friendship and genetics.
- **Stable chunks** — animals should spend the night in claimed stable chunks
  with water and room, or they eventually escape.
- **Farm management** — inspect animal information and farm reports, with
  ownership checks around breeding and interactions.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/BreedingBuddies/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## Tests and coverage

Run `mvn clean verify` with Java 21 after preparing the pinned plugin
dependencies, as the Build workflow does. JUnit 5, Mockito, and MockBukkit cover
ownership, taming, care, breeding, bundles, stable chunks, mount stats, day
changes, saved data, and the admin commands. Surefire test results are in
`target/surefire-reports/`; JaCoCo's HTML and XML reports are in
`target/site/jacoco/`. The build enforces **100% line coverage** across all
production classes, with no exclusions, and CI uploads both reports. Branch and
instruction coverage are reported but not enforced. The suite stubs MMOItems and
MythicLib, so live servers, real MMOItems content, and real players still need
server testing.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
