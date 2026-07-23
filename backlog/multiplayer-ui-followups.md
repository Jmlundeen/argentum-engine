# Multiplayer UI — Follow-ups

Follow-up work after the multiplayer UI pass of **2026-07-23** (commit `19dd44b2f`), which shipped:
table overview mode (rail toggle / key `0`), the combat defender-focus split (combat between two
*other* players shows both boards side-by-side with real arrows), eliminated-player spectating
("Keep Watching" on the defeat overlay), attack-direction clarity (restriction banner + dimmed
🚫 rail chips + sole-defender auto-assign), and the zone-browser portal fix (browsing any player's
graveyard/exile/library works in multiplayer). Architecture notes live in
[`docs/web-client-architecture.md`](../docs/web-client-architecture.md) § *Multiplayer (3-4 player)
board*; original design history in [`multiplayer.md`](multiplayer.md) Phase 3.

Ordered roughly by value.

## 1. Verify + e2e the eliminated-spectating path (unverified!)

The one shipped flow that was **not live-verified**: hotseat connections never receive
`PlayerEliminatedMessage` (the single connection keeps driving the other seats), so the
"Keep Watching" path needs a real multi-connection FFA game.

- Build a 3-4 seat **multi-connection** Playwright fixture (the existing `scenarioFixture.ts` is
  two-context; scenario `mode` currently caps non-hotseat pods — may need a server-side
  `TWO_PLAYER`-style N-token mode, or drive it through the FFA lobby).
- Assert: eliminated overlay shows *Keep Watching* → overlay dismissed, bottom half collapsed,
  overview on, banner + Leave Game button visible, no action UI.
- Confirm server behavior: does an eliminated player's connection keep receiving
  `StateUpdate`/`StateDelta` for the rest of the game (and the final `GameOver` overlay lands on
  top of the spectating layout)? If updates stop, spectating silently freezes — that would need a
  server change.
- Client entry points: `GameOverState.eliminated` (`store/slices/types.ts`),
  `enterEliminatedSpectate` (`boardViewSlice.ts`), `isEliminatedSpectator` in `GameBoard.tsx`.

## 2. ~~Per-board "face" anchor for arrows and player targeting~~ — DONE (2026-07-24)

Shipped as `BoardNamePlate` in `OpponentBoardArea.tsx`: a seat-colored name + life pill at the
top of every visible shared-strip cell. It carries the player anchors (`data-life-id` etc.) for
every visible board except the viewed one (whose anchors stay on the center-HUD orb; rail chips
now drop anchors whenever the board is visible — `OpponentRail visibleBoardIds`), and doubles as
the defender-assignment / player-target click target. `CombatArrows` bundles to the chip only
when no plate is on screen and otherwise ends player arrows on the plate.

## 3. Overview polish — mostly DONE (2026-07-24)

- ~~**Concede overlap**~~ — the strip gets `paddingTop: 48` in shared-strip views, clearing the
  Fullscreen/Concede row for every cell.
- ~~**Viewed-board indicator**~~ — seat-colored inset ring on the viewed cell
  (`viewedRingColor` on `OpponentBoardArea`).
- ~~**Phones**~~ — shared-strip views are disabled on `isMobile` (focused camera only; the rail
  hides the Overview toggle). A 1×N vertically scrollable phone overview remains an idea.
- **MTGO-style per-opponent collapse** (bigger lift, still open): instead of all-or-one, let
  each opponent cell be individually collapsed (+/-) so the remaining boards grow — MTGO's
  model. The `stripWidthPct` plumbing already supports arbitrary shares; needs UI + state
  (`collapsedSeats: Set<EntityId>` in `boardViewSlice`).

## 4. Combat split-view refinements

- **Split during declaration**: the split activates on *confirmed* combat
  (`gameState.combat.attackers`). The real-time `opponentAttackerTargets` echo could trigger it
  earlier so you watch the attack being assembled. Guard against flicker (attackers toggle on/off
  while declaring).
- **"Me + another defender" combats**: when the attacker hits you *and* a third player, today only
  the attacker slides in (pre-existing behavior); the other defender's board stays hidden. The
  split (`useCombatDefenderFocus` early-returns when you're a defender) could include the other
  defender's board alongside the attacker's.
- **Attacker-left ordering**: cells keep turn order; reading combat left→right as
  attacker→defender may parse faster. Trade-off: boards jump if defenders change mid-declaration.
- **Width crunch at 3+ combatants**: attacker + two defenders = three ~30% cells (fine on
  desktop); decide a cap for narrow viewports (e.g. bundle the smaller defender back to their
  chip).

## 5. Attack-direction / restriction follow-ups

- **Authoritative `attackMode` for phrasing**: the banner's "Attack left/right —" prefix reads
  from `lobbyState.settings.attackMode`, which is absent on reconnect-into-game, replays, and
  scenario games (legality itself is always server-authoritative via `validAttackTargets`).
  Carry `attackMode` in `GameStartedMessage` (next to the seat roster) and stamp it in the store
  like `teamByPlayerId`.
- **Goad**: goaded creatures have *forced* attack requirements the rail doesn't surface. A chip
  marker ("must be attacked"/"can't attack you" cases) once goad-heavy sets matter.
- **E2E**: an `attackMode: LEFT` FFA scenario asserting banner text, dimmed chips, and that the
  defender popup never appears (sole-defender auto-assign, incl. mandatory attackers and
  Attack All).
- **Stale scope note**: `multiplayer.md` still says attack-left/right is "permanently out of
  scope" — it shipped (`AttackMode.kt`, CR 803.1); update the doc.

## 6. Spectator-mode parity

The Overview toggle and combat split are gated to players (`!spectatorMode`). Spectators watching
a pod (and the replay viewer) would benefit at least as much — they have no combat to declare and
purely consume the table state. Longer term, eliminated-spectating (#1) and the spectator feature
should converge on one layout: an eliminated player is effectively a spectator whose bottom seat
died (`spectatorBottomSeatId` machinery already exists in `boardViewSlice`).

## 7. Eliminated-spectator layout polish

- The center HUD's right orb still shows the dead player's last life total; replace with a
  tombstone treatment or repurpose the slot (e.g. whose turn / turn number).
- Optionally let an eliminated spectator put a *chosen living player* in the (now empty) bottom
  half instead of collapsing it — closer to how spectating renders a bottom seat.

## 8. Overlay-inside-strip audit (transform trap)

The strip track's `translateX` turns `position: fixed` descendants into cell-relative boxes —
that's what broke multiplayer zone browsing (fixed by portalling the `ZonePiles` browsers to
`document.body`). Audit the remaining subtree rendered under `OpponentBoardArea` for other
fixed/full-screen elements (card context menus, yield menus, any future in-board overlay) and
portal or hoist them. A lint-ish guard is hard; a checklist note in
`web-client-architecture.md` may be enough.

## 9. Overview performance sanity check

Overview keeps *all* boards mounted with live `ResizeObserver` slot-sizing per cell, and
`CombatArrows` polls DOM rects every 100 ms with more anchors on screen. No jank observed with
small boards; re-check with 3 crowded boards (token swarms) — the wrap-line search in
`useSlotSizedResponsive` runs per cell on every resize. If needed: memoize per-cell sizing by
(cardCount, cellWidth) or pause sizing for cells at 0 width.
