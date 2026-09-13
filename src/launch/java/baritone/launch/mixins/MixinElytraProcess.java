/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.launch.mixins;

import baritone.Baritone;
import baritone.process.ElytraProcess;
import baritone.process.elytra.tweaks.ElytraTweaksConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the upstream Elytra-durability safety check while removing the
 * firework-inventory landing requirement for explicit no-firework modes.
 */
@Mixin(value = ElytraProcess.class, remap = false)
public abstract class MixinElytraProcess {

    @Inject(method = "shouldLandForSafety", at = @At("HEAD"), cancellable = true)
    private void baritoneElytraTweaks$noFireworkSafety(CallbackInfoReturnable<Boolean> cir) {
        if (!ElytraTweaksConfig.getMode().isNoFirework()) {
            return;
        }

        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        final ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        final boolean unsafeElytra = chest.getItem() != Items.ELYTRA
                || chest.getMaxDamage() - chest.getDamageValue() < Baritone.settings().elytraMinimumDurability.value;
        cir.setReturnValue(unsafeElytra);
    }
}
