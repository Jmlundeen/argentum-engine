package com.wingedsheep.engine.handlers.effects.drawing

import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.DecisionPhase
import com.wingedsheep.engine.core.DecisionRequestedEvent
import com.wingedsheep.engine.core.DrawReplacementRemainingDrawsContinuation
import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.StaticDrawReplacementContinuation
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.replacement.PendingGameEvent
import com.wingedsheep.engine.replacement.ReplacementEffectProcessor
import com.wingedsheep.engine.replacement.ProcessorResult
import com.wingedsheep.engine.replacement.ReplacementOutcome
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.LinkedExileComponent
import com.wingedsheep.engine.state.components.battlefield.ReplacementEffectSourceComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ModifyDrawAmount
import com.wingedsheep.sdk.scripting.PreventDraw
import com.wingedsheep.sdk.scripting.ReplaceDrawWithEffect
import com.wingedsheep.sdk.scripting.effects.Effect
import java.util.UUID

/**
 * Runs the draw-replacement checks that fire before each individual card
 * is drawn, in the order required by the rules:
 *
 *  1. Centralized replacement effect processor ([ReplacementEffectProcessor])
 *      checks replacement effects to apply and prompts the user when multiple available.
 *  2. (Parallel Thoughts-style optional static replacement handled by
 *     [checkStaticDrawReplacement] as a fallback.)
 *     for [PreventDraw] (CR 121.2a / 615).
 *  3. Static draw replacement effect — Parallel Thoughts-style optional
 *     yes/no prompt that replaces the draw with an alternative effect.
 *
 * The dispatcher is called by [DrawLoop] during each iteration of a multi-draw
 * sequence, shared by both [DrawCardsExecutor] and [DrawPhaseManager].
 *
 * Prompt-on-draw activated abilities (`promptOnDraw = true`) are no longer
 * supported by the engine. Words cycle cards use the floating-shield mechanism
 * via manual activation instead.
 */
