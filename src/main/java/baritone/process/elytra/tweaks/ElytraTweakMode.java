/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.process.elytra.tweaks;

import java.util.Locale;

/**
 * Opt-in Elytra flight policies implemented by the Elytra tweaks fork.
 */
public enum ElytraTweakMode {
    OFF("off", false, 0.0, 0.0, 0.0, 0.0, 0.0),

    NO_FIREWORK_BASELINE("no-firework-baseline", false, 0.0, 32.5, -49.0, 10.0, 43.0),
    NO_FIREWORK_CLIMB("no-firework-climb", false, 0.0, 34.35, -50.59, 10.99, 44.36),
    NO_FIREWORK_CRUISE("no-firework-cruise", false, 0.0, 34.97, -52.77, 12.17, 49.58),

    ROCKET_100("rocket-100", true, 100.0, -19.54, 35.22, 0.0, 0.0),
    ROCKET_200("rocket-200", true, 200.0, -27.70, 38.03, 0.0, 0.0),
    ROCKET_300("rocket-300", true, 300.0, -31.54, 40.08, 0.0, 0.0),
    ROCKET_LARGE("rocket-large", true, 800.0, -44.0681, 45.0008, 0.0, 0.0);

    private final String id;
    private final boolean rocketAssisted;
    private final double corridorHeight;
    private final double primaryPitchDeg;
    private final double secondaryPitchDeg;
    private final double returnRateDegPerSecond;
    private final double triggerSpeedMetersPerSecond;

    ElytraTweakMode(String id, boolean rocketAssisted, double corridorHeight, double primaryPitchDeg,
                    double secondaryPitchDeg, double returnRateDegPerSecond,
                    double triggerSpeedMetersPerSecond) {
        this.id = id;
        this.rocketAssisted = rocketAssisted;
        this.corridorHeight = corridorHeight;
        this.primaryPitchDeg = primaryPitchDeg;
        this.secondaryPitchDeg = secondaryPitchDeg;
        this.returnRateDegPerSecond = returnRateDegPerSecond;
        this.triggerSpeedMetersPerSecond = triggerSpeedMetersPerSecond;
    }

    public String id() {
        return id;
    }

    public boolean isEnabled() {
        return this != OFF;
    }

    public boolean isRocketAssisted() {
        return rocketAssisted;
    }

    public boolean isNoFirework() {
        return isEnabled() && !rocketAssisted;
    }

    public double corridorHeight() {
        return corridorHeight;
    }

    /**
     * Rocket mode: climb pitch. No-firework mode: dive pitch.
     */
    public double primaryPitchDeg() {
        return primaryPitchDeg;
    }

    /**
     * Rocket mode: dive pitch. No-firework mode: snap-up pitch.
     */
    public double secondaryPitchDeg() {
        return secondaryPitchDeg;
    }

    public double returnRateDegPerTick() {
        return returnRateDegPerSecond / 20.0;
    }

    public double triggerSpeedBlocksPerTick() {
        return triggerSpeedMetersPerSecond / 20.0;
    }

    public static ElytraTweakMode parse(String raw) {
        final String value = raw.toLowerCase(Locale.ROOT).replace('_', '-');
        for (ElytraTweakMode mode : values()) {
            if (mode.id.equals(value)) {
                return mode;
            }
        }
        return switch (value) {
            case "baseline", "nofirework-baseline" -> NO_FIREWORK_BASELINE;
            case "climb", "nofirework-climb" -> NO_FIREWORK_CLIMB;
            case "cruise", "nofirework-cruise" -> NO_FIREWORK_CRUISE;
            case "100" -> ROCKET_100;
            case "200" -> ROCKET_200;
            case "300", "rocket" -> ROCKET_300;
            case "large", "800" -> ROCKET_LARGE;
            default -> throw new IllegalArgumentException("Unknown Elytra tweak mode: " + raw);
        };
    }
}
