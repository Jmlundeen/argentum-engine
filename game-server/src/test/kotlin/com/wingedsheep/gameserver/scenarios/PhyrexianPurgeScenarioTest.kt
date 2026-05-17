package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Phyrexian Purge (Mirage).
 *
 * Card:
 *   {2}{B}{R}  Sorcery
 *   This spell costs 3 life more to cast for each target.
 *   Destroy any number of target creatures.
 *
 * Exercises the new [com.wingedsheep.sdk.scripting.AdditionalCost.PayLifePerTarget]
 * cost: the engine multiplies the per-target life by `action.targets.size` at cast
 * resolution.
 */
class PhyrexianPurgeScenarioTest : ScenarioTestBase() {

    init {
        context("Phyrexian Purge — per-target additional life cost") {

            test("destroys two target creatures and pays 6 life (3 per target)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Phyrexian Purge")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardOnBattlefield(2, "Brushwagg")
                    .withCardOnBattlefield(2, "Civic Guildmage")
                    .withLifeTotal(1, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val brushwagg = game.findPermanent("Brushwagg")!!
                val guildmage = game.findPermanent("Civic Guildmage")!!

                val purgeId = game.state.getHand(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Phyrexian Purge"
                }
                val result = game.execute(
                    CastSpell(
                        game.player1Id,
                        purgeId,
                        targets = listOf(
                            ChosenTarget.Permanent(brushwagg),
                            ChosenTarget.Permanent(guildmage)
                        )
                    )
                )

                withClue("Cast should succeed: ${result.error}") {
                    result.error shouldBe null
                }

                // Life is deducted as part of paying the additional cost — before resolution.
                val lifeAfterCast = game.state.getEntity(game.player1Id)!!.get<LifeTotalComponent>()!!.life
                withClue("Two targets * 3 life = 6 life paid as additional cost") {
                    lifeAfterCast shouldBe 14
                }

                game.resolveStack()

                withClue("Both targeted creatures should be in their owner's graveyard") {
                    game.isInGraveyard(2, "Brushwagg") shouldBe true
                    game.isInGraveyard(2, "Civic Guildmage") shouldBe true
                }
                game.isOnBattlefield("Brushwagg") shouldBe false
                game.isOnBattlefield("Civic Guildmage") shouldBe false
            }

            test("casting with zero targets pays no life") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Phyrexian Purge")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardOnBattlefield(2, "Brushwagg")
                    .withLifeTotal(1, 4)  // not enough to pay 3 life even once
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val purgeId = game.state.getHand(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Phyrexian Purge"
                }
                val result = game.execute(
                    CastSpell(game.player1Id, purgeId, targets = emptyList())
                )

                withClue("Zero-target cast should succeed even with low life: ${result.error}") {
                    result.error shouldBe null
                }

                val lifeAfterCast = game.state.getEntity(game.player1Id)!!.get<LifeTotalComponent>()!!.life
                withClue("Zero targets means zero additional life paid") {
                    lifeAfterCast shouldBe 4
                }

                game.resolveStack()

                // Brushwagg untouched — it was not a target.
                game.isOnBattlefield("Brushwagg") shouldBe true
            }
        }
    }
}
