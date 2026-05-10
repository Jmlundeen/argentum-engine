package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Bello, Bard of the Brambles.
 *
 * {1}{R}{G} 3/3 Legendary Creature — Raccoon Bard
 * During your turn, each non-Equipment artifact and non-Aura enchantment you control
 * with mana value 4 or greater is a 4/4 Elemental creature in addition to its other
 * types and has indestructible, haste, and "Whenever this creature deals combat
 * damage to a player, draw a card."
 */
class BelloBardOfTheBramblesScenarioTest : ScenarioTestBase() {

    init {
        context("Bello animates qualifying artifacts and enchantments on your turn") {

            test("artifact with mana value 5 you control becomes a 4/4 Elemental creature") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Bello, Bard of the Brambles")
                    .withCardOnBattlefield(1, "Gilded Lotus")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val lotusId = game.findPermanent("Gilded Lotus")!!
                val clientState = game.getClientState(1)
                val lotus = clientState.cards[lotusId]!!

                withClue("Gilded Lotus should be a creature") {
                    lotus.cardTypes.contains("CREATURE") shouldBe true
                }
                withClue("Gilded Lotus should still be an artifact") {
                    lotus.cardTypes.contains("ARTIFACT") shouldBe true
                }
                withClue("Gilded Lotus should be an Elemental") {
                    lotus.subtypes.any { it.equals("Elemental", ignoreCase = true) } shouldBe true
                }
                withClue("Gilded Lotus should be 4/4") {
                    lotus.power shouldBe 4
                    lotus.toughness shouldBe 4
                }
                withClue("Gilded Lotus should have indestructible") {
                    lotus.keywords.contains(Keyword.INDESTRUCTIBLE) shouldBe true
                }
                withClue("Gilded Lotus should have haste") {
                    lotus.keywords.contains(Keyword.HASTE) shouldBe true
                }
            }

            test("non-Aura enchantment with mana value 5 you control becomes a 4/4 Elemental creature") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Bello, Bard of the Brambles")
                    .withCardOnBattlefield(1, "Festival of Embers")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val festivalId = game.findPermanent("Festival of Embers")!!
                val clientState = game.getClientState(1)
                val festival = clientState.cards[festivalId]!!

                withClue("Festival of Embers should be a creature") {
                    festival.cardTypes.contains("CREATURE") shouldBe true
                }
                withClue("Festival of Embers should still be an enchantment") {
                    festival.cardTypes.contains("ENCHANTMENT") shouldBe true
                }
                withClue("Festival of Embers should be 4/4") {
                    festival.power shouldBe 4
                    festival.toughness shouldBe 4
                }
            }
        }

        context("Bello does not animate excluded permanents") {

            test("Equipment with mana value 4 you control is NOT animated") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Bello, Bard of the Brambles")
                    .withCardOnBattlefield(1, "Starforged Sword")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val swordId = game.findPermanent("Starforged Sword")!!
                val clientState = game.getClientState(1)
                val sword = clientState.cards[swordId]!!

                withClue("Equipment must not be animated by Bello") {
                    sword.cardTypes.contains("CREATURE") shouldBe false
                }
            }

            test("artifact with mana value 2 is NOT animated (below mana value threshold)") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Bello, Bard of the Brambles")
                    .withCardOnBattlefield(1, "Fellwar Stone")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val stoneId = game.findPermanent("Fellwar Stone")!!
                val clientState = game.getClientState(1)
                val stone = clientState.cards[stoneId]!!

                withClue("Mana value 2 artifact must not be animated by Bello") {
                    stone.cardTypes.contains("CREATURE") shouldBe false
                }
            }

            test("opponent's qualifying artifact is NOT animated by your Bello") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Bello, Bard of the Brambles")
                    .withCardOnBattlefield(2, "Gilded Lotus")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val lotusId = game.findPermanent("Gilded Lotus")!!
                val clientState = game.getClientState(1)
                val lotus = clientState.cards[lotusId]!!

                withClue("Opponent-controlled artifact must not be animated by your Bello") {
                    lotus.cardTypes.contains("CREATURE") shouldBe false
                }
            }
        }

        context("Bello's animation is gated to your turn") {

            test("on opponent's turn, qualifying artifact you control is NOT a creature") {
                val game = scenario()
                    .withPlayers()
                    .withCardOnBattlefield(1, "Bello, Bard of the Brambles")
                    .withCardOnBattlefield(1, "Gilded Lotus")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val lotusId = game.findPermanent("Gilded Lotus")!!
                val clientState = game.getClientState(1)
                val lotus = clientState.cards[lotusId]!!

                withClue("Gilded Lotus must not be a creature on opponent's turn") {
                    lotus.cardTypes.contains("CREATURE") shouldBe false
                }
                withClue("Gilded Lotus must not have indestructible on opponent's turn") {
                    lotus.keywords.contains(Keyword.INDESTRUCTIBLE) shouldBe false
                }
            }
        }
    }
}
