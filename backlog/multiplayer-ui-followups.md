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

## 2. Per-board "face" anchor for arrows and player targeting

In shared-strip views (overview / combat split), arrows targeting a *player* aim at the top-center
of their board cell — which is usually their lands row. Add a small per-cell **name plate** (name +
life, seat-colored) rendered inside `OpponentBoardArea` when the board is visible in a multi-view,
carrying the player anchors (`data-life-id` etc.) so:

- attack/targeting arrows end somewhere that reads as "the player",
- the plate doubles as a defender-assignment / player-target click target (today those clicks go
  to the rail chip or the center-HUD orb, which only represents the *viewed* opponent).

Anchor uniqueness rule (one `data-life-id` per player, see `OpponentRail.tsx` comment): the plate
must take over from the rail chip while the board is visible, mirroring the existing viewed-orb
handoff. `CombatArrows.getVisibleBoardRect` then becomes unnecessary — resolve to the plate.

## 3. Overview polish

- **Concede overlap**: top-right Concede button sits over the rightmost cell's deck pile in
  overview (`ZonePiles.OPPONENT_TOP_RESERVED` only reserves height, and only helps the viewed
  layout). Reserve right-edge space in multi-view or move the pile column inward.
- **Viewed-board indicator**: in overview there's no visual for which board is "viewed" (the one
  the center-HUD orb and `1`-`9` focus tracks). A subtle seat-colored inset ring on that cell.
- **Phones/tablets**: three ~33% cells are unusable on portrait phones. Either disable the toggle
  on `isMobile`, or make overview a 1×N vertically scrollable list there.
- **MTGO-style per-opponent collapse** (bigger lift): instead of all-or-one, let each opponent
  cell be individually collapsed (+/-) so the remaining boards grow — MTGO's model. The
  `stripWidthPct` plumbing already supports arbitrary shares; needs UI + state
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
