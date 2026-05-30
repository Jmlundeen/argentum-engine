package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Aura Shards.
 *
 * Aura Shards: {1}{G}{W}
 * Enchantment
 * Whenever a creature you control enters, you may destroy target artifact or enchantment.
 */
class AuraShardsScenarioTest : ScenarioTestBase() {

    init {
        context("Aura Shards") {

            test("a creature entering lets you destroy a target enchantment") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Aura Shards")
                    .withCardOnBattlefield(2, "Collective Restraint")
                    .withCardInHand(1, "Willow Dryad")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val enchantmentId = game.findPermanent("Collective Restraint")!!

                // Casting and resolving the creature triggers Aura Shards' ETB ability.
                game.castSpell(1, "Willow Dryad")
                game.resolveStack()

                // Optional "you may" first, then choose the target.
                game.answerYesNo(true)
                game.selectTargets(listOf(enchantmentId))
                game.resolveStack()

                withClue("Willow Dryad should be on the battlefield") {
                    game.isOnBattlefield("Willow Dryad") shouldBe true
                }
                withClue("the targeted enchantment should be destroyed") {
                    game.findPermanents("Collective Restraint").isEmpty() shouldBe true
                    game.isInGraveyard(2, "Collective Restraint") shouldBe true
                }
            }

            test("declining the trigger leaves the enchantment intact") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Aura Shards")
                    .withCardOnBattlefield(2, "Collective Restraint")
                    .withCardInHand(1, "Willow Dryad")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Willow Dryad")
                game.resolveStack()
                game.answerYesNo(false)
                game.resolveStack()

                withClue("the enchantment should still be on the battlefield") {
                    game.isOnBattlefield("Collective Restraint") shouldBe true
                }
            }
        }
    }
}
