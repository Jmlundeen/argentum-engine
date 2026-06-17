package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for the Secrets of Strixhaven "batch B" cards:
 *  - Comforting Counsel ({1}{G} Enchantment) — growth counter on each life gain; +3/+3 anthem at 5+.
 *  - Mind Roots ({1}{B}{G} Sorcery) — target player discards two; put up to one discarded land tapped under you.
 *  - Snarl Song ({5}{G} Sorcery) — Converge; two 0/0 Fractals, X +1/+1 counters on each, gain X life.
 *
 * (Goblin Glasswright // Craft with Pride is covered by the shared prepare-mechanic tests; its
 * prepare spell only makes a Treasure via existing effects.)
 */
class SosCardsBatchBScenarioTest : ScenarioTestBase() {

    private fun growthCounters(game: TestGame, id: EntityId): Int =
        game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.GROWTH) ?: 0

    private fun plusOneCounters(game: TestGame, id: EntityId): Int =
        game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    init {
        context("Comforting Counsel — growth counters per life gain, +3/+3 anthem at 5+") {
            test("each life gain adds a growth counter; the anthem turns on at five counters") {
                var builder = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Comforting Counsel")
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Plains", 30)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                repeat(6) { builder = builder.withCardInHand(1, "Venerable Monk") }
                repeat(5) { builder = builder.withCardInLibrary(1, "Forest") }
                repeat(5) { builder = builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()

                val counsel = game.findPermanent("Comforting Counsel")!!
                val bear = game.findPermanent("Grizzly Bears")!!

                // Gain life four times (Venerable Monk ETB gains 2 each) → four growth counters.
                repeat(4) {
                    game.castSpell(1, "Venerable Monk")
                    game.resolveStack()
                }
                withClue("Four life gains → four growth counters") {
                    growthCounters(game, counsel) shouldBe 4
                }
                withClue("Below five counters: no anthem (Grizzly Bears stays 2/2)") {
                    game.state.projectedState.getPower(bear) shouldBe 2
                    game.state.projectedState.getToughness(bear) shouldBe 2
                }

                // Fifth life gain crosses the threshold.
                game.castSpell(1, "Venerable Monk")
                game.resolveStack()
                withClue("Five growth counters now present") {
                    growthCounters(game, counsel) shouldBe 5
                }
                withClue("At 5+ counters the anthem gives creatures you control +3/+3 (Grizzly Bears 5/5)") {
                    game.state.projectedState.getPower(bear) shouldBe 5
                    game.state.projectedState.getToughness(bear) shouldBe 5
                }
            }
        }

        context("Mind Roots — target player discards two, put a discarded land tapped under you") {
            test("the target player discards two; you put one discarded land onto the battlefield tapped") {
                var builder = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Mind Roots")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                // Opponent's hand: a land (Mountain) plus two non-lands to choose among.
                builder = builder.withCardInHand(2, "Mountain")
                builder = builder.withCardInHand(2, "Grizzly Bears")
                builder = builder.withCardInHand(2, "Grizzly Bears")
                repeat(5) { builder = builder.withCardInLibrary(1, "Forest") }
                repeat(5) { builder = builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()

                val landsBefore = game.findPermanents("Mountain").size

                game.castSpellTargetingPlayer(1, "Mind Roots", 2)
                game.resolveStack()

                // First decision: the opponent discards two cards — discard the Mountain + a Bears.
                val mountain = game.findCardsInHand(2, "Mountain").first()
                val aBears = game.findCardsInHand(2, "Grizzly Bears").first()
                game.selectCards(listOf(mountain, aBears))
                game.resolveStack()

                withClue("Opponent discarded two — hand drops to one Grizzly Bears") {
                    game.handSize(2) shouldBe 1
                }

                // Second decision: you may put up to one discarded land tapped under your control.
                withClue("A put-the-land decision should be pending") {
                    game.state.pendingDecision shouldNotBe null
                }
                game.selectCards(listOf(mountain))
                game.resolveStack()

                val mountainsAfter = game.findPermanents("Mountain")
                withClue("The discarded Mountain enters the battlefield (one new Mountain permanent)") {
                    mountainsAfter.size shouldBe landsBefore + 1
                }
                val newMountain = mountainsAfter.first()
                withClue("It is controlled by you (player 1)") {
                    game.state.getEntity(newMountain)?.get<ControllerComponent>()?.playerId shouldBe game.player1Id
                }
                withClue("It enters tapped") {
                    (game.state.getEntity(newMountain)?.get<TappedComponent>() != null) shouldBe true
                }
            }

            test("if no land is discarded there is no land to put — the up-to-one is a no-op") {
                var builder = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Mind Roots")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                // Three non-lands in hand so the opponent actually chooses which two to discard.
                builder = builder.withCardInHand(2, "Grizzly Bears")
                builder = builder.withCardInHand(2, "Grizzly Bears")
                builder = builder.withCardInHand(2, "Grizzly Bears")
                repeat(5) { builder = builder.withCardInLibrary(1, "Forest") }
                repeat(5) { builder = builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()

                game.castSpellTargetingPlayer(1, "Mind Roots", 2)
                game.resolveStack()

                // Opponent discards two non-lands; no land among them.
                val bears = game.findCardsInHand(2, "Grizzly Bears").take(2)
                game.selectCards(bears)
                game.resolveStack()

                withClue("Opponent discarded two — one Grizzly Bears remains in hand") {
                    game.handSize(2) shouldBe 1
                }
                withClue("No land discarded → no permanent should appear under your control") {
                    game.findPermanents("Grizzly Bears").isEmpty() shouldBe true
                }
                withClue("No pending land-put decision when nothing land was discarded") {
                    game.state.pendingDecision shouldBe null
                }
            }
        }

        context("Snarl Song — Converge: two Fractals with X counters each, gain X life") {
            test("three colors spent → two 0/0 Fractals with three +1/+1 counters each, gain 3 life") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Snarl Song")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withLifeTotal(1, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // {5}{G} paid with W,W,U,U + G + ... three distinct colors (W, U, G).
                game.castSpell(1, "Snarl Song")
                game.resolveStack()

                val fractals = game.findPermanents("Fractal Token")
                withClue("Two Fractal tokens should have been created") {
                    fractals.size shouldBe 2
                }
                withClue("Three colors spent → three +1/+1 counters on each Fractal") {
                    fractals.forEach { plusOneCounters(game, it) shouldBe 3 }
                }
                withClue("You gain X = 3 life") {
                    game.getLifeTotal(1) shouldBe 23
                }
            }
        }
    }
}
