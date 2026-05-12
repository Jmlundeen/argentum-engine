package com.wingedsheep.engine.handlers

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.AbilityActivatedThisTurnComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AbilityId

class ActivateOnlyOnceEachTurnRestrictionHandler {

    fun isActivationPermitted(state: GameState, sourceId: EntityId, abilityId: AbilityId): Boolean {
        val tracker = state.getEntity(sourceId)?.get<AbilityActivatedThisTurnComponent>()
        return tracker == null || !tracker.hasActivated(abilityId)
    }

    fun recordActivation(state: GameState, sourceId: EntityId, abilityId: AbilityId): GameState {
        if (state.getEntity(sourceId) == null) return state
        return state.updateEntity(sourceId) { container ->
            val tracker = container.get<AbilityActivatedThisTurnComponent>() ?: AbilityActivatedThisTurnComponent()
            container.with(tracker.withActivated(abilityId))
        }
    }
}
