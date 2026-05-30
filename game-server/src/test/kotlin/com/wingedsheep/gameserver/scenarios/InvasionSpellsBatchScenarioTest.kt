package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseColorDecision
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for a batch of Invasion spells: Addle, Hypnotic Cloud, Skizzik, Urza's Rage.
 */
class InvasionSpellsBatchScenarioTest : ScenarioTestBase() {

    // Mono-colored vanilla creatures for unambiguous color/hand contents.
    private val blueDrake = CardDefinition.creature(
        name = "Blue Drake", manaCost = ManaCost.parse("{U}"),
        subtypes = setOf(Subtype("Drake")), power = 2, toughness = 2
    )
    private val greenBear = CardDefinition.creature(
        name = "Green Bear", manaCost = ManaCost.parse("{G}"),
        subtypes = setOf(Subtype("Bear")), power = 2, toughness = 2
    )

    init {
        cardRegistry.register(blueDrake)
        cardRegistry.register(greenBear)

        context("Addle") {
            test("choosing a color discards a card of that color from target player's hand") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Addle")
                    .withLandsOnBattlefield(1, "Swamp", 2) // {1}{B}
                    .withCardInHand(2, "Blue Drake")
                    .withCardInHand(2, "Green Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellTargetingPlayer(1, "Addle", 2)
                game.resolveStack()

                val colorDecision = game.getPendingDecision()
                withClue("Should pause for a color choice on resolution") {
                    (colorDecision is ChooseColorDecision) shouldBe true
                }
                game.submitDecision(ColorChosenResponse(colorDecision!!.id, Color.BLUE))
                game.resolveStack()

                // With only one blue card, the engine selects it automatically (or via prompt).
                if (game.getPendingDecision() != null) {
                    val drake = game.state.getHand(game.player2Id).first {
                        game.state.getEntity(it)?.get<CardComponent>()?.name == "Blue Drake"
                    }
                    game.selectCards(listOf(drake))
                    game.resolveStack()
                }

                withClue("Blue Drake should have been discarded") {
                    game.isInGraveyard(2, "Blue Drake") shouldBe true
                }
                withClue("Green Bear (not the chosen color) should remain in hand") {
                    game.isInGraveyard(2, "Green Bear") shouldBe false
                }
            }
        }

        context("Hypnotic Cloud") {
            test("unkicked makes target player discard one card") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Hypnotic Cloud")
                    .withLandsOnBattlefield(1, "Swamp", 2) // {1}{B}
                    .withCardInHand(2, "Blue Drake")
                    .withCardInHand(2, "Green Bear")
                    .withCardInHand(2, "Hill Giant")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellTargetingPlayer(1, "Hypnotic Cloud", 2)
                game.resolveStack()

                // Opponent chooses one card to discard.
                game.getPendingDecision() shouldNotBe null
                game.selectCards(listOf(game.findCardsInHand(2, "Hill Giant").first()))
                game.resolveStack()

                withClue("Opponent should have discarded exactly one card (3 -> 2)") {
                    game.handSize(2) shouldBe 2
                }
                withClue("The chosen Hill Giant should be in the graveyard") {
                    game.isInGraveyard(2, "Hill Giant") shouldBe true
                }
            }

            test("kicked makes target player discard three cards") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Hypnotic Cloud")
                    .withLandsOnBattlefield(1, "Swamp", 6) // {1}{B} + {4} kicker
                    .withCardInHand(2, "Blue Drake")
                    .withCardInHand(2, "Green Bear")
                    .withCardInHand(2, "Hill Giant")
                    .withCardInHand(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cardId = game.state.getHand(game.player1Id).first {
                    game.state.getEntity(it)?.get<CardComponent>()?.name == "Hypnotic Cloud"
                }
                game.execute(
                    CastSpell(
                        game.player1Id,
                        cardId,
                        targets = listOf(ChosenTarget.Player(game.player2Id)),
                        wasKicked = true
                    )
                )
                game.resolveStack()

                // Opponent (4 cards in hand) chooses three to discard.
                game.getPendingDecision() shouldNotBe null
                game.selectCards(
                    listOf(
                        game.findCardsInHand(2, "Blue Drake").first(),
                        game.findCardsInHand(2, "Green Bear").first(),
                        game.findCardsInHand(2, "Hill Giant").first(),
                    )
                )
                game.resolveStack()

                withClue("Kicked Hypnotic Cloud should make the opponent discard three cards (4 -> 1)") {
                    game.handSize(2) shouldBe 1
                }
            }
        }

        context("Skizzik") {
            test("unkicked Skizzik is sacrificed at the end step") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Skizzik")
                    .withLandsOnBattlefield(1, "Mountain", 4) // {3}{R}, no kicker
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Skizzik")
                game.resolveStack()
                withClue("Skizzik should be on the battlefield after resolving") {
                    game.isOnBattlefield("Skizzik") shouldBe true
                }

                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()

                withClue("Unkicked Skizzik should be sacrificed at end step") {
                    game.isOnBattlefield("Skizzik") shouldBe false
                    game.isInGraveyard(1, "Skizzik") shouldBe true
                }
            }

            test("kicked Skizzik survives the end step") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Skizzik")
                    .withLandsOnBattlefield(1, "Mountain", 5) // {3}{R} + {R} kicker
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cardId = game.state.getHand(game.player1Id).first {
                    game.state.getEntity(it)?.get<CardComponent>()?.name == "Skizzik"
                }
                game.execute(CastSpell(game.player1Id, cardId, wasKicked = true))
                game.resolveStack()

                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()

                withClue("Kicked Skizzik should still be on the battlefield after end step") {
                    game.isOnBattlefield("Skizzik") shouldBe true
                }
            }
        }

        context("Urza's Rage") {
            test("unkicked deals 3 damage to target player") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Urza's Rage")
                    .withLandsOnBattlefield(1, "Mountain", 3) // {2}{R}
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellTargetingPlayer(1, "Urza's Rage", 2)
                game.resolveStack()

                withClue("Opponent should take 3 damage (20 -> 17)") {
                    game.getLifeTotal(2) shouldBe 17
                }
            }

            test("kicked deals 10 damage to target player") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Urza's Rage")
                    .withLandsOnBattlefield(1, "Mountain", 12) // {2}{R} + {8}{R} kicker = 12 mana
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cardId = game.state.getHand(game.player1Id).first {
                    game.state.getEntity(it)?.get<CardComponent>()?.name == "Urza's Rage"
                }
                game.execute(
                    CastSpell(
                        game.player1Id,
                        cardId,
                        targets = listOf(ChosenTarget.Player(game.player2Id)),
                        wasKicked = true
                    )
                )
                game.resolveStack()

                withClue("Opponent should take 10 damage (20 -> 10)") {
                    game.getLifeTotal(2) shouldBe 10
                }
            }
        }
    }
}
