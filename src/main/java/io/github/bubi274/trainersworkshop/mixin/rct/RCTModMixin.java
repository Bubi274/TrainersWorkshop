package io.github.bubi274.trainersworkshop.mixin.rct;

import com.gitlab.srcmc.rctapi.api.trainer.Trainer;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerRegistry;
import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.bubi274.trainersworkshop.compat.rct.RctAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * When RCT starts a battle, use the edited team of this exact trainer (if there is one).
 * makeBattle calls TrainerRegistry.getById twice: ordinal 0 for the player, ordinal 1 for the trainer.
 * TODO(rct): checked against rctmod 0.19.2-beta (same bytecode as 0.18.1-beta); another RCT version may change this ordinal.
 */
@Mixin(targets = "com.gitlab.srcmc.rctmod.api.RCTMod", remap = false)
public abstract class RCTModMixin {
    @WrapOperation(
            method = "makeBattle",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/gitlab/srcmc/rctapi/api/trainer/TrainerRegistry;getById(Ljava/lang/String;Ljava/lang/Class;)Lcom/gitlab/srcmc/rctapi/api/trainer/Trainer;",
                    ordinal = 1
            ),
            require = 0
    )
    private Trainer trainersWorkshop$useEditedTeam(TrainerRegistry registry, String id, Class<?> type, Operation<Trainer> original, @Local(argsOnly = true) TrainerMob mob) {
        Trainer edited = RctAccess.editedTrainer(mob);
        return edited != null ? edited : original.call(registry, id, type);
    }
}
