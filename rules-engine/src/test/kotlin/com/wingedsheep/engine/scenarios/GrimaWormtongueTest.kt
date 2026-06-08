package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.handlers.continuations.entityIdToChosenTarget
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.ltr.cards.GrimaWormtongue
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Supertype
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Gríma Wormtongue (LTR).
 *
 *  - Static "Your opponents can't gain life" — covered by `PreventLifeGain` with
 *    `Player.EachOpponent`; tested by giving an opponent a life-gain spell and checking
 *    their life total didn't change.
 *  - `{T}, Sacrifice another creature: Target player loses 1 life. If the sacrificed
 *    creature was legendary, amass Orcs 2.` — tested for both branches via the Gap 17
 *    `SacrificedWasLegendary` condition.
 */
class GrimaWormtongueTest : FunSpec({

    val abilityId = GrimaWormtongue.activatedAbilities.first().id

    val LegendaryFodder = CardDefinition.creature(
        name = "Legendary Fodder",
        manaCost = ManaCost.parse("{1}"),
        subtypes = setOf(Subtype.HUMAN),
        power = 1,
        toughness = 1,
        supertypes = setOf(Supertype.LEGENDARY),
    )

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(GrimaWormtongue, LegendaryFodder))
        return driver
    }

    test("activated ability with nonlegendary sacrifice deals 1 to target without amassing") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Swamp" to 40), skipMulligans = true)
        val active = driver.activePlayer!!
        val opp = driver.getOpponent(active)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val grima = driver.putCreatureOnBattlefield(active, "Gríma Wormtongue")
        driver.removeSummoningSickness(grima)
        val fodder = driver.putCreatureOnBattlefield(active, "Grizzly Bears")

        val oppLifeBefore = driver.getLifeTotal(opp)
        val armiesBefore = driver.state.getBattlefield().count { id ->
            driver.state.projectedState.getController(id) == active &&
                driver.state.projectedState.hasSubtype(id, "Army")
        }

        val result = driver.submit(
            ActivateAbility(
                playerId = active,
                sourceId = grima,
                abilityId = abilityId,
                targets = listOf(entityIdToChosenTarget(driver.state, opp)),
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(fodder))
            )
        )
        result.isSuccess shouldBe true
        while (driver.state.stack.isNotEmpty()) driver.bothPass()

        driver.getLifeTotal(opp) shouldBe (oppLifeBefore - 1)
        val armiesAfter = driver.state.getBattlefield().count { id ->
            driver.state.projectedState.getController(id) == active &&
                driver.state.projectedState.hasSubtype(id, "Army")
        }
        armiesAfter shouldBe armiesBefore
    }

    test("activated ability with legendary sacrifice ALSO amasses Orcs 2") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Swamp" to 40), skipMulligans = true)
        val active = driver.activePlayer!!
        val opp = driver.getOpponent(active)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val grima = driver.putCreatureOnBattlefield(active, "Gríma Wormtongue")
        driver.removeSummoningSickness(grima)
        val fodder = driver.putCreatureOnBattlefield(active, "Legendary Fodder")

        val oppLifeBefore = driver.getLifeTotal(opp)

        val result = driver.submit(
            ActivateAbility(
                playerId = active,
                sourceId = grima,
                abilityId = abilityId,
                targets = listOf(entityIdToChosenTarget(driver.state, opp)),
                costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(fodder))
            )
        )
        result.isSuccess shouldBe true
        while (driver.state.stack.isNotEmpty()) driver.bothPass()

        // Damage applied
        driver.getLifeTotal(opp) shouldBe (oppLifeBefore - 1)

        // An Army token exists under active's control with 2 +1/+1 counters.
        val army = driver.state.getBattlefield().firstOrNull { id ->
            driver.state.projectedState.getController(id) == active &&
                driver.state.projectedState.hasSubtype(id, "Army")
        }
        army shouldBe army // sanity
        check(army != null) { "Expected an Army token after legendary sacrifice" }
        val plusCounters = driver.state.getEntity(army)
            ?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
        plusCounters shouldBe 2
    }
})
