# Changelog

All notable changes to this project are documented here.

## 0.3.0 - 2026-10-17

### Added

- **Remote destination tanks pay too**: a tank directly below the *destination* waystone now counts as a payer even
  when its chunk is not loaded. The server keeps a slot index of the tank positions it has seen (pool amount and
  conversion rate only, 30-second freshness) and answers the client's question about the destination slots, so the
  teleport buttons no longer go dark just because nobody has loaded the far end yet. The server still performs the
  real split on teleport, and a payout that the index could only estimate is re-checked against the loaded tank.
- **No quote without permission**: every tank hint carries an access state - allowed, denied, or unknown. A player
  who may not use a tank is told so and gets no pool amount at all; the client treats "denied" as "not a payer",
  so a locked tank can no longer light up a teleport button that the server would have refused.
- **Mod icon**: `src/main/resources/waystone_xp_payment.png` - the file `META-INF/neoforge.mods.toml` has pointed
  `logoFile` at since the first release but which had never existed. Mod lists and the mod-detail screen show it now.
  The artwork is built from the mod's own in-game textures: the 16x16 tank block texture is projected onto the
  three visible faces of an orthographic cube (front as drawn, top x1.12, right x0.72), sitting on one 16x16 frame
  of the experience fluid's `experience_still.png` (2x scaled, tiled, dimmed to 0.8). It is reproducible:
  `node tools/make_icon.mjs <output.png>` rebuilds the 256x256 PNG - no dependencies, because the fluid frame is
  embedded in the script while the block texture is read from the repository.

### Changed

- **Network protocol 3 -> 4**: the tank hint gained its access field, so server and clients must run the same
  version. A 0.2.0 client is refused at the handshake instead of misreading the packet.
- **One name chain**: the access list and the team member list now share `PlayerNames` - live name, then the name
  the profile cache remembers after logout, then the first eight characters of the id. Blank names count as no
  name, so a row can no longer render as an empty line.
- **One place for wire limits**: list sizes, hint counts and text lengths are capped in `XppWireLimits` and applied
  when the packet is built, not only when it is read.

### Fixed

- An operator (permission level 2) opening somebody else's tank got every button greyed out: the server accepted the
  edit while the screen had been told otherwise. Both sides now ask the same `canEditOrOp` rule.
- A member of the access list who logged off turned into a raw 36-character UUID on their row; they keep their name.
- One malformed UUID in a stored list no longer makes the whole list unopenable - the bad entry is dropped and
  logged, and the remaining entries still load.
- Packet readers reject counts and sizes above their limits before allocating, so a hostile or corrupt packet
  cannot make the client reserve an unbounded array.
- A tank loaded from a world whose `capacityMb` had been lowered is clamped on load instead of silently holding
  more than it can, and the repeated warnings for the same broken entry were reduced to one line each.
- The remote-destination design document no longer contradicts the code in three places, its two dead references
  are gone, and the fix list's line numbers point at the code they describe again.

### Compatibility

- Minecraft 1.21.1 with NeoForge 21.1.x.
- Waystones **21.1.45 or newer**.
- FTB Teams and KubeJS are optional.
- **0.3.0 changes the network protocol**: a 0.3.0 server refuses a 0.2.0 client and the other way round. Update the
  server and every client together.
- **Upgrading from 0.2.0** needs no world or config migration: the new data is either a network field or a cache the
  server rebuilds by looking at tanks, and no saved data changed shape.

### Known limitations

- The remote preview can only repeat what the index last saw. A tank whose chunk has been unloaded for longer than
  the freshness window is priced from a stale amount, so the final split can differ from what the button promised
  by one purchase.
- Editing or reading a tank still has no range check: a screen that was opened next to a tank keeps working after
  the player is teleported away. Planned for the next round.
- A team row for a team the viewer is not in still falls back to the full team UUID - FTB Teams has no name cache
  to ask.
- The FTB Teams member panel and the payload-registration handshake have still not been exercised on a machine with
  FTB Teams installed.

### Tests

- 101 unit tests (0.2.0: 55), including the hint's three access states, the access decision, the wire caps, the
  pool-amount choice, the name chain and the edit rights.

## 0.2.0 - 2026-10-11

### Added

- **Owner-gated access list**: a tank remembers the player who placed it (NBT `XppOwner`/`XppOwnerName`). Only that
  player, a player who shares an FTB team with them, or an operator (permission level 2) may change the list; the
  screen says whose tank it is and opens read-only for everybody else. A tank placed before this release (or by
  something that is not a player) has no owner yet: it stays editable until the first successful edit, which
  claims it for whoever made it, and that player is told so.
- **UUID-backed access list**: players and teams are stored as UUIDs (`playerUuids`/`teamUuids`, int-array NBT)
  instead of names, so renaming a player or a team no longer breaks an entry. The server accepts a player entry
  only for the sender's own UUID, and a team entry only for a team the sender is actually in. Entries saved by an
  older version by name are kept as read-only legacy entries and still grant access (matching order: player UUID,
  then the UUID of the team they are in, then the legacy name).
- **In-screen picker**: focusing the filter box turns the list into candidates - online players (blue) and teams
  (amber). The client only filters what the server sent and submits the UUID that came with the row, so a typed
  name is never parsed on the server.
