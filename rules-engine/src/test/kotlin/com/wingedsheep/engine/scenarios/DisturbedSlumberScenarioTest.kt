package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Disturbed Slumber (LCI #182).
 *
 * {1}{G} Instant
 * "Until end of turn, target land you control becomes a 4/4 Dinosaur creature with reach and
 * haste. It's still a land. It must be blocked this turn if able."
 */
class DisturbedSlumberScenarioTest : FunSpec({

    val projector = StateProjector()

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        return driver
    }

    test("target land becomes a 4/4 Dinosaur with reach and haste, and is still a land") {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of("Forest" to 40),
            startingLife = 20
        )

        val activePlayer = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val forest = driver.putLandOnBattlefield(activePlayer, "Forest")
        val spell = driver.putCardInHand(activePlayer, "Disturbed Slumber")
        // {1}{G}: give 2 green so the generic {1} and the colored {G} are both covered.
        driver.giveMana(activePlayer, Color.GREEN, 2)

        val castResult = driver.castSpell(activePlayer, spell, targets = listOf(forest))
        castResult.isSuccess shouldBe true

        driver.bothPass()

        val projected = projector.project(driver.state)
        // The land is now a creature.
        projected.hasType(forest, "CREATURE") shouldBe true
        // It's still a land ("it's still a land").
        projected.hasType(forest, "LAND") shouldBe true
        // Base P/T is 4/4.
        projected.getPower(forest) shouldBe 4
        projected.getToughness(forest) shouldBe 4
        // Creature type is Dinosaur.
        projected.hasSubtype(forest, "DINOSAUR") shouldBe true
        // Granted keywords: reach and haste.
        projected.hasKeyword(forest, Keyword.REACH) shouldBe true
        projected.hasKeyword(forest, Keyword.HASTE) shouldBe true
    }

    test("animated land reverts to a non-creature land at end of turn") {
        val driver = createDriver()
        driver.initMirrorMatch(
            deck = Deck.of("Forest" to 40),
            startingLife = 20
        )

        val activePlayer = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val forest = driver.putLandOnBattlefield(activePlayer, "Forest")
        val spell = driver.putCardInHand(activePlayer, "Disturbed Slumber")
        driver.giveMana(activePlayer, Color.GREEN, 2)

        driver.castSpell(activePlayer, spell, targets = listOf(forest))
        driver.bothPass()

        // Confirm the land is animated mid-turn.
        val midTurn = projector.project(driver.state)
        midTurn.hasType(forest, "CREATURE") shouldBe true

        // Advance to the opponent's upkeep — the end-of-turn cleanup fires and the effect expires.
        driver.passPriorityUntil(Step.UPKEEP)

        val nextTurn = projector.project(driver.state)
        nextTurn.hasType(forest, "CREATURE") shouldBe false
        nextTurn.hasType(forest, "LAND") shouldBe true
    }
})
