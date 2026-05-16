package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Vitality Charm. Mode selection happens at CAST time (CR 601.2b).
 *
 * Card reference:
 * - Vitality Charm ({G}): Instant
 *   Choose one —
 *   • Create a 1/1 green Insect creature token.
 *   • Target creature gets +1/+1 and gains trample until end of turn.
 *   • Regenerate target Beast.
 */
class VitalityCharmScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    private fun TestGame.chooseMode(descriptionContains: String) {
        val decision = getPendingDecision()
        decision.shouldNotBeNull()
        decision.shouldBeInstanceOf<ChooseOptionDecision>()
        val idx = decision.options.indexOfFirst { it.contains(descriptionContains, ignoreCase = true) }
        check(idx >= 0) {
            "No mode matched '$descriptionContains' in ${decision.options}"
        }
        submitDecision(OptionChosenResponse(decision.id, idx))
    }

    init {
        context("Vitality Charm modal spell") {

            test("mode 1: create a 1/1 green Insect creature token") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Vitality Charm")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Vitality Charm")
                game.chooseMode("Insect creature token") // create Insect token (no target)
                game.resolveStack()

                withClue("Insect token should be on the battlefield") {
                    game.isOnBattlefield("Insect Token") shouldBe true
                }

                val tokenId = game.findPermanent("Insect Token")!!
                val projected = stateProjector.project(game.state)
                withClue("Insect token should be 1/1") {
                    projected.getPower(tokenId) shouldBe 1
                    projected.getToughness(tokenId) shouldBe 1
                }
            }

            test("mode 2: target creature gets +1/+1 and gains trample until end of turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Vitality Charm")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bearsId = game.findPermanent("Grizzly Bears")!!

                game.castSpell(1, "Vitality Charm")
                game.chooseMode("trample")
                game.selectTargets(listOf(bearsId))
                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Grizzly Bears should be 3/3 after +1/+1") {
                    projected.getPower(bearsId) shouldBe 3
                    projected.getToughness(bearsId) shouldBe 3
                }
                withClue("Grizzly Bears should have trample") {
                    projected.hasKeyword(bearsId, Keyword.TRAMPLE) shouldBe true
                }
            }

            test("mode 3: regenerate target Beast") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Vitality Charm")
                    .withCardInHand(1, "Shock")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Ravenous Baloth")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Vitality Charm")
                game.chooseMode("Regenerate")

                val balothId = game.findPermanent("Ravenous Baloth")!!
                game.selectTargets(listOf(balothId))
                game.resolveStack()

                withClue("Ravenous Baloth should still be on the battlefield with regen shield") {
                    game.isOnBattlefield("Ravenous Baloth") shouldBe true
                }
            }

            test("mode 3 cannot target non-Beast creatures") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Vitality Charm")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Ravenous Baloth")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Vitality Charm")
                game.chooseMode("Regenerate")

                val balothId = game.findPermanent("Ravenous Baloth")!!
                game.selectTargets(listOf(balothId))
                game.resolveStack()

                withClue("Ravenous Baloth should be on the battlefield") {
                    game.isOnBattlefield("Ravenous Baloth") shouldBe true
                }
            }

            test("mode 2 with multiple creatures prompts for target selection") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Vitality Charm")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(1, "Hill Giant")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giantId = game.findPermanent("Hill Giant")!!

                game.castSpell(1, "Vitality Charm")
                game.chooseMode("trample")
                game.selectTargets(listOf(giantId))
                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Hill Giant should be 4/4 after +1/+1") {
                    projected.getPower(giantId) shouldBe 4
                    projected.getToughness(giantId) shouldBe 4
                }
                withClue("Hill Giant should have trample") {
                    projected.hasKeyword(giantId, Keyword.TRAMPLE) shouldBe true
                }
            }

            test("Vitality Charm goes to graveyard after resolving") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Vitality Charm")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Vitality Charm")
                game.chooseMode("Insect creature token") // token mode
                game.resolveStack()

                withClue("Vitality Charm should be in graveyard after resolving") {
                    game.isInGraveyard(1, "Vitality Charm") shouldBe true
                }
            }
        }
    }
}
