# Trainer's Workshop

A Cobblemon addon (Fabric, Minecraft 1.21.1) with tools for trainers and server admins.

## Features

**Shiny Berry** (`trainers_workshop:shiny_berry`)

- Right-click one of **your own** sent-out Pokémon to make it shiny.
- Uses up one berry (not in creative mode). If the Pokémon is already shiny, the berry is not used.
- Doesn't work on wild Pokémon, other trainers' Pokémon, or Pokémon in battle.
- Has no crafting recipe, can't be planted, and can't be eaten.
- With Cobblemon Raid Dens installed, it can drop as a 7-star raid reward (5% by default).
- Available in the "Trainer's Workshop" creative tab.

## Dependencies

| Mod | Version | Required |
| --- | --- | --- |
| Minecraft | 1.21.1 | yes |
| Fabric Loader | 0.17.2 or newer | yes |
| Fabric API | 0.116.17+1.21.1 | yes |
| Cobblemon | 1.7.3+1.21.1 (Fabric) | yes |
| Fabric Language Kotlin | 1.13.6 or newer (bundled with Cobblemon) | yes |
| Cobblemon Raid Dens | any | no |

Install the mod on **both the server and the client**.

## Building

Requires Java 21. From the project folder, run:

```bash
./gradlew build
```

The mod file is `build/libs/trainers_workshop-1.0.0.jar`. Don't use the `-sources.jar` file.

The first build downloads Minecraft and its libraries, so it takes a few minutes and needs access to
`piston-meta.mojang.com`, `libraries.minecraft.net`, `maven.fabricmc.net` and `maven.impactdev.net`.

## Testing in a dev environment

```bash
./gradlew runClient
```

This launches Minecraft with Fabric API, Cobblemon and this mod installed.

## Installing in a modpack

1. Copy `trainers_workshop-1.0.0.jar` into the `mods` folder of your Cobbleverse instance.
2. For a server, also copy it into the server's `mods` folder.

## Config

On first launch, the mod creates `config/trainers_workshop.json`:

```json
{
  "raidTierSevenShinyBerryChance": 0.05
}
```

- `raidTierSevenShinyBerryChance` is the chance for a 7-star raid to drop a Shiny Berry, from `0.0` to `1.0`
  (`0.05` = 5%, `0` = disabled).
- After editing, run `/reload` or restart the server to apply the change.
- To confirm the raid integration loaded, look for `Added Shiny Berry to cobblemonraiddens:raid/tier/tier_seven`
  in `logs/latest.log`.

## Source layout

```text
src/main/kotlin/io/github/bubi274/trainersworkshop/
  TrainersWorkshop.kt        mod entry point
  config/                    config file
  registry/                  item and creative tab registration
  item/                      individual items (ShinyBerryItem, ...)
  interaction/               right-click-on-Pokémon handling (shared by future tools)
  compat/                    integrations with other mods (Raid Dens)
```
