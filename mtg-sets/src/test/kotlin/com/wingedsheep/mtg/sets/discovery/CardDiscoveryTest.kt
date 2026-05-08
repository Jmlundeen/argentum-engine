package com.wingedsheep.mtg.sets.discovery

import com.wingedsheep.mtg.sets.definitions.scg.ScourgeSet
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeGreaterThan

/**
 * Smoke test for [CardDiscovery] against the real Scourge package.
 *
 * Scourge is a small, complete, well-tested set, so it makes a good fixture:
 * if discovery agrees with the hand-maintained `ScourgeSet.cards` list, the
 * mechanism (top-level-val scanning + basic-land filtering) works end-to-end.
 *
 * Phase 2 will generalise this into an equivalence harness that runs across
 * every set in `MtgSetCatalog.all`.
 */
class CardDiscoveryTest : FunSpec({

    val scourgePackage = "com.wingedsheep.mtg.sets.definitions.scg.cards"

    test("discovers a non-empty set of cards from the Scourge package") {
        val discovered = CardDiscovery.findIn(scourgePackage)
        discovered.size shouldBeGreaterThan 0
    }

    test("discovery matches the hand-maintained ScourgeSet.cards by name") {
        val discoveredNames = CardDiscovery.findIn(scourgePackage).map { it.name }
        val manualNames = ScourgeSet.cards.map { it.name }
        discoveredNames.shouldContainExactlyInAnyOrder(manualNames)
    }
})
