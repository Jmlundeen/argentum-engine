package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.fdn.cards.SquadRallier
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Squad Rallier ({3}{W}, 3/4 Human Scout).
 *
 * "{2}{W}: Look at the top four cards of your library. You may reveal a creature card with
 * power 2 or less from among them and put it into your hand. Put the rest on the bottom of
 * your library in a random order."
 *
 * Exercises [com.wingedsheep.sdk.dsl.Patterns.Library.lookAtTopRevealMatchingToHand] driven by
 * an activated ability with a `Creature.powerAtMost(2)` filter: only the small creature among
 * the top four is selectable; choosing it puts it into hand and bottoms the rest.
 */
class SquadRallierScenarioTest : FunSpec({

    val SmallSoldier = CardDefinition.creature("Test Small Soldier", ManaCost.parse("{1}{W}"), setOf(Subtype("Soldier")), 2, 1)
    val BigBrute = CardDefinition.creature("Test Big Brute", ManaCost.parse("{4}"), setOf(Subtype("Ogre")), 4, 4)

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(SquadRallier, SmallSoldier, BigBrute))
        return driver
    }

    fun libraryNames(driver: GameTestDriver, player: EntityId): List<String> =
        driver.state.getZone(ZoneKey(player, com.wingedsheep.sdk.core.Zone.LIBRARY)).mapNotNull {
            driver.state.getEntity(it)?.get<CardComponent>()?.name
        }

    test("reveals a creature with power 2 or less to hand and bottoms the rest") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Plains" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val active = driver.activePlayer!!

        val rallier = driver.putCreatureOnBattlefield(active, "Squad Rallier")

        // Stack the top four: a small creature (selectable), a big creature (power 4 → not
        // selectable), and two non-creature Plains. putCardOnTopOfLibrary prepends, so push in
        // reverse to get [SmallSoldier, BigBrute, Plains, Plains] from the top.
        val plains2 = driver.putCardOnTopOfLibrary(active, "Plains")
        val plains1 = driver.putCardOnTopOfLibrary(active, "Plains")
        val bigId = driver.putCardOnTopOfLibrary(active, "Test Big Brute")
        val smallId = driver.putCardOnTopOfLibrary(active, "Test Small Soldier")

        // Pay {2}{W} from three white mana, then activate the dig ability.
        driver.giveMana(active, Color.WHITE, 3)
        val abilityId = driver.cardRegistry.requireCard("Squad Rallier").activatedAbilities[0].id
        driver.submitSuccess(ActivateAbility(playerId = active, sourceId = rallier, abilityId = abilityId))

        // The ability resolves and pauses on the "reveal up to one" selection.
        driver.bothPass()

        val decision = driver.pendingDecision
        decision shouldNotBe null
        val select = decision.shouldBeInstanceOf<SelectCardsDecision>()
        select.minSelections shouldBe 0
        select.maxSelections shouldBe 1

        // Only the power-2 creature is selectable; the big creature and the lands are shown but not.
        select.options shouldContainExactlyInAnyOrder listOf(smallId)
        select.nonSelectableOptions shouldContainExactlyInAnyOrder listOf(bigId, plains1, plains2)

        driver.submitCardSelection(active, listOf(smallId))

        // The small soldier is in hand; the rest are on the bottom of the library.
        driver.getHand(active).any {
            driver.state.getEntity(it)?.get<CardComponent>()?.name == "Test Small Soldier"
        } shouldBe true

        val library = libraryNames(driver, active)
        library.contains("Test Small Soldier") shouldBe false
        library.takeLast(3) shouldContainExactlyInAnyOrder listOf("Test Big Brute", "Plains", "Plains")
    }
})
