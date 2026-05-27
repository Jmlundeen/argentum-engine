package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Bringer of the Last Gift.
 *
 * Card reference:
 * - Bringer of the Last Gift ({6}{B}{B}): Creature — Vampire Demon, 6/6
 *   "Flying"
 *   "When this creature enters, if you cast it, each player sacrifices all other
 *    creatures they control. Then each player returns all creature cards from their
 *    graveyard that weren't put there this way to the battlefield."
 *
 * Verifies the snapshot-before-sacrifice composition: pre-existing graveyard creatures
 * come back (under their owners' control), the creatures sacrificed during resolution do
 * NOT, and Bringer itself survives (it is excluded from the "all other creatures" sweep).
 * Also pins the intervening "if you cast it" clause (no trigger when reanimated) and that a
 * sacrificed token ceases to exist rather than coming back.
 */
class BringerOfTheLastGiftScenarioTest : ScenarioTestBase() {

    private fun ScenarioBuilder.withLibraryCards(playerNumber: Int, cardName: String, count: Int): ScenarioBuilder {
        repeat(count) { withCardInLibrary(playerNumber, cardName) }
        return this
    }

    private fun TestGame.countControlledTokens(playerNumber: Int): Int {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        return state.getBattlefield().count { entityId ->
            val container = state.getEntity(entityId) ?: return@count false
            container.has<TokenComponent>() &&
                container.get<ControllerComponent>()?.playerId == playerId
        }
    }

    init {
        context("Bringer of the Last Gift ETB") {

            test("sacrifices all other creatures, then reanimates each player's pre-existing graveyard creatures") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Bringer of the Last Gift")
                    // On the battlefield — sacrificed by the trigger ("all other creatures").
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Enormous Baloth")
                    // Already in the graveyard — returned to the battlefield ("not put there this way").
                    .withCardInGraveyard(1, "Hill Giant")
                    .withCardInGraveyard(2, "Glory Seeker")
                    .withLandsOnBattlefield(1, "Swamp", 8)
                    .withLibraryCards(1, "Swamp", 5)
                    .withLibraryCards(2, "Swamp", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Bringer of the Last Gift")
                withClue("Casting Bringer of the Last Gift should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Bringer survives — it is excluded from the sacrifice ('all OTHER creatures')") {
                    game.isOnBattlefield("Bringer of the Last Gift") shouldBe true
                }

                // Creatures that were on the battlefield are sacrificed and stay in their
                // owners' graveyards (they were "put there this way").
                withClue("Grizzly Bears was sacrificed and is not returned") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe false
                    game.isInGraveyard(1, "Grizzly Bears") shouldBe true
                }
                withClue("Enormous Baloth was sacrificed and is not returned") {
                    game.isOnBattlefield("Enormous Baloth") shouldBe false
                    game.isInGraveyard(2, "Enormous Baloth") shouldBe true
                }

                // Pre-existing graveyard creatures return to the battlefield.
                withClue("Hill Giant (caster's graveyard) returns to the battlefield") {
                    game.isOnBattlefield("Hill Giant") shouldBe true
                    game.isInGraveyard(1, "Hill Giant") shouldBe false
                }
                withClue("Glory Seeker (opponent's graveyard) returns to the battlefield") {
                    game.isOnBattlefield("Glory Seeker") shouldBe true
                    game.isInGraveyard(2, "Glory Seeker") shouldBe false
                }
            }

            test("reanimated creatures return under their owners' control") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Bringer of the Last Gift")
                    .withCardInGraveyard(1, "Hill Giant")
                    .withCardInGraveyard(2, "Glory Seeker")
                    .withLandsOnBattlefield(1, "Swamp", 8)
                    .withLibraryCards(1, "Swamp", 5)
                    .withLibraryCards(2, "Swamp", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Bringer of the Last Gift").error shouldBe null
                game.resolveStack()

                val hillGiant = game.findPermanent("Hill Giant")
                val glorySeeker = game.findPermanent("Glory Seeker")

                withClue("Caster's Hill Giant returns under the caster's control") {
                    game.state.getEntity(hillGiant!!)?.get<ControllerComponent>()?.playerId shouldBe game.player1Id
                }
                withClue("Opponent's Glory Seeker returns under the opponent's control, not the caster's") {
                    game.state.getEntity(glorySeeker!!)?.get<ControllerComponent>()?.playerId shouldBe game.player2Id
                }
            }

            test("entering without being cast (reanimated) does not trigger the sacrifice-and-reanimate ability") {
                // The ability has an intervening "if you cast it" clause. Per the card's ruling it
                // does nothing when Bringer is put onto the battlefield without being cast — here via
                // Breath of Life ("Return target creature card from your graveyard to the battlefield").
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Breath of Life")
                    // Bringer reaches the battlefield via reanimation, never cast.
                    .withCardInGraveyard(1, "Bringer of the Last Gift")
                    // Would be sacrificed if the ability triggered.
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    // Would return to the battlefield if the reanimate half triggered.
                    .withCardInGraveyard(1, "Hill Giant")
                    .withLandsOnBattlefield(1, "Plains", 4)
                    .withLibraryCards(1, "Plains", 5)
                    .withLibraryCards(2, "Plains", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bringerInGraveyard = game.state.getGraveyard(game.player1Id).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Bringer of the Last Gift"
                }
                val castResult = game.castSpellTargetingGraveyardCard(1, "Breath of Life", listOf(bringerInGraveyard))
                withClue("Casting Breath of Life should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Bringer is on the battlefield — reanimated by Breath of Life, not cast") {
                    game.isOnBattlefield("Bringer of the Last Gift") shouldBe true
                }
                withClue("Grizzly Bears survives — the 'if you cast it' ability never triggered") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe true
                }
                withClue("Hill Giant stays in the graveyard — the reanimate half never triggered") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                    game.isInGraveyard(1, "Hill Giant") shouldBe true
                }
            }

            test("a sacrificed token ceases to exist rather than returning") {
                // Fungal Infection ({B}) makes a 1/1 Saproling token the caster controls; Grizzly
                // Bears is just its -1/-1 target. The Bringer sweep then sacrifices the token, which
                // is put into the graveyard and ceases to exist (CR 111.7) — it is never a creature
                // *card* in a graveyard, so it cannot be returned.
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Bringer of the Last Gift")
                    .withCardInHand(1, "Fungal Infection")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Swamp", 10)
                    .withLibraryCards(1, "Swamp", 5)
                    .withLibraryCards(2, "Swamp", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val grizzly = game.findPermanent("Grizzly Bears")!!
                game.castSpell(1, "Fungal Infection", grizzly).error shouldBe null
                game.resolveStack()
                withClue("Fungal Infection created a token the caster controls") {
                    game.countControlledTokens(1) shouldBe 1
                }

                game.castSpell(1, "Bringer of the Last Gift").error shouldBe null
                game.resolveStack()

                withClue("The sacrificed token ceased to exist — gone from the battlefield, not reanimated") {
                    game.countControlledTokens(1) shouldBe 0
                }
                withClue("Bringer itself survives the sweep") {
                    game.isOnBattlefield("Bringer of the Last Gift") shouldBe true
                }
            }
        }
    }
}
