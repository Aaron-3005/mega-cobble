package com.aaroncraft.megacobble.mixin;

import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temporary Megas retain learned moves, including during Cobblemon's save decoding. */
@Mixin(value = Pokemon.class, remap = false)
public abstract class PokemonMegaMovesMixin {
    @Inject(method = "updateMovesOnFormChange", at = @At("HEAD"), cancellable = true)
    private void megacobble$preserveMegaMoves(FormData oldForm, CallbackInfo callback) {
        Pokemon pokemon = (Pokemon) (Object) this;
        // Our durable marker is installed before evolving and removed only after reversion.
        // It also survives loading, whose initialize() can temporarily select the standard form.
        if (pokemon.getPersistentData().contains("megacobble:original", Tag.TAG_COMPOUND)
            && (oldForm.getName().startsWith("Mega") || pokemon.getForm().getName().startsWith("Mega"))) {
            callback.cancel();
        }
    }
}
