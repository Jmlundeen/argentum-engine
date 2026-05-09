package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.player.PlayerLostComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Simic Ascendancy.
 *
 * Card reference:
 * - Simic Ascendancy ({G}{U}): Enchantment
 *   {1}{G}{U}: Put a +1/+1 counter on target creature you control.
 *   Whenever one or more +1/+1 counters are put on a creature you control,
 *     put that many growth counters on Simic Ascendancy.
 *   At the beginning of your upkeep, if Simic Ascendancy has twenty or more
 *     growth counters on it, you win the game.
 */
class SimicAscendancyScenarioTest : ScenarioTestBase() {

    init {
        context("Simic Ascendancy growth-counter accumulation") {
            test("activated ability adds +1/+1 counter and triggers a growth counter") {
                val game = scenario()
                    .withPlayers("Simic Player", "Opponent")
                    .withCardOnBattlefield(1, "Simic Ascendancy")
                    .withCardOnBattlefield(1, "Glory Seeker") // 2/2 vanilla creature
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val ascendancy = game.findPermanent("Simic Ascendancy")!!
                val seeker = game.findPermanent("Glory Seeker")!!

                val cardDef = cardRegistry.getCard("Simic Ascendancy")!!
                val pumpAbility = cardDef.script.activatedAbilities[0]

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = ascendancy,
                        abilityId = pumpAbility.id,
                        targets = listOf(ChosenTarget.Permanent(seeker))
                    )
                )

                withClue("Ability should activate successfully") {
                    result.error shouldBe null
                }

                game.resolveStack()

                withClue("Glory Seeker has a +1/+1 counter after the ability resolves") {
                    val counters = game.state.getEntity(seeker)?.get<CountersComponent>()
                    counters?.counters?.get(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
                }

                // Counter-placed trigger fires; opponent gets priority and passes.
                game.resolveStack()

                withClue("Simic Ascendancy gains a growth counter from the trigger") {
                    val counters = game.state.getEntity(ascendancy)?.get<CountersComponent>()
                    counters?.counters?.get(CounterType.GROWTH) shouldBe 1
                }
            }
        }

        context("Simic Ascendancy win condition") {
            test("wins the game at upkeep with twenty or more growth counters") {
                val game = scenario()
                    .withPlayers("Simic Player", "Opponent")
                    .withCardOnBattlefield(1, "Simic Ascendancy")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.ENDING, Step.END)
                    .build()

                val ascendancy = game.findPermanent("Simic Ascendancy")!!
                game.state = game.state.updateEntity(ascendancy) {
                    it.with(CountersComponent(mapOf(CounterType.GROWTH to 20)))
                }

                // Advance to opponent's upkeep -> ours -> ours upkeep trigger fires.
                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP) // opponent's upkeep first
                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP) // back to player 1's upkeep

                // Drain the win-the-game trigger and any subsequent SBAs.
                var iterations = 0
                while (!game.state.gameOver && iterations < 50) {
                    val p = game.state.priorityPlayerId ?: break
                    game.execute(com.wingedsheep.engine.core.PassPriority(p))
                    iterations++
                }

                withClue("Game should be over") {
                    game.state.gameOver shouldBe true
                }

                withClue("Simic player should be the winner") {
                    game.state.winnerId shouldBe game.player1Id
                }

                val opponentId = game.state.turnOrder[1]
                withClue("Opponent should have lost") {
                    game.state.getEntity(opponentId)?.has<PlayerLostComponent>() shouldBe true
                }
            }

            test("does not win at upkeep with fewer than twenty growth counters") {
                val game = scenario()
                    .withPlayers("Simic Player", "Opponent")
                    .withCardOnBattlefield(1, "Simic Ascendancy")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.ENDING, Step.END)
                    .build()

                val ascendancy = game.findPermanent("Simic Ascendancy")!!
                game.state = game.state.updateEntity(ascendancy) {
                    it.with(CountersComponent(mapOf(CounterType.GROWTH to 19)))
                }

                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP) // opponent's upkeep
                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP) // own upkeep — no trigger

                withClue("Game should not be over with only 19 growth counters") {
                    game.state.gameOver shouldBe false
                }
            }
        }
    }
}
