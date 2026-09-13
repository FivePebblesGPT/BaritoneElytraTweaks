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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * State-transition tests for the optimized Elytra controller.
 */
public class ElytraFlightControllerTest {

    @Test
    public void noFireworkBaselineSnapsAndReturnsAtApex() {
        ElytraFlightController controller = new ElytraFlightController();

        ElytraFlightController.Decision dive = controller.tick(
                ElytraTweakMode.NO_FIREWORK_BASELINE,
                200.0,
                new Vec3(1.0, -0.1, 0.0),
                false,
                200.0,
                -64.0,
                320.0
        );
        assertTrue(dive.isActive());
        assertFalse(dive.shouldLaunchRocket());
        assertEquals(32.5, dive.pitchDeg(), 1.0e-6);
        assertEquals(ElytraFlightController.NoFireworkState.DIVE, controller.getNoFireworkState());

        ElytraFlightController.Decision snap = controller.tick(
                ElytraTweakMode.NO_FIREWORK_BASELINE,
                195.0,
                new Vec3(2.2, -0.1, 0.0),
                false,
                195.0,
                -64.0,
                320.0
        );
        assertEquals(-49.0, snap.pitchDeg(), 1.0e-6);
        assertEquals(ElytraFlightController.NoFireworkState.PULL_UP, controller.getNoFireworkState());

        ElytraFlightController.Decision ramp = controller.tick(
                ElytraTweakMode.NO_FIREWORK_BASELINE,
                196.0,
                new Vec3(1.8, 0.2, 0.0),
                false,
                196.0,
                -64.0,
                320.0
        );
        assertEquals(-48.5, ramp.pitchDeg(), 1.0e-6);

        ElytraFlightController.Decision apex = controller.tick(
                ElytraTweakMode.NO_FIREWORK_BASELINE,
                197.0,
                new Vec3(1.6, 0.0, 0.0),
                false,
                197.0,
                -64.0,
                320.0
        );
        assertEquals(32.5, apex.pitchDeg(), 1.0e-6);
        assertEquals(ElytraFlightController.NoFireworkState.DIVE, controller.getNoFireworkState());
    }

    @Test
    public void rocketCycleDoesNotRenewDuringDive() {
        ElytraFlightController controller = new ElytraFlightController();

        ElytraFlightController.Decision climb = controller.tick(
                ElytraTweakMode.ROCKET_300,
                100.0,
                new Vec3(1.0, 0.0, 0.0),
                false,
                100.0,
                -64.0,
                320.0
        );
        assertEquals(-31.54, climb.pitchDeg(), 1.0e-5);
        assertTrue(climb.shouldLaunchRocket());
        assertEquals(ElytraFlightController.RocketState.POWERED_CLIMB, controller.getRocketState());

        ElytraFlightController.Decision coast = controller.tick(
                ElytraTweakMode.ROCKET_300,
                250.0,
                new Vec3(1.0, 1.0, 0.0),
                true,
                controller.getUpperY() + 1.0,
                -64.0,
                320.0
        );
        assertFalse(coast.shouldLaunchRocket());
        assertEquals(ElytraFlightController.RocketState.COAST_BEFORE_DIVE, controller.getRocketState());

        ElytraFlightController.Decision dive = controller.tick(
                ElytraTweakMode.ROCKET_300,
                270.0,
                new Vec3(2.0, -0.2, 0.0),
                false,
                270.0,
                -64.0,
                320.0
        );
        assertEquals(40.08, dive.pitchDeg(), 1.0e-5);
        assertFalse(dive.shouldLaunchRocket());
        assertEquals(ElytraFlightController.RocketState.UNPOWERED_DIVE, controller.getRocketState());

        ElytraFlightController.Decision recover = controller.tick(
                ElytraTweakMode.ROCKET_300,
                controller.getLowerY() + 1.0,
                new Vec3(2.0, -0.5, 0.0),
                false,
                controller.getLowerY() + 1.0,
                -64.0,
                320.0
        );
        assertEquals(-31.54, recover.pitchDeg(), 1.0e-5);
        assertTrue(recover.shouldLaunchRocket());
        assertEquals(ElytraFlightController.RocketState.POWERED_CLIMB, controller.getRocketState());
    }

    @Test
    public void offModeIsInactive() {
        ElytraFlightController controller = new ElytraFlightController();
        ElytraFlightController.Decision decision = controller.tick(
                ElytraTweakMode.OFF,
                100.0,
                Vec3.ZERO,
                false,
                100.0,
                -64.0,
                320.0
        );
        assertFalse(decision.isActive());
        assertFalse(decision.shouldLaunchRocket());
    }
}
