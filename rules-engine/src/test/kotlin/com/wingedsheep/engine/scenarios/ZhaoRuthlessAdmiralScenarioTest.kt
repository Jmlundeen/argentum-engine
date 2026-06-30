package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.tla.cards.ZhaoRuthlessAdmiral
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.effects.ManaExpiry
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Zhao, Ruthless Admiral (TLA #252) — {2}{B/R}{B/R} Legendary Creature —
 * Human Soldier, 3/4.
 *
 * - Firebending 2 (Whenever this creature attacks, add {R}{R}. This mana lasts until end of combat.)
 * - Whenever you sacrifice another permanent, creatures you control get +1/+0 until end of turn.
 *
 * Firebending has no engine handler — the printed keyword is a display tag plus an attack-triggered
 * combat-duration "add {R}{R}" effect — so the firebending test asserts the *behavior* (attacking
 * produces two red combat-duration mana). The sacrifice payoff is the batching
 * "you sacrifice one or more permanents" trigger (ANY binding) over any permanent, applying a +1/+0
 * team pump to every creature you control until end of turn; it is driven here with a {0}
 * "Sacrifice target permanent" sorcery.
 */
class ZhaoRuthlessAdmiralScenarioTest : ScenarioTestBase() {

    // {0} sorcery that sacrifices a target permanent you control — a clean sacrifice outlet.
    private val sacrificePermanent = card("Sacrifice Permanent") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        oracleText = "Sacrifice target permanent you control."
        spell {
            val t = target("target permanent you control", Targets.Permanent)
            effect = Effects.SacrificeTarget(t)
        }
    }

    init {
        cardRegistry.register(ZhaoRuthlessAdmiral)
        cardRegistry.register(sacrificePermanent)

        context("Zhao, Ruthless Admiral") {

            test("firebending 2: attacking with Zhao adds {R}{R} combat-duration mana") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Zhao, Ruthless Admiral", summoningSickness = false)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Zhao, Ruthless Admiral" to 2)).error shouldBe null
                game.resolveStack()

                val combatMana = game.state.getEntity(game.player1Id)
                    ?.get<ManaPoolComponent>()
                    ?.restrictedMana
                    ?.filter { it.expiry == ManaExpiry.END_OF_COMBAT }
                    ?: emptyList()

                withClue("firebending 2 adds two red combat-duration mana on attack") {
                    combatMana.size shouldBe 2
                    combatMana.all { it.color == Color.RED } shouldBe true
                }
            }

            test("sacrificing another permanent gives creatures you control +1/+0 until end of turn") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Zhao, Ruthless Admiral", summoningSickness = false)
                    .withCardOnBattlefield(1, "Centaur Courser", summoningSickness = false) // 3/3 pump target
                    .withCardOnBattlefield(1, "Savannah Lions", summoningSickness = false)    // 1/1 fodder
                    .withCardInHand(1, "Sacrifice Permanent")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val zhao = game.findPermanent("Zhao, Ruthless Admiral")!!
                val ally = game.findPermanent("Centaur Courser")!!
                val fodder = game.findPermanent("Savannah Lions")!!

                withClue("baseline power before any sacrifice") {
                    game.state.projectedState.getPower(zhao) shouldBe 3
                    game.state.projectedState.getPower(ally) shouldBe 3
                }

                // Sacrifice the Savannah Lions (another permanent), firing Zhao's trigger.
                game.castSpell(1, "Sacrifice Permanent", fodder).error shouldBe null
                game.resolveStack()

                withClue("the fodder is gone") {
                    game.isOnBattlefield("Savannah Lions") shouldBe false
                }
                withClue("every creature you control gets +1/+0 until end of turn (toughness unchanged)") {
                    game.state.projectedState.getPower(zhao) shouldBe 4
                    game.state.projectedState.getToughness(zhao) shouldBe 4
                    game.state.projectedState.getPower(ally) shouldBe 4
                    game.state.projectedState.getToughness(ally) shouldBe 3
                }
            }
        }
    }
}
