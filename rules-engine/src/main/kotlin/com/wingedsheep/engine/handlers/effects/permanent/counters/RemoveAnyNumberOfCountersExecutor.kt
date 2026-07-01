package com.wingedsheep.engine.handlers.effects.permanent.counters

import com.wingedsheep.engine.core.ChooseNumberDecision
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.DecisionPhase
import com.wingedsheep.engine.core.DecisionRequestedEvent
import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.RemoveAnyNumberOfCountersContinuation
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.effects.RemoveAnyNumberOfCountersEffect
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Executor for [RemoveAnyNumberOfCountersEffect].
 *
 * "Remove any number of counters from target creature you control." — and, with the effect's
 * `maxTotal` set, its budget-capped form "Remove up to N counters from target creature"
 * (Heartless Act).
 *
 * Enumerates each counter kind currently on the target and prompts the controller with a
 * [ChooseNumberDecision] per kind, sequentially. Each prompt's cap is that kind's count, further
 * clamped to the remaining `maxTotal` budget when one is set. The continuation applies each chosen
 * amount, decrements the budget, and queues the next prompt while counters (and budget) remain.
 */
class RemoveAnyNumberOfCountersExecutor : EffectExecutor<RemoveAnyNumberOfCountersEffect> {

    override val effectType: KClass<RemoveAnyNumberOfCountersEffect> = RemoveAnyNumberOfCountersEffect::class

    override fun execute(
        state: GameState,
        effect: RemoveAnyNumberOfCountersEffect,
        context: EffectContext
    ): EffectResult {
        val maxTotal = effect.maxTotal
        if (maxTotal != null && maxTotal <= 0) return EffectResult.success(state, emptyList())

        val targetId = context.resolveTarget(effect.target)
            ?: return EffectResult.success(state, emptyList())

        val targetEntity = state.getEntity(targetId)
            ?: return EffectResult.success(state, emptyList())

        val counters = targetEntity.get<CountersComponent>() ?: return EffectResult.success(state, emptyList())
        val present = counters.counters.entries
            .filter { it.value > 0 }
            .map { counterTypeToString(it.key) to it.value }

        if (present.isEmpty()) return EffectResult.success(state, emptyList())

        val targetName = targetEntity.get<CardComponent>()?.name ?: ""
        val sourceName = context.sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name }

        val (firstType, firstCount) = present.first()
        val remaining = present.drop(1)
        // Clamp the first prompt to the total budget when one is in force.
        val firstMax = maxTotal?.let { minOf(firstCount, it) } ?: firstCount

        val decisionId = UUID.randomUUID().toString()
        val decision = ChooseNumberDecision(
            id = decisionId,
            playerId = context.controllerId,
            prompt = "Remove how many $firstType counters from $targetName? (0-$firstMax)",
            context = DecisionContext(
                sourceId = context.sourceId,
                sourceName = sourceName,
                phase = DecisionPhase.RESOLUTION
            ),
            minValue = 0,
            maxValue = firstMax
        )

        val continuation = RemoveAnyNumberOfCountersContinuation(
            decisionId = decisionId,
            targetId = targetId,
            controllerId = context.controllerId,
            currentCounterType = firstType,
            currentMaxAmount = firstMax,
            remainingCounterTypes = remaining,
            targetName = targetName,
            sourceId = context.sourceId,
            sourceName = sourceName,
            remainingBudget = maxTotal
        )

        val newState = state
            .withPendingDecision(decision)
            .pushContinuation(continuation)

        val events = listOf(
            DecisionRequestedEvent(
                decisionId = decisionId,
                playerId = context.controllerId,
                decisionType = "CHOOSE_NUMBER",
                prompt = decision.prompt
            )
        )

        return EffectResult.paused(newState, decision, events)
    }
}
