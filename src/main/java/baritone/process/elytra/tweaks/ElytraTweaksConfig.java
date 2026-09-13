/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.process.elytra.tweaks;

/**
 * Runtime configuration for the experimental Elytra controller.
 *
 * <p>The mode is intentionally opt-in. The numerical policies are candidates
 * that still need target-server telemetry before they should replace the
 * stock Baritone solver by default.</p>
 */
public final class ElytraTweaksConfig {
    private static volatile ElytraTweakMode mode = ElytraTweakMode.OFF;

    private ElytraTweaksConfig() {
    }

    public static ElytraTweakMode getMode() {
        return mode;
    }

    public static void setMode(ElytraTweakMode newMode) {
        if (newMode == null) {
            throw new IllegalArgumentException("mode cannot be null");
        }
        mode = newMode;
    }
}
