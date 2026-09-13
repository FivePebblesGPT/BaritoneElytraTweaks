/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.process.elytra.tweaks.ElytraTweakMode;
import baritone.process.elytra.tweaks.ElytraTweaksConfig;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Selects the opt-in Elytra optimization policy used by this fork.
 */
public final class ElytraTweaksCommand extends Command {

    public ElytraTweaksCommand(IBaritone baritone) {
        super(baritone, "elytratweaks", "elytramode");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            logDirect("Elytra tweak mode: " + ElytraTweaksConfig.getMode().id());
            return;
        }

        final String first = args.getString();
        final String requested;
        if (first.equalsIgnoreCase("mode")) {
            if (!args.hasAny()) {
                throw new CommandInvalidStateException("Usage: elytratweaks mode <mode>");
            }
            requested = args.getString();
        } else {
            requested = first;
        }

        if (args.hasAny()) {
            throw new CommandInvalidStateException("Too many arguments");
        }

        final ElytraTweakMode mode;
        try {
            mode = ElytraTweakMode.parse(requested);
        } catch (IllegalArgumentException ex) {
            throw new CommandInvalidStateException(ex.getMessage());
        }

        ElytraTweaksConfig.setMode(mode);
        logDirect("Elytra tweak mode set to " + mode.id());
        if (mode.isNoFirework()) {
            logDirect("Firework renewal is disabled while this mode is active; Baritone may still use a forced firework for collision recovery.");
        } else if (mode.isRocketAssisted()) {
            logDirect("Rocket cycle active: climb with rockets, coast until boost expiry, then descend without renewing rockets.");
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        final TabCompleteHelper helper = new TabCompleteHelper();
        if (args.hasExactlyOne()) {
            helper.append("off", "no-firework-baseline", "no-firework-climb", "no-firework-cruise",
                    "rocket-100", "rocket-200", "rocket-300", "rocket-large", "mode");
        }
        return helper.filterPrefix(args.getString()).stream();
    }

    @Override
    public String getShortDesc() {
        return "Select optimized Elytra flight policy";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Selects an opt-in Elytra flight policy for the Elytra tweaks fork.",
                "",
                "No-firework policies use a dive -> snap-up -> pitch-return cycle.",
                "Rocket policies use powered climb -> boost expiry -> unpowered dive cycles.",
                "The stock Baritone solver remains the collision/landing fallback.",
                "",
                "Usage:",
                "> elytratweaks - show current mode",
                "> elytratweaks off",
                "> elytratweaks no-firework-baseline",
                "> elytratweaks no-firework-climb",
                "> elytratweaks no-firework-cruise",
                "> elytratweaks rocket-100",
                "> elytratweaks rocket-200",
                "> elytratweaks rocket-300",
                "> elytratweaks rocket-large"
        );
    }
}
