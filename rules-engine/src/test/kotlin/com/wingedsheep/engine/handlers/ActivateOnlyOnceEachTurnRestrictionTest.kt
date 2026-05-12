package com.wingedsheep.engine.handlers

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.ActivationRestriction
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * BDD tests for the once-per-turn activation restriction.
 *
 * GIVEN a permanent with an activated ability flagged 'Activate only once each turn'
 * AND the ability has already been activated once this turn and resolved
 * WHEN the controller attempts a second activation during the same turn
 * THEN the engine rejects it and legal actions no longer include the ability
 */
class ActivateOnlyOnceEachTurnRestrictionTest : FunSpec({

    val testCard = card("Once Per Turn Ability") {
        manaCost = "{1}"
        typeLine = "Creature — Human"
        power = 1
        toughness = 1

        activatedAbility {
            cost = Costs.Mana("{0}")
            effect = Effects.GainLife(1)
            restrictions = listOf(ActivationRestriction.OncePerTurn)
            description = "Activate only once each turn."
        }
    }

    val abilityId = testCard.activatedAbilities.first().id

    fun setup(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(testCard)
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver
    }

    test("second activation of a once-per-turn ability in the same turn is illegal") {
        val driver = setup()
        val activePlayer = driver.activePlayer!!
        val source = driver.putCreatureOnBattlefield(activePlayer, "Once Per Turn Ability")
        driver.removeSummoningSickness(source)

        val firstResult = driver.submit(ActivateAbility(playerId = activePlayer, sourceId = source, abilityId = abilityId))
        firstResult.isSuccess shouldBe true

        val secondResult = driver.submit(ActivateAbility(playerId = activePlayer, sourceId = source, abilityId = abilityId))
        secondResult.isSuccess shouldBe false
    }

    test("restriction handler permits first activation when ability has not been used this turn") {
        val driver = setup()
        val activePlayer = driver.activePlayer!!
        val source = driver.putCreatureOnBattlefield(activePlayer, "Once Per Turn Ability")

        val handler = ActivateOnlyOnceEachTurnRestrictionHandler()
        handler.isActivationPermitted(driver.state, source, abilityId) shouldBe true
    }

    test("restriction handler blocks activation after recording it this turn") {
        val driver = setup()
        val activePlayer = driver.activePlayer!!
        val source = driver.putCreatureOnBattlefield(activePlayer, "Once Per Turn Ability")

        val handler = ActivateOnlyOnceEachTurnRestrictionHandler()
        val stateAfterRecording = handler.recordActivation(driver.state, source, abilityId)
        handler.isActivationPermitted(stateAfterRecording, source, abilityId) shouldBe false
    }
})
