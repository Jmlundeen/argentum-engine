package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Tsabo's Decree.
 *
 * Tsabo's Decree: {5}{B} Instant
 * Choose a creature type. Target player reveals their hand and discards all creature cards of
 * that type. Then destroy all creatures of that type that player controls. They can't be
 * regenerated.
 *
 * Composes ChooseCreatureType + RevealHand + a chosen-subtype discard + a chosen-subtype board
 * wipe, all keyed off the same chosen type via `HasChosenSubtype`.
 */
class TsabosDecreeScenarioTest : ScenarioTestBase() {

    private fun TestGame.chooseCreatureType(typeName: String) {
        val decision = getPendingDecision()
        decision.shouldNotBeNull()
        decision.shouldBeInstanceOf<ChooseOptionDecision>()
        val index = decision.options.indexOf(typeName)
        withClue("Creature type '$typeName' should be an option") {
            (index >= 0) shouldBe true
        }
        submitDecision(OptionChosenResponse(decision.id, index))
    }

    init {
        context("Tsabo's Decree") {

            test("destroys creatures of the chosen type and discards matching cards") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Tsabo's Decree")
                    .withLandsOnBattlefield(1, "Swamp", 6)
                    .withCardOnBattlefield(2, "Nomadic Elf")     // Elf — destroyed
                    .withCardOnBattlefield(2, "Ardent Soldier")  // Human Soldier — survives
                    .withCardInHand(2, "Nomadic Elf")            // Elf card — discarded
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val result = game.castSpellTargetingPlayer(1, "Tsabo's Decree", 2)
                withClue("Cast should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                game.resolveStack() // resolve the instant; pauses for the creature-type choice
                game.chooseCreatureType("Elf")
                game.resolveStack()

                withClue("Opponent's Elf creature should be destroyed") {
                    game.isOnBattlefield("Nomadic Elf") shouldBe false
                }
                withClue("Opponent's non-Elf creature should survive") {
                    game.isOnBattlefield("Ardent Soldier") shouldBe true
                }
                withClue("The Elf card in hand should have been discarded") {
                    game.isInHand(2, "Nomadic Elf") shouldBe false
                    game.isInGraveyard(2, "Nomadic Elf") shouldBe true
                }
            }
        }
    }
}
