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
 * Scenario tests for Darigaaz, the Igniter.
 *
 * Card reference:
 * - Darigaaz, the Igniter ({3}{B}{R}{G}): 6/6 Legendary Creature — Dragon, Flying
 *   "Whenever Darigaaz deals combat damage to a player, you may pay {2}{R}. If you do, choose a color,
 *    then that player reveals their hand and Darigaaz deals damage to the player equal to the number
 *    of cards of that color revealed this way."
 *
 * The combat damage (6) drops the defender to 14; the trigger then deals damage equal to the count
 * of chosen-color cards in the defender's revealed hand.
 */
class DarigaazTheIgniterScenarioTest : ScenarioTestBase() {

    private val redCard = CardDefinition.creature(
        name = "Red Goblin", manaCost = ManaCost.parse("{R}"),
        subtypes = setOf(Subtype("Goblin")), power = 1, toughness = 1
    )
    private val blueCard = CardDefinition.creature(
        name = "Blue Bird", manaCost = ManaCost.parse("{U}"),
        subtypes = setOf(Subtype("Bird")), power = 1, toughness = 1
    )

    init {
        cardRegistry.register(redCard)
        cardRegistry.register(blueCard)

        context("Darigaaz combat-damage trigger") {

            fun freshGame() = scenario()
                .withPlayers("Attacker", "Defender")
                .withCardOnBattlefield(1, "Darigaaz, the Igniter")
                .withCardInHand(2, "Red Goblin")
                .withCardInHand(2, "Red Goblin")
                .withCardInHand(2, "Blue Bird")
                .withLandsOnBattlefield(1, "Mountain", 3) // pays {2}{R}
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            fun dealCombatDamage(game: TestGame) {
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Darigaaz, the Igniter" to 2))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers()

                var iterations = 0
                while (!game.hasPendingDecision() && iterations < 50) {
                    val p = game.state.priorityPlayerId ?: break
                    game.execute(PassPriority(p))
                    iterations++
                }
            }

            test("choosing red deals damage equal to the two red cards in hand") {
                val game = freshGame()
                dealCombatDamage(game)

                withClue("Darigaaz's combat-damage trigger should ask whether to pay {2}{R}") {
                    game.hasPendingDecision() shouldBe true
                }

                game.answerYesNo(true)
                game.submitManaSourcesAutoPay()

                val colorDecision = game.getPendingDecision()
                withClue("Should pause for a color choice after paying") {
                    (colorDecision is ChooseColorDecision) shouldBe true
                }
                game.submitDecision(ColorChosenResponse(colorDecision!!.id, Color.RED))

                // 20 - 6 combat - 2 (two red cards) = 12.
                withClue("Defender takes 6 combat + 2 damage for two red cards") {
                    game.getLifeTotal(2) shouldBe 12
                }
            }

            test("declining the payment deals only combat damage") {
                val game = freshGame()
                dealCombatDamage(game)

                game.answerYesNo(false)

                withClue("Declining {2}{R} deals only the 6 combat damage") {
                    game.getLifeTotal(2) shouldBe 14
                }
            }
        }
    }
}
