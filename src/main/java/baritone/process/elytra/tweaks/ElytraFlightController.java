/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.process.elytra.tweaks;

import net.minecraft.world.phys.Vec3;

/**
 * Deterministic state machine for the Elytra optimization policies.
 *
 * <p>This class deliberately contains no world or inventory access. The
 * Baritone integration supplies collision safety and rocket lifetime
 * prediction, while this class only decides the desired pitch and whether a
 * new rocket should be launched.</p>
 */
public final class ElytraFlightController {

    public enum RocketState {
        POWERED_CLIMB,
        COAST_BEFORE_DIVE,
        UNPOWERED_DIVE
    }

    public enum NoFireworkState {
        DIVE,
        PULL_UP
    }

    /**
     * Immutable control output for one client tick.
     */
    public static final class Decision {
        private final boolean active;
        private final float pitchDeg;
        private final boolean launchRocket;

        private Decision(boolean active, float pitchDeg, boolean launchRocket) {
            this.active = active;
            this.pitchDeg = pitchDeg;
            this.launchRocket = launchRocket;
        }

        public static Decision inactive() {
            return new Decision(false, 0.0f, false);
        }

        public static Decision active(double pitchDeg, boolean launchRocket) {
            return new Decision(true, (float) pitchDeg, launchRocket);
        }

        public boolean isActive() {
            return active;
        }

        public float pitchDeg() {
            return pitchDeg;
        }

        public boolean shouldLaunchRocket() {
            return launchRocket;
        }
    }

    private ElytraTweakMode previousMode = ElytraTweakMode.OFF;
    private RocketState rocketState = RocketState.POWERED_CLIMB;
    private NoFireworkState noFireworkState = NoFireworkState.DIVE;
    private double commandedPitchDeg;
    private double previousVy;
    private double lowerY;
    private double upperY;

    /**
     * Computes the next optimized command.
     *
     * @param mode selected optimization mode
     * @param y current player Y
     * @param velocity current velocity in blocks/tick
     * @param rocketActive whether a firework is currently attached
     * @param predictedMaxYAtRocketExpiry conservative maximum Y before the
     *                                      current rocket can expire
     * @param worldMinY minimum legal flight Y used to clamp the corridor
     * @param worldMaxY maximum legal flight Y used to clamp the corridor
     * @return pitch/rocket command for this tick
     */
    public Decision tick(ElytraTweakMode mode, double y, Vec3 velocity, boolean rocketActive,
                         double predictedMaxYAtRocketExpiry, double worldMinY, double worldMaxY) {
        if (!mode.isEnabled()) {
            reset(mode, y, velocity.y, worldMinY, worldMaxY);
            return Decision.inactive();
        }
        if (mode != previousMode) {
            reset(mode, y, velocity.y, worldMinY, worldMaxY);
        }

        final Decision decision = mode.isRocketAssisted()
                ? tickRocket(mode, y, rocketActive, predictedMaxYAtRocketExpiry)
                : tickNoFirework(mode, velocity);
        previousVy = velocity.y;
        return decision;
    }

    private Decision tickNoFirework(ElytraTweakMode mode, Vec3 velocity) {
        final double trigger = mode.triggerSpeedBlocksPerTick();
        switch (noFireworkState) {
            case DIVE -> {
                commandedPitchDeg = mode.primaryPitchDeg();
                if (velocity.lengthSqr() >= trigger * trigger) {
                    noFireworkState = NoFireworkState.PULL_UP;
                    commandedPitchDeg = mode.secondaryPitchDeg();
                }
            }
            case PULL_UP -> {
                final boolean reachedApex = previousVy > 0.0 && velocity.y <= 0.0;
                if (reachedApex) {
                    noFireworkState = NoFireworkState.DIVE;
                    commandedPitchDeg = mode.primaryPitchDeg();
                } else {
                    commandedPitchDeg = Math.min(
                            mode.primaryPitchDeg(),
                            commandedPitchDeg + mode.returnRateDegPerTick()
                    );
                }
            }
        }
        return Decision.active(commandedPitchDeg, false);
    }

    private Decision tickRocket(ElytraTweakMode mode, double y, boolean rocketActive,
                                double predictedMaxYAtRocketExpiry) {
        switch (rocketState) {
            case POWERED_CLIMB -> {
                commandedPitchDeg = mode.primaryPitchDeg();
                if (y >= upperY - 2.0 || predictedMaxYAtRocketExpiry >= upperY) {
                    rocketState = RocketState.COAST_BEFORE_DIVE;
                    return Decision.active(commandedPitchDeg, false);
                }
                return Decision.active(commandedPitchDeg, !rocketActive);
            }
            case COAST_BEFORE_DIVE -> {
                if (!rocketActive) {
                    rocketState = RocketState.UNPOWERED_DIVE;
                    commandedPitchDeg = mode.secondaryPitchDeg();
                } else {
                    commandedPitchDeg = mode.primaryPitchDeg();
                }
                return Decision.active(commandedPitchDeg, false);
            }
            case UNPOWERED_DIVE -> {
                commandedPitchDeg = mode.secondaryPitchDeg();
                if (y <= lowerY + 2.0) {
                    rocketState = RocketState.POWERED_CLIMB;
                    commandedPitchDeg = mode.primaryPitchDeg();
                    return Decision.active(commandedPitchDeg, !rocketActive);
                }
                return Decision.active(commandedPitchDeg, false);
            }
            default -> throw new IllegalStateException("Unhandled rocket state " + rocketState);
        }
    }

    private void reset(ElytraTweakMode mode, double y, double vy, double worldMinY, double worldMaxY) {
        previousMode = mode;
        previousVy = vy;
        rocketState = RocketState.POWERED_CLIMB;
        noFireworkState = NoFireworkState.DIVE;
        commandedPitchDeg = mode.isNoFirework() ? mode.primaryPitchDeg() : 0.0;

        if (mode.isRocketAssisted()) {
            final double floor = worldMinY + 8.0;
            final double ceiling = worldMaxY - 8.0;
            final double available = Math.max(16.0, ceiling - floor);
            final double height = Math.min(mode.corridorHeight(), available);

            // Begin roughly one-sixth of the way into the corridor so enabling
            // the mode does not immediately command a long dive.
            lowerY = y - height / 6.0;
            lowerY = Math.max(floor, Math.min(lowerY, ceiling - height));
            upperY = lowerY + height;
        } else {
            lowerY = y;
            upperY = y;
        }
    }

    public RocketState getRocketState() {
        return rocketState;
    }

    public NoFireworkState getNoFireworkState() {
        return noFireworkState;
    }

    public double getLowerY() {
        return lowerY;
    }

    public double getUpperY() {
        return upperY;
    }
}
