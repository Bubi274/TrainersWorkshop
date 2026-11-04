package io.github.bubi274.trainersworkshop.mixin.rct;

import com.gitlab.srcmc.rctmod.world.entities.TrainerMob;
import io.github.bubi274.trainersworkshop.compat.rct.RctAccess;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Khi trainer bị đánh bại: thả đồ rơi đã chỉnh, và bỏ bảng loot gốc nếu được cấu hình như vậy. */
@Mixin(targets = "com.gitlab.srcmc.rctmod.world.entities.TrainerMob")
public abstract class TrainerMobMixin {
    @Inject(method = "dropBattleLoot", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void trainersWorkshop$dropEditedLoot(Identifier lootTable, PlayerEntity player, CallbackInfo ci) {
        if (RctAccess.dropEditedLoot((TrainerMob) (Object) this, player)) {
            ci.cancel();
        }
    }
}
