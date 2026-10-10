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
- **Commands**: `/waystonexppayment info` and `/waystonexppayment access ...` for the tank the caller looks at.
- **Configuration** (`config/waystone_xp_payment-common.toml`): conversion rate, tank capacity, bucket
  interaction, creative-mode exemption, and the accepted-fluid list.
- **Data-driven fluid declarations**: the `waystone_xp_payment:xp_fluid` recipe type accepts a fluid id or a
  fluid tag with a mB-per-XP rate, usable from a datapack or KubeJS.

### Known limitations

- The in-game screen for editing the access list is not implemented yet; the `/waystonexppayment access`
  commands cover the same operations.
- XP *level* requirements are not paid from the tank (only XP points are). The stock Waystones configuration
  uses XP points.
- Breaking a tank drops the block but discards the fluid inside it.
- A tank's capacity is fixed when it is placed; changing `capacityMb` later only affects newly placed tanks.
