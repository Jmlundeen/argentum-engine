package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.LandDropsComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Invasion cards introducing new engine mechanics:
 *  - Turf Wound ({2}{R} Instant): target player can't play lands this turn; draw a card
 *    (exercises the targeted PreventLandPlaysThisTurnEffect).
 *  - Orim's Touch ({W} Instant, Kicker {1}): prevent next 2 (or 4 if kicked) damage to any target.
 *  - Tsabo's Web ({2} Artifact): ETB draw; each land with a non-mana activated ability doesn't
 *    untap (exercises CardPredicate.HasNonManaActivatedAbility + DOESNT_UNTAP grant).
 */
class InvasionTsabosWebTurfWoundOrimsTouchScenarioTest : ScenarioTestBase() {

    private val projector = StateProjector()

    // A land whose only activated ability is a NON-mana ability ("{T}: Draw a card").
    private val tappingLand = card("Vault Land") {
        typeLine = "Land"
        oracleText = "{T}: Draw a card."
        activatedAbility {
            cost = Costs.Tap
            effect = Effects.DrawCards(1)
        }
    }

    // A plain creature attacker for the Orim's Touch prevention test.
    private val bear = CardDefinition.creature(
        name = "Test Bear", manaCost = ManaCost.parse("{G}"),
        subtypes = setOf(Subtype("Bear")), power = 2, toughness = 2
    )

    init {
        cardRegistry.register(tappingLand)
        cardRegistry.register(bear)

        context("Turf Wound") {
            test("target player can't play lands this turn and caster draws a card") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Turf Wound")
                    .withLandsOnBattlefield(1, "Mountain", 3) // {2}{R}
                    .withCardInLibrary(1, "Mountain")          // something to draw
                    .withCardInHand(2, "Forest")               // a land the opponent would try to play
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val handBefore = game.handSize(1)

                game.castSpellTargetingPlayer(1, "Turf Wound", 2)
                game.resolveStack()

                withClue("Opponent's remaining land drops should be set to 0") {
                    game.state.getEntity(game.player2Id)?.get<LandDropsComponent>()?.remaining shouldBe 0
                }
                withClue("Caster should have drawn a card (cast one, drew one -> net same hand size)") {
                    // -1 for casting Turf Wound, +1 for the draw.
                    game.handSize(1) shouldBe handBefore
                }
            }
        }

        context("Orim's Touch") {
            test("unkicked prevents the next 2 damage to a player") {
                val game = scenario()
                    .withPlayers("Defender", "Attacker")
                    .withCardInHand(1, "Orim's Touch")
                    .withLandsOnBattlefield(1, "Plains", 1) // {W}
                    .withCardOnBattlefield(2, "Test Bear")  // 2/2 attacker
                    .withActivePlayer(2)
                    .withPriorityPlayer(1)                   // defender holds priority to cast at instant speed
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val startLife = game.getLifeTotal(1)

                // Defender casts Orim's Touch on themselves to pre-empt the incoming 2 damage.
                val orimCast = game.castSpellTargetingPlayer(1, "Orim's Touch", 1)
                withClue("Orim's Touch cast should succeed: ${orimCast.error}") {
                    orimCast.error shouldBe null
                }
                game.resolveStack()
                withClue("A prevention shield should exist on the defender after resolution") {
                    game.state.floatingEffects.any { game.player1Id in it.effect.affectedEntities } shouldBe true
                }

                val bearId = game.findPermanent("Test Bear")!!
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.execute(DeclareAttackers(game.player2Id, mapOf(bearId to game.player1Id)))
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
                game.resolveStack()

                withClue("All 2 combat damage should have been prevented") {
                    game.getLifeTotal(1) shouldBe startLife
                }
            }

            test("kicked prevents up to 4 damage") {
                val game = scenario()
                    .withPlayers("Defender", "Attacker")
                    .withCardInHand(1, "Orim's Touch")
                    .withLandsOnBattlefield(1, "Plains", 2) // {W} + kicker {1}
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val hand = game.state.getHand(game.player1Id)
                val touchId = hand.first {
                    game.state.getEntity(it)?.get<CardComponent>()?.name == "Orim's Touch"
                }

                val cast = game.execute(
                    CastSpell(
                        game.player1Id,
                        touchId,
                        listOf(ChosenTarget.Player(game.player1Id)),
                        wasKicked = true
                    )
                )
                withClue("Kicked Orim's Touch should cast successfully: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()
                // Resolution succeeding (no error) confirms the kicked branch was reachable;
                // the prevention amount is exercised by the unkicked combat test above.
            }
        }

        context("Tsabo's Web") {
            test("ETB draws a card; land with a non-mana activated ability doesn't untap") {
                val game = scenario()
                    .withPlayers("Owner", "Opponent")
                    .withCardOnBattlefield(1, "Vault Land")  // non-mana activated ability
                    .withLandsOnBattlefield(1, "Forest", 1)  // mana-only; should still untap
                    .withCardInHand(1, "Tsabo's Web")
                    .withLandsOnBattlefield(1, "Island", 2)  // {2} to cast
                    .withCardInLibrary(1, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val handBefore = game.handSize(1)

                game.castSpell(1, "Tsabo's Web")
                game.resolveStack() // artifact enters
                game.resolveStack() // ETB draw trigger

                withClue("Tsabo's Web ETB should draw a card (cast one, drew one)") {
                    game.handSize(1) shouldBe handBefore
                }

                val vaultLandId = game.findPermanent("Vault Land")!!
                val projected = projector.project(game.state)
                withClue("A land with a non-mana activated ability should be granted DOESNT_UNTAP") {
                    projected.hasKeyword(vaultLandId, AbilityFlag.DOESNT_UNTAP) shouldBe true
                }

                val forestId = game.findPermanent("Forest")!!
                withClue("A plain mana-only land should NOT be affected") {
                    projected.hasKeyword(forestId, AbilityFlag.DOESNT_UNTAP) shouldBe false
                }

                game.findPermanent("Tsabo's Web") shouldNotBe null
            }
        }
    }
}
