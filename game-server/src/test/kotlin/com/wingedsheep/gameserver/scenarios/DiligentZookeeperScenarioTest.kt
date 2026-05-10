package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.tla.AvatarTheLastAirbenderSet
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Diligent Zookeeper's static ability:
 * "Each non-Human creature you control gets +1/+1 for each of its creature types,
 *  to a maximum of 10."
 *
 * Test cases:
 * 1. Single-type non-Human (Hill Giant — Giant) → +1/+1
 * 2. Two-type non-Human (Elvish Warrior — Elf Warrior) → +2/+2
 * 3. Human creature (Glory Seeker — Human Soldier) → no bonus
 * 4. Changeling (Feisty Spikeling — all types, including Human) → no bonus
 * 5. Dummy creature with 11 non-Human types → bonus capped at +10/+10
 */
class DiligentZookeeperScenarioTest : ScenarioTestBase() {

    // 11 non-Human creature types — bonus would be 11 without cap, but Zookeeper caps at 10.
    private val manyTypesCreature = CardDefinition.creature(
        name = "Many-Type Beast",
        manaCost = ManaCost.parse("{5}"),
        subtypes = setOf(
            Subtype.WOLF, Subtype.BEAR, Subtype.CAT, Subtype.BIRD, Subtype.FISH,
            Subtype.SNAKE, Subtype.FROG, Subtype.BEAST, Subtype.ELF, Subtype.DRAGON,
            Subtype.TURTLE      // 11th type — no Human
        ),
        power = 1,
        toughness = 1
    )

    init {
        cardRegistry.register(AvatarTheLastAirbenderSet.cards)
        cardRegistry.register(manyTypesCreature)
        context("Diligent Zookeeper bonus per creature type") {

            test("single-type non-Human creature gets +1/+1") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Diligent Zookeeper")
                    .withCardOnBattlefield(1, "Hill Giant")   // 3/3 Giant — 1 creature type
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giant = game.findPermanent("Hill Giant")!!
                val clientState = game.getClientState(1)
                val giantInfo = clientState.cards[giant]

                withClue("Giant should have its projected P/T visible") {
                    giantInfo shouldNotBe null
                }
                withClue("Hill Giant (3/3 Giant, 1 type) should become 4/4 with Zookeeper") {
                    giantInfo!!.power shouldBe 4
                    giantInfo.toughness shouldBe 4
                }
            }

            test("two-type non-Human creature gets +2/+2") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Diligent Zookeeper")
                    .withCardOnBattlefield(1, "Elvish Warrior")  // 2/3 Elf Warrior — 2 creature types
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val elf = game.findPermanent("Elvish Warrior")!!
                val clientState = game.getClientState(1)
                val elfInfo = clientState.cards[elf]

                withClue("Elvish Warrior (2/3 Elf Warrior, 2 types) should become 4/5 with Zookeeper") {
                    elfInfo shouldNotBe null
                    elfInfo!!.power shouldBe 4
                    elfInfo.toughness shouldBe 5
                }
            }

            test("Human creature gets no bonus") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Diligent Zookeeper")
                    .withCardOnBattlefield(1, "Glory Seeker")   // 2/2 Human Soldier — Human, excluded
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val soldier = game.findPermanent("Glory Seeker")!!
                val clientState = game.getClientState(1)
                val soldierInfo = clientState.cards[soldier]

                withClue("Glory Seeker (Human Soldier) should not get a bonus from Zookeeper") {
                    soldierInfo shouldNotBe null
                    soldierInfo!!.power shouldBe 2
                    soldierInfo.toughness shouldBe 2
                }
            }

            test("Changeling (has Human type) gets no bonus") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Diligent Zookeeper")
                    .withCardOnBattlefield(1, "Feisty Spikeling")  // 2/1 Changeling — all types, including Human
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val changeling = game.findPermanent("Feisty Spikeling")!!
                val clientState = game.getClientState(1)
                val changelingInfo = clientState.cards[changeling]

                withClue("Feisty Spikeling (Changeling, has Human) should get no bonus — stays 2/1") {
                    changelingInfo shouldNotBe null
                    changelingInfo!!.power shouldBe 2
                    changelingInfo.toughness shouldBe 1
                }
            }

            test("bonus is capped at +10/+10 for a creature with 11 non-Human types") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Diligent Zookeeper")
                    .withCardOnBattlefield(1, "Many-Type Beast")  // 11 types, no Human → cap at 10
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val beast = game.findPermanent("Many-Type Beast")!!
                val clientState = game.getClientState(1)
                val beastInfo = clientState.cards[beast]

                withClue("Many-Type Beast (11 types, base 1/1) should be capped at +10/+10 → 11/11") {
                    beastInfo shouldNotBe null
                    beastInfo!!.power shouldBe 11
                    beastInfo.toughness shouldBe 11
                }
            }

            test("no bonus when Zookeeper is not on battlefield") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Elvish Warrior")  // 2/3 Elf Warrior — without Zookeeper
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val elf = game.findPermanent("Elvish Warrior")!!
                val clientState = game.getClientState(1)
                val elfInfo = clientState.cards[elf]

                withClue("Elvish Warrior should be base 2/3 without Zookeeper") {
                    elfInfo shouldNotBe null
                    elfInfo!!.power shouldBe 2
                    elfInfo.toughness shouldBe 3
                }
            }
        }
    }
}
