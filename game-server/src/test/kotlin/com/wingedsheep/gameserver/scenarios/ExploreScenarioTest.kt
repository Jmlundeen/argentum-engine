package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.RevealedToComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for the Map token and Explore mechanic.
 *
 * Map token: Artifact — Map
 *   "{1}, {T}, Sacrifice this token: Target creature you control explores.
 *    Activate only as a sorcery."
 *
 * Explore: "Reveal the top card of your library. If it's a land card, put it into your hand.
 *   Otherwise, put a +1/+1 counter on this creature, then put the card back or put it into
 *   your graveyard."
 *
 * Test cases:
 * 1. Sentinel of the Nameless City creates a Map token on ETB
 * 2. Sentinel of the Nameless City creates a Map token when attacking
 * 3. Explore with a land on top → land goes directly to hand, no counter
 * 4. Explore with a non-land on top → +1/+1 counter placed, player puts it back on top of library
 * 5. Explore with a non-land on top → +1/+1 counter placed, player puts it in graveyard
 */
class ExploreScenarioTest : ScenarioTestBase() {

    // -------------------------------------------------------------------------
    // Helper: activate the first activated ability of the Map token
    // -------------------------------------------------------------------------
    private fun activateMapToken(
        game: TestGame,
        mapId: com.wingedsheep.sdk.model.EntityId,
        creatureId: com.wingedsheep.sdk.model.EntityId
    ) {
        val mapCardDef = cardRegistry.getCard("Map")!!
        val ability = mapCardDef.script.activatedAbilities.first()
        val result = game.execute(
            ActivateAbility(
                playerId = game.player1Id,
                sourceId = mapId,
                abilityId = ability.id,
                targets = listOf(ChosenTarget.Permanent(creatureId))
            )
        )
        withClue("Map ability should activate without error: ${result.error}") {
            result.error shouldBe null
        }
    }

    private fun plusOneCounters(game: TestGame, entityId: com.wingedsheep.sdk.model.EntityId): Int =
        game.state.getEntity(entityId)
            ?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE)
            ?: 0

    init {
        // ------------------------------------------------------------------
        // Map token creation via Sentinel of the Nameless City
        // ------------------------------------------------------------------
        context("Map token creation") {

            test("Sentinel creates a Map token when it enters the battlefield") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Sentinel of the Nameless City")
                    .withLandsOnBattlefield(1, "Forest", 3) // {2}{G}
                    .withCardInLibrary(2, "Forest") // prevent draw-loss
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Sentinel of the Nameless City")
                game.resolveStack()

                withClue("Sentinel should be on battlefield") {
                    game.isOnBattlefield("Sentinel of the Nameless City") shouldBe true
                }
                withClue("Exactly one Map token should be created on ETB") {
                    game.findAllPermanents("Map").size shouldBe 1
                }
            }

            test("Sentinel creates a Map token when it attacks") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Sentinel of the Nameless City")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Sentinel of the Nameless City" to 2))
                game.resolveStack()

                withClue("Map token should be created on attack") {
                    game.findAllPermanents("Map").size shouldBe 1
                }
            }
        }

        // ------------------------------------------------------------------
        // Explore: land on top of library
        // ------------------------------------------------------------------
        context("Explore with land on top") {

            test("land goes to hand and no counter is placed") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Map")       // Map token
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2 Human Soldier to explore
                    .withLandsOnBattlefield(1, "Forest", 1) // {1} for Map activation
                    .withCardInLibrary(1, "Forest")         // land on top → goes to hand
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val mapId = game.findPermanent("Map")!!
                val creatureId = game.findPermanent("Glory Seeker")!!

                activateMapToken(game, mapId, creatureId)

                withClue("Map token should be sacrificed as part of cost") {
                    game.isOnBattlefield("Map") shouldBe false
                }

                // No decision expected — land path is automatic
                game.resolveStack()

                withClue("No pending decision expected for land path") {
                    game.hasPendingDecision() shouldBe false
                }
                withClue("Land card should go to hand") {
                    game.isInHand(1, "Forest") shouldBe true
                }
                withClue("Library should be empty after explore") {
                    game.librarySize(1) shouldBe 0
                }
                withClue("Exploring creature should have no +1/+1 counter (land path)") {
                    plusOneCounters(game, creatureId) shouldBe 0
                }
            }
        }

        // ------------------------------------------------------------------
        // Explore: non-land on top of library
        // ------------------------------------------------------------------
        context("Explore with non-land on top") {

            test("player puts the revealed non-land card back on top of library") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Map")
                    .withCardOnBattlefield(1, "Glory Seeker")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withCardInLibrary(1, "Hill Giant")    // non-land on top
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val mapId = game.findPermanent("Map")!!
                val creatureId = game.findPermanent("Glory Seeker")!!

                activateMapToken(game, mapId, creatureId)
                game.resolveStack() // pauses at yes/no

                withClue("Should have pending yes/no decision (top of library vs graveyard)") {
                    game.hasPendingDecision() shouldBe true
                }

                game.answerYesNo(true) // "Yes" = put back on top of library

                withClue("Non-land card should be back on top of library") {
                    game.librarySize(1) shouldBe 1
                }
                withClue("Non-land card should NOT be in graveyard") {
                    game.isInGraveyard(1, "Hill Giant") shouldBe false
                }
                withClue("Non-land card should NOT be in hand") {
                    game.isInHand(1, "Hill Giant") shouldBe false
                }
                withClue("Exploring creature should receive a +1/+1 counter") {
                    plusOneCounters(game, creatureId) shouldBe 1
                }
                // The card sits on top of the library face-up — both players know what it is.
                val topCardId = game.state.getLibrary(game.player1Id).first()
                val revealedTo = game.state.getEntity(topCardId)?.get<RevealedToComponent>()
                withClue("Top card should still be marked revealed after going back to library") {
                    revealedTo shouldNotBe null
                    revealedTo!!.isRevealedTo(game.player1Id) shouldBe true
                    revealedTo.isRevealedTo(game.player2Id) shouldBe true
                }
                withClue("Exploring creature should be 3/3 after the +1/+1 counter") {
                    val clientState = game.getClientState(1)
                    val creatureInfo = clientState.cards[creatureId]
                    creatureInfo shouldNotBe null
                    creatureInfo!!.power shouldBe 3
                    creatureInfo.toughness shouldBe 3
                }
            }

            test("player puts the revealed non-land card in graveyard") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Map")
                    .withCardOnBattlefield(1, "Glory Seeker")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withCardInLibrary(1, "Hill Giant")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val mapId = game.findPermanent("Map")!!
                val creatureId = game.findPermanent("Glory Seeker")!!

                activateMapToken(game, mapId, creatureId)
                game.resolveStack() // pauses at yes/no

                withClue("Should have pending yes/no decision") {
                    game.hasPendingDecision() shouldBe true
                }

                game.answerYesNo(false) // "No" = put in graveyard

                withClue("Revealed non-land card should be in graveyard") {
                    game.isInGraveyard(1, "Hill Giant") shouldBe true
                }
                withClue("Library should be empty after explore (graveyard path)") {
                    game.librarySize(1) shouldBe 0
                }
                withClue("Exploring creature should receive a +1/+1 counter") {
                    plusOneCounters(game, creatureId) shouldBe 1
                }
            }
        }
    }
}
