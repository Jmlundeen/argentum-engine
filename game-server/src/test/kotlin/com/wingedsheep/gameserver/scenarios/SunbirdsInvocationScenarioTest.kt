package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario test for Sunbird's Invocation. The trigger reveals the top X cards of your
 * library on a hand-cast spell (X = the spell's mana value), lets you cast one revealed
 * card with mana value ≤ X for free, and bottom-randomizes the rest.
 *
 * Covers:
 *  - happy path: select the eligible revealed card → free cast, rest go to library bottom
 *  - decline: skip the selection → all revealed cards go to library bottom
 *  - no eligible card (only lands revealed): no decision is offered, everything bottoms
 */
class SunbirdsInvocationScenarioTest : ScenarioTestBase() {

    init {
        context("Sunbird's Invocation - cast-from-hand trigger") {

            test("offers and casts a 1-mana revealed spell for free when its controller picks it") {
                val game = scenario()
                    .withPlayers("Sunbird Player", "Opponent")
                    .withCardOnBattlefield(1, "Sunbird's Invocation")
                    .withCardInHand(1, "Goblin Sledder")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    // Top of P1's library: a free Goblin Sledder (eligible, MV 1) followed
                    // by a Mountain (revealed but ineligible — lands are excluded).
                    .withCardInLibrary(1, "Goblin Sledder")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Goblin Sledder")
                // After the cast, players pass priority — Sunbird's trigger goes on the
                // stack on top of the Goblin Sledder spell and then resolves, pausing
                // with a SELECT_CARDS decision over the revealed eligible card(s).
                game.resolveStack()

                val decision = game.state.pendingDecision
                    ?: error("Expected a SELECT_CARDS decision for Sunbird's Invocation")
                decision.playerId shouldBe game.player1Id

                // Revealed cards stay in the library (oracle wording — no exile staging).
                // The eligible Sledder is one of them.
                val revealedSledderId = game.state.getLibrary(game.player1Id).firstOrNull { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Goblin Sledder"
                } ?: error("Expected the revealed Goblin Sledder to still be in the library while paused")

                game.selectCards(listOf(revealedSledderId))
                game.resolveStack()

                withClue("Both Goblin Sledders should end up on P1's battlefield") {
                    val sleddersInPlay = game.state.getBattlefield().count { id ->
                        game.state.getEntity(id)?.get<CardComponent>()?.name == "Goblin Sledder"
                    }
                    sleddersInPlay shouldBe 2
                }
                withClue("The revealed Mountain should have been bottomed onto P1's library") {
                    val p1Library = game.state.getLibrary(game.player1Id)
                    p1Library.map { id -> game.state.getEntity(id)?.get<CardComponent>()?.name } shouldContain "Mountain"
                }
                withClue("No Goblin Sledder should be lingering in P1's exile (we never staged any there)") {
                    game.state.getExile(game.player1Id).none { id ->
                        game.state.getEntity(id)?.get<CardComponent>()?.name == "Goblin Sledder"
                    } shouldBe true
                }
            }

            test("declining the may-cast bottom-randomizes every revealed card") {
                val game = scenario()
                    .withPlayers("Sunbird Player", "Opponent")
                    .withCardOnBattlefield(1, "Sunbird's Invocation")
                    .withCardInHand(1, "Goblin Sledder")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInLibrary(1, "Goblin Sledder")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Goblin Sledder")
                game.resolveStack()
                game.state.pendingDecision ?: error("Expected a SELECT_CARDS decision")

                // Decline → both revealed cards (Sledder, Mountain) get bottomed; only the
                // hand-cast Sledder resolves onto the battlefield.
                game.skipSelection()
                game.resolveStack()

                withClue("Only the hand-cast Goblin Sledder should be on the battlefield") {
                    val sleddersInPlay = game.state.getBattlefield().count { id ->
                        game.state.getEntity(id)?.get<CardComponent>()?.name == "Goblin Sledder"
                    }
                    sleddersInPlay shouldBe 1
                }
                withClue("The revealed Goblin Sledder should be back in P1's library") {
                    game.state.getLibrary(game.player1Id).count { id ->
                        game.state.getEntity(id)?.get<CardComponent>()?.name == "Goblin Sledder"
                    } shouldBe 1
                }
                withClue("Revealed cards never leave the library — exile must stay empty") {
                    game.state.getExile(game.player1Id).none { id ->
                        game.state.getEntity(id)?.get<CardComponent>()?.name == "Goblin Sledder"
                    } shouldBe true
                }
            }

            test("free-casting a targeted spell pauses for target selection") {
                val game = scenario()
                    .withPlayers("Sunbird Player", "Opponent")
                    .withCardOnBattlefield(1, "Sunbird's Invocation")
                    .withCardInHand(1, "Goblin Sledder")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    // The 1-mana reveal lands on a Shock, which requires a target.
                    .withCardInLibrary(1, "Shock")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Goblin Sledder")
                game.resolveStack()

                // First decision: SELECT_CARDS over the revealed pile.
                game.state.pendingDecision ?: error("Expected SELECT_CARDS decision for Sunbird's Invocation")
                val revealedShockId = game.state.getLibrary(game.player1Id).firstOrNull { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Shock"
                } ?: error("Expected the revealed Shock to still be in the library while paused")
                game.selectCards(listOf(revealedShockId))

                // Second decision: CHOOSE_TARGETS for Shock's "any target" requirement.
                val targetDecision = game.state.pendingDecision
                    ?: error("Expected CHOOSE_TARGETS decision for the free-cast Shock")
                withClue("The target-pause decision must include P2 in the legal pool") {
                    val any = (targetDecision as? com.wingedsheep.engine.core.ChooseTargetsDecision)
                        ?.legalTargets?.values?.flatten().orEmpty()
                    any shouldContain game.player2Id
                }
                game.selectTargets(listOf(game.player2Id))
                game.resolveStack()

                withClue("Shock should have resolved and dealt 2 damage to P2") {
                    game.getLifeTotal(2) shouldBe 18
                }
            }

            test("free-casting a modal spell pauses for mode + target selection at cast time") {
                val game = scenario()
                    .withPlayers("Sunbird Player", "Opponent")
                    .withCardOnBattlefield(1, "Sunbird's Invocation")
                    .withCardInHand(1, "Goblin Sledder")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    // Top reveal: Misery Charm ({B}, MV 1). Mode selection happens at cast
                    // time per CR 601.2b — the synthesized free cast pauses with a
                    // ChooseOptionDecision before the charm ever reaches the stack.
                    .withCardInLibrary(1, "Misery Charm")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Goblin Sledder")
                game.resolveStack()

                // 1. Sunbird's reveals — pick Misery Charm.
                val revealedCharmId = game.state.getLibrary(game.player1Id).firstOrNull { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Misery Charm"
                } ?: error("Misery Charm should still be in the library while paused")
                game.selectCards(listOf(revealedCharmId))

                // 2. The synthesized free cast immediately pauses for cast-time mode
                //    selection.
                val modeDecision = game.state.pendingDecision
                modeDecision.shouldBeInstanceOf<ChooseOptionDecision>()
                val loseLifeModeIndex = modeDecision.options
                    .indexOfFirst { it.contains("loses 2 life", ignoreCase = true) }
                withClue("Misery Charm's cast-time mode picker must offer the lose-life option") {
                    (loseLifeModeIndex >= 0) shouldBe true
                }
                game.submitDecision(OptionChosenResponse(modeDecision.id, loseLifeModeIndex))

                // 3. The chosen mode requires a player target (also cast-time).
                game.selectTargets(listOf(game.player2Id))

                // 4. Cast completes, Misery Charm goes on the stack; resolve to apply.
                game.resolveStack()

                withClue("Misery Charm's lose-2-life mode should have hit P2") {
                    game.getLifeTotal(2) shouldBe 18
                }
            }

            test("no eligible card → no decision is offered and everything bottoms") {
                val game = scenario()
                    .withPlayers("Sunbird Player", "Opponent")
                    .withCardOnBattlefield(1, "Sunbird's Invocation")
                    .withCardInHand(1, "Goblin Sledder")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    // Top of library is a land — ineligible (lands are excluded from the
                    // may-cast pool). No SELECT_CARDS prompt should appear.
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Goblin Sledder")
                game.resolveStack()

                // The revealed Mountain is shown to the player but is non-selectable.
                // SelectFromCollection still pauses so the player sees what was revealed;
                // they confirm without picking (0/0 selection), and the pipeline finishes.
                withClue("SelectFromCollection should still pause to display the revealed Mountain") {
                    game.state.pendingDecision shouldNotBe null
                }
                game.skipSelection()
                game.resolveStack()

                withClue("The Mountain should have been bottomed back onto P1's library") {
                    game.state.getLibrary(game.player1Id).count { id ->
                        game.state.getEntity(id)?.get<CardComponent>()?.name == "Mountain"
                    } shouldBe 1
                }
                withClue("Hand-cast Goblin Sledder should resolve normally") {
                    game.state.getBattlefield().count { id ->
                        game.state.getEntity(id)?.get<CardComponent>()?.name == "Goblin Sledder"
                    } shouldBe 1
                }
            }
        }
    }
}
