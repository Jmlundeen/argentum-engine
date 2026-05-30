package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseColorDecision
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Crosis, the Purger.
 *
 * Card reference:
 * - Crosis, the Purger ({3}{U}{B}{R}): 6/6 Legendary Creature — Dragon, Flying
 *   "Whenever Crosis deals combat damage to a player, you may pay {2}{B}. If you do, choose a color,
 *    then that player reveals their hand and discards all cards of that color."
 *
 * The damaged player ([com.wingedsheep.sdk.scripting.references.Player.TriggeringPlayer]) reveals and
 * discards every card of the chosen color via `CardPredicate.HasChosenColor`.
 */
class CrosisThePurgerScenarioTest : ScenarioTestBase() {

    private val blueCard = CardDefinition.creature(
        name = "Blue Bird", manaCost = ManaCost.parse("{U}"),
        subtypes = setOf(Subtype("Bird")), power = 1, toughness = 1
    )
    private val redCard = CardDefinition.creature(
        name = "Red Goblin", manaCost = ManaCost.parse("{R}"),
        subtypes = setOf(Subtype("Goblin")), power = 1, toughness = 1
    )

    init {
        cardRegistry.register(blueCard)
        cardRegistry.register(redCard)

        context("Crosis combat-damage trigger") {

            fun freshGame() = scenario()
                .withPlayers("Attacker", "Defender")
                .withCardOnBattlefield(1, "Crosis, the Purger")
                .withCardInHand(2, "Blue Bird")
                .withCardInHand(2, "Blue Bird")
                .withCardInHand(2, "Red Goblin")
                .withLandsOnBattlefield(1, "Swamp", 3) // pays {2}{B}
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            fun dealCombatDamage(game: TestGame) {
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Crosis, the Purger" to 2))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers()

                var iterations = 0
                while (!game.hasPendingDecision() && iterations < 50) {
                    val p = game.state.priorityPlayerId ?: break
                    game.execute(PassPriority(p))
                    iterations++
                }
            }

            test("paying and choosing blue discards both blue cards but not the red one") {
                val game = freshGame()
                dealCombatDamage(game)

                withClue("Crosis's combat-damage trigger should ask whether to pay {2}{B}") {
                    game.hasPendingDecision() shouldBe true
                }

                game.answerYesNo(true)
                game.submitManaSourcesAutoPay()

                val colorDecision = game.getPendingDecision()
                withClue("Should pause for a color choice after paying") {
                    (colorDecision is ChooseColorDecision) shouldBe true
                }
                game.submitDecision(ColorChosenResponse(colorDecision!!.id, Color.BLUE))

                withClue("Both blue cards discarded, red one kept") {
                    game.handSize(2) shouldBe 1
                    game.graveyardSize(2) shouldBe 2
                    game.isInGraveyard(2, "Blue Bird") shouldBe true
                }
            }

            test("declining the payment discards nothing") {
                val game = freshGame()
                dealCombatDamage(game)

                game.answerYesNo(false)

                withClue("Declining {2}{B} should discard nothing") {
                    game.handSize(2) shouldBe 3
                    game.graveyardSize(2) shouldBe 0
                }
            }
        }
    }
}
