package io.github.bubi274.trainersworkshop.challenge

/**
 * Pasting a code from the clipboard ("/challenge paste" or the key bind). The client checks the text first, the server
 * checks it again. Messages never repeat the pasted text: the clipboard could hold anything.
 */
object ChallengePaste {
    /** Longest text the client sends and the server accepts (a TW1 code is about 1,400 characters). */
    const val MAX_LENGTH = 8192

    enum class Kind { EMPTY, TOO_LONG, REROLL_CODE, NOT_A_CODE, CHALLENGE_CODE }

    /** What the (trimmed) clipboard text looks like. Only the prefix is checked here; the decoder checks the rest. */
    fun classify(raw: String?): Kind {
        val text = raw?.trim().orEmpty()
        return when {
            text.isEmpty() -> Kind.EMPTY
            text.length > MAX_LENGTH -> Kind.TOO_LONG
            text.startsWith(ChallengeCode.CHALLENGE_PREFIX) -> Kind.CHALLENGE_CODE
            text.startsWith(ChallengeCode.REROLL_PREFIX) -> Kind.REROLL_CODE
            else -> Kind.NOT_A_CODE
        }
    }

    /** The sentence for a text that can't be sent, or null for something that looks like a TW1 code. */
    fun problem(kind: Kind): String? = when (kind) {
        Kind.EMPTY -> "Your clipboard is empty. Copy the TW1 code on PokéRoll first."
        Kind.TOO_LONG -> "Your clipboard holds something far longer than a Challenge code. Copy the TW1 code on PokéRoll again."
        Kind.REROLL_CODE -> "That is a reroll code (TWR1). Paste it on PokéRoll, then copy the new TW1 code it gives you."
        Kind.NOT_A_CODE -> "Your clipboard doesn't hold a Challenge code. A Challenge code starts with \"TW1.\"; copy it on PokéRoll."
        Kind.CHALLENGE_CODE -> null
    }

    enum class Action { START, REROLL }

    /** A pasted TW1 code continues the challenge after "Reroll 1"; otherwise it starts a new one. */
    fun action(active: ActiveChallenge?): Action = if (active?.rerollAllow != null) Action.REROLL else Action.START
}
