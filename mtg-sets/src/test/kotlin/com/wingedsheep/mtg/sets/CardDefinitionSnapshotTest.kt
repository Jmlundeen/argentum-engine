package com.wingedsheep.mtg.sets

import com.wingedsheep.sdk.serialization.CardExporter
import io.kotest.core.spec.style.FunSpec

/**
 * Golden snapshot of every registered card's compiled effect tree (Lesson 2,
 * `backlog/phase-rs-lessons.md`).
 *
 * For each set we serialize every [com.wingedsheep.sdk.model.CardDefinition] to canonical JSON via
 * [CardExporter] and assert it against a committed golden at
 * `src/test/resources/snapshots/cards/<code>.json`. Any SDK change that alters a card's lowered tree
 * — even on a card with no scenario test — shows up as a precise per-card diff instead of shipping
 * silently. This is the corpus-wide regression net the FacadeBoundary / `Condition`-unification kind
 * of refactor lacked.
 *
 * **Re-blessing.** After an *intentional* SDK change, review the failing diff (the fresh output is
 * written under `build/snapshots-actual/`), then regenerate the goldens:
 *
 * ```
 * ./gradlew :mtg-sets:test --tests "*CardDefinitionSnapshotTest" -DupdateSnapshots=true
 * ```
 *
 * A green re-bless with an expected diff is the normal workflow — see [GoldenSnapshot].
 *
 * **Determinism.** [com.wingedsheep.sdk.scripting.AbilityId.generate] draws from a process-global
 * counter, so the raw `ability_N` numbers depend on JVM load order and are not stable across runs.
 * We renumber them per card (first-appearance order) so the snapshot is deterministic while still
 * preserving any intra-card cross-references between abilities. Cards are sorted by name so set
 * iteration order never leaks into the file.
 *
 * **Scope.** This pins the *encode* direction (CardDefinition → JSON). It does not exercise the
 * decode path; the hand-picked [com.wingedsheep.sdk.serialization.CardLoader] round-trip tests still
 * own that. A corpus-wide round-trip was prototyped here but revealed a pre-existing compact/expand
 * asymmetry in `CompactJsonTransformer` (several sets' exported JSON does not re-decode); fixing that
 * is tracked separately and is not a prerequisite for this regression net.
 */
class CardDefinitionSnapshotTest : FunSpec({

    MtgSetCatalog.all.forEach { set ->
        test("${set.code} (${set.displayName}): card trees match golden") {
            val actual = set.cards.sortedBy { it.name }.joinToString("\n\n") { card ->
                "// ${card.name}\n${normalizeAbilityIds(CardExporter.exportToJson(card))}"
            }
            GoldenSnapshot.verify("snapshots/cards/${set.code}.json", actual)
        }
    }
})

/**
 * Replace each distinct `ability_<n>` with a per-card sequential id in first-appearance order, so the
 * process-global ability counter (and thus JVM load order) cannot leak into the snapshot while
 * cross-references within a single card stay consistent. Deterministic ids minted elsewhere
 * (`class_level_up_*`, `intrinsic_mana_*`) are left untouched.
 */
private fun normalizeAbilityIds(json: String): String {
    val mapping = LinkedHashMap<String, String>()
    return Regex("""ability_\d+""").replace(json) { match ->
        mapping.getOrPut(match.value) { "ability_${mapping.size + 1}" }
    }
}
