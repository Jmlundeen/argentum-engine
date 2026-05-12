package com.wingedsheep.engine.handlers.costs

import com.wingedsheep.engine.handlers.CostPaymentResult
import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.mechanics.mana.ManaPool
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AbilityCost

object RemoveCountersFromAmongFilteredPermanentsCostHandler {

    private val predicateEvaluator = PredicateEvaluator()

    fun canPay(
        state: GameState,
        cost: AbilityCost.RemoveCountersFromAmongFilteredPermanents,
        controllerId: EntityId
    ): Boolean {
        val counterType = resolveCounterType(cost.counterType)
        val context = PredicateContext(controllerId = controllerId)
        val projected = state.projectedState
        val total = state.entities.entries.sumOf { (entityId, container) ->
            if (container.get<ControllerComponent>()?.playerId != controllerId) return@sumOf 0
            if (!predicateEvaluator.matchesWithProjection(state, projected, entityId, cost.filter, context)) return@sumOf 0
            container.get<CountersComponent>()?.getCount(counterType) ?: 0
        }
        return total >= cost.count
    }

    fun pay(
        state: GameState,
        cost: AbilityCost.RemoveCountersFromAmongFilteredPermanents,
        controllerId: EntityId,
        manaPool: ManaPool,
        counterRemovals: Map<EntityId, Int>
    ): CostPaymentResult {
        val counterType = resolveCounterType(cost.counterType)
        val totalChosen = counterRemovals.values.sum()
        if (totalChosen != cost.count) {
            return CostPaymentResult.failure(
                "Counter removal total ($totalChosen) does not match required count (${cost.count})"
            )
        }
        val context = PredicateContext(controllerId = controllerId)
        val projected = state.projectedState
        var newState = state
        for ((permanentId, toRemove) in counterRemovals) {
            if (toRemove <= 0) continue
            val container = state.getEntity(permanentId)
                ?: return CostPaymentResult.failure("Permanent not found for counter removal: $permanentId")
            if (container.get<ControllerComponent>()?.playerId != controllerId) {
                return CostPaymentResult.failure("Cannot remove counters from a permanent you do not control")
            }
            if (!predicateEvaluator.matchesWithProjection(state, projected, permanentId, cost.filter, context)) {
                return CostPaymentResult.failure("Permanent does not match the required filter for counter removal")
            }
            val available = container.get<CountersComponent>()?.getCount(counterType) ?: 0
            if (available < toRemove) {
                return CostPaymentResult.failure(
                    "Permanent does not have enough ${cost.counterType} counters (need $toRemove, have $available)"
                )
            }
            newState = newState.updateEntity(permanentId) { c ->
                val counters = c.get<CountersComponent>() ?: CountersComponent()
                c.with(counters.withRemoved(counterType, toRemove))
            }
        }
        return CostPaymentResult.success(newState, manaPool)
    }

    private fun resolveCounterType(counterType: String): CounterType = when (counterType) {
        "+1/+1" -> CounterType.PLUS_ONE_PLUS_ONE
        "-1/-1" -> CounterType.MINUS_ONE_MINUS_ONE
        else -> CounterType.entries.firstOrNull {
            it.name.equals(counterType.uppercase().replace("-", "_").replace("+", "PLUS_").replace("/", "_"), ignoreCase = true)
        } ?: CounterType.CHARGE
    }
}
