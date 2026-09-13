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
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.process.elytra.ElytraBehavior;
import baritone.process.elytra.NetherPath;
import baritone.process.elytra.tweaks.ElytraFlightController;
import baritone.process.elytra.tweaks.ElytraTweakMode;
import baritone.process.elytra.tweaks.ElytraTweaksConfig;
import baritone.utils.accessor.IFireworkRocketEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Opt-in integration of the reconstructed Elytra policies with Baritone's
 * existing path and collision solver.
 */
@Mixin(value = ElytraBehavior.class, remap = false)
public abstract class MixinElytraBehavior {

    @Unique
    private static final int SAFETY_HORIZON_TICKS = 40;

    @Shadow
    @Final
    private Baritone baritone;

    @Shadow
    @Final
    private IPlayerContext ctx;

    @Shadow
    @Final
    public ElytraBehavior.PathManager pathManager;

    @Shadow
    public boolean landingMode;

    @Shadow
    private int remainingFireworkTicks;

    @Shadow
    private int remainingSetBackTicks;

    @Shadow
    private int minimumBoostTicks;

    @Shadow
    private boolean deployedFireworkLastTick;

    @Shadow
    private BlockPos aimPos;

    @Unique
    private ElytraFlightController baritoneElytraTweaks$controller;

    @Unique
    private ElytraFlightController.Decision baritoneElytraTweaks$decision = ElytraFlightController.Decision.inactive();

    @Unique
    private float baritoneElytraTweaks$routeYaw;

    @Unique
    private int baritoneElytraTweaks$boostTicksRemaining;

