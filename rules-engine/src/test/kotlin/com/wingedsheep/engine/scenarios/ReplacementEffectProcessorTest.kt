package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.replacement.PendingGameEvent
import com.wingedsheep.engine.replacement.ReplacementEffectProcessor
import com.wingedsheep.engine.replacement.ReplacementOutcome
import com.wingedsheep.engine.replacement.ProcessorResult
import com.wingedsheep.engine.handlers.effects.drawing.DrawReplacementDispatcher
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.ReplacementEffectSourceComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.ModifyDrawAmount
import com.wingedsheep.sdk.scripting.PreventDraw
import com.wingedsheep.sdk.scripting.ReplacementEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.TargetPlayer
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ReplacementEffectProcessorTest : ScenarioTestBase() {

    fun GameTestDriver.addPermanentWithReplacement(
        playerId: EntityId,
        name: String,
        effect: ReplacementEffect
    ): EntityId {
        val permanentId = EntityId.generate()
        val container = ComponentContainer.of(
            CardComponent(
                cardDefinitionId = name,
                name = name,
                manaCost = ManaCost.ZERO,
                typeLine = TypeLine.parse("Enchantment"),
                oracleText = effect.description,
                colors = emptySet(),
                ownerId = playerId,
            ),
            OwnerComponent(playerId),
            ControllerComponent(playerId),
            ReplacementEffectSourceComponent(listOf(effect))
        )
        var newState = state.withEntity(permanentId, container)
        newState = newState.addToZone(ZoneKey(playerId, Zone.BATTLEFIELD), permanentId)
        replaceState(newState)
        return permanentId
    }

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        return driver
    }

    init {
        test("zero matches — processor returns pass for unrelated event") {
            val driver = createDriver()
            driver.initMirrorMatch(deck = Deck.of("Plains" to 20))
            val playerId = driver.activePlayer!!

            val processor = ReplacementEffectProcessor()
            val event = PendingGameEvent.DrawPending(playerId, 1)

            val result = processor.process(driver.state, event)
            withClue("No replacement effects on the battlefield") {
                (result is ProcessorResult.Pass) shouldBe true
            }
        }

        test("single ModifyDrawAmount increases draw count") {
            val driver = createDriver()
            driver.initMirrorMatch(deck = Deck.of("Plains" to 20))
            val playerId = driver.activePlayer!!

            driver.addPermanentWithReplacement(
                playerId, "Draw Booster",
                ModifyDrawAmount(modifier = 1, appliesTo = EventPattern.DrawEvent(Player.You))
            )

            val processor = ReplacementEffectProcessor()
            val event = PendingGameEvent.DrawPending(playerId, 1)

            val result = processor.process(driver.state, event)
            val resolved = result as ProcessorResult.Resolved
            resolved.outcome shouldBe ReplacementOutcome.Modified(
                PendingGameEvent.DrawPending(playerId, count = 1, remainingDraws = 1)
            )
        }

        test("single PreventDraw consumes draw") {
            val driver = createDriver()
            driver.initMirrorMatch(deck = Deck.of("Plains" to 20))
            val playerId = driver.activePlayer!!

            driver.addPermanentWithReplacement(
                playerId, "Draw Preventer",
                PreventDraw(appliesTo = EventPattern.DrawEvent(Player.You))
            )

            val processor = ReplacementEffectProcessor()
            val event = PendingGameEvent.DrawPending(playerId, 1)

            val result = processor.process(driver.state, event)
            val resolved = result as ProcessorResult.Resolved
            resolved.outcome shouldBe ReplacementOutcome.Consumed
        }

        test("prevent + modify both in ANY group so choice is presented") {
            // Both effects are in the ANY group; the processor presents a choice.
            // This test confirms the behavior, not the specific outcome.
            val driver = createDriver()
            driver.initMirrorMatch(deck = Deck.of("Plains" to 20))
            val playerId = driver.activePlayer!!

            driver.addPermanentWithReplacement(
                playerId, "Draw Booster",
                ModifyDrawAmount(modifier = 1, appliesTo = EventPattern.DrawEvent(Player.You))
            )
            driver.addPermanentWithReplacement(
                playerId, "Draw Preventer",
                PreventDraw(appliesTo = EventPattern.DrawEvent(Player.You))
            )

            val processor = ReplacementEffectProcessor()
            val event = PendingGameEvent.DrawPending(playerId, 1)

            val result = processor.process(driver.state, event)
            // Both effects are in the ANY group → multiple same-group → choice
            (result is ProcessorResult.Paused) shouldBe true
        }

        test("gatherReplacements, opponent's 'You' effects don't match active player") {
            val driver = createDriver()
            driver.initMirrorMatch(deck = Deck.of("Plains" to 20))
            val playerId = driver.activePlayer!!
            val opponentId = driver.getOpponent(playerId)

            driver.addPermanentWithReplacement(
                opponentId, "Opponent Booster",
                ModifyDrawAmount(modifier = 1, appliesTo = EventPattern.DrawEvent(Player.You))
            )

            val processor = ReplacementEffectProcessor()
            val event = PendingGameEvent.DrawPending(playerId, 1)

            val gathered = processor.gatherReplacements(driver.state, event)
            gathered.size shouldBe 0
        }

        test("gatherReplacements, EachOpponent filter matches opponent's draw") {
            val driver = createDriver()
            driver.initMirrorMatch(deck = Deck.of("Plains" to 20))
            val playerId = driver.activePlayer!!
            val opponentId = driver.getOpponent(playerId)

            driver.addPermanentWithReplacement(
                opponentId, "Opponent Modifier",
                ModifyDrawAmount(modifier = 1, appliesTo = EventPattern.DrawEvent(Player.EachOpponent))
            )

            val processor = ReplacementEffectProcessor()
            // Active player draws → opponent's EachOpponent matches
            val gathered = processor.gatherReplacements(driver.state, PendingGameEvent.DrawPending(playerId, 1))
            gathered.size shouldBe 1

            // Opponent draws → opponent's EachOpponent does NOT match (source controller is drawing)
            val gathered2 = processor.gatherReplacements(driver.state, PendingGameEvent.DrawPending(opponentId, 1))
            gathered2.size shouldBe 0
        }

        test("Quantum Riddler ETB draws 2 with +1 modifier") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Quantum Riddler")
                .withLandsOnBattlefield(1, "Island", 5)
                .withCardInLibrary(1, "Grizzly Bears")
                .withCardInLibrary(1, "Hill Giant")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            // Cast Riddler {3}{U}{U}.
            // Hand was 1 (Riddler) → 0 after casting. ETB draw 1 + ModifyDrawAmount(+1)
            // (condition: CardsInHandAtMost(1), 0 ≤ 1) → draws 2 cards.
            val cast = game.castSpell(1, "Quantum Riddler")
            cast.error shouldBe null
            game.resolveStack()

            game.handSize(1) shouldBe 2
        }

        test("PreventDraw blocks all draws via replacement loop") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardOnBattlefield(1, "Mornsong Aria")
                .withCardInHand(1, "Inspiration")
                .withCardInLibrary(1, "Grizzly Bears")
                .withCardInLibrary(1, "Hill Giant")
                .withLandsOnBattlefield(1, "Island", 4)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            // Mornsong Aria has PreventDraw for all players. Cast Inspiration (draw 2) →
            // PreventDraw fires on each card → no cards drawn, hand ends at 0.
            val action = game.getLegalActions(1)
                .map { it.action }
                .filterIsInstance<CastSpell>()
                .firstOrNull()
            action shouldNotBe null
            game.execute(action!!.copy(targets = listOf(ChosenTarget.Player(game.player1Id))))
            game.resolveStack()

            game.handSize(1) shouldBe 0
        }

        test("Multiple draw effect replacements") {
            val game = scenario()
                .withPlayers()
                .withCardOnBattlefield(1, "Quantum Riddler")
                .withCardOnBattlefield(1, "Phial of Galadriel")
                .withCardInHand(1, "Inspiration")
                .withLandsOnBattlefield(1, "Island", 4)
                .build()

            val action = game.getLegalActions(1)
                .map {it.action}
                .filterIsInstance<CastSpell>()
                .firstOrNull()
                ?: error("No action found")
            action shouldNotBe null
            game.execute(action.copy(
                targets = listOf(ChosenTarget.Player(game.player1Id))
            ))
            game.resolveStack()
            game.state.isPaused() shouldBe true
            val modeDecision = game.state.pendingDecision as ChooseOptionDecision
            game.submitDecision(OptionChosenResponse(modeDecision.id, 0))
        }
    }
}
