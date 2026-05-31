package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.EntityNumericProperty
import com.wingedsheep.sdk.scripting.values.EntityReference
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Gap 12 substrate: `DynamicAmount.EntityProperty(EntityReference.AmassedArmy, …)` reads the
 * Army that received the +1/+1 counters from the most recent Amass step in the current
 * resolution pipeline (CR 701.47). Composes with [CompositeEffect] of `[Amass, ...]` so a
 * follow-up sibling effect can scale by the just-amassed Army's power — Foray of Orcs ("…deals
 * damage equal to the amassed Army's power") and Surrounded by Orcs ("…mills X cards, where X
 * is the amassed Army's power"). Also covers the multi-Army choice path, which goes through
 * a continuation: the pipeline slot has to survive the pause.
 */
class AmassedArmyReferenceScenarioTest : FunSpec({

    val projector = StateProjector()

    val amassedArmyPower = DynamicAmount.EntityProperty(
        EntityReference.AmassedArmy,
        EntityNumericProperty.Power
    )

    // {0} sorcery — Foray of Orcs shape: "Amass Orcs 2. Then ~ deals damage to target player
    // equal to the amassed Army's power."
    val ForayShape = card("Foray Shape") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        oracleText = "Amass Orcs 2. Then this deals damage equal to the amassed Army's power to target player."
        spell {
            val player = target("target player", Targets.Player)
            effect = CompositeEffect(listOf(
                Effects.Amass(2, "Orc"),
                Effects.DealDamage(amassedArmyPower, player)
            ))
        }
    }

    // {0} sorcery — Foray with a variable Amass amount (mirrors Foray of Orcs' printed "Amass
    // Orcs 2", but also exercises that Amass-amount + amassed-army-power use the same slot).
    val ForayShapeOne = card("Foray Shape One") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        oracleText = "Amass Orcs 1. Then this deals damage equal to the amassed Army's power to target player."
        spell {
            val player = target("target player", Targets.Player)
            effect = CompositeEffect(listOf(
                Effects.Amass(1, "Orc"),
                Effects.DealDamage(amassedArmyPower, player)
            ))
        }
    }

    // Pre-existing Armies for the multi-Army choice. Base 2/2 so they survive state-based actions.
    val ZombieArmyA = card("Zombie Army Alpha") {
        manaCost = "{0}"
        typeLine = "Creature — Zombie Army"
        power = 2; toughness = 2
    }
    val ZombieArmyB = card("Zombie Army Beta") {
        manaCost = "{0}"
        typeLine = "Creature — Zombie Army"
        power = 2; toughness = 2
    }

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(
            TestCards.all + listOf(ForayShape, ForayShapeOne, ZombieArmyA, ZombieArmyB)
        )
        return driver
    }

    test("amassed Army's power scales the follow-up damage in the same composite (no prior Army)") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Mountain" to 40))
        val active = driver.activePlayer!!
        val opponent = driver.getOpponent(active)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val startingLife = driver.getLifeTotal(opponent)
        val foray = driver.putCardInHand(active, "Foray Shape")
        driver.castSpell(active, foray, listOf(opponent))
        driver.bothPass()

        // Amassed a fresh 0/0 token → +1/+1 counters → 2/2. Then "deal damage equal to that
        // Army's power" reads 2.
        driver.getLifeTotal(opponent) shouldBe startingLife - 2
    }

    test("amassed Army's power reflects counters stacked from a prior Amass") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Mountain" to 40))
        val active = driver.activePlayer!!
        val opponent = driver.getOpponent(active)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val first = driver.putCardInHand(active, "Foray Shape")
        driver.castSpell(active, first, listOf(opponent))
        driver.bothPass()
        val afterFirstLife = driver.getLifeTotal(opponent)
        afterFirstLife shouldBe 20 - 2  // baseline check before second cast

        // Second cast — Amass Orcs 1 grows the same Army from 2/2 to 3/3, deals 3 damage.
        val second = driver.putCardInHand(active, "Foray Shape One")
        driver.castSpell(active, second, listOf(opponent))
        driver.bothPass()

        driver.getLifeTotal(opponent) shouldBe afterFirstLife - 3
    }

    test("multi-Army choice carries the amassed-Army slot through the continuation") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Mountain" to 40))
        val active = driver.activePlayer!!
        val opponent = driver.getOpponent(active)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // Two pre-existing 2/2 Armies. After Amass Orcs 2 onto the chosen one it grows to 4/4,
        // so the follow-up damage should be 4 — *not* the other Army's stale power.
        val alpha = driver.putCreatureOnBattlefield(active, "Zombie Army Alpha")
        val beta = driver.putCreatureOnBattlefield(active, "Zombie Army Beta")

        val startingLife = driver.getLifeTotal(opponent)
        val foray = driver.putCardInHand(active, "Foray Shape")
        driver.castSpell(active, foray, listOf(opponent))
        driver.bothPass()

        // Decision: pick Beta.
        val decision = driver.pendingDecision
        (decision is SelectCardsDecision) shouldBe true
        decision as SelectCardsDecision
        decision.options.toSet() shouldBe setOf(alpha, beta)
        driver.submitDecision(active, CardsSelectedResponse(decision.id, listOf(beta)))

        // Beta is now 4/4; the composite's follow-up damage reads Beta's power.
        projector.project(driver.state).getPower(beta) shouldBe 4
        driver.getLifeTotal(opponent) shouldBe startingLife - 4
    }
})
