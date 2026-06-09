package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Vengeful Townsfolk (OTJ #37) — "Whenever one or more other creatures you control die, put a
 * +1/+1 counter on this creature."
 *
 * Exercises the once-per-batch death trigger ([com.wingedsheep.sdk.dsl.Triggers.OneOrMoreCreaturesYouControlDie]).
 * The critical case is a board wipe: several of your creatures dying simultaneously must add a
 * single counter, not one per creature (the over-counting a per-creature death trigger suffers).
 *
 * Pyroclasm ("2 damage to each creature") is the simultaneous-death engine: it kills the 2/2s
 * outright while the 3/3 Vengeful Townsfolk survives, so its counter is observable.
 */
class VengefulTownsfolkScenarioTest : ScenarioTestBase() {

    private fun plusOneCounters(game: TestGame): Int {
        val id = game.findPermanent("Vengeful Townsfolk")!!
        return game.state.getEntity(id)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
    }

    init {
        context("Vengeful Townsfolk") {
            test("board wipe killing two of your creatures adds exactly one +1/+1 counter") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Vengeful Townsfolk")
                    .withCardOnBattlefield(1, "Glory Seeker")
                    .withCardOnBattlefield(1, "Glory Seeker")
                    .withCardInHand(1, "Pyroclasm")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Pyroclasm")
                withClue("Casting Pyroclasm should succeed: ${cast.error}") {
                    cast.error shouldBe null
                }
                game.resolveStack()

                withClue("Both 2/2 Glory Seekers should have died to 2 damage") {
                    game.findPermanents("Glory Seeker").size shouldBe 0
                }
                withClue("The 3/3 Vengeful Townsfolk should survive 2 damage") {
                    (game.findPermanent("Vengeful Townsfolk") != null) shouldBe true
                }
                withClue("Two simultaneous deaths form one batch → exactly one +1/+1 counter, not two") {
                    plusOneCounters(game) shouldBe 1
                }
            }

            test("a single other creature dying adds one +1/+1 counter") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Vengeful Townsfolk")
                    .withCardOnBattlefield(1, "Glory Seeker")
                    .withCardInHand(1, "Pyroclasm")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Pyroclasm")
                game.resolveStack()

                withClue("The single Glory Seeker should have died") {
                    game.findPermanents("Glory Seeker").size shouldBe 0
                }
                withClue("One other creature died → one +1/+1 counter") {
                    plusOneCounters(game) shouldBe 1
                }
            }

            test("creatures an opponent controls dying does not trigger your Vengeful Townsfolk") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Vengeful Townsfolk")
                    .withCardOnBattlefield(2, "Glory Seeker")
                    .withCardOnBattlefield(2, "Glory Seeker")
                    .withCardInHand(1, "Pyroclasm")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Pyroclasm")
                game.resolveStack()

                withClue("The opponent's Glory Seekers should have died") {
                    game.findPermanents("Glory Seeker").size shouldBe 0
                }
                withClue("Only creatures the opponent controls died → no counter on your Vengeful Townsfolk") {
                    plusOneCounters(game) shouldBe 0
                }
            }
        }
    }
}
