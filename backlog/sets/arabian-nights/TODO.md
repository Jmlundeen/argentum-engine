# Arabian Nights (ARN) — Implementation Plan

> **Every card must be implemented perfectly — exactly as stated in the rules.** No
> approximations, no "close enough", no silently dropped clauses. Each card's behavior must
> match its oracle text (from `arn_set.json`) and the Comprehensive Rules
> (`MagicCompRules_20260417.pdf`) in full, including edge cases, timing, and interactions.
> A card is not done until its scenario test proves the rules-correct behavior.

Set scaffolding is done:

- `mtg-sets/.../definitions/arn/ArabianNightsSet.kt` — registered in `MtgSetCatalog.all`.
- `mtg-sets/.../definitions/arn/cards/` — empty, ready for one file per card.
- `backlog/sets/arabian-nights/cards.md` — the 78-card checklist (mark `[x]` as you go).
- `backlog/sets/arabian-nights/arn_set.json` — the **offline card-data source** for this set.

Verify status anytime with: `scripts/card-status --set ARN` (and `--list --set ARN`).

## Progress (as of 2026-05-26)

**Implemented: 46 / 78** (`scripts/card-status --set ARN`). Full per-card status lives in
`cards.md` — that file's `Implemented:` count is the source of truth; keep it in sync as boxes
are checked. The triage buckets below are pruned to **remaining work only**; a card is removed
from its bucket once it lands on a branch.

Note: **Banding is fully modelled in the engine** (CR 702.22 — `Keyword.BANDING`, handled in
`CombatManager` / `CombatDamageManager` / `AttackPhaseManager`; `BandDeclarationTest`,
`BandingTrampleDrainScenarioTest`, and `BandingCombatScenarioTest` all pass, and Mirage's Noble
Elephant uses it with no per-card wiring). Banding cards therefore compose onto `arn-cards`.

The original banding triage was inaccurate (verified against `arn_set.json`): Moorish Cavalry is
Trample only and Hurr Jackal is regeneration-denial — neither has banding (both already done).
Aladdin's control-change also composed onto `arn-cards`. The only true banding cards are **Camel**
and **War Elephant**; Abu Ja'far is a death trigger, not banding (moved to Bucket B).

**In-flight engine-change branches** (each branched off the `arn` scaffolding branch — which is
`main` + ARN scaffolding — with one commit, pending its own PR; not on `arn-cards`):

- `arn-sindbad` — Sindbad, via a new `DrawRevealDiscardUnless` SDK effect + executor.
- `arn-control-by-life` — Ghazbán Ogre, via a new `GainControlByMostLife` SDK effect + executor.
- `arn-camel` — Camel (banding), via a new source-relative `StatePredicate.InSameBandAsSource`
  filter (CR 702.22) wired into the `PreventDamage` recipient evaluation, for its "while
  attacking, prevent Desert damage to this creature and creatures banded with it" clause. Tests
  pass; the existing banding suites are unaffected.

Do **not** fold these into the `arn-cards` PR — they touch `mtg-sdk` / `rules-engine`, which
`arn-cards` is meant to keep clean. Each will bump the `cards.md` count by one when merged.

## Data sources — do NOT hit the network

- **Card data** (name, mana cost, type line, oracle text, P/T, rarity, collector number,
  artist, flavor, image URI): read from `arn_set.json`, **not** Scryfall. It is a full
  Scryfall dump of all 78 cards (`.data[]`, keyed by the usual field names). When running
  `add-card`, feed it the matching entry from this file instead of doing a Scryfall lookup.
- **Rules**: cite and verify against `MagicCompRules_20260417.pdf` (in the repo root),
  **not** yawgatog or any web source. Read the relevant pages with the `Read` tool's
  `pages` parameter (the PDF is 24 pages). Quote rule numbers from that document.

## Workflow

Each card is implemented with the **`add-card` skill** (oracle errata, set registration,
scenario test) — but source its card data from `arn_set.json` and its rule references from
`MagicCompRules_20260417.pdf` per the section above. Run one card per Claude invocation:

```
/add-card <Card Name>   # from set ARN; use arn_set.json for data, the CompRules PDF for rules
```

The skill is the source of truth on whether a card needs an engine change. The buckets
below are a *provisional* triage to sequence the work — confirm during implementation.

### Git strategy

1. **Foundation commit** (this scaffolding) lands first.
2. **One big PR — "Arabian Nights: cards (no engine change)"**, branch `arn-cards`,
   **one commit per card**. Only cards that compose from existing SDK primitives go here.
3. **One PR per engine-change card**, each off `main` (e.g. `arn-banding`, `arn-coinflip`).
   If several cards share one new engine feature (e.g. banding), that feature's PR can
   land all of them together — note it in the PR.

