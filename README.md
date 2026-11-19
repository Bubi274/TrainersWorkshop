# Trainer's Workshop

A Cobblemon addon (Fabric, Minecraft 1.21.1) with tools for trainers and server admins.

## Items

All items are in the **Trainer's Workshop** creative tab.

| Item | ID | How to get it | Who can use it |
| --- | --- | --- | --- |
| Shiny Berry | `trainers_workshop:shiny_berry` | 7-star raid reward (with Cobblemon Raid Dens), creative tab | Everyone |
| PokeEditor | `trainers_workshop:pokeeditor` | Crafting (see below), creative tab | Everyone |
| PokeEditor (Creative) | `trainers_workshop:pokeeditor_creative` | Creative tab only | Operators (permission level 2) |
| Trainer Editor | `trainers_workshop:trainer_editor` | Creative tab only | Operators (permission level 2) |

All checks and changes run on the server. The client only sends requests, and the server checks
every request again.

### Shiny Berry

- Right-click one of **your own** sent-out Pokémon to make it shiny.
- Uses up one berry (not in creative mode). If the Pokémon is already shiny, the berry is not used.
- Doesn't work on wild Pokémon, other trainers' Pokémon, or Pokémon in battle.
- Has no crafting recipe, can't be planted, and can't be eaten.
- With Cobblemon Raid Dens installed, it can drop as a 7-star raid reward (5% by default, see Config).

### PokeEditor (Creative)

Right-click in the air to open the editor for your party. Everything is free:

- IVs (0–31), EVs (0–252 each, 510 total)
- Nature
- Moves (any move the species can learn)
- Shiny, ability, gender, level, Poké Ball

Click a `+`/`-` button to change a value by 1, or shift-click to change it by 10. Changes apply to
the selected Pokémon when you press **Apply**. Switching to another Pokémon discards changes you
haven't applied.

### PokeEditor (Survival)

Same screen, but every change costs items from your inventory. The items are taken when you press
**Apply**. If an item is missing, that part turns red and **Apply** is locked. In creative mode no
items are used.

| Change | Cost |
| --- | --- |
| Raise or lower an effective IV by 1 | The matching IV candy (Cobblemon): Health, Mighty, Tough, Smart, Courage or Quick Candy to raise; Sickly, Weak, Brittle, Numb, Coward or Slow Candy to lower |
| Set one IV to 31 / to 0 | The matching Bottle Cap / Withered Bottle Cap (Only Bottle Caps) |
| Set all six IVs to 31 / to 0 | One Gold Bottle Cap / Gold Withered Bottle Cap (Only Bottle Caps) |
| Raise an EV | A vitamin (+10) or a feather (+1) for that stat (Cobblemon) |
| Lower an EV | An EV-reducing berry for that stat, such as Pomeg or Kelpsy (−10 each) |
| Change nature | The mint for the new nature (Cobblemon). Works exactly like using the mint normally. |
| Change ability | An Ability Capsule (to the other common ability) or an Ability Patch (to the hidden ability), from Cobblemon. Like in Cobblemon 1.8, each Ability Capsule gives back an empty Glass Bottle. |
| Switch to a move the Pokémon has already learned | Free |
| Teach an egg move that no TM can teach | Type Gems of the move's type (Cobblemon 1.8), for example 3 Water Gems for Aqua Jet. The amount is `eggMoveGemCost` in the config (default 3). |
| Teach a new move | One TM for that move: a Cobblemon TM (`cobblemon:technical_machine`) or a TMCraft TM. If you have both, the Cobblemon TM is used. The TM is used up, except a Cobblemon TM when Cobblemon's `infiniteTmUses` config is on. |
| Make shiny | One Shiny Berry |
| Raise the level by 1 | One Rare Candy (Cobblemon). See below. |

