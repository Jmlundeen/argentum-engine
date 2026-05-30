package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Elfhame Sanctuary.
 *
 * Elfhame Sanctuary: {1}{G}
 * Enchantment
 * At the beginning of your upkeep, you may search your library for a basic land card, reveal
 * that card, put it into your hand, then shuffle. If you do, you skip your draw step this turn.
 *
 * Exercises the new SkipNextDrawStep effect / SkipDrawStepComponent gated by IfYouDoEffect:
 * fetching a land skips the draw step; declining the search (selecting no card) draws normally.
 */
class ElfhameSanctuaryScenarioTest : ScenarioTestBase() {

    private fun ScenarioTestBase.ScenarioBuilder.withCardsInLibrary(
        playerNumber: Int, cardName: String, count: Int
    ): ScenarioBuilder {
        repeat(count) { withCardInLibrary(playerNumber, cardName) }
        return this
    }

    init {
        context("Elfhame Sanctuary upkeep trigger") {

            test("fetching a basic land skips the draw step") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Elfhame Sanctuary")
                    .withCardsInLibrary(1, "Forest", 5)
                    .withCardsInLibrary(2, "Plains", 10)
                    .withActivePlayer(1)
                    .withTurnNumber(3)
                    .inPhase(Phase.BEGINNING, Step.UNTAP)
                    .build()

                withClue("hand starts empty") { game.handSize(1) shouldBe 0 }

                // Advance to upkeep; the search trigger surfaces a card-selection decision.
                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
                game.resolveStack()

                withClue("upkeep trigger should ask which basic land to fetch") {
                    game.hasPendingDecision() shouldBe true
                }

                val forest = game.state.getLibrary(game.player1Id).firstOrNull { id ->
                    game.state.getEntity(id)
                        ?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()
                        ?.name == "Forest"
                }!!
                game.selectCards(listOf(forest))

                withClue("fetched Forest should be in hand") { game.handSize(1) shouldBe 1 }

                // Advance through the draw step into the main phase.
                game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

                withClue("draw step was skipped, so hand still only holds the fetched land") {
                    game.handSize(1) shouldBe 1
                }
            }

            test("declining the search performs a normal draw") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Elfhame Sanctuary")
                    .withCardsInLibrary(1, "Forest", 5)
                    .withCardsInLibrary(2, "Plains", 10)
                    .withActivePlayer(1)
                    .withTurnNumber(3)
                    .inPhase(Phase.BEGINNING, Step.UNTAP)
                    .build()

                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
                game.resolveStack()

                withClue("upkeep trigger should ask which basic land to fetch") {
                    game.hasPendingDecision() shouldBe true
                }
                // Decline by choosing no card.
                game.skipSelection()

                withClue("declining leaves hand empty") { game.handSize(1) shouldBe 0 }

                // Advance through the draw step into the main phase: a normal draw happens.
                game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)

                withClue("draw step was not skipped, so the player drew one card") {
                    game.handSize(1) shouldBe 1
                }
            }
        }
    }
}
