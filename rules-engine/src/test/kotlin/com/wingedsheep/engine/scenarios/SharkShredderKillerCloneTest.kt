package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.tmt.cards.SharkShredderKillerClone
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Shark Shredder, Killer Clone (TMT #73) — "Whenever Shark Shredder deals combat damage to a
 * player, put up to one target creature card from that player's graveyard onto the battlefield
 * under your control. It enters tapped and attacking that player."
 *
 * Reanimates the damaged player's graveyard creature under the attacker's control.
 */
class SharkShredderKillerCloneTest : FunSpec({

    val bear = card("Test Bear") {
        manaCost = "{1}{G}"
        typeLine = "Creature — Bear"
        power = 2
        toughness = 2
    }

    test("combat damage reanimates a creature from the damaged player's graveyard under your control") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(SharkShredderKillerClone, bear))
        driver.initMirrorMatch(deck = Deck.of("Swamp" to 30), startingLife = 20)
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)

        val shark = driver.putCreatureOnBattlefield(player, "Shark Shredder, Killer Clone")
        driver.removeSummoningSickness(shark)
        val bearInGy = driver.putCardInGraveyard(opponent, "Test Bear")

        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)
        driver.declareAttackers(player, listOf(shark), opponent)
        driver.passPriorityUntil(Step.DECLARE_BLOCKERS)
        driver.declareBlockers(opponent, emptyMap())

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

        // The Bear left the opponent's graveyard and is now a permanent under the player's control.
        driver.getGraveyard(opponent).contains(bearInGy) shouldBe false
        driver.findPermanent(player, "Test Bear") shouldNotBe null
    }
})