    @Inject(method = "tick", at = @At("HEAD"))
    private void baritoneElytraTweaks$prepareDecision(CallbackInfo ci) {
        final ElytraTweakMode mode = ElytraTweaksConfig.getMode();
        this.baritoneElytraTweaks$decision = ElytraFlightController.Decision.inactive();
        this.baritoneElytraTweaks$boostTicksRemaining = 0;

        if (!mode.isEnabled() || this.landingMode || !this.ctx.player().isFallFlying()) {
            if (this.baritoneElytraTweaks$controller != null) {
                this.baritoneElytraTweaks$controller.invalidate();
            }
            return;
        }

        if (this.baritoneElytraTweaks$controller == null) {
            this.baritoneElytraTweaks$controller = new ElytraFlightController();
        }

        this.baritoneElytraTweaks$routeYaw = this.baritoneElytraTweaks$findRouteYaw();

        final Optional<FireworkRocketEntity> attachedRocket = this.baritoneElytraTweaks$getAttachedFirework();
        final boolean rocketActive = attachedRocket.isPresent();
        if (rocketActive) {
            final int conservativeLifetime = this.minimumBoostTicks > 0 ? this.minimumBoostTicks + 11 : 52;
            this.baritoneElytraTweaks$boostTicksRemaining = Math.max(0, conservativeLifetime - attachedRocket.get().tickCount);
        }

        final double predictedMaxY = mode.isRocketAssisted() && rocketActive
                ? this.baritoneElytraTweaks$predictMaximumY(
                        mode.primaryPitchDeg(),
                        this.baritoneElytraTweaks$routeYaw,
                        this.baritoneElytraTweaks$boostTicksRemaining
                )
                : this.ctx.player().getY();

        final ElytraFlightController.Decision candidate = this.baritoneElytraTweaks$controller.tick(
                mode,
                this.ctx.player().getY(),
                this.ctx.player().getDeltaMovement(),
                rocketActive,
                predictedMaxY,
                this.ctx.world().getMinY(),
                this.ctx.world().getMaxY()
        );

        if (candidate.isActive() && this.baritoneElytraTweaks$isTrajectorySafe(
                candidate.pitchDeg(),
                this.baritoneElytraTweaks$routeYaw,
                rocketActive,
                this.baritoneElytraTweaks$boostTicksRemaining
        )) {
            this.baritoneElytraTweaks$decision = candidate;
        } else {
            this.baritoneElytraTweaks$controller.invalidate();
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void baritoneElytraTweaks$applyPitch(CallbackInfo ci) {
        if (!this.baritoneElytraTweaks$decision.isActive()) {
            return;
        }
        if (this.aimPos == null || this.landingMode) {
            this.baritoneElytraTweaks$controller.invalidate();
            this.baritoneElytraTweaks$decision = ElytraFlightController.Decision.inactive();
            return;
        }

        final Vec3 start = this.ctx.playerFeetAsVec();
        final Vec3 target = Vec3.atCenterOf(this.aimPos);
        final float safeYaw = RotationUtils.calcRotationFromVec3d(start, target, this.ctx.playerRotations()).getYaw();
        final boolean rocketActive = this.baritoneElytraTweaks$getAttachedFirework().isPresent();

        if (!this.baritoneElytraTweaks$isTrajectorySafe(
                this.baritoneElytraTweaks$decision.pitchDeg(),
                safeYaw,
                rocketActive,
                this.baritoneElytraTweaks$boostTicksRemaining
        )) {
            this.baritoneElytraTweaks$controller.invalidate();
            this.baritoneElytraTweaks$decision = ElytraFlightController.Decision.inactive();
            return;
        }

        this.baritone.getLookBehavior().updateTarget(
                new Rotation(safeYaw, this.baritoneElytraTweaks$decision.pitchDeg()),
                false
        );
    }

    @Inject(method = "tickUseFireworks", at = @At("HEAD"), cancellable = true)
    private void baritoneElytraTweaks$controlFireworks(Vec3 start, Vec3 goingTo, boolean isBoosted,
                                                       boolean forceUseFirework, CallbackInfo ci) {
        if (forceUseFirework && this.baritoneElytraTweaks$decision.isActive()) {
            this.baritoneElytraTweaks$controller.invalidate();
            this.baritoneElytraTweaks$decision = ElytraFlightController.Decision.inactive();
            return;
        }
        if (!this.baritoneElytraTweaks$decision.isActive() || this.landingMode) {
            return;
        }

        // A forced firework from Baritone's safety solver remains an emergency
        // escape hatch. Normal renewal is controlled by the optimization mode.
        ci.cancel();
        if (this.baritoneElytraTweaks$decision.shouldLaunchRocket()) {
            this.baritoneElytraTweaks$launchRocket();
        }
    }

    @Unique
    private float baritoneElytraTweaks$findRouteYaw() {
        final NetherPath path = this.pathManager.getPath();
        if (path.isEmpty()) {
            return this.ctx.playerRotations().getYaw();
        }
        final int index = Math.min(this.pathManager.getNear() + 12, path.size() - 1);
        return RotationUtils.calcRotationFromVec3d(
                this.ctx.playerFeetAsVec(),
                path.getVec(index),
                this.ctx.playerRotations()
        ).getYaw();
    }

    @Unique
    private Optional<FireworkRocketEntity> baritoneElytraTweaks$getAttachedFirework() {
        return this.ctx.entitiesStream()
                .filter(entity -> entity instanceof FireworkRocketEntity)
                .map(entity -> (FireworkRocketEntity) entity)
                .filter(entity -> Objects.equals(((IFireworkRocketEntity) entity).getBoostedEntity(), this.ctx.player()))
                .findFirst();
    }

    @Unique
    private void baritoneElytraTweaks$launchRocket() {
        if (this.remainingSetBackTicks > 0 || this.remainingFireworkTicks > 0 || this.baritoneElytraTweaks$getAttachedFirework().isPresent()) {
            return;
        }

        if (!this.baritone.getInventoryBehavior().throwaway(true, MixinElytraBehavior::baritoneElytraTweaks$isBoostingFirework)
                && !this.baritone.getInventoryBehavior().throwaway(true, MixinElytraBehavior::baritoneElytraTweaks$isSafeFirework)) {
            return;
        }

        final ItemStack rocket = this.ctx.player().getItemInHand(InteractionHand.MAIN_HAND);
        final OptionalInt flight = baritoneElytraTweaks$getFireworkFlight(rocket);
        if (flight.isEmpty()) {
            return;
        }

        this.ctx.playerController().processRightClick(this.ctx.player(), this.ctx.world(), InteractionHand.MAIN_HAND);
        this.minimumBoostTicks = 10 * (1 + flight.getAsInt());
        this.remainingFireworkTicks = 10;
        this.deployedFireworkLastTick = true;
    }

    @Unique
    private static boolean baritoneElytraTweaks$isSafeFirework(ItemStack stack) {
        if (stack.getItem() != Items.FIREWORK_ROCKET) {
            return false;
        }
        final Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
        return fireworks != null && fireworks.explosions().isEmpty();
    }

    @Unique
    private static boolean baritoneElytraTweaks$isBoostingFirework(ItemStack stack) {
        return baritoneElytraTweaks$getFireworkFlight(stack).isPresent();
    }

    @Unique
    private static OptionalInt baritoneElytraTweaks$getFireworkFlight(ItemStack stack) {
        final Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
        if (stack.getItem() == Items.FIREWORK_ROCKET && fireworks != null && fireworks.explosions().isEmpty()) {
            return OptionalInt.of(fireworks.flightDuration());
        }
        return OptionalInt.empty();
    }

    @Unique
    private double baritoneElytraTweaks$predictMaximumY(double pitchDeg, float yawDeg, int boostedTicks) {
        Vec3 motion = this.ctx.player().getDeltaMovement();
        double y = this.ctx.player().getY();
        double maxY = y;
        final Vec3 look = RotationUtils.calcLookDirectionFromRotation(new Rotation(yawDeg, (float) pitchDeg));

        for (int tick = 0; tick < boostedTicks; tick++) {
            motion = baritoneElytraTweaks$step(motion, look, (float) pitchDeg);
            y += motion.y;
            maxY = Math.max(maxY, y);
            motion = baritoneElytraTweaks$applyRocket(motion, look);
        }
        return maxY;
    }

    @Unique
    private boolean baritoneElytraTweaks$isTrajectorySafe(float pitchDeg, float yawDeg, boolean rocketActive,
                                                           int boostedTicksRemaining) {
        Vec3 motion = this.ctx.player().getDeltaMovement();
        AABB hitbox = this.ctx.player().getBoundingBox();
        final Vec3 look = RotationUtils.calcLookDirectionFromRotation(new Rotation(yawDeg, pitchDeg));
        int boostTicks = rocketActive ? boostedTicksRemaining : 0;

        for (int tick = 0; tick < SAFETY_HORIZON_TICKS; tick++) {
            motion = baritoneElytraTweaks$step(motion, look, pitchDeg);
            final AABB swept = hitbox.expandTowards(motion.x, motion.y, motion.z).inflate(0.01);

            final int minX = Mth.floor(swept.minX);
            final int maxX = Mth.ceil(swept.maxX);
            final int minY = Mth.floor(swept.minY);
            final int maxY = Mth.ceil(swept.maxY);
            final int minZ = Mth.floor(swept.minZ);
            final int maxZ = Mth.ceil(swept.maxZ);
            for (int x = minX; x < maxX; x++) {
                for (int y = minY; y < maxY; y++) {
                    for (int z = minZ; z < maxZ; z++) {
                        if (!((ElytraBehavior) (Object) this).passable(x, y, z)) {
                            return false;
                        }
                    }
                }
            }

            hitbox = hitbox.move(motion);
            if (boostTicks-- > 0) {
                motion = baritoneElytraTweaks$applyRocket(motion, look);
            }
        }
        return true;
    }

    @Unique
    private static Vec3 baritoneElytraTweaks$applyRocket(Vec3 motion, Vec3 look) {
        return motion.add(
                look.x * 0.1 + (look.x * 1.5 - motion.x) * 0.5,
                look.y * 0.1 + (look.y * 1.5 - motion.y) * 0.5,
                look.z * 0.1 + (look.z * 1.5 - motion.z) * 0.5
        );
    }

    @Unique
    private static Vec3 baritoneElytraTweaks$step(Vec3 motion, Vec3 lookDirection, float pitch) {
        double motionX = motion.x;
        double motionY = motion.y;
        double motionZ = motion.z;

        final float pitchRadians = pitch * RotationUtils.DEG_TO_RAD_F;
        final double lookHorizontal = Math.sqrt(lookDirection.x * lookDirection.x + lookDirection.z * lookDirection.z);
        final double horizontalSpeed = Math.sqrt(motionX * motionX + motionZ * motionZ);
        final double lookLength = lookDirection.length();
        double lift = Mth.cos(pitchRadians);
        lift = lift * lift * Math.min(1.0, lookLength / 0.4);

        motionY += -0.08 + lift * 0.06;
        if (motionY < 0.0 && lookHorizontal > 0.0) {
            final double conversion = motionY * -0.1 * lift;
            motionY += conversion;
            motionX += lookDirection.x * conversion / lookHorizontal;
            motionZ += lookDirection.z * conversion / lookHorizontal;
        }
        if (pitchRadians < 0.0 && lookHorizontal > 0.0) {
            final double pullUp = horizontalSpeed * -Mth.sin(pitchRadians) * 0.04;
            motionY += pullUp * 3.2;
            motionX -= lookDirection.x * pullUp / lookHorizontal;
            motionZ -= lookDirection.z * pullUp / lookHorizontal;
        }
        if (lookHorizontal > 0.0) {
            motionX += (lookDirection.x / lookHorizontal * horizontalSpeed - motionX) * 0.1;
            motionZ += (lookDirection.z / lookHorizontal * horizontalSpeed - motionZ) * 0.1;
        }

        return new Vec3(motionX * 0.99, motionY * 0.98, motionZ * 0.99);
    }
}
