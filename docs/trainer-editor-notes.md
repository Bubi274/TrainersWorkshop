# Trainer Editor: research notes

These notes record how Radical Cobblemon Trainers (RCT) works and what that means for editing one
trainer in the world. They come from reading the class files of the exact versions in the
Cobbleverse modpack with `javap`:

- `rctmod-fabric-1.21.1-0.18.1-beta`
- `rctapi-fabric-1.21.1-0.15.2-beta`

## How RCT stores trainers

- A trainer in the world is a `TrainerMob` entity. Its only link to its data is a **trainer id**
  string (`getTrainerId()`), for example `youngster_yasu_027a`.
- Everything else comes from data packs and is **shared by every entity with that id**:
  - The team lives in `data/rctmod/trainers/<id>.json`. rctapi turns it into one `TrainerNPC`
    object per id, stored in `TrainerRegistry` (`ModCommon.RCT.getTrainerRegistry()`).
  - The loot table id comes from `TrainerMobData.getLootTableResource()`.
- When RCT reloads its data pack, it calls `TrainerRegistry.clearNPCs()` and registers every
  trainer again from JSON.
- So RCT has **no per-entity team or loot**. Changing the shared `TrainerNPC` would change every
  trainer with the same id, and the next data pack reload would undo it.

## Where a battle gets its team

`RCTMod.makeBattle(TrainerMob mob, PlayerEntity player)`:

1. `registry.getById(<player id>, TrainerPlayer.class)`: the player side.
2. `registry.getById(mob.getTrainerId(), TrainerNPC.class)`: the trainer side (the second
   `getById` call in the method).
3. `npc.setEntity(mob)`, then `BattleManager.startBattle(...)`, which **clones and heals** each
   Pokémon from `npc.getTeam()` for the battle.

## Where loot is dropped

`TrainerMob.finishBattle(TrainerBattle, boolean)`: when the trainer is defeated, it calls
`dropBattleLoot(Identifier lootTable, PlayerEntity player)` **once per win**. That method rolls the
loot table and drops the items at the trainer with `Entity.dropStack`.

## Conclusion: what is possible

Editing **one trainer entity** is possible without touching RCT's data packs:

| Feature | How |
|---|---|
| Per-entity team | A mixin wraps the second `getById` call in `RCTMod.makeBattle`. If this entity has a saved team, it returns our own `TrainerNPC`. That `TrainerNPC` copies the original's name, bag, AI, battle theme and gimmicks, but uses the edited team. It is registered in rctapi's registry as `trainers_workshop_<entity uuid>`, so rctapi can find it by original trainer (OT) during the battle. |
| Per-entity loot | A mixin injects at the start of `TrainerMob.dropBattleLoot`. It drops the custom loot (item, count, chance). It can then either let RCT's default loot table drop as well, or cancel it. |
| "Every win" or "first win per player" | We keep our own set of player UUIDs that have already received the custom loot from this entity. |
| Persistence across restarts | Overrides are saved in world data (`PersistentState`, file `data/trainers_workshop_trainers.dat` in the world folder), keyed by entity UUID. Entity UUIDs survive restarts. |
| Survives RCT data pack reload | Our `TrainerNPC` is rebuilt from saved data before each battle, so `clearNPCs()` does no harm. |
| Reset to default | Delete the override. RCT's normal team and loot come back. |

## Limitations

1. **Only that entity.** The edit is bound to one entity's UUID. If the trainer dies or is removed,
   the edit is deleted. Trainers that a *Trainer Spawner* block spawns again later are new entities,
   so they use RCT's default team.
2. **Despawning.** RCT trainers normally despawn when players walk away. Saving an edit therefore
   marks the trainer as persistent (`TrainerMob.setPersistent(true)`) so the edit is not lost.
   Reset to default does **not** make the trainer non-persistent again.
3. **Gimmicks** (Mega, Tera, Dynamax settings from the trainer JSON) are linked to the Pokémon's
   UUIDs. They carry over for Pokémon that are still the original ones in the same session. After
   a restart or data pack reload the original team gets new UUIDs, so an edited team **loses its
   gimmick settings**.
4. **Battle memory, defeat counts, series progress, advancements, cooldowns and dialog** still use
   the trainer id. They are not per-entity.
5. **Mixin targets.** The hooks target RCT 0.18.1-beta. A later RCT version may rename or change
   `RCTMod.makeBattle` or `TrainerMob.dropBattleLoot`. The mixins are written not to crash the game
   in that case (`require = 0`), and the mod logs a warning at startup if the methods are missing.
   The Trainer Editor screen still opens, but the edits would have no effect in battle.
6. Changes are used from the **next** battle. A battle that is already running keeps its team.
