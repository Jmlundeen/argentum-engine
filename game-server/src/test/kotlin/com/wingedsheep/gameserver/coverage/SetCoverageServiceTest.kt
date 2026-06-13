package com.wingedsheep.gameserver.coverage

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * The denominator (canonical totals) is a committed resource; the numerator (implemented
 * cards) is the live [com.wingedsheep.mtg.sets.MtgSetCatalog]. These tests pin the join:
 * coverage is an intersection, so `implemented` can never exceed `total`, and a fully
 * implemented set reads 100%.
 */
class SetCoverageServiceTest : FunSpec({

    val service = SetCoverageService()
    val coverage = service.coverage()

    test("reports coverage for the catalogued sets") {
        coverage.shouldNotBeEmpty()
        coverage.map { it.code }.toSet().size shouldBe coverage.size // codes are unique
    }

    test("implemented never exceeds total (booster and extra) and percent is well-formed") {
        coverage.forEach { s ->
            withClue(s.code) {
                s.implemented shouldBeGreaterThanOrEqualTo 0
                s.implemented shouldBeLessThanOrEqualTo s.total
                s.extraImplemented shouldBeGreaterThanOrEqualTo 0
                s.extraImplemented shouldBeLessThanOrEqualTo s.extraTotal
                s.percent shouldBeGreaterThanOrEqual 0.0
                s.percent shouldBeLessThanOrEqual 100.0
            }
        }
    }

    test("percent is over booster (draft) cards only") {
        coverage.forEach { s ->
            val expected = if (s.total == 0) 0.0 else Math.round(s.implemented * 1000.0 / s.total) / 10.0
            withClue(s.code) { s.percent shouldBe expected }
        }
    }

    test("rows are ordered newest release first") {
        val dates = coverage.map { it.releaseDate ?: "" }
        dates shouldBe dates.sortedDescending()
    }

    test("a set with all booster cards implemented reads 100% — Bloomburrow is 261/261 draft") {
        val blb = coverage.find { it.code == "BLB" }.shouldNotBeNull()
        blb.total shouldBe 261
        blb.implemented shouldBe 261
        blb.percent shouldBe 100.0
        // The 18 completionist extras are reported separately, not folded into the headline %.
        blb.extraTotal shouldBe 18
    }

    test("a set with no booster falls back to the whole set — Bloomburrow Commander has cards, not 0%") {
        // Commander / supplemental sets flag every card booster:false, so without the fallback
        // the draft-only headline would read a useless 0/0. The whole set is the main pool instead.
        val blc = coverage.find { it.code == "BLC" }.shouldNotBeNull()
        blc.total shouldBe 312
        blc.implemented shouldBeGreaterThanOrEqualTo 1
        blc.extraTotal shouldBe 0 // no separate "extra" bucket — the set IS the main pool
    }

    test("detail lists every canonical card with an implemented flag, counts agreeing with the grid") {
        val grid = coverage.find { it.code == "BLB" }.shouldNotBeNull()
        val detail = service.detail("blb").shouldNotBeNull() // case-insensitive
        detail.draft.size shouldBe grid.total
        detail.extra.size shouldBe grid.extraTotal
        detail.draft.count { it.implemented } shouldBe grid.implemented
        detail.extra.count { it.implemented } shouldBe grid.extraImplemented
        detail.percent shouldBe grid.percent
    }

    test("detail returns null for an unknown set") {
        service.detail("ZZZ") shouldBe null
    }
})
