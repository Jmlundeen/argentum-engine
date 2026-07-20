package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for the fourth batch of Wilds of Eldraine cards:
 *
 *  - Tanglespan Lookout ({2}{G} 2/3) — draws a card whenever an Aura you control enters
 *    (Role tokens are Auras, so they count).
 *  - Up the Beanstalk ({1}{G}) — draws on enter and on each mana value 5+ spell you cast.
 *  - Succumb to the Cold ({2}{U}) — taps one or two target creatures an opponent controls and
 *    puts a stun counter on each.
 *  - Savior of the Sleeping ({2}{W} 2/3, vigilance) — grows when an enchantment you control
 *    hits the graveyard from the battlefield.
 *  - Spiteful Hexmage ({B} 3/2) — enters with a Cursed Role for a creature you control.
 */
class WoeCardsBatch4ScenarioTest : ScenarioTestBase() {

    private fun stunCounters(game: TestGame, id: EntityId): Int =
        game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.STUN) ?: 0

    private fun isTapped(game: TestGame, id: EntityId): Boolean =
        game.state.getEntity(id)?.get<TappedComponent>() != null

    private fun plusOneCounters(game: TestGame, id: EntityId): Int =
        game.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    init {
        context("Tanglespan Lookout — an Aura you control entering draws a card") {
            test("a Role token created by another spell triggers the draw") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Tanglespan Lookout", summoningSickness = false)
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withCardInHand(1, "Monstrous Rage")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bear = game.findPermanent("Grizzly Bears")!!
                val handBefore = game.handSize(1)

                game.castSpell(1, "Monstrous Rage", bear).error shouldBe null
                game.resolveStack()

                withClue("Monstrous Rage's Monster Role is an Aura, so the Lookout draws a card") {
                    (game.findPermanent("Monster Role") != null) shouldBe true
                    // -1 for the Monstrous Rage that left hand, +1 for the Lookout's draw.
                    game.handSize(1) shouldBe handBefore
                }
            }
        }

        context("Up the Beanstalk — draws on enter and on expensive spells") {
            test("entering draws one card, then a mana value 6 spell draws another") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Up the Beanstalk")
                    .withCardInHand(1, "Craw Wurm")
                    .withLandsOnBattlefield(1, "Forest", 8)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val handBefore = game.handSize(1)

                game.castSpell(1, "Up the Beanstalk").error shouldBe null
                game.resolveStack()

                withClue("Beanstalk left hand (-1) and its enters trigger drew a card (+1)") {
                    game.handSize(1) shouldBe handBefore
                }

                game.castSpell(1, "Craw Wurm").error shouldBe null
                game.resolveStack()

                withClue("Craw Wurm is mana value 6, so casting it drew another card") {
                    // Craw Wurm left hand (-1), the cast trigger drew (+1).
                    game.handSize(1) shouldBe handBefore
                }
            }
        }

        context("Succumb to the Cold — taps one or two creatures and stuns them") {
            test("two targets are each tapped with a stun counter") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Succumb to the Cold")
                    .withCardOnBattlefield(2, "Grizzly Bears", summoningSickness = false)
                    .withCardOnBattlefield(2, "Craw Wurm", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Island", 3)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                val wurm = game.findPermanent("Craw Wurm")!!
                val spell = game.state.getHand(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Succumb to the Cold"
                }

                game.execute(
                    CastSpell(
                        playerId = game.player1Id,
                        cardId = spell,
                        targets = listOf(ChosenTarget.Permanent(bears), ChosenTarget.Permanent(wurm))
                    )
                ).error shouldBe null
                game.resolveStack()

                withClue("both targets tapped") {
                    isTapped(game, bears) shouldBe true
                    isTapped(game, wurm) shouldBe true
                }
                withClue("one stun counter each") {
                    stunCounters(game, bears) shouldBe 1
                    stunCounters(game, wurm) shouldBe 1
                }
            }

            test("a single target is legal — minCount is 1") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Succumb to the Cold")
                    .withCardOnBattlefield(2, "Grizzly Bears", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Island", 3)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!

                game.castSpell(1, "Succumb to the Cold", bears).error shouldBe null
                game.resolveStack()

                isTapped(game, bears) shouldBe true
                stunCounters(game, bears) shouldBe 1
            }
        }

        context("Savior of the Sleeping — grows when your enchantments die") {
            test("a Role token falling off the battlefield adds a +1/+1 counter") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Savior of the Sleeping", summoningSickness = false)
                    .withCardOnBattlefield(1, "Castle")
                    .withCardInHand(1, "Disenchant")
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val savior = game.findPermanent("Savior of the Sleeping")!!
                plusOneCounters(game, savior) shouldBe 0

                // Castle is a plain static enchantment (no death trigger of its own), so
                // Disenchanting it isolates the Savior's trigger.
                game.castSpell(1, "Disenchant", game.findPermanent("Castle")!!).error shouldBe null
                game.resolveStack()

                withClue("an enchantment you control hit the graveyard from the battlefield") {
                    plusOneCounters(game, savior) shouldBe 1
                }
                withClue("2/3 base plus one +1/+1 counter") {
                    game.state.projectedState.getPower(savior) shouldBe 3
                    game.state.projectedState.getToughness(savior) shouldBe 4
                }
            }
        }

        context("Spiteful Hexmage — enters with a Cursed Role") {
            test("the Cursed Role sets the enchanted creature to base 1/1") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Spiteful Hexmage")
                    .withCardOnBattlefield(1, "Craw Wurm", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val wurm = game.findPermanent("Craw Wurm")!!

                game.castSpell(1, "Spiteful Hexmage").error shouldBe null
                game.resolveStack() // Hexmage enters -> ETB trigger asks for its target

                game.selectTargets(listOf(wurm)).error shouldBe null
                game.resolveStack()

                withClue("a Cursed Role token was created") {
                    (game.findPermanent("Cursed Role") != null) shouldBe true
                }
                withClue("Cursed Role sets base P/T to 1/1, overriding the Wurm's 6/4") {
                    game.state.projectedState.getPower(wurm) shouldBe 1
                    game.state.projectedState.getToughness(wurm) shouldBe 1
                }
            }
        }
    }
}