IVs work the same way as in Cobblemon. Candies change the **effective IV** (Cobblemon's hyper training),
not the base IV. Bottle Caps do exactly what Only Bottle Caps does on your server:

- Only Bottle Caps 1.5 or newer with `breedingBuff = false` (the default, and the COBBLEVERSE 1.8.0
  setting in `config/obc-common.toml`): caps change the **effective IV** with hyper training. The base
  IV stays the same, so it isn't passed on by breeding.
- Only Bottle Caps 1.5 or newer with `breedingBuff = true`: caps change **both** the base IV and the
  effective IV.
- Only Bottle Caps 1.3: caps change the **base IV** only.

A cap is locked when it would change nothing (Only Bottle Caps refuses it too). A Gold cap changes every
stat it can. When a Pokémon has an effective IV, that value is used in battle. The IV tab shows how many of each item you have next
to its button. Buttons are locked when you don't have enough items or the IV would leave 0–31. When you
press **Apply**, caps are used first, then candies.

**TMs.** A Cobblemon TM can teach the moves Cobblemon's own TMs teach (the species' TM moves). A TMCraft
TM can teach the species' TM moves and level-up moves. The Moves tab and the Cost list show which TM
will be used (for example "Cobblemon TM: Thunderbolt" or "TMCraft TM-24: Thunderbolt").

**Level.** The level can only go up, never down. The highest level you can reach is the lower of your
Radical Cobblemon Trainers level cap and Cobblemon's maximum level (without RCT, only Cobblemon's maximum
applies). At the cap, the buttons are locked. Each level costs one Rare Candy: going from Lv 20 to Lv 23
costs 3. **+1** raises one level and **Max** raises to the lower of the cap and your level plus the Rare Candies
you have. The EXP is added through
Cobblemon's normal EXP system, so the Pokémon learns level-up moves as usual. It never evolves by itself:
Cobblemon shows its usual evolution prompt and you choose.

Gender and Poké Ball can't be changed in Survival.

**Recipe** (shaped):

```text
.  .  B      B = Poké Ball (cobblemon:poke_ball)
.  S  .      S = Stick
S  .  .
```

### Trainer Editor

Right-click a trainer from Radical Cobblemon Trainers (RCT) to edit **that one trainer**:

- **Team** (up to 6 Pokémon): type a species name, then **Set** (replace the selected Pokémon) or
  **Add**. **Remove** deletes the selected Pokémon. Each Pokémon can be edited like in the Creative
  PokeEditor.
- **Battle** tab: per Pokémon, the **Tera** type it Terastallizes into (or Off), **Dynamax**,
  **Gigantamax** (only for species that have a G-Max form; changing to a species without one turns it
  off) and the **held item** (copy the item in your main hand, or clear it). Mega Evolution comes from a
  held Mega Stone. RCT trainers use these gimmicks through Mega Showdown. Editing anything else, or
  changing the species, keeps the slot's gimmick settings.
- **Alpha** (Battle tab): makes the Pokémon an Alpha (bigger, alpha eyes and mark). Because Cobblemon only
  gives the Alpha battle boost in wild battles, the editor also sets all IVs to 31, adds levels
  (`alphaTrainerLevelBonus` in the config, default 5, never above level 100) and, like a wild Alpha, teaches
  a random TM move of the species if it knows none. Turning Alpha off restores the IVs, level and moves.
  **Trainer Alphas get real stat boosts from IVs/levels, not the wild Alpha battle rule.**
- **Loot** tab: add the item in your main hand (Shiny Berries work too), then set its count and drop
  chance. Choose whether the custom loot drops on **every win** or only on **each player's first
  win**, and whether the trainer's **default loot** still drops.
- **Save** stores the changes in the world. They are used from the trainer's next battle and survive
  server restarts. Saving also stops the trainer from despawning.
- **Reset** deletes all edits right away and brings back the trainer's original team and loot.

Limits: edits belong to that trainer entity only. Trainers that a spawner spawns again later use their
default team. Teams saved with version 2.1.0 get their gimmicks back from the original team when the
species in the same slot still matches. Details are in
[docs/trainer-editor-notes.md](docs/trainer-editor-notes.md).

## Dependencies

