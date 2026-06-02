package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardScript
import com.wingedsheep.sdk.scripting.effects.GainLifeEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Bre of Clan Stoutarm's end-step ability:
 *   "At the beginning of your end step, if you gained life this turn, exile cards from the top of
 *    your library until you exile a nonland card. You may cast that card without paying its mana
 *    cost if the spell's mana value is less than or equal to the amount of life you gained this
 *    turn. Otherwise, put it into your hand."
 *
 * Per the printed rulings, casting the exiled card happens *while Bre's ability is resolving*
 * (an inline "you may cast it for free" choice), not as deferred permission to cast later. When
 * the nonland's mana value exceeds the life gained, there is no cast option and the card is put
 * straight into hand.
 *
 * The library is seeded with a land on top of the nonland so the "exile until a nonland" walk
 * exiles more than one card — the lands stay in exile; only the nonland is ever put into hand.
 */
class BreOfClanStoutarmTest : ScenarioTestBase() {

    init {
        // Gains the controller 3 life so Bre's "if you gained life this turn" trigger condition holds.
        cardRegistry.register(
            CardDefinition.instant(
                name = "Healing Salve",
                manaCost = ManaCost.parse("{W}"),
                oracleText = "You gain 3 life.",
                script = CardScript.spell(effect = GainLifeEffect(3, EffectTarget.Controller))
            )
        )
        // A nonland whose mana value (9) exceeds 3 life gained → "otherwise, put it into your hand".
        cardRegistry.register(
            CardDefinition.creature(
                name = "Colossal Wurm",
                manaCost = ManaCost.parse("{7}{G}{G}"),
                subtypes = setOf(Subtype("Wurm")),
                power = 9,
                toughness = 9
            )
        )
        // A cheap nonland whose mana value (2) is ≤ 3 life gained → may be cast for free.
        cardRegistry.register(
            CardDefinition.creature(
                name = "Cheap Ogre",
                manaCost = ManaCost.parse("{1}{R}"),
                subtypes = setOf(Subtype("Ogre")),
                power = 2,
                toughness = 2
            )
        )

        context("Bre of Clan Stoutarm — end-step impulse") {

            test("mana value > life gained: the nonland is put into hand (lands stay exiled)") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Bre of Clan Stoutarm")
                    .withCardInHand(1, "Healing Salve")
                    .withCardInLibrary(1, "Forest")      // top — a land, exiled along the way
                    .withCardInLibrary(1, "Colossal Wurm") // the nonland the walk stops on
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Gain 3 life this turn so the end-step ability triggers.
                game.castSpell(1, "Healing Salve").error shouldBe null
                game.resolveStack()

                // Advance to the end step; Bre's trigger resolves.
                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()

                withClue("MV 9 > 3 life gained → Colossal Wurm is put into hand") {
                    namesInHand(game, 1).contains("Colossal Wurm") shouldBe true
                }
                withClue("Colossal Wurm is no longer stranded in exile") {
                    namesInExile(game, 1).contains("Colossal Wurm") shouldBe false
                }
                withClue("The land exiled along the way stays in exile (only the nonland moves)") {
                    namesInExile(game, 1).contains("Forest") shouldBe true
                }
            }

            test("mana value ≤ life gained: accepting the free cast puts it on the battlefield") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Bre of Clan Stoutarm")
                    .withCardInHand(1, "Healing Salve")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Cheap Ogre")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Healing Salve").error shouldBe null
                game.resolveStack()

                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()
                // "You may cast that card without paying its mana cost" — accept (cast during resolution).
                game.answerYesNo(true)
                game.resolveStack()

                withClue("MV 2 ≤ 3 life gained, accepted → Cheap Ogre is cast onto the battlefield") {
                    game.isOnBattlefield("Cheap Ogre") shouldBe true
                }
                withClue("The cast card is not also put into hand") {
                    namesInHand(game, 1).contains("Cheap Ogre") shouldBe false
                }
            }

            test("mana value ≤ life gained: declining the free cast leaves it in exile (not hand)") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Bre of Clan Stoutarm")
                    .withCardInHand(1, "Healing Salve")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Cheap Ogre")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Healing Salve").error shouldBe null
                game.resolveStack()

                game.passUntilPhase(Phase.ENDING, Step.END)
                game.resolveStack()
                // Decline the free cast. "Otherwise" (put into hand) only applies when MV > life, so
                // a declined castable card stays in exile rather than going to hand.
                game.answerYesNo(false)

                withClue("Declined free-cast → Cheap Ogre stays in exile") {
                    namesInExile(game, 1).contains("Cheap Ogre") shouldBe true
                }
                withClue("It is not put into hand (the MV ≤ life branch never moves to hand)") {
                    namesInHand(game, 1).contains("Cheap Ogre") shouldBe false
                }
            }
        }
    }

    private fun namesInHand(game: TestGame, playerNumber: Int): List<String> {
        val playerId = if (playerNumber == 1) game.player1Id else game.player2Id
        return game.state.getHand(playerId).mapNotNull {
            game.state.getEntity(it)?.get<CardComponent>()?.name
        }
    }

    private fun namesInExile(game: TestGame, playerNumber: Int): List<String> {
        val playerId = if (playerNumber == 1) game.player1Id else game.player2Id
        return game.state.getExile(playerId).mapNotNull {
            game.state.getEntity(it)?.get<CardComponent>()?.name
        }
    }
}
