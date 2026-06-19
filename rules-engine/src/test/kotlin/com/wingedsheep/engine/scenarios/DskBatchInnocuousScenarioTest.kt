package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for a DSK batch:
 *   - Erratic Apparition (#54)  — {2}{U} 1/3 Spirit. Flying, vigilance. Eerie: +1/+1 EOT.
 *   - Cult Healer (#2)          — {2}{W} 3/3 Human Doctor. Eerie: gains lifelink EOT.
 *   - Daggermaw Megalodon (#48) — {4}{U}{U} 5/7 Shark. Vigilance, Islandcycling {2}.
 *   - Shrewd Storyteller (#232) — {1}{G}{W} 3/3 Human Survivor. Survival: +1/+1 counter on target.
 *
 * (Resurrected Cultist is covered separately in ResurrectedCultistScenarioTest — its graveyard
 * activated ability is driven via GameTestDriver.)
 */
class DskBatchInnocuousScenarioTest : ScenarioTestBase() {

    private val projector = StateProjector()

    init {
        context("Erratic Apparition — Eerie (enchantment enters)") {
            test("an enchantment you control entering gives +1/+1 until end of turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Erratic Apparition")
                    .withCardInHand(1, "Test Enchantment") // {1}{W}
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val apparition = game.findPermanent("Erratic Apparition")!!
                withClue("base power/toughness 1/3") {
                    val p = projector.project(game.state)
                    p.getPower(apparition) shouldBe 1
                    p.getToughness(apparition) shouldBe 3
                }

                val cast = game.castSpell(1, "Test Enchantment")
                withClue("Casting Test Enchantment should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("Eerie trigger pumps to 2/4 until end of turn") {
                    val p = projector.project(game.state)
                    p.getPower(apparition) shouldBe 2
                    p.getToughness(apparition) shouldBe 4
                }
            }

            test("an opponent's enchantment entering does NOT pump") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Erratic Apparition")
                    .withCardInHand(2, "Test Enchantment")
                    .withLandsOnBattlefield(2, "Plains", 2)
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val apparition = game.findPermanent("Erratic Apparition")!!
                val cast = game.castSpell(2, "Test Enchantment")
                withClue("Opponent casting should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("no pump — the enchantment isn't controlled by the Apparition's controller") {
                    val p = projector.project(game.state)
                    p.getPower(apparition) shouldBe 1
                    p.getToughness(apparition) shouldBe 3
                }
            }
        }

        context("Cult Healer — Eerie (gains lifelink)") {
            test("an enchantment you control entering grants lifelink until end of turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Cult Healer")
                    .withCardInHand(1, "Test Enchantment")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val healer = game.findPermanent("Cult Healer")!!
                withClue("no lifelink before the Eerie trigger") {
                    projector.project(game.state).hasKeyword(healer, Keyword.LIFELINK) shouldBe false
                }

                val cast = game.castSpell(1, "Test Enchantment")
                withClue("Casting Test Enchantment should succeed: ${cast.error}") { cast.error shouldBe null }
                game.resolveStack()

                withClue("Eerie trigger grants lifelink until end of turn") {
                    projector.project(game.state).hasKeyword(healer, Keyword.LIFELINK) shouldBe true
                }
            }
        }

        context("Daggermaw Megalodon — Islandcycling {2}") {
            test("typecycling discards the Shark and fetches an Island into hand") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Daggermaw Megalodon")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Forest")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val result = game.typecycleCard(1, "Daggermaw Megalodon")
                withClue("typecycling should succeed: ${result.error}") { result.error shouldBe null }
                game.resolveStack()
                // The search pauses for a library selection; pick the Island.
                if (game.hasPendingDecision()) {
                    val p1 = com.wingedsheep.sdk.model.EntityId.of("player-1")
                    val islandInLib = game.state.getLibrary(p1).firstOrNull { id ->
                        game.state.getEntity(id)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Island"
                    }
                    if (islandInLib != null) game.selectCards(listOf(islandInLib))
                    game.resolveStack()
                }

                withClue("Daggermaw Megalodon is discarded to the graveyard") {
                    game.isInGraveyard(1, "Daggermaw Megalodon") shouldBe true
                }
                withClue("an Island is now in hand") {
                    game.isInHand(1, "Island") shouldBe true
                }
            }
        }

        context("Shrewd Storyteller — Survival trigger") {
            test("a tapped Storyteller puts a +1/+1 counter on a target creature at second main") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Shrewd Storyteller", tapped = true)
                    .withCardOnBattlefield(1, "Centaur Courser")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Centaur Courser")!!

                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                var guard = 0
                while (game.state.pendingDecision !is ChooseTargetsDecision && guard < 20) {
                    game.resolveStack(); guard++
                }
                val td = game.state.pendingDecision as? ChooseTargetsDecision
                    ?: error("expected ChooseTargetsDecision for Survival trigger; got ${game.state.pendingDecision}")
                game.submitDecision(TargetsResponse(td.id, mapOf(0 to listOf(bears))))
                game.resolveStack()

                withClue("Grizzly Bears gains a +1/+1 counter") {
                    val counters = game.state.getEntity(bears)?.get<CountersComponent>()?.counters ?: emptyMap()
                    counters[CounterType.PLUS_ONE_PLUS_ONE] shouldBe 1
                }
            }

            test("an untapped Storyteller does NOT fire the Survival trigger") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Shrewd Storyteller", tapped = false)
                    .withCardOnBattlefield(1, "Centaur Courser")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Centaur Courser")!!
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                repeat(5) { if (game.hasPendingDecision()) Unit else game.resolveStack() }

                withClue("No Survival trigger — the Storyteller is untapped") {
                    (game.state.pendingDecision is ChooseTargetsDecision) shouldBe false
                    val counters = game.state.getEntity(bears)?.get<CountersComponent>()?.counters ?: emptyMap()
                    counters[CounterType.PLUS_ONE_PLUS_ONE] shouldBe null
                }
            }
        }
    }
}
