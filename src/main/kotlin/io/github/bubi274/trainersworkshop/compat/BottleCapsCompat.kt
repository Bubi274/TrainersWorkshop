package io.github.bubi274.trainersworkshop.compat

import io.github.bubi274.trainersworkshop.TrainersWorkshop
import io.github.bubi274.trainersworkshop.editor.BottleCapMode
import net.fabricmc.loader.api.FabricLoader
import java.util.function.Supplier

/**
 * Optional integration with Only Bottle Caps (mod id "obc"): finds out what a Bottle Cap does on this server,
 * so the Survival PokeEditor does exactly the same. Server side only; the result is sent to the client.
 *
 * OBC 1.5.0 has a static `Supplier<Boolean> breedingBuff` in `com.pcrgaming.obc.utils.Config`
 * (config/obc-common.toml, default false). OBC 1.3.0 has no such field and always changes the base IV.
 */
object BottleCapsCompat {
    private const val CONFIG_CLASS = "com.pcrgaming.obc.utils.Config"
    private const val BREEDING_BUFF_FIELD = "breedingBuff"

    private val loaded: Boolean by lazy { FabricLoader.getInstance().isModLoaded("obc") }
    private var warned = false

    fun mode(): BottleCapMode {
        // Without OBC there are no caps, so the mode doesn't matter.
        if (!loaded) return BottleCapMode.HYPER_TRAIN
        val field = try {
            Class.forName(CONFIG_CLASS).getField(BREEDING_BUFF_FIELD)
        } catch (e: NoSuchFieldException) {
            // OBC before 1.5: caps always use Pokemon.setIV.
            return BottleCapMode.BASE_IV
        } catch (e: Throwable) {
            warnOnce("Could not find the Only Bottle Caps config; assuming Bottle Caps use hyper training.", e)
            return BottleCapMode.HYPER_TRAIN
        }
        return try {
            val breedingBuff = (field.get(null) as Supplier<*>).get() as Boolean
            if (breedingBuff) BottleCapMode.BOTH else BottleCapMode.HYPER_TRAIN
        } catch (e: Throwable) {
            warnOnce("Could not read the Only Bottle Caps breedingBuff option; assuming Bottle Caps use hyper training.", e)
            BottleCapMode.HYPER_TRAIN
        }
    }

    private fun warnOnce(message: String, e: Throwable) {
        if (warned) return
        warned = true
        TrainersWorkshop.LOGGER.warn(message, e)
    }
}
