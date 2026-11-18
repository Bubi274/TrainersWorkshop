package io.github.bubi274.trainersworkshop.editor

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.api.types.tera.TeraTypes
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.util.Identifier

/** Cobblemon side of trainer battle gimmicks (server only). */
object TrainerGimmickRules {
    /** Tera type ids ("normal", "fire", ..., "stellar"): the 18 elemental types in Cobblemon's order, then the rest. */
    fun teraTypes(): List<String> {
        val byType = ElementalTypes.all().map { TeraTypes.forElementalType(it) }
        val rest = TeraTypes.filter { it !in byType }.sortedBy { it.id.path }
        return (byType + rest).map { it.id.path }.distinct()
    }

    /**
     * Same rule as Cobblemon's Pokemon.gmaxFactor setter (Pokemon.kt 1.8.4, line 474): the species
     * or one of its evolutions has a Gigantamax form.
     */
    fun canGmax(pokemon: Pokemon): Boolean {
        if (pokemon.species.canGmax()) return true
        return pokemon.species.standardForm.evolutions.any { evolution ->
            val name = evolution.result.species ?: return@any false
            val id = Identifier.tryParse(if (':' in name) name else "cobblemon:$name") ?: return@any false
            PokemonSpecies.getByIdentifier(id)?.canGmax() == true
        }
    }

    /** Writes the gimmicks onto the Pokémon the same way RCT does when it reads a trainer (setTeraType, setGmaxFactor). */
    fun applyToPokemon(pokemon: Pokemon, gimmicks: TrainerGimmicks) {
        if (gimmicks.tera.isNotEmpty()) TeraTypes.get(gimmicks.tera)?.let { pokemon.teraType = it }
        pokemon.gmaxFactor = gimmicks.gmax && canGmax(pokemon)
    }

    /** Keeps a slot's gimmicks when its species changes, but turns Gigantamax off if the new species can't use it. */
    fun forNewSpecies(gimmicks: TrainerGimmicks, pokemon: Pokemon): TrainerGimmicks =
        if (gimmicks.gmax && !canGmax(pokemon)) gimmicks.copy(gmax = false) else gimmicks
}
