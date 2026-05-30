package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Tectonic Instability.
 *
 * Tectonic Instability: {2}{R} Enchantment
 * Whenever a land enters, tap all lands its controller controls.
 *
 * Exercises the new `EffectTarget.ControllerOfTriggeringEntity` resolution in the
 * `ControlledByReferencedPlayer` controller predicate: the trigger taps every land controlled
 * by the entering land's controller (and only that controller's).
 */
class TectonicInstabilityScenarioTest : ScenarioTestBase() {

    private fun TestGame.playLand(playerNumber: Int, landName: String): com.wingedsheep.engine.core.ExecutionResult {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        val cardId = state.getHand(playerId).find { entityId ->
            state.getEntity(entityId)?.get<CardComponent>()?.name == landName
        } ?: error("Card '$landName' not found in player $playerNumber's hand")
        return execute(PlayLand(playerId, cardId))
    }

    private fun TestGame.allTapped(playerNumber: Int, landName: String): Boolean {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        val lands = findAllPermanents(landName).filter {
            state.getEntity(it)?.get<CardComponent>()?.ownerId == playerId
        }
        return lands.isNotEmpty() && lands.all { state.getEntity(it)?.has<TappedComponent>() == true }
    }

    init {
        context("Tectonic Instability") {

            test("playing a land taps all lands that player controls") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Tectonic Instability")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withCardInHand(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val result = game.playLand(1, "Forest")
                withClue("Play land should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                game.resolveStack()

                withClue("All of player 1's Mountains should be tapped") {
                    game.allTapped(1, "Mountain") shouldBe true
                }
                withClue("The newly-played Forest should also be tapped") {
                    game.allTapped(1, "Forest") shouldBe true
                }
            }

            test("only the entering land's controller is affected") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Tectonic Instability")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withLandsOnBattlefield(2, "Island", 2)
                    .withCardInHand(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.playLand(1, "Forest")
                game.resolveStack()

                withClue("Player 1's Mountains should be tapped") {
                    game.allTapped(1, "Mountain") shouldBe true
                }
                withClue("Player 2's Islands should be untapped") {
                    val islands = game.findAllPermanents("Island")
                    islands.none { game.state.getEntity(it)?.has<TappedComponent>() == true } shouldBe true
                }
            }
        }
    }
}
