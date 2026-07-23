package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for the fifth batch of Wilds of Eldraine cards:
 *
 *  - Ashiok's Reaper ({3}{B} 3/3) — draws whenever an enchantment you control hits the graveyard
 *    from the battlefield.
 *  - Song of Totentanz ({X}{R}) — X Rat tokens, then haste for the creatures you control
 *    (including the freshly-made Rats).
 *  - Redtooth Genealogist ({2}{G} 2/3) — enters with a Royal Role for *another* creature you control.
 *  - Protective Parents ({2}{W} 3/2) — dies into a Young Hero Role on up to one creature you control.
 *  - Imodane's Recruiter // Train Troops ({2}{R} 2/2 // {4}{W}) — team pump + haste on enter; the
 *    Adventure makes two vigilant Knights.
 */
class WoeCardsBatch5ScenarioTest : ScenarioTestBase() {

    init {
        context("Ashiok's Reaper — your enchantments dying draw cards") {
            test("an enchantment you control hitting the graveyard draws a card") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Ashiok's Reaper", summoningSickness = false)
                    .withCardOnBattlefield(1, "Castle")
                    .withCardInHand(1, "Disenchant")
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val handBefore = game.handSize(1)

                game.castSpell(1, "Disenchant", game.findPermanent("Castle")!!).error shouldBe null
                game.resolveStack()

                withClue("Disenchant left hand (-1) and the Reaper's trigger drew a card (+1)") {
                    game.handSize(1) shouldBe handBefore
                }
            }

            test("an opponent's enchantment dying does not trigger it") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Ashiok's Reaper", summoningSickness = false)
                    .withCardOnBattlefield(2, "Castle")
                    .withCardInHand(1, "Disenchant")
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val handBefore = game.handSize(1)

                game.castSpell(1, "Disenchant", game.findPermanent("Castle")!!).error shouldBe null
                game.resolveStack()

                withClue("the enchantment was not yours, so no draw — only Disenchant left hand") {
                    game.handSize(1) shouldBe handBefore - 1
                }
            }
        }

        context("Song of Totentanz — X Rats, then haste for the team") {
            test("X=2 makes two Rats and every creature you control gains haste") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Song of Totentanz")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Craw Wurm")
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                val wurm = game.findPermanent("Craw Wurm")!!

                game.castXSpell(1, "Song of Totentanz", 2).error shouldBe null
                game.resolveStack()

                val rats = game.findAllPermanents("Rat Token")
                withClue("X = 2, so two Rat tokens") { rats.size shouldBe 2 }

                withClue("the Rats were created before the haste grant, so they get haste too") {
                    rats.forEach { game.state.projectedState.hasKeyword(it, Keyword.HASTE) shouldBe true }
                }
                withClue("creatures you already controlled gain haste") {
                    game.state.projectedState.hasKeyword(bears, Keyword.HASTE) shouldBe true
                }
                withClue("an opponent's creature is untouched") {
                    game.state.projectedState.hasKeyword(wurm, Keyword.HASTE) shouldBe false
                }
            }

            test("X=0 makes no Rats but still grants haste") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Song of Totentanz")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!

                game.castXSpell(1, "Song of Totentanz", 0).error shouldBe null
                game.resolveStack()

                game.findAllPermanents("Rat Token").size shouldBe 0
                game.state.projectedState.hasKeyword(bears, Keyword.HASTE) shouldBe true
            }
        }

        context("Redtooth Genealogist — a Royal Role for another creature you control") {
            test("the Role lands on the other creature and grants +1/+1 and ward") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Redtooth Genealogist")
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Forest", 3)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!

                game.castSpell(1, "Redtooth Genealogist").error shouldBe null
                game.resolveStack() // Genealogist enters -> ETB trigger asks for its target

                game.selectTargets(listOf(bears)).error shouldBe null
                game.resolveStack()

                withClue("a Royal Role token was created") {
                    (game.findPermanent("Royal Role") != null) shouldBe true
                }
                withClue("the Bears are 2/2 base plus the Role's +1/+1") {
                    game.state.projectedState.getPower(bears) shouldBe 3
                    game.state.projectedState.getToughness(bears) shouldBe 3
                }
                withClue("and the Genealogist itself stayed a plain 2/3 — it can't crown itself") {
                    val genealogist = game.findPermanent("Redtooth Genealogist")!!
                    game.state.projectedState.getPower(genealogist) shouldBe 2
                    game.state.projectedState.getToughness(genealogist) shouldBe 3
                }
            }

            test("with no other creature there is no legal target and no Role appears") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Redtooth Genealogist")
                    .withLandsOnBattlefield(1, "Forest", 3)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Redtooth Genealogist").error shouldBe null
                game.resolveStack()

                withClue("'another target creature you control' has no legal choice") {
                    (game.findPermanent("Royal Role") == null) shouldBe true
                }
            }
        }

        context("Protective Parents — a Young Hero Role on death") {
            test("dying creates the Role on the chosen creature you control") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Protective Parents", summoningSickness = false)
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withCardInHand(1, "Lightning Bolt")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val parents = game.findPermanent("Protective Parents")!!
                val bears = game.findPermanent("Grizzly Bears")!!

                // 3 damage kills the 3/2 Parents.
                game.castSpell(1, "Lightning Bolt", parents).error shouldBe null
                game.resolveStack()

                game.selectTargets(listOf(bears)).error shouldBe null
                game.resolveStack()

                withClue("the Parents died and their trigger made a Young Hero Role") {
                    game.isInGraveyard(1, "Protective Parents") shouldBe true
                    (game.findPermanent("Young Hero Role") != null) shouldBe true
                }
            }

            test("'up to one' may be declined — no Role is created") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Protective Parents", summoningSickness = false)
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withCardInHand(1, "Lightning Bolt")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val parents = game.findPermanent("Protective Parents")!!

                game.castSpell(1, "Lightning Bolt", parents).error shouldBe null
                game.resolveStack()

                game.skipTargets().error shouldBe null
                game.resolveStack()

                withClue("declining the optional target creates nothing") {
                    (game.findPermanent("Young Hero Role") == null) shouldBe true
                }
            }
        }

        context("Imodane's Recruiter // Train Troops") {
            test("the enters trigger gives creatures you control +1/+0 and haste") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Imodane's Recruiter")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(2, "Craw Wurm")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                val wurm = game.findPermanent("Craw Wurm")!!

                game.castSpell(1, "Imodane's Recruiter").error shouldBe null
                game.resolveStack()

                val recruiter = game.findPermanent("Imodane's Recruiter")!!

                withClue("the Bears got +1/+0 and haste") {
                    game.state.projectedState.getPower(bears) shouldBe 3
                    game.state.projectedState.getToughness(bears) shouldBe 2
                    game.state.projectedState.hasKeyword(bears, Keyword.HASTE) shouldBe true
                }
                withClue("the Recruiter is on the battlefield when its own trigger resolves") {
                    game.state.projectedState.getPower(recruiter) shouldBe 3
                    game.state.projectedState.hasKeyword(recruiter, Keyword.HASTE) shouldBe true
                }
                withClue("the opponent's creature is untouched") {
                    game.state.projectedState.getPower(wurm) shouldBe 6
                    game.state.projectedState.hasKeyword(wurm, Keyword.HASTE) shouldBe false
                }
            }

            test("Train Troops makes two 2/2 vigilant Knights and exiles the card") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Imodane's Recruiter")
                    .withLandsOnBattlefield(1, "Plains", 5)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cardId = game.state.getHand(game.player1Id).first { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Imodane's Recruiter"
                }

                // faceIndex = 0 casts the Adventure half, Train Troops.
                game.execute(
                    CastSpell(playerId = game.player1Id, cardId = cardId, faceIndex = 0)
                ).error shouldBe null
                game.resolveStack()

                val knights = game.findAllPermanents("Knight Token")
                withClue("two Knight tokens") { knights.size shouldBe 2 }
                knights.forEach {
                    game.state.projectedState.getPower(it) shouldBe 2
                    game.state.projectedState.getToughness(it) shouldBe 2
                    game.state.projectedState.hasKeyword(it, Keyword.VIGILANCE) shouldBe true
                }
                withClue("the Adventure exiled itself so the creature can be cast later") {
                    game.isInExile(1, "Imodane's Recruiter") shouldBe true
                }
            }
        }
    }
}
