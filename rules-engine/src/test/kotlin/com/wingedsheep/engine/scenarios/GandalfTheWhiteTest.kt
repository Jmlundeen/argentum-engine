package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.ltr.cards.GandalfTheWhite
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Supertype
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Gandalf the White ({3}{W}{W} Legendary Creature — Avatar Wizard 4/5) — partial:
 *   Flash                                                                       (printed keyword)
 *   You may cast legendary spells and artifact spells as though they had flash. (Gap 4)
 *   …extra-trigger replacement…                                                 (deferred; separate gap)
 *
 * Pins the two implemented clauses:
 *  * a legendary creature spell becomes castable at instant speed
 *  * an artifact spell becomes castable at instant speed
 *  * a vanilla non-legendary non-artifact creature spell does NOT become castable
 *  * the static is controller-only — an opponent does not benefit
 */
class GandalfTheWhiteTest : FunSpec({

    val testLegendaryBeast = CardDefinition.creature(
        name = "Test Legendary Beast",
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(Subtype("Beast")),
        power = 2,
        toughness = 2,
        supertypes = setOf(Supertype.LEGENDARY)
    )

    val testArtifact = CardDefinition(
        name = "Test Trinket",
        manaCost = ManaCost.parse("{2}"),
        typeLine = TypeLine.artifact(),
        oracleText = ""
    )

    val testPlainCreature = CardDefinition.creature(
        name = "Test Bear",
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(Subtype("Bear")),
        power = 2,
        toughness = 2
    )

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(
            TestCards.all + listOf(
                GandalfTheWhite,
                testLegendaryBeast,
                testArtifact,
                testPlainCreature
            )
        )
        return driver
    }

    test("controller may cast a legendary creature at instant speed with Gandalf the White") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 20, "Plains" to 20))
        val p1 = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        driver.putPermanentOnBattlefield(p1, "Gandalf the White")
        val legendary = driver.putCardInHand(p1, "Test Legendary Beast")
        driver.giveMana(p1, Color.GREEN, 2)
        driver.passPriorityUntil(Step.END)

        val result = driver.submit(
            CastSpell(playerId = p1, cardId = legendary, paymentStrategy = PaymentStrategy.FromPool)
        )
        result.isSuccess shouldBe true
    }

    test("controller may cast an artifact at instant speed with Gandalf the White") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Plains" to 40))
        val p1 = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        driver.putPermanentOnBattlefield(p1, "Gandalf the White")
        val trinket = driver.putCardInHand(p1, "Test Trinket")
        driver.giveColorlessMana(p1, 2)
        driver.passPriorityUntil(Step.END)

        val result = driver.submit(
            CastSpell(playerId = p1, cardId = trinket, paymentStrategy = PaymentStrategy.FromPool)
        )
        result.isSuccess shouldBe true
    }

    test("a vanilla non-legendary non-artifact creature still can't be cast at instant speed") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 20, "Plains" to 20))
        val p1 = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        driver.putPermanentOnBattlefield(p1, "Gandalf the White")
        val bear = driver.putCardInHand(p1, "Test Bear")
        driver.giveMana(p1, Color.GREEN, 2)
        driver.passPriorityUntil(Step.END)

        val result = driver.submit(
            CastSpell(playerId = p1, cardId = bear, paymentStrategy = PaymentStrategy.FromPool)
        )
        result.isSuccess shouldBe false
    }

    test("the static is controller-only: an opponent's legendary creature doesn't gain flash") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 20, "Plains" to 20))
        val p1 = driver.activePlayer!!
        val p2 = driver.player2
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        driver.putPermanentOnBattlefield(p1, "Gandalf the White")
        val legendary = driver.putCardInHand(p2, "Test Legendary Beast")
        driver.giveMana(p2, Color.GREEN, 2)
        driver.passPriorityUntil(Step.END)
        driver.passPriority(p1)

        val result = driver.submit(
            CastSpell(playerId = p2, cardId = legendary, paymentStrategy = PaymentStrategy.FromPool)
        )
        result.isSuccess shouldBe false
    }
})
