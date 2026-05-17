package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Cards put directly onto the battlefield by `MoveCollectionEffect` (e.g., Celestial
 * Reunion's tutor-and-play branch) must still apply their own `EntersWithCounters`
 * replacement effects. Regression for Bristlebane Battler arriving via Celestial Reunion
 * with no -1/-1 counters.
 */
class CelestialReunionEntersWithCountersTest : ScenarioTestBase() {

    init {
        test("Bristlebane Battler tutored to battlefield by Celestial Reunion enters with five -1/-1 counters") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Celestial Reunion")
                // Two Kithkin Soldiers on the battlefield to satisfy "behold two creatures of that type".
                .withCardOnBattlefield(1, "Timid Shieldbearer")
                .withCardOnBattlefield(1, "Timid Shieldbearer")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withCardInLibrary(1, "Bristlebane Battler")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            // X=2 covers Bristlebane Battler's {1}{G} mana value of 2.
            val castResult = game.castXSpell(1, "Celestial Reunion", xValue = 2)
            withClue("Cast should succeed: ${castResult.error}") {
                castResult.error shouldBe null
            }

            game.resolveStack()

            // 1. May we behold the additional cost? Yes.
            game.answerYesNo(true)

            // 2. Choose creature type — Kithkin, so the revealed Bristlebane Battler will match.
            val typeDecision = game.state.pendingDecision
            typeDecision.shouldBeInstanceOf<ChooseOptionDecision>()
            val kithkinIndex = typeDecision.options.indexOf("Kithkin")
            withClue("'Kithkin' should be in creature type options") {
                (kithkinIndex >= 0) shouldBe true
            }
            game.submitDecision(OptionChosenResponse(typeDecision.id, kithkinIndex))

            // 3. Behold-two: exactly two candidates (the two Timid Shieldbearers) so it auto-resolves.

            // 4. Library search: Bristlebane Battler is the only creature card with MV ≤ 2 — select it.
            val battlerInLibrary = game.state.getLibrary(game.player1Id).first { entityId ->
                game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Bristlebane Battler"
            }
            game.selectCards(listOf(battlerInLibrary))

            game.resolveStack()

            // Bristlebane Battler should be on the battlefield (additional cost paid + type matches).
            val battlerId = game.findPermanent("Bristlebane Battler")
            withClue("Bristlebane Battler should have been put onto the battlefield") {
                battlerId shouldBe battlerId
                (battlerId != null) shouldBe true
            }

            // And it should have arrived with five -1/-1 counters from its replacement effect.
            val counters = game.state.getEntity(battlerId!!)?.get<CountersComponent>()
            withClue("Bristlebane Battler should have five -1/-1 counters from its EntersWithCounters effect") {
                counters?.getCount(CounterType.MINUS_ONE_MINUS_ONE) shouldBe 5
            }

            val projected = game.state.projectedState
            withClue("Bristlebane Battler should be 1/1 (6/6 base minus five -1/-1 counters)") {
                projected.getPower(battlerId) shouldBe 1
                projected.getToughness(battlerId) shouldBe 1
            }
        }
    }
}
