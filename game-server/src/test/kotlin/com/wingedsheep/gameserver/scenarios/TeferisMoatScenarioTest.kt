package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseColorDecision
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.state.components.identity.ChosenColorComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Teferi's Moat.
 *
 * Teferi's Moat: {3}{W}{U} Enchantment
 * As this enchantment enters, choose a color.
 * Creatures of the chosen color without flying can't attack you.
 *
 * Exercises the new `CantBeAttackedWithout(keyword, attackerFilter)` parameter: only attackers
 * sharing the chosen color are restricted; other-color and flying creatures are unaffected.
 */
class TeferisMoatScenarioTest : ScenarioTestBase() {

    private fun TestGame.chooseColor(color: Color) {
        val decision = getPendingDecision()
        decision.shouldNotBeNull()
        decision.shouldBeInstanceOf<ChooseColorDecision>()
        submitDecision(ColorChosenResponse(decision.id, color))
    }

    init {
        context("Teferi's Moat") {

            test("choosing green stores the chosen color on the enchantment") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Teferi's Moat")
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val result = game.castSpell(1, "Teferi's Moat")
                withClue("Cast should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                game.resolveStack() // resolve the enchantment; pauses for the color choice
                game.chooseColor(Color.GREEN)
                game.resolveStack()

                val moatId = game.findPermanent("Teferi's Moat")!!
                val chosen = game.state.getEntity(moatId)?.get<ChosenColorComponent>()
                withClue("Chosen color should be stored") {
                    chosen.shouldNotBeNull()
                    chosen.color shouldBe Color.GREEN
                }
            }

            test("non-flying creature of the chosen color can't attack the controller") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Teferi's Moat")
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardOnBattlefield(2, "Elvish Aberration") // green, no flying
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Teferi's Moat")
                game.resolveStack()
                game.chooseColor(Color.GREEN)
                game.resolveStack()

                // Hand over to the opponent's combat
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                val attackerId = game.findPermanent("Elvish Aberration")!!
                val result = game.execute(
                    DeclareAttackers(game.player2Id, mapOf(attackerId to game.player1Id))
                )
                withClue("Green non-flier should not be able to attack the Moat's controller") {
                    result.error shouldNotBe null
                }
            }

            test("creature of a different color can attack normally") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Teferi's Moat")
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardOnBattlefield(2, "Elvish Aberration") // green, no flying
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Choose red — the green Elvish Aberration is not restricted.
                game.castSpell(1, "Teferi's Moat")
                game.resolveStack()
                game.chooseColor(Color.RED)
                game.resolveStack()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                val attackerId = game.findPermanent("Elvish Aberration")!!
                val result = game.execute(
                    DeclareAttackers(game.player2Id, mapOf(attackerId to game.player1Id))
                )
                withClue("Off-color creature should be able to attack: ${result.error}") {
                    result.error shouldBe null
                }
            }
        }
    }
}
