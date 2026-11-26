package io.github.bubi274.trainersworkshop.challenge

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import io.github.bubi274.trainersworkshop.TrainersWorkshop
import net.minecraft.util.Identifier

/**
 * Turns team slots into the Cobblemon species / forms they allow, using the loaded species data at runtime
 * (so forms added by datapacks, like Kazeran, are included).
 *
 * - special = null, rule "evo": every form of that species (regional forms too) except Special forms, plus every
 *   later evolution of those forms.
 * - special = "kazeran-…": that Special form, plus its later evolutions.
 * - rule "exact" (older codes): only that species, base form.
 */
object CobblemonForms {
    /** Special id prefix (PokéRoll data/special.js) → Cobblemon form name. */
    private val SPECIAL_FORMS = mapOf("kazeran-" to "Kazeran")

    private val allowedCache = HashMap<ChallengeSlot, Set<FormKey>>()
    private val resultCache = HashMap<String, FormKey?>()

    /** Species data changed (datapack reload): forget everything computed from it. */
    fun clearCache() {
        allowedCache.clear()
        resultCache.clear()
    }

    fun key(species: Species, form: FormData): FormKey = FormKey.of(species.resourceIdentifier.toString(), form.name)

    fun key(pokemon: Pokemon): FormKey = key(pokemon.species, pokemon.form)

    /** Every form [slot] allows. */
    fun allowed(slot: ChallengeSlot): Set<FormKey> = allowedCache.getOrPut(slot) {
        PartyCheck.allowedForms(startForms(slot), evolve = slot.rule == SlotRule.EVO, next = ::evolutions)
    }

    private fun isSpecialForm(form: FormData): Boolean = SPECIAL_FORMS.values.any { it.equals(form.name, ignoreCase = true) }

    private fun forms(species: Species): List<FormData> = (listOf(species.standardForm) + species.forms).distinctBy { it.name.lowercase() }

    /** The forms a slot starts from (before evolutions). */
    fun startForms(slot: ChallengeSlot): List<FormKey> {
        val species = PokemonSpecies.getByPokedexNumber(slot.dex, "cobblemon") ?: return emptyList()
        val special = slot.special
        if (special != null) {
            val formName = SPECIAL_FORMS.entries.firstOrNull { special.startsWith(it.key) }?.value ?: return emptyList()
            val form = forms(species).firstOrNull { it.name.equals(formName, ignoreCase = true) } ?: return emptyList()
            return listOf(key(species, form))
        }
        if (slot.rule == SlotRule.EXACT) return listOf(key(species, species.standardForm))
        return forms(species).filterNot(::isSpecialForm).map { key(species, it) }
    }

    /** Direct evolutions of a form. */
    fun evolutions(from: FormKey): List<FormKey> {
        val id = Identifier.tryParse(from.species) ?: return emptyList()
        val species = PokemonSpecies.getByIdentifier(id) ?: return emptyList()
        val form = forms(species).firstOrNull { it.name.lowercase() == from.form } ?: return emptyList()
        return form.evolutions.mapNotNull { evolution ->
            val result = evolution.result
            val text = result.originalString.ifBlank { result.asString(" ") }
            resultCache.getOrPut(text) {
                // Creating the Pokémon applies the result's species, form and aspects exactly like a real evolution.
                try {
                    key(result.create())
                } catch (e: Exception) {
                    TrainersWorkshop.LOGGER.warn("[Challenge] Could not read the evolution result \"{}\"", text, e)
                    null
                }
            }
        }
    }
}
