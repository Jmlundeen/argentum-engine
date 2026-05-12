package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.MayPlayFromExileComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Interdimensional Web Watch.
 *
 * Card reference (SPM):
 * "When Interdimensional Web Watch enters, exile the top two cards of your library.
 *  You may play those cards until the end of your next turn."
 * (Activated tap ability for mana spendable only on spells from exile handled separately.)
 */
class InterdimensionalWebWatchScenarioTest : ScenarioTestBase() {

    init {
        context("Interdimensional Web Watch — ETB exile and play permission") {

            test("entering the battlefield exiles the top two library cards and grants play permission until end of controller's next turn") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Interdimensional Web Watch")
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val initialLibrarySize = game.librarySize(1)

                val castResult = game.castSpell(1, "Interdimensional Web Watch")
                withClue("Casting Interdimensional Web Watch should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Interdimensional Web Watch should be on the battlefield") {
                    game.isOnBattlefield("Interdimensional Web Watch") shouldBe true
                }
                withClue("Library should have two fewer cards after the ETB exile") {
                    game.librarySize(1) shouldBe initialLibrarySize - 2
                }

                val exile = game.state.getExile(game.player1Id)
                withClue("Two cards should be in exile after ETB resolves") {
                    exile.size shouldBe 2
                }

                exile.forEach { cardId ->
                    val mayPlay = game.state.getEntity(cardId)?.get<MayPlayFromExileComponent>()
                    val cardName = game.state.getEntity(cardId)?.get<CardComponent>()?.name
                    withClue("Exiled card '$cardName' should be tagged with play permission for the controller") {
                        mayPlay shouldNotBe null
                    }
                    withClue("Play permission on '$cardName' should be granted to player 1 (the controller)") {
                        mayPlay!!.controllerId shouldBe game.player1Id
                    }
                }
            }
        }
    }
}
