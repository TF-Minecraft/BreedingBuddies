# BreedingBuddies

> Animal care, breeding, and mount development for TF-Minecraft.

BreedingBuddies gives farm animals individual ownership, care routines, and
inherited traits. Players build relationships with their animals over time,
while breeding connects the next generation to the animals they have raised.

## Features

- **Animal ownership** — track owned animals and share responsibility through
  co-ownership.
- **Daily care** — feeding hungry animals, brushing dirty ones, and daily
  petting or riding earns trust, displayed as five hearts.
- **Inherited traits** — offspring genetics reflect their parents' genetics
  and trust, with variation between generations.
- **Mount development** — pass health, movement speed, and jump characteristics
  from parent mounts to their offspring.
- **Animal products** — collect milk, wool, manure, and other species-specific
  products, with care and genetics affecting yields.
- **Farm management** — inspect animal information, share ownership, and find
  animals with `/animals`, with character ownership and estate inheritance.

## Upgrading to 1.2

Requires Cooking **0.5.1** and TLibs **2.6.0**. Before starting the new version,
set `enabled: false` in `plugins/Cooking/husbandry.yml`, install BreedingBuddies,
and create its writable `plugins/BreedingBuddies/Data` directory if the host
does not allow the server to create plugin folders. Activate the switch with a
server restart.

The first start copies Cooking's `Data/husbandry.db` and its journal, preserving
the originals as a backup. Existing Cooking animal and ownership keys remain
compatible. Settings start from BreedingBuddies' own `config.yml` defaults;
review them before activation. Staff need `breedingbuddies.admin` for
`/bb reload`, `/bb spawn`, and `/bb save`.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/BreedingBuddies/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## Tests and coverage

Run `mvn clean verify` with Java 21 after preparing the pinned plugin
dependencies, as the Build workflow does. JUnit 5, Mockito, and MockBukkit cover
ownership, taming, trust, breeding, products, mount stats, simulation,
database migration, saved data, and the admin commands. Surefire test results are in
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
