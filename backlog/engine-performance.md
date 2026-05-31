# Engine Performance — Hotspot Analysis & Improvement Plan

CPU profile of the rules engine's hot path (legal-action enumeration + action processing),
with a prioritized plan to cut total CPU. Generated May 2026 from a sampled profiling run.

**Status:** Analysis complete, no fixes applied yet. Items below are ordered by impact ÷ risk.

## Methodology

Profiled the random-action throughput benchmark — pure engine work, no AI evaluation —
so the numbers reflect the engine itself, not search heuristics:

```bash
just benchmark-random 60 BLB        # 60 Bloomburrow sealed games, random legal actions
```

Sampling was done with **async-profiler** (itimer event, 2 ms interval) attached as a JVM
agent, collecting ~42,000 CPU samples across the 8 worker threads. The `.collapsed` stacks
were aggregated into self-time (leaf) and inclusive (any-frame) totals.

To reproduce the profile (the agent dylib ships inside JetBrains IDEs):

```bash
AGENT="/Applications/DataGrip.app/Contents/lib/async-profiler/libasyncProfiler.dylib"
./gradlew --stop
JAVA_TOOL_OPTIONS="-agentpath:$AGENT=start,event=itimer,interval=2ms,collapsed,file=/tmp/prof/engine-%p.collapsed" \
  ./gradlew --no-daemon :ai:test --tests "*.RandomActionBenchmark" \
  -Dbenchmark=true -DbenchmarkGames=60 -DbenchmarkSet=BLB
# The worker JVM's collapsed file (the one with the most com/wingedsheep frames) is the engine profile.
```

`--no-daemon` is required so the forked test-worker JVM inherits `JAVA_TOOL_OPTIONS` and loads
the agent; pick the per-PID collapsed file containing engine frames.

## Bottom line

The workload is dominated by `LegalActionEnumerator.enumerate` (~76% inclusive — expected, it
runs at every priority step). The cost inside it concentrates in a few structural problems that
are **mechanical to fix and card-agnostic**:

1. **Component lookup uses kotlin-reflect + String-keyed maps** on every single access.
2. **`GameState.getBattlefield()` re-filters and re-allocates** the battlefield list on every
   call and is never memoized (unlike `projectedState`, which is already `by lazy`).
3. **Ward / trigger / mana detection re-scan the whole battlefield repeatedly**, including one
   O(n²) inner scan.

State projection (`StateProjector.project`, 7.4%) is **already cached** per `GameState` via
`by lazy` and is *not* a problem — leave it alone.

## Measured hotspots

Inclusive CPU (method appears anywhere on the stack):

| Method | Inclusive % | Note |
|--------|-------------|------|
| `LegalActionEnumerator.enumerate` | ~76% | the workload itself; runs every priority step |
| `ManaSolver.findAvailableManaSources` | ~59% | scans whole battlefield repeatedly |
| `GameState.getBattlefield()` | **19%** | recomputed + 2–3 list allocations per call |
| `TriggerAbilityResolver.getWardTriggeredAbilities` | **13.7%** | full battlefield scan per event; O(n²) suppressor check |
| `TriggerDetector.detectTriggers` | 16.3% | per-event trigger scan |
| `StateProjector.project` | 7.4% | already cached — **not** a target |

Self-time CPU (leaf frame — where cycles are actually spent):

| Leaf | Self % | Attributable to |
|------|--------|-----------------|
| `java/util/HashMap.getNode` | 7.5% | String-keyed component map lookups |
| `ManaSolver.getStaticGrantedManaAbilities` | 3.5% | per-enumerate battlefield scan |
| `KClassImpl.getQualifiedName` (+ `SoftReference.get` 1.7%) | ~4% | reflective component keying |
| `String.equals` / `String.hashCode` | ~3% | String-keyed component map |
| `TriggerAbilityResolver.getWardTriggeredAbilities` | 2.3% | battlefield re-scan |
| `Arena::grow` / `zero_blocks` / `posix_madvise` | ~3% | allocation / GC churn |
| `__psynch_*` | ~6% | thread-pool lock contention (benchmark harness, not engine) |

## Root causes

### A. `ComponentContainer` keys every component by `T::class.qualifiedName`

`rules-engine/.../state/ComponentContainer.kt`:

```kotlin
inline fun <reified T : Component> get(): T? = components[T::class.qualifiedName] as? T
inline fun <reified T : Component> has(): Boolean = components.containsKey(T::class.qualifiedName)
inline fun <reified T : Component> with(component: T) =
    ComponentContainer(components + (T::class.qualifiedName!! to component))
```

`qualifiedName` is a **kotlin-reflect** call (`KClassImpl.getQualifiedName`, backed by a
`SoftReference` cache), and `components` is `Map<String, Component>`, so every access also pays
String `hashCode` + `equals`. This single design choice feeds the reflection cluster (~4%), most
of `HashMap.getNode` (7.5%), and the String hashing (~3%). Every component access in the engine —
and there are many per enumerate — goes through it.

### B. `getBattlefield()` is recomputed and allocates on every call (19% inclusive)

`rules-engine/.../state/GameState.kt`:

```kotlin
fun getBattlefield(): List<EntityId> =
    zones.filterKeys { it.zoneType == Zone.BATTLEFIELD }   // allocates a new map
         .values.flatten()                                  // allocates a new list
         .filter { entities[it]?.has<PhasedOutComponent>() != true }  // new list + reflective has<> per entity
```

