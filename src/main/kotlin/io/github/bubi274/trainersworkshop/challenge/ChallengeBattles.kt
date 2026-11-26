package io.github.bubi274.trainersworkshop.challenge

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.config.WorkshopConfig
import io.github.bubi274.trainersworkshop.registry.ModItems
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier

/**
 * Challenge battles: the party check before a battle, the result after it, and the rewards.
 * Called from RctAccess (the RCT mixins); has no RCT classes itself.
 *
 * Until the screens of part 4 exist, the choices (Defeat, Shiny reward) are clickable chat lines that run
 * /challenge defeat … and /challenge shiny ….
 */
object ChallengeBattles {
    private const val KEY = "message.${TrainersWorkshop.MOD_ID}.challenge"

    private fun role(trainerId: String): TrainerRole? = ChallengeTrainers.role(WorkshopConfig.data.challengeRegions, trainerId)

    /** Right before RCT starts a battle with [trainerId]: returns true to cancel it (the player is told why). */
    fun shouldBlock(player: ServerPlayerEntity, trainerId: String): Boolean {
        role(trainerId) ?: return false
        val data = ChallengeService.data(player)
        val active = data.active ?: return false
        if (active.defeatPending) {
            player.sendMessage(Text.translatable("$KEY.blocked_defeat").formatted(Formatting.RED), false)
            player.sendMessage(defeatChoices(), false)
            return true
        }
        if (active.rerollAllow != null) {
            player.sendMessage(Text.translatable("$KEY.blocked_reroll").formatted(Formatting.RED), false)
            player.sendMessage(ChallengeCommand.rerollLink(ChallengeService.rerollCode(player)), false)
            return true
        }
        val party = Cobblemon.storage.getParty(player).toList()
        val result = checkParty(player, active, party)
        if (result.valid) return false
        player.sendMessage(Text.translatable("$KEY.blocked_party").formatted(Formatting.RED), false)
        result.problems.forEach { problem ->
            val name = party[problem.member].getDisplayName(false).copy()
            val reason = Text.translatable("$KEY.reason.${problem.reason.name.lowercase()}")
            player.sendMessage(Text.literal("- ").append(name).append(Text.literal(": ")).append(reason).formatted(Formatting.GRAY), false)
        }
        return true
    }

    /** Checks [party] against the active team. The party is recorded first, so a missed event can't change the result. */
    fun checkParty(player: ServerPlayerEntity, active: ActiveChallenge, party: List<Pokemon>): PartyCheck.Result {
        ChallengeService.sweep(player)
        val data = ChallengeService.data(player)
        val members = party.map { pokemon ->
            val key = CobblemonForms.key(pokemon)
            val slots = active.challenge.team.indices.filter { key in CobblemonForms.allowed(active.challenge.team[it]) }.toSet()
            PartyCheck.Member(data.obtainedDuringChallenge(pokemon.uuid), slots)
        }
        return PartyCheck.check(members)
    }

    /** RCT finished a battle between [player] and the trainers [trainerIds] ([won] = the player won). */
    fun onResult(player: ServerPlayerEntity, trainerIds: List<String>, won: Boolean) {
        val (trainerId, role) = trainerIds.firstNotNullOfOrNull { id -> role(id)?.let { id to it } } ?: return
        val state = ChallengeState.get(player.server)
        val data = state.player(player.uuid)
        val active = data.active ?: return
        if (!won) {
            state.recordLoss(player.uuid)
            player.sendMessage(Text.translatable("$KEY.defeat").formatted(Formatting.RED), false)
            player.sendMessage(defeatChoices(), false)
            return
        }
        state.complete(player.uuid)
        player.sendMessage(Text.translatable("$KEY.victory", active.challenge.id).formatted(Formatting.GREEN, Formatting.BOLD), false)
        when (role.kind) {
            TrainerRole.Kind.GYM_LEADER -> Unit
            TrainerRole.Kind.ELITE_FOUR -> {
                data.eliteFourBeaten += trainerId
                val region = role.region
                if (region.id !in data.eliteFourRegionsRewarded && ChallengeTrainers.eliteFourComplete(region, data.eliteFourBeaten)) {
                    data.eliteFourRegionsRewarded += region.id
                    give(player, Identifier.of("cobblemon", "master_ball"), 1)
                    player.sendMessage(Text.translatable("$KEY.reward_master_ball", region.id).formatted(Formatting.GOLD), false)
                }
                state.markDirty()
            }
            TrainerRole.Kind.CHAMPION -> {
                if (data.championsRewarded.add(role.memberKey)) {
                    giveItem(player, ModItems.SHINY_BERRY, 1)
                    player.sendMessage(Text.translatable("$KEY.reward_shiny_berry").formatted(Formatting.GOLD), false)
                }
                state.markDirty()
            }
        }
        player.sendMessage(Text.translatable("$KEY.victory_next").formatted(Formatting.GRAY), false)
    }

