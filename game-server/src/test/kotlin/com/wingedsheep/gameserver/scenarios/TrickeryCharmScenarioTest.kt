package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Trickery Charm. Mode selection happens at CAST time (CR 601.2b).
 * Mode 1's "creature type of your choice" is a resolution-time effect choice, so it
 * pauses after the spell starts resolving (i.e., after the final `resolveStack()`).
 *
 * Card reference:
 * - Trickery Charm ({U}): Instant
 *   Choose one —
 *   • Target creature gains flying until end of turn.
 *   • Target creature becomes the creature type of your choice until end of turn.
 *   • Look at the top four cards of your library, then put them back in any order.
 */
class TrickeryCharmScenarioTest : ScenarioTestBase() {

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

    private fun TestGame.chooseCreatureType(type: String) {
        val decision = getPendingDecision()
        decision.shouldNotBeNull()
        decision.shouldBeInstanceOf<ChooseOptionDecision>()
        val index = Subtype.ALL_CREATURE_TYPES.indexOf(type)
        check(index >= 0) { "Unknown creature type: $type" }
        submitDecision(OptionChosenResponse(decision.id, index))
    }

    init {
        context("Trickery Charm modal spell") {

            test("mode 1: target creature gains flying until end of turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Trickery Charm")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Trickery Charm")
                game.chooseMode("gains flying")

                val bearsId = game.findPermanent("Grizzly Bears")!!
                game.selectTargets(listOf(bearsId))
                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Grizzly Bears should have flying") {
                    projected.hasKeyword(bearsId, Keyword.FLYING) shouldBe true
                }
            }

            test("mode 2: target creature becomes the creature type of your choice") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Trickery Charm")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bearsId = game.findPermanent("Grizzly Bears")!!

                game.castSpell(1, "Trickery Charm")
                game.chooseMode("creature type of your choice")
                game.selectTargets(listOf(bearsId))

                // Resolve the spell; the "creature type of your choice" pick is part of
                // the effect and pauses during resolution.
                game.resolveStack()
                game.chooseCreatureType("Goblin")

                val projected = stateProjector.project(game.state)
                withClue("Grizzly Bears should be a Goblin") {
                    projected.hasSubtype(bearsId, "Goblin") shouldBe true
                }
                withClue("Grizzly Bears should no longer be a Bear") {
                    projected.hasSubtype(bearsId, "Bear") shouldBe false
                }
                withClue("Grizzly Bears should still be a Creature") {
                    projected.hasType(bearsId, "CREATURE") shouldBe true
                }
            }

            test("mode 2: creature type change replaces all creature subtypes") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Trickery Charm")
                    .withCardOnBattlefield(2, "Sage Aven")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val avenId = game.findPermanent("Sage Aven")!!

                game.castSpell(1, "Trickery Charm")
                game.chooseMode("creature type of your choice")
                game.selectTargets(listOf(avenId))
                game.resolveStack()
                game.chooseCreatureType("Elf")

                val projected = stateProjector.project(game.state)
                withClue("Sage Aven should be an Elf") {
                    projected.hasSubtype(avenId, "Elf") shouldBe true
                }
                withClue("Sage Aven should no longer be a Bird") {
                    projected.hasSubtype(avenId, "Bird") shouldBe false
                }
                withClue("Sage Aven should no longer be a Wizard") {
                    projected.hasSubtype(avenId, "Wizard") shouldBe false
                }
            }

            test("mode 3: look at top four cards and reorder") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Trickery Charm")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Trickery Charm")
                game.chooseMode("Look at the top four")
                game.resolveStack()

                withClue("Trickery Charm should be in graveyard after resolving") {
                    game.isInGraveyard(1, "Trickery Charm") shouldBe true
                }
            }

            test("mode 1 with multiple creatures prompts for target selection") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Trickery Charm")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(1, "Hill Giant")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giantId = game.findPermanent("Hill Giant")!!

                game.castSpell(1, "Trickery Charm")
                game.chooseMode("gains flying")
                game.selectTargets(listOf(giantId))
                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Hill Giant should have flying") {
                    projected.hasKeyword(giantId, Keyword.FLYING) shouldBe true
                }
            }

            test("Trickery Charm goes to graveyard after resolving") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Trickery Charm")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Trickery Charm")
                game.chooseMode("gains flying") // flying mode

                val bearsId = game.findPermanent("Grizzly Bears")!!
                game.selectTargets(listOf(bearsId))
                game.resolveStack()

                withClue("Trickery Charm should be in graveyard after resolving") {
                    game.isInGraveyard(1, "Trickery Charm") shouldBe true
                }
            }
        }
    }
}
