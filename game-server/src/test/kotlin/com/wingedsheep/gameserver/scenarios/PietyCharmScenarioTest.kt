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
 * Scenario tests for Piety Charm. Mode selection happens at CAST time (CR 601.2b).
 *
 * Card reference:
 * - Piety Charm ({W}): Instant
 *   Choose one —
 *   • Destroy target Aura attached to a creature.
 *   • Target Soldier creature gets +2/+2 until end of turn.
 *   • Creatures you control gain vigilance until end of turn.
 */
class PietyCharmScenarioTest : ScenarioTestBase() {

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
        context("Piety Charm modal spell") {

            test("mode 1: destroy target Aura attached to a creature") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Pacifism")
                    .withCardInHand(1, "Piety Charm")
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giantId = game.findPermanent("Hill Giant")!!

                game.castSpell(1, "Pacifism", giantId)
                game.resolveStack()
                withClue("Pacifism should be on the battlefield") {
                    game.isOnBattlefield("Pacifism") shouldBe true
                }

                game.castSpell(1, "Piety Charm")
                game.chooseMode("Destroy target Aura")

                val pacifismId = game.findPermanent("Pacifism")!!
                game.selectTargets(listOf(pacifismId))
                game.resolveStack()

                withClue("Pacifism should be destroyed") {
                    game.isOnBattlefield("Pacifism") shouldBe false
                }
                withClue("Pacifism should be in player's graveyard") {
                    game.isInGraveyard(1, "Pacifism") shouldBe true
                }
            }

            test("mode 2: target Soldier creature gets +2/+2 until end of turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Piety Charm")
                    .withCardOnBattlefield(1, "Glory Seeker")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val seekerId = game.findPermanent("Glory Seeker")!!

                game.castSpell(1, "Piety Charm")
                game.chooseMode("Soldier creature gets")
                game.selectTargets(listOf(seekerId))
                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Glory Seeker should be 4/4 after +2/+2") {
                    projected.getPower(seekerId) shouldBe 4
                    projected.getToughness(seekerId) shouldBe 4
                }
            }

            test("mode 2 cannot target non-Soldier creatures") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Piety Charm")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(1, "Glory Seeker")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val seekerId = game.findPermanent("Glory Seeker")!!

                game.castSpell(1, "Piety Charm")
                game.chooseMode("Soldier creature gets")
                game.selectTargets(listOf(seekerId))
                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Glory Seeker should be 4/4 after +2/+2") {
                    projected.getPower(seekerId) shouldBe 4
                    projected.getToughness(seekerId) shouldBe 4
                }
                withClue("Grizzly Bears should be unaffected at 2/2") {
                    val bearsId = game.findPermanent("Grizzly Bears")!!
                    projected.getPower(bearsId) shouldBe 2
                    projected.getToughness(bearsId) shouldBe 2
                }
            }

            test("mode 3: creatures you control gain vigilance until end of turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Piety Charm")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(1, "Glory Seeker")
                    .withCardOnBattlefield(2, "Hill Giant")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bearsId = game.findPermanent("Grizzly Bears")!!
                val seekerId = game.findPermanent("Glory Seeker")!!
                val giantId = game.findPermanent("Hill Giant")!!

                game.castSpell(1, "Piety Charm")
                game.chooseMode("vigilance") // vigilance mode (no target needed)
                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Grizzly Bears should have vigilance") {
                    projected.hasKeyword(bearsId, Keyword.VIGILANCE) shouldBe true
                }
                withClue("Glory Seeker should have vigilance") {
                    projected.hasKeyword(seekerId, Keyword.VIGILANCE) shouldBe true
                }
                withClue("Opponent's Hill Giant should NOT have vigilance") {
                    projected.hasKeyword(giantId, Keyword.VIGILANCE) shouldBe false
                }
            }

            test("Piety Charm goes to graveyard after resolving") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Piety Charm")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Piety Charm")
                game.chooseMode("vigilance") // vigilance mode (no target needed)
                game.resolveStack()

                withClue("Piety Charm should be in graveyard after resolving") {
                    game.isInGraveyard(1, "Piety Charm") shouldBe true
                }
            }
        }
    }
}
