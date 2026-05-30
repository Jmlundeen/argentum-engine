package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Molimo, Maro-Sorcerer.
 *
 * Card reference:
 * - Molimo, Maro-Sorcerer {4}{G}{G}{G} — Legendary Creature — Elemental Sorcerer (star/star)
 *   Trample
 *   Molimo's power and toughness are each equal to the number of lands you control.
 *
 * Characteristic-defining ability: base P/T equals the controller's land count (lands only count
 * for the controller, not opponents).
 */
class MolimoMaroSorcererScenarioTest : ScenarioTestBase() {

    init {
        context("Molimo, Maro-Sorcerer") {

            test("P/T equals the number of lands you control") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Molimo, Maro-Sorcerer")
                    .withLandsOnBattlefield(1, "Forest", 5)
                    .withLandsOnBattlefield(2, "Mountain", 3) // opponent lands don't count
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val projected = game.state.projectedState
                val molimo = game.findPermanent("Molimo, Maro-Sorcerer")!!
                withClue("Molimo is 5/5 with five controlled lands (opponent's lands ignored)") {
                    projected.getPower(molimo) shouldBe 5
                    projected.getToughness(molimo) shouldBe 5
                }
            }

            test("P/T scales with additional lands") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Molimo, Maro-Sorcerer")
                    .withLandsOnBattlefield(1, "Forest", 8)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val projected = game.state.projectedState
                val molimo = game.findPermanent("Molimo, Maro-Sorcerer")!!
                withClue("Molimo is 8/8 with eight controlled lands") {
                    projected.getPower(molimo) shouldBe 8
                    projected.getToughness(molimo) shouldBe 8
                }
            }
        }
    }
}