- **Row menu**: every row has a menu (right-click it, or click the `···` at its right end) with *Add to list*,
  *Remove from list* and *View team members*. A left click only selects a row, so no single stray click can remove
  an entry any more; an item that cannot be used stays visible but disabled rather than disappearing.
- **Team member panel** (*View team members*): lists a team's members and how many of them are online. Teams are
  read from FTB Teams reflectively; offline names come from the server's profile cache, falling back to the first
  eight characters of the UUID. The panel tells loading, empty, "the server has no team mod", "that team no longer
  exists", "not allowed" and "timed out" (the last one with a click-to-retry) apart.

### Changed

- **Localisation**: every user-visible string the mod adds is now a translation key - 46 of them, in `en_us` and
  `zh_cn` alike. (The 0.1.0 access screen was hardcoded English; the English-only limitation listed under 0.1.0 is
  resolved.)
- The access screen gained a status line (*Sending…* / *Applied* / *Refused by the server* / *No answer from the
  server*), so a rejected or lost edit is visible instead of being guessed at, and `Esc` now backs out one layer at
  a time: an open menu first, then the member panel, then the filter, and only then the screen.
- The tank screen no longer reads the synchronised state before the first snapshot has arrived, and the client no
  longer decides anything from a cached copy of a server-side value.

### Fixed

- **Opening the waystone teleport list for the first time disconnected the client.** The client mixin around
  Waystones' teleport button declared `@Shadow protected boolean active`, but `active` is declared by
  `AbstractWidget`, so the mixin could not be applied:
  `InvalidMixinException @Shadow field active was not located in the target class
  net.blay09.mods.waystones.client.gui.widget.WaystoneButton`. The field is now written through the parent class
  (`((AbstractWidget) (Object) this).active = false`), and a guard test (`MixinShadowPolicyTest`) fails the build
  if any `@Shadow` in this repository stops matching its target class.
- Typing into the access screen's filter box did nothing.
- Opening the tank screen could throw `NullPointerException: Cannot invoke "XppStatePayload.ownTeams()" because
  "this.state" is null` out of the payload handler, because the screen drew its buttons from a state that had not
  arrived yet; NeoForge treats an exception escaping a payload handler as a protocol error and disconnects the
  client. The screen now waits for its first snapshot (the `Waiting for the server…` line) instead of reading a
  state that is not there yet.
- `gradlew` is executable in the repository again, so a fresh clone can build without a manual `chmod +x`.

### Compatibility

- Minecraft 1.21.1 with NeoForge 21.1.x.
- Waystones **21.1.45 or newer**. The addon is compiled against 21.1.46, but every Waystones API it uses was
  checked with `javap` against the 21.1.45 jar, so the older patch release loads too.
- FTB Teams and KubeJS are optional. Without FTB Teams, access control is player-only, and the member panel and
  the "add my teams" button both report that no team mod is installed.
- **Upgrading from 0.1.0**: existing tanks and their access lists keep working - no world or config migration is
  needed. A tank placed by 0.1.0 has no owner until somebody edits its list, and name-based entries keep granting
  access as legacy entries.

### Known limitations

- The team member panel reads FTB Teams reflectively, so an FTB Teams release that renames its API degrades to
  "player entries only" (logged as a warning) instead of failing to load; team members cannot be listed in that
  case. A member who has never been seen by the server's profile cache is shown as a UUID prefix.
- XP *level* requirements are not paid from the tank (only XP points are). The stock Waystones configuration uses
  XP points.
- Breaking a tank drops the block but discards the fluid inside it.
- A tank's capacity is fixed when it is placed; changing `capacityMb` later only affects newly placed tanks.
- The FTB Teams member panel and the `registrar("2")` payload-registration handshake have still not been exercised
  on a machine with FTB Teams installed; everything else was play-tested by hand on a real 1.21.1 client (that is
  how the teleport-list disconnect and the dead filter box were found) and on a dedicated dev server over RCON
  for everything that does not need a player. See `docs/verification.md`.

### Tests

- 55 unit tests (0.1.0: 25), including new pure-logic coverage for the row-menu placement, the pending-request
  state, the member status and the mixin shadow guard.

## 0.1.0 - 2026-10-10

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
- **Clickable teleport previews**: Waystones decides *client-side* whether its teleport buttons can be pressed,
  based on the config-resolved XP requirement, so a client-only mixin wraps that requirement and treats the tank
  below the source as a payer. Without it, a player whose own XP is short could not even start a teleport that
  the tank would have paid for. The server stays authoritative: it performs the real split on teleport.
- **Either end of the trip can pay**: the tank below the *destination* waystone pays as well, so arriving
  somewhere can be funded by that place's pool; the source tank is preferred when both exist.
- **More forgiving fluid matching**: a declared still fluid also covers its `flowing_` counterpart, and fluids
  whose id contains a whole `xp`/`experience` word (`some_mod:xp_fluid`, `some_mod:flowing_xp_still`,
  `some_mod:liquid_experience`, ...) count as experience fluid at `mbPerXp` unless `acceptExperienceNamedFluids`
  is turned off.
- **Self-explaining diagnostics**: `/waystonexppayment info` prints the tank fluid id, why it was accepted (or
  that it was not) and which tags matched; every tank-paid teleport logs one line with the same detail.

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