class DrawReplacementDispatcher(
    private val effectExecutor: ((GameState, Effect, EffectContext) -> EffectResult)?
) {
    /**
     * The outcome of [checkBeforeDraw].
     */
    sealed interface DispatchResult {
        /** No replacement fires — caller should perform a primitive draw. */
        data object None : DispatchResult

        /**
         * A replacement completed synchronously. The caller should **not**
         * perform a primitive draw this iteration; it should fold [state] and
         * [events] into its running state and proceed to the next iteration.
         */
        data class Replaced(val state: GameState, val events: List<GameEvent>) : DispatchResult

        /**
         * A replacement emitted a decision and the draw is paused. The caller
         * must return this result (possibly with its own events prepended).
         */
        data class Paused(val result: EffectResult) : DispatchResult

        /**
         * A [ModifyDrawAmount] replacement adjusted the draw count. The caller
         * should add [delta] to the remaining draws and re-check without drawing
         * a card this iteration.
         */
        data class Modified(val state: GameState, val delta: Int) : DispatchResult
    }

    /**
     * Run the three checks in order. Returns as soon as one fires.
     *
     * @param drawsLeftIncludingThis the number of draws remaining including the
     *     current one (i.e., `count - i` in an outer `for (i in 0 until count)`
     *     loop).
     * @param drawnCardsSoFar cards already drawn before this iteration, used
     *     for continuation state and partial-draw event flushing.
     * @param isDrawStep whether this is the active player's draw-step draw
     *     (vs a spell/ability draw).
     * @param skipStaticReplacement skip the Parallel Thoughts check; set by
     *     callers that pass the historical `skipPrompts = true` flag when
     *     resuming after a prior decision already handled replacements.
     */
    fun checkBeforeDraw(
        state: GameState,
        playerId: EntityId,
        drawsLeftIncludingThis: Int,
        drawnCardsSoFar: List<EntityId>,
        isDrawStep: Boolean,
        skipStaticReplacement: Boolean = false
    ): DispatchResult {
        val remainingDraws = drawsLeftIncludingThis - 1
        val event = PendingGameEvent.DrawPending(
            playerId = playerId,
            count = 1,
            remainingDraws = remainingDraws,
            isDrawStep = isDrawStep
        )
        when (val processorResult = processor.process(state, event)) {
            is ProcessorResult.Paused -> {
                // Player must choose between competing replacements (CR 616.1).
                return DispatchResult.Paused(
                    EffectResult.paused(processorResult.state, processorResult.decision)
                )
            }
            is ProcessorResult.Resolved -> {
                when (val outcome = processorResult.outcome) {
                    is ReplacementOutcome.Consumed -> {
                        // Draw prevented
                        return DispatchResult.Replaced(processorResult.state, emptyList())
                    }
                    is ReplacementOutcome.Replaced -> {
                        // A replacement effect should execute instead of drawing.
                        val ctx = processorResult.executionContext
                        if (ctx != null) {
                            return executeFromShield(
                                processorResult.state, playerId,
                                outcome.newEffect, ctx,
                                remainingDraws, isDrawStep
                            )
                        }
                        // Battlefield replacement (not a shield) — mark replaced without execution.
                        return DispatchResult.Replaced(processorResult.state, emptyList())
                    }
                    is ReplacementOutcome.Modified -> {
                        val modifiedEvent = outcome.modifiedEvent as? PendingGameEvent.DrawPending
                        val newRemaining = modifiedEvent?.remainingDraws ?: remainingDraws
                        val delta = newRemaining - remainingDraws
                        if (delta != 0) {
                            return DispatchResult.Modified(processorResult.state, delta)
                        }
                    }
                }
            }
            is ProcessorResult.Pass -> {
                /* No replacement matched — fall through to step 3. */
            }
        }

        // 3. Static draw replacement (Parallel Thoughts).
        if (!skipStaticReplacement) {
            val staticResult = checkStaticDrawReplacement(
                state, playerId, drawsLeftIncludingThis, drawnCardsSoFar, isDrawStep
            )
            if (staticResult != null) {
                return DispatchResult.Paused(staticResult)
            }
        }

        return DispatchResult.None
    }

    /**
     * Execute the replacement effect that came from a NextUse floating shield,
     * using the execution context built by the [ReplacementEffectProcessor].
     * Pushes a [DrawReplacementRemainingDrawsContinuation] if more draws remain.
     *
     * Returns the appropriate [DispatchResult].
     */
    private fun executeFromShield(
        processorState: GameState,
        playerId: EntityId,
        replacementEffect: Effect,
        context: EffectContext,
        remainingDraws: Int,
        isDrawStep: Boolean
    ): DispatchResult {
        val executor = effectExecutor ?: return DispatchResult.Replaced(processorState, emptyList())

        // Push remaining-draws continuation so the draw loop resumes after the pipeline
        var state = processorState
        if (remainingDraws > 0) {
            state = state.pushContinuation(
                DrawReplacementRemainingDrawsContinuation(
                    drawingPlayerId = playerId,
                    remainingDraws = remainingDraws,
                    isDrawStep = isDrawStep
                )
            )
        }

        // Execute the stored replacement effect.
        // The processor has already stamped the activeReplacementChain onto state
        // (containing all effects applied in this chain), so nested effect execution
        // won't re-trigger them. Clear the chain after execution.
        val pipelineResult = executor(state, replacementEffect, context)
        if (pipelineResult.isPaused) {
            // Clear chain on pause so subsequent draw iterations are unaffected.
            val clearedState = pipelineResult.state.copy(activeReplacementChain = null)
            return DispatchResult.Paused(
                EffectResult.paused(clearedState, pipelineResult.pendingDecision!!, pipelineResult.events)
            )
        }

        // Pipeline completed synchronously — pop remaining-draws continuation
        var resultState = pipelineResult.state
        if (remainingDraws > 0) {
            val (popped, stateAfterPop) = resultState.popContinuation()
            if (popped is DrawReplacementRemainingDrawsContinuation) {
                resultState = stateAfterPop
            }
        }

        // Clear the active replacement chain so subsequent draw iterations
        // (and any continuations) start with a clean slate.
        resultState = resultState.copy(activeReplacementChain = null)

        return DispatchResult.Replaced(resultState, pipelineResult.events)
    }

    /**
     * Check if [playerId] controls a permanent with an optional static draw
     * replacement effect (e.g., Parallel Thoughts). If so, returns a paused
     * [ExecutionResult] with a yes/no decision; otherwise returns `null`.
     *
     * This is exposed as part of the dispatcher's internal API (rather than
     * strictly private) so that the draw-step resume path can ask the question
     * in isolation without running the whole dispatch pipeline.
     */
    fun checkStaticDrawReplacement(
        state: GameState,
        playerId: EntityId,
        drawCount: Int,
        drawnCardsSoFar: List<EntityId>,
        isDrawStep: Boolean
    ): EffectResult? {
        val projected = state.projectedState
        val controlledPermanents = projected.getBattlefieldControlledBy(playerId)

        for (permanentId in controlledPermanents) {
            val container = state.getEntity(permanentId) ?: continue
            val replacementSource = container.get<ReplacementEffectSourceComponent>() ?: continue

            for (re in replacementSource.replacementEffects) {
                if (re !is ReplaceDrawWithEffect) continue
                if (!re.optional) continue

                val card = container.get<CardComponent>() ?: continue
                val linkedExile = container.get<LinkedExileComponent>()
                val pileCount = linkedExile?.exiledIds?.size ?: 0

                val decisionId = UUID.randomUUID().toString()
                val prompt = "Use ${card.name}? Put the top card of the exiled pile " +
                    "($pileCount cards remaining) into your hand instead of drawing?"

                val decision = YesNoDecision(
                    id = decisionId,
                    playerId = playerId,
                    prompt = prompt,
                    context = DecisionContext(
                        sourceId = permanentId,
                        sourceName = card.name,
                        phase = DecisionPhase.RESOLUTION
                    )
                )

                val continuation = StaticDrawReplacementContinuation(
                    decisionId = decisionId,
                    drawingPlayerId = playerId,
                    sourceId = permanentId,
                    sourceName = card.name,
                    replacementEffect = re.replacementEffect,
                    drawCount = drawCount,
                    isDrawStep = isDrawStep,
                    drawnCardsSoFar = drawnCardsSoFar
                )

                val stateWithDecision = state.withPendingDecision(decision)
                val stateWithContinuation = stateWithDecision.pushContinuation(continuation)

                return EffectResult.paused(
                    stateWithContinuation,
                    decision,
                    listOf(
                        DecisionRequestedEvent(
                            decisionId = decisionId,
                            playerId = playerId,
                            decisionType = "YES_NO",
                            prompt = decision.prompt
                        )
                    )
                )
            }
        }
        return null
    }

    private val processor = ReplacementEffectProcessor()
}
