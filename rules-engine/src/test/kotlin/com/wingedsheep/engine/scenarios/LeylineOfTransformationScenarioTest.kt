package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.battlefield.ChoiceValue
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.dsk.cards.LeylineOfTransformation
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.ChoiceSlot
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private val projector = StateProjector()

/**
 * Scenarios for Leyline of Transformation (DSK) — the new [com.wingedsheep.sdk.scripting.GrantChosenSubtype]
 * static ability ("Creatures you control are the chosen type in addition to their other types").
 *
 * Only the battlefield clause is modeled (see the card definition for the documented limitation), so
 * these tests cover: creatures you control gain the chosen type *in addition to* their printed types,
 * opponents' creatures are unaffected, and the choice is made as the enchantment enters.
 */
class LeylineOfTransformationScenarioTest : FunSpec({

    val bear = CardDefinition.creature(
        name = "Test Bear",
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(Subtype("Bear")),
        power = 2,
        toughness = 2
    )

    val goblin = CardDefinition.creature(
        name = "Test Goblin",
        manaCost = ManaCost.parse("{1}{R}"),
        subtypes = setOf(Subtype("Goblin")),
        power = 1,
        toughness = 1
    )

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(LeylineOfTransformation, bear, goblin))
        return driver
    }

    test("creatures you control gain the chosen type in addition to their printed types") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 20, "Mountain" to 20), skipMulligans = true)
        val activePlayer = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // Leyline of Transformation on the battlefield with the chosen type "Goblin".
        val leyline = driver.putPermanentOnBattlefield(activePlayer, "Leyline of Transformation")
        driver.replaceState(driver.state.updateEntity(leyline) { c ->
            c.with(CastChoicesComponent(chosen = mapOf(ChoiceSlot.CREATURE_TYPE to ChoiceValue.TextChoice("Goblin"))))
        })

        val bearId = driver.putCreatureOnBattlefield(activePlayer, "Test Bear")

        val projected = projector.project(driver.state)
        // The Bear is now a Goblin in addition to its other types.
        projected.hasSubtype(bearId, "Goblin") shouldBe true
        projected.hasSubtype(bearId, "Bear") shouldBe true
        // P/T untouched — this is a pure type-change.
        projected.getPower(bearId) shouldBe 2
        projected.getToughness(bearId) shouldBe 2
    }

    test("opponents' creatures are unaffected (Creatures YOU control)") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 20, "Mountain" to 20), skipMulligans = true)
        val activePlayer = driver.activePlayer!!
        val opponent = driver.getOpponent(activePlayer)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val leyline = driver.putPermanentOnBattlefield(activePlayer, "Leyline of Transformation")
        driver.replaceState(driver.state.updateEntity(leyline) { c ->
            c.with(CastChoicesComponent(chosen = mapOf(ChoiceSlot.CREATURE_TYPE to ChoiceValue.TextChoice("Goblin"))))
        })

        val myBear = driver.putCreatureOnBattlefield(activePlayer, "Test Bear")
        val theirBear = driver.putCreatureOnBattlefield(opponent, "Test Bear")

        val projected = projector.project(driver.state)
        projected.hasSubtype(myBear, "Goblin") shouldBe true
        projected.hasSubtype(theirBear, "Goblin") shouldBe false
        projected.hasSubtype(theirBear, "Bear") shouldBe true
    }

    test("a creature already of the chosen type keeps its type and is unchanged") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 20, "Mountain" to 20), skipMulligans = true)
        val activePlayer = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val leyline = driver.putPermanentOnBattlefield(activePlayer, "Leyline of Transformation")
        driver.replaceState(driver.state.updateEntity(leyline) { c ->
            c.with(CastChoicesComponent(chosen = mapOf(ChoiceSlot.CREATURE_TYPE to ChoiceValue.TextChoice("Goblin"))))
        })

        val goblinId = driver.putCreatureOnBattlefield(activePlayer, "Test Goblin")

        val projected = projector.project(driver.state)
        projected.hasSubtype(goblinId, "Goblin") shouldBe true
    }

    test("choice is made as the enchantment enters (EntersWithChoice)") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Island" to 20, "Forest" to 20), skipMulligans = true)
        val activePlayer = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val bearId = driver.putCreatureOnBattlefield(activePlayer, "Test Bear")

        // Cast Leyline of Transformation from hand — it should pause for the creature-type choice.
        val spell = driver.putCardInHand(activePlayer, "Leyline of Transformation")
        driver.giveMana(activePlayer, Color.BLUE, 4)
        driver.castSpell(activePlayer, spell)
        driver.bothPass()

        driver.isPaused shouldBe true
        val decision = driver.pendingDecision
        decision.shouldBeInstanceOf<ChooseOptionDecision>()
        val goblinIndex = decision.options.indexOf("Goblin")
        driver.submitDecision(activePlayer, OptionChosenResponse(decision.id, goblinIndex))

        val projected = projector.project(driver.state)
        projected.hasSubtype(bearId, "Goblin") shouldBe true
        projected.hasSubtype(bearId, "Bear") shouldBe true
    }
})