| Mod | Version | Required |
| --- | --- | --- |
| Minecraft | 1.21.1 | yes |
| Fabric Loader | 0.19.5 or newer | yes |
| Fabric API | 0.116.18+1.21.1 | yes |
| Cobblemon | 1.8.4+1.21.1 (Fabric) | yes |
| Fabric Language Kotlin | 1.14.2+kotlin.2.4.21 or newer | yes |
| Cobblemon Raid Dens | 0.13.5+1.21.1 | no: Shiny Berry raid drop |
| Radical Cobblemon Trainers (rctmod + rctapi) | 0.19.2-beta / 0.16.2-beta | no: Trainer Editor, level cap in the Survival PokeEditor |
| TMCraft | 1.4.19+1.8.4 | no: TMCraft TMs in the Survival PokeEditor (Cobblemon TMs work without it) |
| Only Bottle Caps | 1.5.0 | no: raising IVs in the Survival PokeEditor |

Without an optional mod, the game still runs. The features that need it show as unavailable.

Install the mod on **both the server and the client**.

## Download a build

GitHub builds the mod on every push (workflow `.github/workflows/build.yml`), so you don't need to build it yourself:

1. Open the repository on GitHub and click the **Actions** tab.
2. Click the newest **Build** run with a green check mark for your branch.
3. At the bottom of the run page, under **Artifacts**, click `trainers_workshop-<version>` to download a zip.
4. Unzip it and copy `trainers_workshop-<version>.jar` into your `mods` folder.

Artifacts are kept for 30 days. If a build fails, the run also has a `test-reports` artifact.
Pushing a tag such as `v3.0.0` also creates a GitHub Release with the jar attached (see **Releases**).

## Building

Requires Java 21. From the project folder, run:

```bash
./gradlew build
```

The mod file is `build/libs/trainers_workshop-3.0.0.jar`. Don't use the `-sources.jar` file.

The first build downloads Minecraft and its libraries, so it takes a few minutes and needs access to
`piston-meta.mojang.com`, `libraries.minecraft.net`, `maven.fabricmc.net`, `maven.impactdev.net` and
`api.modrinth.com`.

## Testing in a dev environment

```bash
./gradlew runClient
```

This launches Minecraft with Fabric API, Cobblemon and this mod. The optional mods are not included,
so test the raid drop, Trainer Editor, TMs and Bottle Caps in the real modpack.

## Installing in a modpack

1. Copy `trainers_workshop-3.0.0.jar` into the `mods` folder of your COBBLEVERSE 1.8.0 instance.
2. For a server, also copy it into the server's `mods` folder.

## Config

On first launch, the mod creates `config/trainers_workshop.json`:

```json
{
  "raidTierSevenShinyBerryChance": 0.05,
  "eggMoveGemCost": 3,
  "alphaTrainerLevelBonus": 5
}
```

- `raidTierSevenShinyBerryChance` is the chance for a 7-star raid to drop a Shiny Berry, from `0.0` to `1.0`
  (`0.05` = 5%, `0` = disabled).
- `alphaTrainerLevelBonus` is how many levels the Trainer Editor's Alpha toggle adds (`0` to `100`; the result never goes above level 100).
- `eggMoveGemCost` is how many Type Gems teaching one egg move costs in the Survival PokeEditor (`1` to `64`).
- After editing, restart the server to apply the change (`/reload` also re-reads the file when Cobblemon Raid Dens is installed).
- To confirm the raid integration loaded, look for `Added Shiny Berry to cobblemonraiddens:raid/tier/tier_seven`
  in `logs/latest.log`.

## Source layout

```text
src/main/kotlin/io/github/bubi274/trainersworkshop/
  TrainersWorkshop.kt        mod entry point
  config/                    config file
  registry/                  item and creative tab registration
  item/                      individual items
  interaction/               right-click-on-entity handling shared by the tools
  editor/                    editor data, server-side checks and changes, survival costs, trainer edits
  network/                   packets between client and server
  client/                    client entry point and the editor screen
  compat/                    integrations with other mods (Raid Dens, RCT)
src/main/java/.../mixin/     mixins into RCT (only applied when RCT is installed)
docs/                        design notes
```
