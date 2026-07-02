package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

/**
 * Diamond Mare (M19 #231, reprinted in FDN) — {2} Artifact Creature — Horse, 1/3.
 *
 * "As this creature enters, choose a color.
 *  Whenever you cast a spell of the chosen color, you gain 1 life."
 *
 * The chosen color is stored on the permanent at ETB (EntersWithChoice → CastChoicesComponent).
 * The cast trigger's spell filter reads that color back via SharesChosenColorWithSource, so only
 * spells matching the chosen color pay off. This exercises the chosen-color predicate inside a
 * spell-cast trigger (previously only used in a replacement effect).
 */
class DiamondMareScenarioTest : ScenarioTestBase() {

    init {
        context("Diamond Mare — gain life when casting a spell of the chosen color") {

            test("casting a spell of the chosen color gains 1 life") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Diamond Mare")
                    .withCardInHand(1, "Lightning Bolt") // red spell
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withActivePlayer(1)
                    .build()

                // Cast Diamond Mare and choose Red as it enters.
                game.castSpell(1, "Diamond Mare").error shouldBe null
                game.resolveStack()

                withClue("Diamond Mare's enter-choice prompt should be pending") {
                    game.hasPendingDecision().shouldBeTrue()
                }
                game.submitDecision(
                    ColorChosenResponse(game.getPendingDecision()!!.id, Color.RED)
                ).error shouldBe null

                val lifeBefore = game.getLifeTotal(1)

                // Cast a red spell → the chosen-color trigger fires and gains 1 life.
                game.castSpellTargetingPlayer(1, "Lightning Bolt", 2).error shouldBe null
                game.resolveStack()

                withClue("casting a red spell with Red chosen → gain 1 life") {
                    game.getLifeTotal(1) shouldBe lifeBefore + 1
                }
            }

            test("casting a spell that is not the chosen color gains no life") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Diamond Mare")
                    .withCardInHand(1, "Divination") // blue spell
                    .withLandsOnBattlefield(1, "Island", 5)
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withCardInLibrary(1, "Shock")
                    .withActivePlayer(1)
                    .build()

                game.castSpell(1, "Diamond Mare").error shouldBe null
                game.resolveStack()
                game.submitDecision(
                    ColorChosenResponse(game.getPendingDecision()!!.id, Color.RED)
                ).error shouldBe null

                val lifeBefore = game.getLifeTotal(1)

                // Cast a blue spell → Red was chosen, so no trigger, no life gain.
                game.castSpell(1, "Divination").error shouldBe null
                game.resolveStack()

                withClue("casting a blue spell with Red chosen → no life gain") {
                    game.getLifeTotal(1) shouldBe lifeBefore
                }
            }
        }
    }
}
