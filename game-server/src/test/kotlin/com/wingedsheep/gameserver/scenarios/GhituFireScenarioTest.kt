package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Ghitu Fire — exercises the flash-timing kicker primitive
 * (KeywordAbility.OptionalAdditionalCost with grantsFlashTiming = true).
 *
 * Card reference:
 * - Ghitu Fire ({X}{R}): Sorcery
 *   You may cast this spell as though it had flash if you pay {2} more to cast it.
 *   Ghitu Fire deals X damage to any target.
 */
class GhituFireScenarioTest : ScenarioTestBase() {

    init {
        context("Ghitu Fire") {

            test("normal sorcery-speed cast deals X damage to a creature") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Ghitu Fire")
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3
                    .withLandsOnBattlefield(1, "Mountain", 4) // X=3 plus {R}
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giantId = game.findPermanent("Hill Giant")!!

                val castResult = game.castXSpell(1, "Ghitu Fire", xValue = 3, targetId = giantId)
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Hill Giant (3/3) should die from 3 damage") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                }
            }

            test("flash-kicker mode lets player cast on opponent's turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Ghitu Fire")
                    .withCardOnBattlefield(2, "Hill Giant") // opponent's 3/3
                    .withLandsOnBattlefield(1, "Mountain", 6) // X=3, {R}, plus {2} kicker
                    .withActivePlayer(2)
                    .withPriorityPlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giantId = game.findPermanent("Hill Giant")!!
                val playerId = game.player1Id
                val hand = game.state.getHand(playerId)
                val cardId = hand.find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Ghitu Fire"
                }!!

                val castResult = game.execute(
                    CastSpell(
                        playerId = playerId,
                        cardId = cardId,
                        targets = listOf(ChosenTarget.Permanent(giantId)),
                        xValue = 3,
                        wasKicked = true,
                    )
                )
                withClue("Flash-kicker cast on opponent's turn should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Hill Giant should die from 3 damage") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                }
            }

            test("sorcery-speed cast on opponent's turn is rejected (no kicker)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Ghitu Fire")
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withLandsOnBattlefield(1, "Mountain", 4) // enough for X=3 + R but NOT the {2} kicker
                    .withActivePlayer(2)
                    .withPriorityPlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giantId = game.findPermanent("Hill Giant")!!
                val playerId = game.player1Id
                val hand = game.state.getHand(playerId)
                val cardId = hand.find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Ghitu Fire"
                }!!

                val plainResult = game.execute(
                    CastSpell(
                        playerId = playerId,
                        cardId = cardId,
                        targets = listOf(ChosenTarget.Permanent(giantId)),
                        xValue = 3,
                        wasKicked = false,
                    )
                )
                withClue("Sorcery-speed cast on opponent's turn must be blocked") {
                    plainResult.error shouldNotBe null
                }
            }

            test("X damage to a player") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Ghitu Fire")
                    .withLandsOnBattlefield(1, "Mountain", 5) // X=4 + R
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val playerId = game.player1Id
                val opponentId = game.player2Id
                val hand = game.state.getHand(playerId)
                val cardId = hand.find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Ghitu Fire"
                }!!

                val castResult = game.execute(
                    CastSpell(
                        playerId = playerId,
                        cardId = cardId,
                        targets = listOf(ChosenTarget.Player(opponentId)),
                        xValue = 4,
                    )
                )
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Opponent should have taken 4 damage") {
                    game.getLifeTotal(2) shouldBe 20 - 4
                }
            }
        }
    }
}
