package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.player.PermanentTypesEnteredBattlefieldThisTurnComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Mechan Shieldmate (EOE #65).
 *
 *   Mechan Shieldmate {1}{U} — Artifact Creature — Robot Soldier 3/2
 *   Defender
 *   As long as an artifact entered the battlefield under your control this turn, this creature
 *   can attack as though it didn't have defender.
 *
 * These tests exercise the per-player ETB-by-type tracker end-to-end through the real
 * spell-resolution / land-play pipeline, *not* by injecting the tracker component by hand.
 * That's the whole point — the wiring (StackResolver → BattlefieldEntry.place →
 * PermanentEntryTracker.record) is what makes the static ability work in actual play.
 */
class MechanShieldmateScenarioTest : ScenarioTestBase() {

    init {
        context("Mechan Shieldmate - CanAttackDespiteDefender") {

            test("no artifact entered this turn ⇒ defender restriction applies") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Mechan Shieldmate")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                val result = game.declareAttackers(mapOf("Mechan Shieldmate" to 2))
                withClue("With no artifact entry recorded, defender should block the attack") {
                    (result.error != null) shouldBe true
                }
                withClue("Tracker should not have been populated for the active player") {
                    game.state.getEntity(game.player1Id)
                        ?.get<PermanentTypesEnteredBattlefieldThisTurnComponent>() shouldBe null
                }
            }

            test("casting an artifact spell records ARTIFACT in the tracker and unlocks the attack") {
                // Sparring Construct ({1}, Artifact Creature) resolves via StackResolver, which
                // routes through BattlefieldEntry.place. That's how PermanentEntryTracker stays
                // in sync — calling state.addToZone(battlefield, …) directly would bypass it.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Mechan Shieldmate")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withCardInHand(1, "Sparring Construct")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Sparring Construct")
                withClue("Cast should succeed (1 Island covers the {1} cost)") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                val tracker = game.state.getEntity(game.player1Id)
                    ?.get<PermanentTypesEnteredBattlefieldThisTurnComponent>()
                withClue("Tracker should have been populated by the spell-resolution path") {
                    tracker shouldBe PermanentTypesEnteredBattlefieldThisTurnComponent(
                        setOf(CardType.ARTIFACT, CardType.CREATURE)
                    )
                }

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                val attackResult = game.declareAttackers(mapOf("Mechan Shieldmate" to 2))
                withClue("Mechan Shieldmate should be a legal attacker after an artifact entered") {
                    attackResult.error shouldBe null
                }
            }

            test("playing a land records LAND only — Mechan Shieldmate stays locked") {
                // Lands enter via PlayLandHandler (not StackResolver). That path is wired
                // through BattlefieldEntry too, so the tracker fills in CardType.LAND — and
                // crucially *not* CardType.ARTIFACT, which is what Mechan Shieldmate checks.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Mechan Shieldmate")
                    .withCardInHand(1, "Plains")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val plainsId = game.findCardsInHand(1, "Plains").single()
                val playResult = game.execute(
                    com.wingedsheep.engine.core.PlayLand(game.player1Id, plainsId)
                )
                withClue("Playing the land should succeed") { playResult.error shouldBe null }

                val tracker = game.state.getEntity(game.player1Id)
                    ?.get<PermanentTypesEnteredBattlefieldThisTurnComponent>()
                checkNotNull(tracker) { "Tracker should be populated after playing the land" }
                withClue("Tracker should record LAND but not ARTIFACT") {
                    tracker.cardTypes shouldContain CardType.LAND
                    tracker.cardTypes shouldNotContain CardType.ARTIFACT
                }

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)

                val attackResult = game.declareAttackers(mapOf("Mechan Shieldmate" to 2))
                withClue("Land entries don't satisfy the artifact-entry condition") {
                    (attackResult.error != null) shouldBe true
                }
            }

            test("cleanup clears the tracker — next turn locks the defender back up") {
                // CleanupPhaseManager removes PermanentTypesEnteredBattlefieldThisTurnComponent
                // for every player as the turn ends. The next time Mechan Shieldmate's
                // controller wants to attack, the condition has to be re-satisfied from scratch.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Mechan Shieldmate")
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withCardInHand(1, "Sparring Construct")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Sparring Construct")
                game.resolveStack()

                val preCleanupTracker = game.state.getEntity(game.player1Id)
                    ?.get<PermanentTypesEnteredBattlefieldThisTurnComponent>()
                checkNotNull(preCleanupTracker) { "Tracker should be populated before cleanup" }
                withClue("Sanity check: tracker is populated before cleanup") {
                    preCleanupTracker.cardTypes shouldContain CardType.ARTIFACT
                }

                // Advance past END/CLEANUP into player 2's upkeep. (CLEANUP and UNTAP don't
                // grant priority, so the loop walks past them automatically.)
                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)

                withClue("Active-player tracker should have been cleared on cleanup") {
                    game.state.getEntity(game.player1Id)
                        ?.get<PermanentTypesEnteredBattlefieldThisTurnComponent>() shouldBe null
                }
            }
        }
    }
}