    /** Gives the 128 feathers of [stat] for shiny slot [slot] (0-5) of the active team. */
    fun claimShiny(player: ServerPlayerEntity, slot: Int, stat: String) {
        val data = ChallengeService.data(player)
        if (data.active == null) throw ChallengeActionException("You have no challenge.")
        if (slot !in data.unpaidShinySlots()) throw ChallengeActionException("Slot ${slot + 1} has no shiny reward waiting.")
        val feather = ShinyReward.feather(stat) ?: throw ChallengeActionException("Unknown stat \"$stat\". Pick one of: ${ShinyReward.FEATHERS.keys.joinToString()}.")
        ChallengeState.get(player.server).markShinyPaid(player.uuid, slot)
        give(player, Identifier.of(feather), ShinyReward.FEATHERS_PER_SLOT)
    }

    private fun give(player: ServerPlayerEntity, id: Identifier, count: Int) {
        val item = Registries.ITEM.getOrEmpty(id).orElse(null)
        if (item == null) {
            TrainersWorkshop.LOGGER.warn("[Challenge] Item {} doesn't exist; reward not given to {}", id, player.name.string)
            return
        }
        giveItem(player, item, count)
    }

    /** Puts the items in the inventory; whatever doesn't fit drops next to the player. */
    private fun giveItem(player: ServerPlayerEntity, item: Item, count: Int) {
        ShinyReward.stacks(count, item.maxCount).forEach { size ->
            val stack = ItemStack(item, size)
            player.inventory.insertStack(stack)
            if (!stack.isEmpty) player.dropItem(stack, false)
        }
    }

    // ---------- Chat choices (until the screens of part 4) ----------

    private fun button(label: Text, command: String, hover: Text): MutableText =
        Text.literal("[").append(label).append("]").formatted(Formatting.AQUA).styled {
            it.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
        }

    /** [Keep going] [Reroll 1] [Reroll all 6] */
    fun defeatChoices(): Text = Text.empty()
        .append(button(Text.translatable("$KEY.keep_going"), "/challenge defeat keep", Text.translatable("$KEY.keep_going.hover")))
        .append(" ")
        .append(button(Text.translatable("$KEY.reroll_one"), "/challenge defeat reroll1", Text.translatable("$KEY.reroll_one.hover")))
        .append(" ")
        .append(button(Text.translatable("$KEY.reroll_all"), "/challenge defeat rerollall", Text.translatable("$KEY.reroll_all.hover")))

    /** One line per unpaid shiny slot: "Slot 3 Flabébé: [HP] [Atk] [Def] [SpA] [SpD] [Spe]". */
    fun shinyChoices(active: ActiveChallenge, unpaid: List<Int>): List<Text> = unpaid.map { slot ->
        val line = Text.translatable("$KEY.shiny_slot", slot + 1, active.challenge.team[slot].name).formatted(Formatting.GOLD).append(" ")
        ShinyReward.LABELS.forEach { (stat, label) ->
            line.append(button(Text.literal(label), "/challenge shiny ${slot + 1} $stat", Text.translatable("$KEY.shiny_pick", ShinyReward.FEATHERS_PER_SLOT, label)))
            line.append(" ")
        }
        line
    }
}
