package io.github.bubi274.trainersworkshop.challenge

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.network.ChallengeClipboardPayload
import io.github.bubi274.trainersworkshop.network.ChallengePastePayload
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.command.CommandSource
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * /challenge commands. The Roster screen (part 4) will replace most of them.
 *
 * Chat input is limited to 256 characters and a TW1 code is longer, so players use "/challenge paste" (read from the
 * clipboard on the client and sent as a payload, see ClientChallenge) or the "Paste challenge code" key.
 * "start <code>" and "reroll <code>" stay for command blocks and the server console:
 * `/execute as <player> run challenge start <code>`.
 */
object ChallengeCommand {
    private const val KEY = "command.${TrainersWorkshop.MOD_ID}.challenge"

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                CommandManager.literal("challenge")
                    .executes { run(it) { player, reply -> status(player, reply) } }
                    .then(CommandManager.literal("status").executes { run(it) { player, reply -> status(player, reply) } })
                    .then(
                        CommandManager.literal("start").then(
                            CommandManager.argument("code", StringArgumentType.greedyString())
                                .executes { run(it) { player, reply -> start(player, StringArgumentType.getString(it, "code"), reply) } },
                        ),
                    )
                    .then(
                        CommandManager.literal("reroll").then(
                            CommandManager.argument("code", StringArgumentType.greedyString())
                                .executes { run(it) { player, reply -> reroll(player, StringArgumentType.getString(it, "code"), reply) } },
                        ),
                    )
                    // The client catches "/challenge paste" before it is sent; this only runs without the mod on the client.
                    .then(CommandManager.literal("paste").executes { run(it) { _, _ -> throw ChallengeActionException(PASTE_NEEDS_CLIENT) } })
                    .then(CommandManager.literal("copyreroll").executes { run(it) { player, reply -> copyReroll(player, reply) } })
                    .then(CommandManager.literal("abandon").executes { run(it) { player, reply -> abandon(player, reply) } })
                    .then(
                        CommandManager.literal("defeat")
                            .then(CommandManager.literal("keep").executes { run(it) { player, reply -> keepGoing(player, reply) } })
                            .then(CommandManager.literal("reroll1").executes { run(it) { player, reply -> rerollOne(player, reply) } })
                            .then(CommandManager.literal("rerollall").executes { run(it) { player, reply -> rerollAll(player, reply) } }),
                    )
                    .then(
                        CommandManager.literal("shiny").then(
                            CommandManager.argument("slot", IntegerArgumentType.integer(1, ChallengeCode.TEAM_SIZE)).then(
                                CommandManager.argument("stat", StringArgumentType.word())
                                    .suggests { _, builder -> CommandSource.suggestMatching(ShinyReward.FEATHERS.keys, builder) }
                                    .executes {
                                        run(it) { player, reply ->
                                            shiny(player, IntegerArgumentType.getInteger(it, "slot") - 1, StringArgumentType.getString(it, "stat"), reply)
                                        }
                                    },
                            ),
                        ),
                    ),
            )
        }

        ServerPlayNetworking.registerGlobalReceiver(ChallengePastePayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute { paste(player, payload.code) }
        }
    }

    private const val PASTE_NEEDS_CLIENT =
        "/challenge paste reads your clipboard, so it needs Trainer's Workshop on your game too. Use a command block instead: execute as @p run challenge start <code>"

    /** Runs [action] for the player who ran the command; refusals are shown in red. */
    private fun run(context: CommandContext<ServerCommandSource>, action: (ServerPlayerEntity, (Text) -> Unit) -> Unit): Int {
        val source = context.source
        val player = source.player
        if (player == null) {
            source.sendError(Text.translatable("$KEY.players_only"))
            return 0
        }
        return try {
            action(player) { line -> source.sendFeedback({ line }, false) }
            1
        } catch (e: ChallengeActionException) {
            source.sendError(Text.literal(e.message ?: ""))
            0
        }
    }

    /** A code that arrived from the clipboard: starts a challenge, or continues it after "Reroll 1". */
    fun paste(player: ServerPlayerEntity, code: String) {
        val reply: (Text) -> Unit = { player.sendMessage(it, false) }
        try {
            ChallengePaste.problem(ChallengePaste.classify(code))?.let { throw ChallengeActionException(it) }
            when (ChallengePaste.action(ChallengeService.data(player).active)) {
                ChallengePaste.Action.START -> start(player, code, reply)
                ChallengePaste.Action.REROLL -> reroll(player, code, reply)
            }
        } catch (e: ChallengeActionException) {
            reply(Text.literal(e.message ?: "").formatted(Formatting.RED))
        }
    }

    private fun start(player: ServerPlayerEntity, code: String, reply: (Text) -> Unit) {
        val active = ChallengeService.start(player, code)
        reply(Text.translatable("$KEY.started", active.challenge.id).formatted(Formatting.GREEN))
        teamLines(active).forEach(reply)
        shinyPrompt(player, active, reply)
    }

    /** "Shiny reward waiting" plus one line of stat buttons per unpaid shiny slot. */
    private fun shinyPrompt(player: ServerPlayerEntity, active: ActiveChallenge, reply: (Text) -> Unit) {
        val unpaid = ChallengeService.data(player).unpaidShinySlots()
        if (unpaid.isEmpty()) return
        reply(Text.translatable("$KEY.shiny_pending", unpaid.size).formatted(Formatting.GOLD))
        ChallengeBattles.shinyChoices(active, unpaid).forEach(reply)
    }

    private fun reroll(player: ServerPlayerEntity, code: String, reply: (Text) -> Unit) {
        val active = ChallengeService.applyReroll(player, code)
        val slots = active.challenge.rerolled.joinToString(", ") { (it + 1).toString() }
        reply(Text.translatable("$KEY.rerolled", active.challenge.id, slots).formatted(Formatting.GREEN))
        teamLines(active).forEach(reply)
        shinyPrompt(player, active, reply)
    }

    private fun keepGoing(player: ServerPlayerEntity, reply: (Text) -> Unit) {
        ChallengeState.get(player.server).keepGoing(player.uuid)
        reply(Text.translatable("$KEY.kept_going").formatted(Formatting.GREEN))
    }

    private fun rerollOne(player: ServerPlayerEntity, reply: (Text) -> Unit) {
        ChallengeState.get(player.server).requestReroll(player.uuid, 1)
        reply(Text.translatable("$KEY.reroll_requested").formatted(Formatting.GOLD))
        copyReroll(player, reply)
    }

    private fun rerollAll(player: ServerPlayerEntity, reply: (Text) -> Unit) {
        val old = ChallengeState.get(player.server).rerollAll(player.uuid)
        reply(Text.translatable("$KEY.rerolled_all", old.challenge.id).formatted(Formatting.GOLD))
    }

    private fun shiny(player: ServerPlayerEntity, slot: Int, stat: String, reply: (Text) -> Unit) {
        ChallengeBattles.claimShiny(player, slot, stat)
        reply(Text.translatable("$KEY.shiny_given", ShinyReward.FEATHERS_PER_SLOT, ShinyReward.LABELS[stat.lowercase()] ?: stat, slot + 1).formatted(Formatting.GREEN))
    }

    /** Puts the TWR1 code on the player's clipboard (or, without the mod on the client, a chat line to click). */
    fun copyReroll(player: ServerPlayerEntity, reply: (Text) -> Unit) {
        val code = ChallengeService.rerollCode(player)
        if (ServerPlayNetworking.canSend(player, ChallengeClipboardPayload.ID)) {
            ServerPlayNetworking.send(player, ChallengeClipboardPayload(code))
        } else {
            reply(rerollLink(code))
        }
    }

    /** "[Copy reroll code]": clicking it copies the code (vanilla chat feature). */
    fun rerollLink(code: String): Text =
        Text.translatable("$KEY.copy_reroll").formatted(Formatting.AQUA, Formatting.UNDERLINE).styled {
            it.withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code))
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("$KEY.copy_reroll.hover")))
        }

    private fun abandon(player: ServerPlayerEntity, reply: (Text) -> Unit) {
        val active = ChallengeService.abandon(player)
        reply(Text.translatable("$KEY.abandoned", active.challenge.id).formatted(Formatting.YELLOW))
    }

    private fun status(player: ServerPlayerEntity, reply: (Text) -> Unit) {
        val data = ChallengeService.data(player)
        val active = data.active
        if (active == null) {
            reply(Text.translatable("$KEY.none", data.completed).formatted(Formatting.GRAY))
            return
        }
        reply(Text.translatable("$KEY.active", active.challenge.id, data.completed).formatted(Formatting.GREEN))
        teamLines(active).forEach(reply)
        if (active.rerollAllow != null) {
            reply(Text.translatable("$KEY.reroll_waiting", active.rerollAllow).formatted(Formatting.GOLD))
        }
        if (active.defeatPending) {
            reply(Text.translatable("$KEY.defeat_waiting").formatted(Formatting.RED))
            reply(ChallengeBattles.defeatChoices())
        }
        shinyPrompt(player, active, reply)
    }

    /** One line per slot, for example "1. Nidoran♀ – evo" or "3. Flabébé – evo ★". */
    private fun teamLines(active: ActiveChallenge): List<Text> = active.challenge.team.mapIndexed { i, slot ->
        val line: MutableText = Text.literal("${i + 1}. ${slot.name} – ${slot.rule.id}").formatted(Formatting.WHITE)
        if (slot.shiny) line.append(Text.literal(" ★").formatted(Formatting.GOLD))
        line
    }
}
