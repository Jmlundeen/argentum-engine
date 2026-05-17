package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario test for Zhalfirin Knight (Mirage).
 *
 * Card reference:
 * - Zhalfirin Knight {2}{W}: 2/2 Human Knight
 *   Flanking (Whenever a creature without flanking blocks this creature, the
 *   blocking creature gets -1/-1 until end of turn.)
 *   {W}{W}: This creature gains first strike until end of turn.
 *
 * Exercises the SELF-binding `BecomesBlockedEvent` detection path with a partner
 * filter (`Creature.withoutKeyword(FLANKING)`).
 */
class ZhalfirinKnightScenarioTest : ScenarioTestBase() {

    init {
        context("Zhalfirin Knight — Flanking") {
            test("blocker without flanking gets -1/-1 until end of turn") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Zhalfirin Knight") // 2/2
                    .withCardOnBattlefield(2, "Glory Seeker")     // 2/2, no flanking
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val knightId = game.findPermanent("Zhalfirin Knight")!!
                val seekerId = game.findPermanent("Glory Seeker")!!

                val attackResult = game.execute(
                    DeclareAttackers(game.player1Id, mapOf(knightId to game.player2Id))
                )
                withClue("Attack should succeed: ${attackResult.error}") {
                    attackResult.error shouldBe null
                }

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

                val blockResult = game.execute(
                    DeclareBlockers(game.player2Id, mapOf(seekerId to listOf(knightId)))
                )
                withClue("Block should succeed: ${blockResult.error}") {
                    blockResult.error shouldBe null
                }

                // Resolve the flanking trigger queued by DeclareBlockers.
                game.resolveStack()

                val clientState = game.getClientState(1)
                val seekerInfo = clientState.cards[seekerId]
                withClue("Glory Seeker should be 1/1 after Flanking trigger") {
                    seekerInfo shouldNotBe null
                    seekerInfo!!.power shouldBe 1
                    seekerInfo.toughness shouldBe 1
                }

                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                withClue("Glory Seeker (1/1) dies to Knight's 2 damage") {
                    game.findPermanent("Glory Seeker") shouldBe null
                    game.isInGraveyard(2, "Glory Seeker") shouldBe true
                }
                withClue("Zhalfirin Knight (2/2) survives 1 damage from weakened Seeker") {
                    game.findPermanent("Zhalfirin Knight") shouldNotBe null
                }
            }

            test("{W}{W}: gains first strike until end of turn") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Zhalfirin Knight")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val knightId = game.findPermanent("Zhalfirin Knight")!!
                val cardDef = cardRegistry.getCard("Zhalfirin Knight")!!
                val firstStrikeAbility = cardDef.script.activatedAbilities.first()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = knightId,
                        abilityId = firstStrikeAbility.id,
                        targets = emptyList()
                    )
                )
                withClue("Activation should succeed: ${result.error}") {
                    result.error shouldBe null
                }

                game.resolveStack()

                val clientState = game.getClientState(1)
                val knightInfo = clientState.cards[knightId]
                withClue("Zhalfirin Knight should have first strike after activation") {
                    knightInfo shouldNotBe null
                    knightInfo!!.keywords.contains(Keyword.FIRST_STRIKE) shouldBe true
                }
            }
        }
    }
}
