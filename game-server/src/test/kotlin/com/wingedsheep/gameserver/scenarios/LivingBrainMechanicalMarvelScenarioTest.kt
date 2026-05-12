package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Living Brain, Mechanical Marvel.
 *
 * Card reference:
 * - Living Brain, Mechanical Marvel ({4}): Legendary Artifact Creature — Robot Villain, 3/3
 */
class LivingBrainMechanicalMarvelScenarioTest : ScenarioTestBase() {

    init {
        context("Living Brain, Mechanical Marvel enters the battlefield") {

            test("cast for {4} resolves and enters battlefield as a 3/3 Legendary Artifact Creature — Robot Villain") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Living Brain, Mechanical Marvel")
                    .withLandsOnBattlefield(1, "Swamp", 4)
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Living Brain, Mechanical Marvel")
                withClue("Casting Living Brain, Mechanical Marvel should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Living Brain, Mechanical Marvel should be on the battlefield") {
                    game.isOnBattlefield("Living Brain, Mechanical Marvel") shouldBe true
                }

                val permanentId = game.findPermanent("Living Brain, Mechanical Marvel")
                withClue("Permanent ID should not be null") {
                    permanentId shouldNotBe null
                }

                val clientState = game.getClientState(1)
                val cardInfo = clientState.cards[permanentId]
                withClue("Card info should not be null") {
                    cardInfo shouldNotBe null
                }

                withClue("Should have power 3") {
                    cardInfo!!.power shouldBe 3
                }
                withClue("Should have toughness 3") {
                    cardInfo!!.toughness shouldBe 3
                }
                withClue("Should be a Legendary Artifact Creature — Robot Villain") {
                    cardInfo!!.typeLine shouldBe "Legendary Artifact Creature — Robot Villain"
                }
            }
        }
    }
}
