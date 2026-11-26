package io.github.bubi274.trainersworkshop.mixin.rct;

import com.gitlab.srcmc.rctmod.api.data.TrainerBattle;
import io.github.bubi274.trainersworkshop.compat.rct.RctAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Challenge mode: the result of an RCT battle. RCT handles Cobblemon's BATTLE_VICTORY in ModServer.onBattleVictory and
 * removes the battle there, then calls distributeRewards(initiatorWon); this is the one place that still has the
 * player, the trainers and the result together. A forfeit is a loss; a stopped battle never gets here.
 * TODO(rct): checked against rctmod 0.19.2-beta.
 */
@Mixin(targets = "com.gitlab.srcmc.rctmod.api.data.TrainerBattle", remap = false)
public abstract class TrainerBattleMixin {
    @Inject(method = "distributeRewards", at = @At("HEAD"), require = 0)
    private void trainersWorkshop$challengeResult(boolean initiatorWon, CallbackInfo ci) {
        RctAccess.onBattleResult((TrainerBattle) (Object) this, initiatorWon);
    }
}
