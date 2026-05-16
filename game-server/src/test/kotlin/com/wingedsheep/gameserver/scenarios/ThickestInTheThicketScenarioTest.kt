package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Thickest in the Thicket ({3}{G}{G} Enchantment).
 *
 * - ETB: put X +1/+1 counters on target creature, where X is that creature's power.
 * - At the beginning of your end step, draw two cards if you control the creature
 *   with the greatest power, or one tied for the greatest power.
 *
 * Note: the end-step ability is gated by an intervening-if condition. When the
 * condition is false at trigger time the trigger never goes on the stack, so we
 * assert on whether the controller actually drew cards rather than on stack size.
 */
class ThickestInTheThicketScenarioTest : ScenarioTestBase() {

    // Vanilla test creatures with known power so we don't depend on specific set cards
    // and so each test fixes the power statically.
    private val bear2_2 = CardDefinition.creature(
        name = "Test Bear",
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(Subtype.BEAR),
        power = 2,
        toughness = 2,
    )

    private val wolf3_3 = CardDefinition.creature(
        name = "Test Wolf",
        manaCost = ManaCost.parse("{2}{G}"),
        subtypes = setOf(Subtype.WOLF),
        power = 3,
        toughness = 3,
    )

    private val ogre5_5 = CardDefinition.creature(
        name = "Test Ogre",
        manaCost = ManaCost.parse("{3}{R}{R}"),
        subtypes = setOf(Subtype.OGRE),
        power = 5,
        toughness = 5,
    )

    init {
        cardRegistry.register(bear2_2)
        cardRegistry.register(wolf3_3)
        cardRegistry.register(ogre5_5)

        context("ETB: put X +1/+1 counters where X is target's power") {

            test("a 2/2 target ends up with two +1/+1 counters") {
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Thickest in the Thicket")
                    .withLandsOnBattlefield(1, "Forest", 5)
                    .withCardOnBattlefield(1, "Test Bear")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bearId = game.findPermanent("Test Bear")!!

                game.castSpell(1, "Thickest in the Thicket")
                // Enchantment resolves; ETB trigger goes on the stack and pauses for target.
                game.resolveStack()
                game.selectTargets(listOf(bearId))
                game.resolveStack()

                val counters = game.state.getEntity(bearId)?.get<CountersComponent>()
                withClue("ETB should have placed +1/+1 counters equal to the target's power (2)") {
                    counters shouldNotBe null
                    counters!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
                }
            }

            test("a 5/5 target ends up with five +1/+1 counters") {
                val game = scenario()
                    .withPlayers()
                    .withCardInHand(1, "Thickest in the Thicket")
                    .withLandsOnBattlefield(1, "Forest", 5)
                    .withCardOnBattlefield(2, "Test Ogre")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val ogreId = game.findPermanent("Test Ogre")!!

                game.castSpell(1, "Thickest in the Thicket")
                game.resolveStack()
                game.selectTargets(listOf(ogreId))
                game.resolveStack()

                val counters = game.state.getEntity(ogreId)?.get<CountersComponent>()
                withClue("ETB should have placed +1/+1 counters equal to opponent's 5-power Ogre") {
                    counters!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 5
                }
            }
        }

        context("End-step draw: you control the creature with the greatest power") {

            /**
             * Pass priority until we reach the end step. Stops if a decision is
             * waiting (a trigger paused for input).
             */
            fun advanceToEndStep(game: TestGame) {
                var iterations = 0
                while (game.state.step != Step.END && game.state.pendingDecision == null && iterations++ < 12) {
                    game.passPriority()
                }
            }

            test("draws two cards when your creature has the global greatest power") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Thickest in the Thicket")
                    .withCardOnBattlefield(1, "Test Ogre")     // your 5/5
                    .withCardOnBattlefield(2, "Test Bear")     // opponent's 2/2
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                    .build()

                val handBefore = game.handSize(1)

                advanceToEndStep(game)
                game.resolveStack()

                withClue("Should draw two cards because you control the strongest creature") {
                    game.handSize(1) shouldBe handBefore + 2
                }
            }

            test("draws two cards on a tie — your creature shares the greatest power") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Thickest in the Thicket")
                    .withCardOnBattlefield(1, "Test Wolf")     // your 3/3
                    .withCardOnBattlefield(2, "Test Wolf")     // opponent's 3/3
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                    .build()

                val handBefore = game.handSize(1)

                advanceToEndStep(game)
                game.resolveStack()

                withClue("Tying for greatest power should still trigger the draw") {
                    game.handSize(1) shouldBe handBefore + 2
                }
            }

            test("does NOT draw when an opponent controls a strictly bigger creature") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Thickest in the Thicket")
                    .withCardOnBattlefield(1, "Test Bear")     // your 2/2
                    .withCardOnBattlefield(2, "Test Ogre")     // opponent's 5/5
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                    .build()

                val handBefore = game.handSize(1)

                advanceToEndStep(game)
                game.resolveStack()

                withClue("Trigger's intervening-if is false; no card should be drawn") {
                    game.handSize(1) shouldBe handBefore
                }
            }

            test("does NOT draw when no creatures are on the battlefield") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Thickest in the Thicket")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                    .build()

                val handBefore = game.handSize(1)

                advanceToEndStep(game)
                game.resolveStack()

                withClue("With no creatures, the 'you control a creature' conjunct keeps the trigger from firing") {
                    game.handSize(1) shouldBe handBefore
                }
            }

            test("does NOT draw on the opponent's end step") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Thickest in the Thicket")
                    .withCardOnBattlefield(1, "Test Ogre")     // your 5/5 — globally greatest
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(2)                       // opponent's turn
                    .inPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                    .build()

                val handBefore = game.handSize(1)

                advanceToEndStep(game)
                game.resolveStack()

                withClue("Trigger keys on YOUR end step; opponent's end step shouldn't fire it") {
                    game.handSize(1) shouldBe handBefore
                }
            }
        }
    }
}
