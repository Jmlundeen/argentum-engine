package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Uthros Psionicist.
 *
 * Uthros Psionicist: {2}{U} Creature — Jellyfish Scientist 2/4
 * "The second spell you cast each turn costs {2} less to cast."
 *
 * Per Scryfall ruling (2025-07-25), Uthros Psionicist itself counts toward the per-turn
 * spell tally. If it's the first spell you cast this turn, your next spell is the second.
 */
class UthrosPsionicistScenarioTest : ScenarioTestBase() {

    init {
        context("Uthros Psionicist — second-spell cost reduction") {

            test("first spell of the turn is not reduced") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Uthros Psionicist")
                    .withCardInHand(1, "Divination") // {2}{U}
                    .withLandsOnBattlefield(1, "Island", 3)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val divination = cardRegistry.getCard("Divination")!!
                val costCalc = CostCalculator(cardRegistry)
                val effective = costCalc.calculateEffectiveCost(game.state, divination, game.player1Id)

                withClue("First spell of the turn: Divination ({2}{U}) should cost its full CMC 3") {
                    effective.cmc shouldBe 3
                }
            }

            test("second spell of the turn costs {2} less") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Uthros Psionicist")
                    .withCardInHand(1, "Shock")
                    .withCardInHand(1, "Divination") // {2}{U}
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast the first spell of the turn (Shock) targeting Player2
                val shockResult = game.castSpellTargetingPlayer(1, "Shock", 2)
                withClue("Shock should cast normally: ${shockResult.error}") {
                    shockResult.error shouldBe null
                }
                game.resolveStack()

                // Divination is now the second spell — reduced by {2}, so {U} instead of {2}{U}
                val divination = cardRegistry.getCard("Divination")!!
                val costCalc = CostCalculator(cardRegistry)
                val effective = costCalc.calculateEffectiveCost(game.state, divination, game.player1Id)

                withClue("Second spell: Divination ({2}{U}) should be reduced by {2} → CMC 1") {
                    effective.cmc shouldBe 1
                }
            }

            test("third spell of the turn is not reduced") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Uthros Psionicist")
                    .withCardInHand(1, "Shock")
                    .withCardInHand(1, "Opt") // {U}
                    .withCardInHand(1, "Divination") // {2}{U}
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Cast two spells first so Divination becomes the third.
                game.castSpellTargetingPlayer(1, "Shock", 2)
                game.resolveStack()
                game.castSpell(1, "Opt")
                game.resolveStack()

                val divination = cardRegistry.getCard("Divination")!!
                val costCalc = CostCalculator(cardRegistry)
                val effective = costCalc.calculateEffectiveCost(game.state, divination, game.player1Id)

                withClue("Third spell: Divination ({2}{U}) should cost its full CMC 3") {
                    effective.cmc shouldBe 3
                }
            }

            test("Uthros Psionicist itself counts as a cast spell") {
                // Uthros is the first spell of the turn — its own static ability is active
                // once it's on the battlefield, so the next spell qualifies as the "second".
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Uthros Psionicist") // {2}{U}
                    .withCardInHand(1, "Divination")        // {2}{U}
                    .withLandsOnBattlefield(1, "Island", 6)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val uthrosResult = game.castSpell(1, "Uthros Psionicist")
                withClue("Uthros should cast: ${uthrosResult.error}") {
                    uthrosResult.error shouldBe null
                }
                game.resolveStack()

                // Uthros is on the battlefield and was the first cast spell. Divination is the second.
                val divination = cardRegistry.getCard("Divination")!!
                val costCalc = CostCalculator(cardRegistry)
                val effective = costCalc.calculateEffectiveCost(game.state, divination, game.player1Id)

                withClue("Second spell after casting Uthros: Divination reduced to {U} → CMC 1") {
                    effective.cmc shouldBe 1
                }
            }

            test("cost increase applies before the reduction, flooring the generic at {0} (CR 601.2f)") {
                // Glowrider taxes noncreature spells +{1}; Uthros reduces the second spell by {2}.
                // Per CR 601.2f the total cost is the base cost plus increases minus reductions, and the
                // generic component can't drop below {0}. Opt is {U} (0 generic): 0 + 1 − 2 floors to {0},
                // leaving {U} (CMC 1). Applying the reduction before the increase would instead floor 0 − 2
                // to {0} and then add {1}, giving {1}{U} (CMC 2) — so CMC 1 confirms the correct ordering.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Uthros Psionicist")
                    .withCardOnBattlefield(1, "Glowrider") // Noncreature spells cost {1} more
                    .withCardInHand(1, "Shock")
                    .withCardInHand(1, "Opt") // {U}
                    .withLandsOnBattlefield(1, "Mountain", 2) // Shock is taxed to {1}{R}
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // First spell of the turn (Shock — itself taxed to {1}{R} by Glowrider).
                val shockResult = game.castSpellTargetingPlayer(1, "Shock", 2)
                withClue("Shock should cast: ${shockResult.error}") {
                    shockResult.error shouldBe null
                }
                game.resolveStack()

                // Opt is now the second spell: {U} +{1} (Glowrider) −{2} (Uthros), floored → {U}.
                val opt = cardRegistry.getCard("Opt")!!
                val costCalc = CostCalculator(cardRegistry)
                val effective = costCalc.calculateEffectiveCost(game.state, opt, game.player1Id)

                withClue("Second spell Opt: {U} +{1} −{2} floored at {0} → {U} (CMC 1)") {
                    effective.cmc shouldBe 1
                }
            }
        }
    }
}
