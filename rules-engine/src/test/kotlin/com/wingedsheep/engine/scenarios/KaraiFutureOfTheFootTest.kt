package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.tmt.cards.KaraiFutureOfTheFoot
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * Karai, Future of the Foot (TMT #151) — "Whenever Karai deals combat damage to a player, return
 * target creature card from your graveyard to your hand. If her sneak cost was paid this turn,
 * instead return that card to the battlefield."
 *
 * This covers the default (no-sneak) branch: Karai was cast normally, so her sneak cost was not
 * paid and the chosen creature card returns to hand rather than the battlefield. The sneak-paid →
 * battlefield branch leans on the Sneak cast pipeline proven in SneakTest (a creature can only be
 * put onto the battlefield by its sneak cost on the turn it's sneaked in, which is exactly when
 * the ANDed `SourceEnteredThisTurn` also holds).
 */
class KaraiFutureOfTheFootTest : FunSpec({

    val bear = card("Test Bear") {
        manaCost = "{1}{G}"
        typeLine = "Creature — Bear"
        power = 2
        toughness = 2
    }

    test("combat damage returns a creature card from your graveyard to hand when not sneaked") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(KaraiFutureOfTheFoot, bear))
        driver.initMirrorMatch(deck = Deck.of("Swamp" to 30), startingLife = 20)
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)

        val karai = driver.putCreatureOnBattlefield(player, "Karai, Future of the Foot")
        driver.removeSummoningSickness(karai)
        val bearInGy = driver.putCardInGraveyard(player, "Test Bear")

        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(player, listOf(karai), opponent)
        driver.passPriorityUntil(Step.DECLARE_BLOCKERS)
        driver.declareBlockers(opponent, emptyMap())

        // Combat damage fires the trigger, which pauses to choose the graveyard creature card.
        var guard = 0
        while (driver.state.step != Step.POSTCOMBAT_MAIN && guard++ < 40) {
            val decision = driver.pendingDecision
            val holder = driver.state.priorityPlayerId
            if (decision is ChooseTargetsDecision) {
                driver.submitTargetSelection(decision.playerId, listOf(bearInGy))
            } else if (driver.state.stack.isNotEmpty()) {
                driver.bothPass()
            } else if (holder != null) {
                driver.passPriority(holder)
            } else {
                break
            }
        }

        // Not sneaked → the Bear returns to hand, not the battlefield.
        driver.getHand(player) shouldContain bearInGy
        driver.findPermanent(player, "Test Bear") shouldBe null
    }
})