### Per-card procedure

For each unchecked card in `cards.md`:

1. `/add-card <name>` — implement via the DSL, no class inheritance.
2. If it composes from existing primitives → commit on `arn-cards` (`Add <Card>` ).
3. If `add-card` finds it needs a new `Effect`/keyword/replacement/SDK change →
   stop, branch off `main`, build the engine feature + the card + tests, open its own PR.
   Update `docs/card-sdk-language-reference.md` in the same PR (required for any SDK change).
4. Check the box in `cards.md` and update the `Implemented:` count.

## Provisional triage

> ⚠️ First-pass guess from oracle text — **the `add-card` skill decides for real.**
> A "Bucket B" card that turns out to compose cleanly belongs in the big PR; a "Bucket A"
> card that surprises you moves to its own PR.

### Bucket A — likely no engine change → big `arn-cards` PR

Vanilla bodies, plain keywords (flying, first strike, protection, landwalk), stat mods,
simple targeted/activated abilities, set-base-P/T, regeneration, token-on-death,
"deal N to any target", conditional static buffs, simple upkeep self-damage.

Remaining:

- Mountain — basic land; already covered by `basicLandsFallback`. Add an ARN-art
  `basicLand("Mountain")` variant only if you want the distinct printing.

(Sindbad started here but needed a new SDK effect — now on `arn-sindbad`; see the in-flight
branches note above.)

### Bucket B — verify; composable but non-trivial (most land in the big PR)

Conditional attack restrictions, upkeep "pay-or-sacrifice", delayed payments, O-Ring-style
exile/return, grant-ability auras, timing-restricted abilities, set-power-of-flyer,
conditional draw, damage prevention, death-count counters, untap restrictions.

Remaining:

- Dandân, Island Fish Jasconius, Merchant Ship (can't attack unless defender has an Island)
- Serendib Djinn (sac a land or take 3)
- Oubliette (exile + auras, return)
- Erg Raiders, Nafs Asp (delayed life loss unless pay {1})
- Metamorphosis, Desert Nomads
- Aladdin's Lamp, Pyramids, Drop of Honey
- Abu Ja'far (dies → destroy all creatures blocking or blocked by it; they can't be regenerated)
- Guardian Beast (untriaged in the original plan — untapped, makes noncreature artifacts
  unenchantable / untargetable / indestructible; verify whether this composes or needs engine
  work, it may belong in Bucket C).

### Bucket C — likely needs engine work → own PR(s)

Group by the shared feature so one PR can clear several cards:

Remaining:

- **Banding** (keyword): **done.** War Elephant (plain Trample + Banding) on `arn-cards`;
  Camel (banding + the Desert-damage-prevention clause) on `arn-camel` via the new
  `InSameBandAsSource` filter (see the in-flight branches note above). Banding itself was
  already engine-supported.
- **Coin flips**: Ydwen Efreet. (Mijae Djinn, Bottle of Suleiman done.)
- **Continuous control change**: Old Man of the Sea.
  (Aladdin done on `arn-cards`; Ghazbán Ogre done on `arn-control-by-life`, pending its own PR.)
- **Replacement / redirection**: Ali from Cairo (life can't drop below 1),
  Eye for an Eye (redirect damage to its source's controller).
- **Opponent-chosen target**: Cuombajj Witches (1 dmg you choose + 1 dmg an opponent chooses).
- **Set-membership effects**: City in a Bottle (destroy/lock out "Arabian Nights" cards).
- **Untap-cost lock**: Magnetic Mountain (blue creatures don't untap unless {4} each).
- **Cumulative counters dealing damage**: Cyclone.
- **Card-from-outside-the-game**: Ring of Ma'rûf (wish-style).
- **Last-drawn-card tracking**: Jandor's Ring.
- **Ante**: Jeweled Bird — needs the ante mechanic (often unsupported / house-ruled).
- **Subgame**: Shahrazad — a full Magic subgame; the largest single feature here.
- **Jihad** — multi-condition enchantment that references a chosen color/player. Implement
  it like any other card, faithfully to its oracle text.

## Notes

- **Implement every card, including culturally sensitive ones** (e.g. Jihad). This is a
  faithful reimplementation of the 1993 set; reproduce oracle text as printed.
- Verify any MTG rule number against `MagicCompRules_20260417.pdf` (repo root) before
  citing it — read the relevant pages with `Read(pages=...)`. Do not use web sources.
- Battlefield filtering must use projected state (`matchesWithProjection`).
- `Jeweled Bird`, `Shahrazad`, `Library of Alexandria`, `Bazaar of Baghdad` are
  banned/ante cards — implement the rules faithfully; deck-legality is a separate concern.
