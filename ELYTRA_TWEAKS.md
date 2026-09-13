# Elytra Tweaks for Minecraft 26.2

This branch is based on upstream Baritone **v1.19.0** for Minecraft **26.2** and Java 25.

The optimization layer is intentionally **off by default**. The fixed pitches and speed thresholds below are reconstructed numerical candidates and should be validated against the target server's tick ordering, latency, collision environment, and movement checks before they are treated as final tuning values.

## Selecting a mode

Use either command name:

```text
#elytratweaks <mode>
#elytramode <mode>
```

Run `#elytratweaks` without arguments to print the current mode.

Available modes:

| Mode | Policy |
|---|---|
| `off` | Stock Baritone Elytra behavior |
| `no-firework-baseline` | +32.5° dive, -49.0° snap, +10.0°/s return, 43.0 m/s trigger |
| `no-firework-climb` | +34.35° dive, -50.59° snap, +10.99°/s return, 44.36 m/s trigger |
| `no-firework-cruise` | +34.97° dive, -52.77° snap, +12.17°/s return, 49.58 m/s trigger |
| `rocket-100` | 100-block altitude-cycle candidate: -19.54° climb / +35.22° dive |
| `rocket-200` | 200-block altitude-cycle candidate: -27.70° climb / +38.03° dive |
| `rocket-300` | 300-block altitude-cycle candidate: -31.54° climb / +40.08° dive |
| `rocket-large` | Large-corridor candidate: -44.0681° climb / +45.0008° dive |

Minecraft pitch convention is used: positive pitch looks downward and negative pitch looks upward.

## No-firework controller

The no-firework controller uses the following cycle:

```text
DIVE
  -> when absolute 3-D speed reaches the configured trigger
PULL_UP (snap immediately to the configured negative pitch)
  -> add the configured pitch return rate every tick
  -> when vertical velocity crosses from positive to non-positive
DIVE
```

Normal firework renewal is disabled in these modes. The upstream emergency firework path is still allowed if Baritone's collision solver explicitly requests a forced boost. The upstream low-firework emergency-landing condition is bypassed in no-firework modes, but Elytra durability safety remains enabled.

## Rocket-assisted controller

The rocket modes use an altitude corridor:

```text
POWERED_CLIMB
  -> stop renewing before the predicted active rocket overshoots upperY
COAST_BEFORE_DIVE
  -> wait for the attached rocket to expire
UNPOWERED_DIVE
  -> never renew the rocket during the normal fast descent
  -> at lowerY, launch and return to POWERED_CLIMB
```

A forced firework requested by Baritone's existing safety solver remains available as emergency braking/climb.

## Collision safety

The optimized pitch is only applied when a 40-tick (2-second) prediction remains collision-free using swept player AABBs. If the optimized prediction is unsafe, the tweak controller invalidates its state and lets the stock Baritone solution take over. It rebuilds its state from observed position and velocity when optimization becomes safe again.

## Validation

For meaningful tuning, test in open sky first, ignore transient cycles, then measure at least 20 settled cycles. Record exact snap velocity, position, pitch, horizontal distance, altitude change, rocket state/lifetime, and prediction error. The target version's observed movement should win over the standalone model when they disagree.
