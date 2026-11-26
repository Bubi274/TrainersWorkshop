package io.github.bubi274.trainersworkshop.challenge

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * /challenge commands. The Roster screen (part 4) will replace most of them; they stay for testing and servers.
 *
 * Note: chat input is limited to 256 characters and a TW1 code is longer, so pasting a code into chat cuts it
 * (the checksum then fails). Until the screen exists, use a command block or the server console:
 * `/execute as <player> run challenge start <code>`.
 */
object ChallengeCommand {
    private const val KEY = "command.${TrainersWorkshop.MOD_ID}.challenge"

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                CommandManager.literal("challenge")
                    .executes { run(it) { player -> status(it.source, player) } }
                    .then(CommandManager.literal("status").executes { run(it) { player -> status(it.source, player) } })
                    .then(
                        CommandManager.literal("start").then(
                            CommandManager.argument("code", StringArgumentType.greedyString())
                                .executes { run(it) { player -> start(it.source, player, StringArgumentType.getString(it, "code")) } },
                        ),
                    )
                    .then(
                        CommandManager.literal("reroll").then(
                            CommandManager.argument("code", StringArgumentType.greedyString())
                                .executes { run(it) { player -> reroll(it.source, player, StringArgumentType.getString(it, "code")) } },
                        ),
                    )
                    .then(CommandManager.literal("abandon").executes { run(it) { player -> abandon(it.source, player) } }),
            )
        }
    }

    /** Runs [action] for the player who ran the command; refusals are shown in red. */
    private fun run(context: CommandContext<ServerCommandSource>, action: (ServerPlayerEntity) -> Unit): Int {
        val player = context.source.player
        if (player == null) {
            context.source.sendError(Text.translatable("$KEY.players_only"))
            return 0
        }
        return try {
            action(player)
            1
        } catch (e: ChallengeActionException) {
            context.source.sendError(Text.literal(e.message ?: ""))
            0
        }
    }

    private fun start(source: ServerCommandSource, player: ServerPlayerEntity, code: String) {
        val active = ChallengeService.start(player, code)
        source.sendFeedback({ Text.translatable("$KEY.started", active.challenge.id).formatted(Formatting.GREEN) }, false)
        teamLines(active).forEach { line -> source.sendFeedback({ line }, false) }
        val unpaid = ChallengeService.data(player).unpaidShinySlots()
        if (unpaid.isNotEmpty()) {
            source.sendFeedback({ Text.translatable("$KEY.shiny_pending", unpaid.size).formatted(Formatting.GOLD) }, false)
        }
    }

    private fun reroll(source: ServerCommandSource, player: ServerPlayerEntity, code: String) {
        val active = ChallengeService.applyReroll(player, code)
        val slots = active.challenge.rerolled.joinToString(", ") { (it + 1).toString() }
        source.sendFeedback({ Text.translatable("$KEY.rerolled", active.challenge.id, slots).formatted(Formatting.GREEN) }, false)
        teamLines(active).forEach { line -> source.sendFeedback({ line }, false) }
    }

    private fun abandon(source: ServerCommandSource, player: ServerPlayerEntity) {
        val active = ChallengeService.abandon(player)
        source.sendFeedback({ Text.translatable("$KEY.abandoned", active.challenge.id).formatted(Formatting.YELLOW) }, false)
    }

    private fun status(source: ServerCommandSource, player: ServerPlayerEntity) {
        val data = ChallengeService.data(player)
        val active = data.active
        if (active == null) {
            source.sendFeedback({ Text.translatable("$KEY.none", data.completed).formatted(Formatting.GRAY) }, false)
            return
        }
        source.sendFeedback({ Text.translatable("$KEY.active", active.challenge.id, data.completed).formatted(Formatting.GREEN) }, false)
        teamLines(active).forEach { line -> source.sendFeedback({ line }, false) }
        if (active.rerollAllow != null) {
            source.sendFeedback({ Text.translatable("$KEY.reroll_waiting", active.rerollAllow).formatted(Formatting.GOLD) }, false)
        }
        val unpaid = data.unpaidShinySlots()
        if (unpaid.isNotEmpty()) {
            source.sendFeedback({ Text.translatable("$KEY.shiny_pending", unpaid.size).formatted(Formatting.GOLD) }, false)
        }
    }

    /** One line per slot, for example "1. Nidoran♀ – evo" or "2. Kazeran Vaporeon – evo ★". */
    private fun teamLines(active: ActiveChallenge): List<Text> = active.challenge.team.mapIndexed { i, slot ->
        val line: MutableText = Text.literal("${i + 1}. ${slot.name} – ${slot.rule.id}").formatted(Formatting.WHITE)
        if (slot.shiny) line.append(Text.literal(" ★").formatted(Formatting.GOLD))
        line
    }
}