Called in tight loops by ward detection (4 separate loops), `ManaSolver` (5+ loops), and
cast-permission checks. It is never memoized, even though `GameState` is immutable and
`projectedState` on the same class already demonstrates the `by lazy` pattern.

### C. Ward / trigger detection re-scan the battlefield, with an O(n²) inner scan

`TriggerAbilityResolver` iterates `state.getBattlefield()` in `getTriggeredAbilities`,
`getTriggeredAbilitiesWithProviders`, `getWardTriggeredAbilities`, and `isWardSuppressed` —
and `isWardSuppressed` does `getBattlefield().any { … }` **inside** a `getBattlefield()` loop,
making ward resolution quadratic in board size. Each of those `getBattlefield()` calls also pays
cost (B).

### D. Secondary: allocation churn & lock contention

`Arena::grow` / `zero_blocks` / `madvise` (~3%) is allocation pressure fed by the list/map copies
in (B) and by `with`/`without` rebuilding the component map on every mutation. `__psynch_*` (~6%)
is thread-pool lock contention from the benchmark's 8-way fan-out — a harness artifact, not engine
logic; ignore it for engine optimization.

## Improvement plan

Ordered by impact ÷ risk. Steps 1 and 3 are independent, individually shippable, and together
should be the bulk of the win.

### Step 1 — Remove reflection from component keys *(quick · low risk · ~5–8%)*

Replace `T::class.qualifiedName` → `T::class.java.name` in all six sites in `ComponentContainer`
(plus the two raw `::class.qualifiedName` lookups in `DamageUtils` / elsewhere). `Class.getName()`
is JVM-cached and avoids kotlin-reflect entirely. The map stays `Map<String, Component>`, so the
serialization shape is **unchanged**. Pure mechanical swap; kills the
`KClassImpl.getQualifiedName` / `SoftReference` cluster outright.

- **Risk:** very low. `qualifiedName` and `java.name` differ only for nested classes (`.` vs `$`),
  and the key is internal — never persisted as a public contract. Verify with `just test-rules`.

### Step 2 — Key components by `Class<*>` instead of `String` *(medium · ~8–12% on top of Step 1)*

Change the internal map to `Map<Class<*>, Component>` (`T::class.java` as key). `Class` uses
identity hashCode — no string hashing at all — eliminating most of `HashMap.getNode` and the
remaining `String.equals` / `hashCode`. `T::class.java` compiles to a constant-pool class-literal
load (no reflection).

- **Requires** a custom `KSerializer<ComponentContainer>` that serializes keys via class name to
  preserve JSON round-trips (the wire format keeps using names; only the in-memory key changes).
- **Risk:** medium — touches serialization. Gate behind the serialization tests; confirm a
  save/load round-trip of a live `GameState`.

### Step 3 — Memoize `getBattlefield()` per `GameState` *(quick · low risk · ~10–15%)*

Make `getBattlefield()` a `by lazy val` mirroring `projectedState` — safe because `GameState` is
immutable, so the battlefield set is constant for the lifetime of a state instance. Precompute the
phased-out filter once rather than a reflective `has<PhasedOutComponent>()` per entity per call.

- **Risk:** low. The only correctness concern is that nothing mutates a `GameState` in place after
  construction — which the immutability invariant already guarantees. Keep `allBattlefieldEntities()`
  (the phased-out-inclusive variant) as-is.

### Step 4 — Hoist battlefield scans in ward / trigger / mana detection *(medium · ~5–8%)*

Compute the battlefield list **once** per `detectTriggers` / `findAvailableManaSources` call and
pass it down to the inner loops instead of re-calling `getBattlefield()`. Fix the O(n²)
`isWardSuppressed` by precomputing the set of ward-suppressors once per detection pass.

- Largely subsumed by Step 3's memoization for the allocation cost, but eliminating the redundant
  *iteration* (and the quadratic suppressor check) is a separate, additive win on big boards.
- **Risk:** medium — refactors signatures in `TriggerAbilityResolver` / `ManaSolver`; behavior
  must be identical. Cover with existing ward / trigger scenario tests.

### Step 5 (optional) — Reduce component-map copy churn *(only if still hot)*

If `Arena::grow` is still prominent after 1–4, reduce the `map + (k to v)` / `map - k` rebuild
cost in `with` / `without` for hot single-component updates (e.g. a small persistent map or a
copy-on-write builder). Profile-gated — don't pre-optimize this.

## Validation loop

After **each** step:

1. `just test-rules` — correctness must be unchanged.
2. `just benchmark-random 200 BLB` — compare wall time / throughput against the baseline below.
3. Re-profile (commands above) — confirm the targeted leaf shrank and nothing regressed.

### Baseline (pre-optimization, for comparison)

`just benchmark-random 200 BLB`, 8 threads:

- Completed 200 / 200, 0 crashes
- Turns avg 26.5, actions avg 1,569 / game
- Throughput ~404 actions/sec per thread
- Wall time ~98 s; ~2.0 games/sec wall-clock
- Time split: Enumerate ~57% / Process ~43%

Conservatively, **Steps 1 + 3 alone** should cut total CPU by **~20–30%** at low risk. Start there.
