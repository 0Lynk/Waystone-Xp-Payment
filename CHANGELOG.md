# Changelog

All notable changes to this project are documented here.

## 0.1.0 - unreleased

### Added

- **XP Payment Tank** (`waystone_xp_payment:xp_payment_tank`): a block with a 4-bucket internal tank that
  exposes the standard NeoForge `Capabilities.FluidHandler.BLOCK` capability, so any tech mod's pipes can
  fill it, plus bucket interaction by right-click.
- **Teleport payment**: when the teleport source (waystone, warp plate, warp portal, sharestone) sits directly
  above a tank, the teleport's XP cost is paid from the tank first and from the player's XP for the remainder.
  If neither covers the cost, the teleport is cancelled and nothing is consumed.
- **Access control**: the shared pool is protected by a whitelist or blacklist of players and teams
  (FTB Teams when installed; player-only otherwise).
- **Access screen**: right-clicking a tank opens a screen that shows the pool and edits the access list
  (mode toggle, "add me"/"remove me", and a toggle per FTB team the player is in). The server revalidates every
  request and answers with a fresh snapshot.
- **Commands**: `/waystonexppayment info` and `/waystonexppayment access ...` for the tank the caller looks at.
- **Configuration** (`config/waystone_xp_payment-common.toml`): conversion rate, tank capacity, bucket
  interaction, creative-mode exemption, and the accepted-fluid list.
- **Data-driven fluid declarations**: the `waystone_xp_payment:xp_fluid` recipe type accepts a fluid id or a
  fluid tag with a mB-per-XP rate, usable from a datapack or KubeJS.

### Compatibility

- Minecraft 1.21.1 with NeoForge 21.1.x.
- Waystones **21.1.45 or newer**. The addon is compiled against 21.1.46, but every Waystones API it uses was
  checked with `javap` against the 21.1.45 jar, so the older patch release loads too.
- FTB Teams and KubeJS are optional; the tank works without them (player-only access control, config-declared
  fluids only).

### Known limitations

- The access screen's own labels are English-only for now (its feature strings are hardcoded rather than
  translated).
- XP *level* requirements are not paid from the tank (only XP points are). The stock Waystones configuration
  uses XP points.
- Breaking a tank drops the block but discards the fluid inside it.
- A tank's capacity is fixed when it is placed; changing `capacityMb` later only affects newly placed tanks.
- FTB Teams support is reflective, so an FTB Teams release that renames its API degrades to "player entries
  only" (logged as a warning) instead of failing to load.
- The tank has been smoke-tested on a dedicated dev server for everything that does not need a player: block and
  block-entity registration, NBT round-trip (fluid + access list), datapack-declared fluid rates and config
  generation. The in-game payment path, the access screen and the FTB Teams path still need a real client or a
  GameTest — see `docs/verification.md`, section 3, for the evidence and the exact list of what is not covered.
