package io.github.bubi274.trainersworkshop.mixin.rct;

import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;
import io.github.bubi274.trainersworkshop.compat.rct.RctAccess;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** When a trainer is defeated: drop the edited loot, and skip the original loot table if configured that way. */
@Mixin(targets = "com.gitlab.srcmc.rctmod.world.entities.TrainerMob")
public abstract class TrainerMobMixin {
    @Inject(method = "dropBattleLoot", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void trainersWorkshop$dropEditedLoot(Identifier lootTable, PlayerEntity player, CallbackInfo ci) {
        if (RctAccess.dropEditedLoot((TrainerMob) (Object) this, player)) {
            ci.cancel();
        }
    }

    /**
     * Unlimited rematches (config unlimitedRematches): RCT stops a battle, and makes a beaten trainer walk away, once the
     * player beat it maxTrainerDefeats times (or lost maxTrainerWins times). Only these two checks are lifted;
     * "force battle on sight" and the dialogs still see the real counts, so a beaten trainer doesn't chase the player.
     */
    @WrapOperation(
            method = {"couldBattleAgainst", "finishBattle"},
            at = @At(value = "INVOKE", target = "Lcom/gitlab/srcmc/rctmod/world/entities/TrainerMob;wasDefeatedBy(Ljava/util/UUID;)Z"),
            remap = false,
            require = 0
    )
    private boolean trainersWorkshop$rematchAfterDefeat(TrainerMob mob, UUID player, Operation<Boolean> original) {
        return !RctAccess.unlimitedRematches() && original.call(mob, player);
    }

    @WrapOperation(
            method = {"couldBattleAgainst", "finishBattle"},
            at = @At(value = "INVOKE", target = "Lcom/gitlab/srcmc/rctmod/world/entities/TrainerMob;wasVictoriousAgainst(Ljava/util/UUID;)Z"),
            remap = false,
            require = 0
    )
    private boolean trainersWorkshop$rematchAfterVictory(TrainerMob mob, UUID player, Operation<Boolean> original) {
        return !RctAccess.unlimitedRematches() && original.call(mob, player);
    }
}
