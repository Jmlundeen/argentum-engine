package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.inv.InvasionSet
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Kangee, Aerie Keeper.
 *
 * Card reference:
 * - Kangee, Aerie Keeper {2}{W}{U} — Legendary Creature — Bird Wizard 2/2
 *   Kicker {X}{2}, Flying
 *   When Kangee enters, if it was kicked, put X feather counters on it.
 *   Other Bird creatures get +1/+1 for each feather counter on Kangee.
 *
 * Test cases:
 * 1. Unkicked — no feather counters; other Birds unmodified; Kangee doesn't pump itself.
 * 2. Kicked X=2 — two feather counters; other Birds get +2/+2; Kangee itself unchanged.
 */
class KangeeAerieKeeperScenarioTest : ScenarioTestBase() {

    private val vanillaBird = card("Vanilla Sparrow") {
        manaCost = "{1}{W}"
        colorIdentity = "W"
        typeLine = "Creature — Bird"
        power = 1
        toughness = 1
    }

    init {
        cardRegistry.register(InvasionSet.cards)
        cardRegistry.register(vanillaBird)

        context("Kangee, Aerie Keeper") {

            test("unkicked: no feather counters, other Birds unmodified") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Kangee, Aerie Keeper")
                    .withCardOnBattlefield(1, "Vanilla Sparrow")
                    .withLandsOnBattlefield(1, "Plains", 3) // {2}{W}
                    .withLandsOnBattlefield(1, "Island", 1) // {U}
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Kangee, Aerie Keeper")
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                val projected = game.state.projectedState
                val bird = game.findPermanent("Vanilla Sparrow")!!
                withClue("With no feather counters, other Birds stay 1/1") {
                    projected.getPower(bird) shouldBe 1
                    projected.getToughness(bird) shouldBe 1
                }
            }

            test("kicked X=2: two feather counters pump other Birds +2/+2 but not Kangee") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Kangee, Aerie Keeper")
                    .withCardOnBattlefield(1, "Vanilla Sparrow")
                    .withLandsOnBattlefield(1, "Plains", 7) // {2}{W} + {X}{2} generic (X=2)
                    .withLandsOnBattlefield(1, "Island", 1) // {U}
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val playerId = game.player1Id
                val cardId = game.state.getHand(playerId).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Kangee, Aerie Keeper"
                }

                val castResult = game.execute(
                    CastSpell(playerId, cardId, wasKicked = true, xValue = 2)
                )
                withClue("Kicked cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                val projected = game.state.projectedState
                val kangee = game.findPermanent("Kangee, Aerie Keeper")!!
                val bird = game.findPermanent("Vanilla Sparrow")!!

                withClue("Other Bird gets +2/+2 (1/1 -> 3/3) from two feather counters") {
                    projected.getPower(bird) shouldBe 3
                    projected.getToughness(bird) shouldBe 3
                }
                withClue("Kangee doesn't pump itself (excludeSelf): stays 2/2") {
                    projected.getPower(kangee) shouldBe 2
                    projected.getToughness(kangee) shouldBe 2
                }
            }
        }
    }
}
