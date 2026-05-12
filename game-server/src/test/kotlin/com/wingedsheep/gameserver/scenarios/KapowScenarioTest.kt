package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Kapow!
 *
 * Card reference:
 * - Kapow! ({2}{G}): Instant/Sorcery
 *   "Put a +1/+1 counter on target creature you control, then it fights target creature
 *    an opponent controls."
 */
class KapowScenarioTest : ScenarioTestBase() {

    init {
        context("Kapow! counter then fight") {

            test("puts +1/+1 counter on ally then ally and foe fight and both are destroyed") {
                // GIVEN active player has Kapow! in hand, controls Grizzly Bears (2/2),
                // opponent controls Hill Giant (3/3), {2}{G} mana available
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Kapow!")
                    .withCardOnBattlefield(1, "Grizzly Bears")  // ally: 2/2 → 3/3 after counter
                    .withCardOnBattlefield(2, "Hill Giant")     // foe: 3/3
                    .withLandsOnBattlefield(1, "Forest", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val allyId = game.findPermanent("Grizzly Bears")!!
                val foeId = game.findPermanent("Hill Giant")!!

                // WHEN cast Kapow! targeting ally (Grizzly Bears) and foe (Hill Giant)
                val playerId = game.player1Id
                val cardId = game.state.getHand(playerId).find { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Kapow!"
                }!!

                val castResult = game.execute(
                    CastSpell(
                        playerId = playerId,
                        cardId = cardId,
                        targets = listOf(
                            ChosenTarget.Permanent(allyId),
                            ChosenTarget.Permanent(foeId)
                        )
                    )
                )
                withClue("Casting Kapow! should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                // Resolve: counter applied then fight; SBAs destroy both creatures
                // Without the counter Grizzly Bears (2/2) would deal only 2 damage to Hill Giant
                // (3/3) — not lethal. Hill Giant dying proves the counter boosted Grizzly Bears to 3/3.
                game.resolveStack()

                // THEN both creatures are destroyed (each dealt 3 damage to the other)
                withClue("Grizzly Bears (3/3 after counter) should be destroyed by Hill Giant dealing 3 damage") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe false
                    game.isInGraveyard(1, "Grizzly Bears") shouldBe true
                }
                withClue("Hill Giant should be destroyed by Grizzly Bears dealing 3 damage (counter applied)") {
                    game.isOnBattlefield("Hill Giant") shouldBe false
                    game.isInGraveyard(2, "Hill Giant") shouldBe true
                }
                withClue("Kapow! should be in its owner's graveyard") {
                    game.isInGraveyard(1, "Kapow!") shouldBe true
                }
            }
        }
    }
}
