package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.PermanentTypesEnteredBattlefieldThisTurnComponent
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.model.EntityId

/**
 * Records the entry of a permanent for per-player, per-turn "an X entered the battlefield
 * under your control this turn" tracking (e.g. Mechan Shieldmate).
 *
 * Cleared at end of turn by [com.wingedsheep.engine.core.CleanupPhaseManager].
 *
 * **All battlefield entries go through [BattlefieldEntry.place].** Callers that put an
 * entity into the battlefield zone must use that helper rather than calling
 * `state.addToZone(...)` directly — that's how this tracker stays in sync.
 *
 * Types are read from the **projected** state (post-layer), not the printed type line, so
 * a permanent that is an artifact by continuous effect at the moment of entry is recorded
 * as having entered as an artifact. The record itself is permanent for the rest of the
 * turn — once recorded, it stays true even if the permanent later leaves the battlefield
 * or changes type.
 */
object PermanentEntryTracker {

    /**
     * Record that [entityId] just entered the battlefield under [controllerId]. The
     * recorded card types are read from the projected state, which the caller is
     * responsible for having brought up to date (i.e. [entityId] must already be on the
     * battlefield with its identity components in place).
     */
    fun record(state: GameState, controllerId: EntityId, entityId: EntityId): GameState {
        val cardTypes = projectedCardTypes(state, entityId)
        if (cardTypes.isEmpty()) return state
        return state.updateEntity(controllerId) { container ->
            val existing = container.get<PermanentTypesEnteredBattlefieldThisTurnComponent>()
                ?: PermanentTypesEnteredBattlefieldThisTurnComponent()
            val merged = existing.cardTypes + cardTypes
            if (merged == existing.cardTypes) return@updateEntity container
            container.with(PermanentTypesEnteredBattlefieldThisTurnComponent(merged))
        }
    }

    private fun projectedCardTypes(state: GameState, entityId: EntityId): Set<CardType> {
        val typeNames = state.projectedState.getTypes(entityId)
        if (typeNames.isEmpty()) return emptySet()
        val byName = CardType.entries.associateBy { it.name }
        return typeNames.mapNotNull { byName[it] }.toSet()
    }
}
