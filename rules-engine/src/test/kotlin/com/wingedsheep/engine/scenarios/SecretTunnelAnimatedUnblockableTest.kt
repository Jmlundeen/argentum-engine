package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.tla.cards.EarthenAlly
import com.wingedsheep.mtg.sets.definitions.tla.cards.SecretTunnel
import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Secret Tunnel's printed "This land can't be blocked" must be honored once the land is animated
 * into a creature (e.g. via Earthen Ally's Earthbend 5) and attacks — the case where the static
 * actually matters. Regression coverage for the engine's projection + block-evasion enforcement:
 *  - Test A: after Earthbend animates it, the projected CANT_BE_BLOCKED keyword survives the type change.
 *  - Test B: end-to-end — declaring a blocker on the animated attacker is rejected by the engine.
 *
 * (The engine enforces this correctly; the user-visible "I could block it" was a web-client UX gap
 * where the block-assignment UI doesn't respect attacker evasion — tracked separately.)
 */
class SecretTunnelAnimatedUnblockableTest : FunSpec({

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(SecretTunnel, EarthenAlly))
        driver.initMirrorMatch(
            deck = Deck.of(
                "Forest" to 20,
                "Grizzly Bears" to 10,
                "Secret Tunnel" to 5,
                "Earthen Ally" to 5
            ),
            skipMulligans = true
        )
        return driver
    }

    fun GameTestDriver.giveEarthbendMana(playerId: EntityId) {
        giveMana(playerId, Color.WHITE)
        giveMana(playerId, Color.BLUE)
        giveMana(playerId, Color.BLACK)
        giveMana(playerId, Color.RED)
        giveMana(playerId, Color.GREEN)
        giveColorlessMana(playerId, 2)
    }

    fun GameTestDriver.advanceToPlayer1DeclareAttackers() {
        passPriorityUntil(Step.DECLARE_ATTACKERS)
        var safety = 0
        while (activePlayer != player1 && safety < 50) {
            bothPass()
            passPriorityUntil(Step.DECLARE_ATTACKERS)
            safety++
        }
    }

    test("A: animated Secret Tunnel keeps its printed CANT_BE_BLOCKED after Earthbend") {
        val driver = createDriver()
        val tunnel = driver.putLandOnBattlefield(driver.player1, "Secret Tunnel")
        val ally = driver.putPermanentOnBattlefield(driver.player1, "Earthen Ally")
        driver.removeSummoningSickness(ally)
        driver.giveEarthbendMana(driver.player1)

        driver.submit(
            ActivateAbility(
                playerId = driver.player1,
                sourceId = ally,
                abilityId = EarthenAlly.activatedAbilities[0].id,
                targets = listOf(ChosenTarget.Permanent(tunnel))
            )
        ).error shouldBe null
        driver.bothPass() // resolve Earthbend

        withClue("Secret Tunnel is now a creature-land; its own 'this land can't be blocked' static must still apply") {
            driver.state.projectedState.hasKeyword(tunnel, AbilityFlag.CANT_BE_BLOCKED) shouldBe true
        }
    }

    test("B: a defender cannot block the animated Secret Tunnel") {
        val driver = createDriver()
        val tunnel = driver.putLandOnBattlefield(driver.player1, "Secret Tunnel")
        val ally = driver.putPermanentOnBattlefield(driver.player1, "Earthen Ally")
        driver.removeSummoningSickness(ally)
        val blocker = driver.putCreatureOnBattlefield(driver.player2, "Grizzly Bears")
        driver.removeSummoningSickness(blocker)
        driver.giveEarthbendMana(driver.player1)

        driver.submit(
            ActivateAbility(
                playerId = driver.player1,
                sourceId = ally,
                abilityId = EarthenAlly.activatedAbilities[0].id,
                targets = listOf(ChosenTarget.Permanent(tunnel))
            )
        ).error shouldBe null
        driver.bothPass() // resolve Earthbend (grants haste, so the land can attack)

        driver.advanceToPlayer1DeclareAttackers()
        driver.currentStep shouldBe Step.DECLARE_ATTACKERS
        driver.declareAttackers(driver.player1, listOf(tunnel), driver.player2).isSuccess shouldBe true
        driver.bothPass()
        driver.currentStep shouldBe Step.DECLARE_BLOCKERS

        val result = driver.submitExpectFailure(
            DeclareBlockers(driver.player2, mapOf(blocker to listOf(tunnel)))
        )
        withClue("Secret Tunnel can't be blocked, so declaring a blocker on it must be rejected") {
            result.isSuccess shouldBe false
        }
    }
})
