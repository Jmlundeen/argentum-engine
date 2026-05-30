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
 * Scenario tests for Dromar, the Banisher.
 *
 * Card reference:
 * - Dromar, the Banisher ({3}{W}{U}{B}): 6/6 Legendary Creature — Dragon, Flying
 *   "Whenever Dromar deals combat damage to a player, you may pay {2}{U}. If you do, choose a color,
 *    then return all creatures of that color to their owners' hands."
 *
 * The chosen-color creature bounce is the creature-only variant of Wash Out's pipeline.
 */
class DromarTheBanisherScenarioTest : ScenarioTestBase() {

    private val redCreature = CardDefinition.creature(
        name = "Red Goblin", manaCost = ManaCost.parse("{R}"),
        subtypes = setOf(Subtype("Goblin")), power = 2, toughness = 2
    )
    private val greenCreature = CardDefinition.creature(
        name = "Green Bear", manaCost = ManaCost.parse("{G}"),
        subtypes = setOf(Subtype("Bear")), power = 2, toughness = 2
    )

    init {
        cardRegistry.register(redCreature)
        cardRegistry.register(greenCreature)

        context("Dromar combat-damage trigger") {

            fun freshGame() = scenario()
                .withPlayers("Attacker", "Defender")
                .withCardOnBattlefield(1, "Dromar, the Banisher")
                .withCardOnBattlefield(2, "Red Goblin")
                .withCardOnBattlefield(2, "Red Goblin")
                .withCardOnBattlefield(2, "Green Bear")
                .withLandsOnBattlefield(1, "Island", 3) // pays {2}{U}
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            fun dealCombatDamage(game: TestGame) {
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Dromar, the Banisher" to 2))
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareNoBlockers()

                var iterations = 0
                while (!game.hasPendingDecision() && iterations < 50) {
                    val p = game.state.priorityPlayerId ?: break
                    game.execute(PassPriority(p))
                    iterations++
                }
            }

            test("choosing red returns both red creatures but leaves the green one") {
                val game = freshGame()
                dealCombatDamage(game)

                withClue("Dromar's combat-damage trigger should ask whether to pay {2}{U}") {
                    game.hasPendingDecision() shouldBe true
                }

                game.answerYesNo(true)
                game.submitManaSourcesAutoPay()

                val colorDecision = game.getPendingDecision()
                withClue("Should pause for a color choice after paying") {
                    (colorDecision is ChooseColorDecision) shouldBe true
                }
                game.submitDecision(ColorChosenResponse(colorDecision!!.id, Color.RED))

                withClue("Both red creatures bounced, green creature stays") {
                    game.findAllPermanents("Red Goblin").size shouldBe 0
                    game.findAllPermanents("Green Bear").size shouldBe 1
                    game.handSize(2) shouldBe 2
                }
            }

            test("declining the payment bounces nothing") {
                val game = freshGame()
                dealCombatDamage(game)

                game.answerYesNo(false)

                withClue("Declining {2}{U} should bounce nothing") {
                    game.findAllPermanents("Red Goblin").size shouldBe 2
                    game.findAllPermanents("Green Bear").size shouldBe 1
                }
            }
        }
    }
}
