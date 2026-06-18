package com.wingedsheep.engine.handlers.effects.linkedexile

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.PermanentAttachedEvent
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.BattlefieldEntry
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.battlefield.AttachmentsComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.LinkedExileComponent
import com.wingedsheep.engine.state.components.battlefield.NotedExileComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.ReturnNotedExileTappedWithAurasEffect
import kotlin.reflect.KClass

/**
 * Executor for [ReturnNotedExileTappedWithAurasEffect] (Tawnos's Coffin's return).
 *
 * Reads the source's [NotedExileComponent] + [LinkedExileComponent], then:
 *  1. Returns the noted principal creature to the battlefield **tapped, under its owner's control**,
 *     with the noted number and kind of counters restored (CR: "tapped with the noted number and
 *     kind of counters on it").
 *  2. Returns the other linked-exiled cards (the creature's Auras) to the battlefield attached to
 *     that creature. Auras that can't legally re-attach are sent to their owners' graveyards by the
 *     unattached-Aura state-based action (CR 704.5m) — the "If you don't …" fallback.
 *
 * A no-op when nothing is noted or the principal is no longer in exile, so firing it from both the
 * becomes-untapped and the leaves-the-battlefield trigger is safe (whichever fires first does the
 * return; the other finds nothing). Clears the noted/linked bookkeeping on the source after a
 * successful return.
 */
class ReturnNotedExileTappedWithAurasExecutor : EffectExecutor<ReturnNotedExileTappedWithAurasEffect> {

    override val effectType: KClass<ReturnNotedExileTappedWithAurasEffect> =
        ReturnNotedExileTappedWithAurasEffect::class

    override fun execute(
        state: GameState,
        effect: ReturnNotedExileTappedWithAurasEffect,
        context: EffectContext
    ): EffectResult {
        val sourceId = context.sourceId ?: return EffectResult.success(state)
        val sourceContainer = state.getEntity(sourceId) ?: return EffectResult.success(state)

        val noted = sourceContainer.get<NotedExileComponent>() ?: return EffectResult.success(state)
        val linked = sourceContainer.get<LinkedExileComponent>()?.exiledIds ?: emptyList()

        fun inExile(id: EntityId): Boolean {
            val ownerId = ownerOf(state, id) ?: return false
            return id in state.getZone(ZoneKey(ownerId, Zone.EXILE))
        }

        val creatureId = noted.principalId
        if (!inExile(creatureId)) {
            // Principal already returned (or otherwise gone) — clear stale bookkeeping and stop.
            return EffectResult.success(clearBookkeeping(state, sourceId))
        }

        val events = mutableListOf<com.wingedsheep.engine.core.GameEvent>()
        var newState = state

        // 1. Return the creature tapped, under its owner's control, with the noted counters.
        val creatureOwner = ownerOf(newState, creatureId)
            ?: return EffectResult.success(clearBookkeeping(newState, sourceId))
        val creatureName = newState.getEntity(creatureId)?.get<CardComponent>()?.name ?: "Creature"

        newState = newState.removeFromZone(ZoneKey(creatureOwner, Zone.EXILE), creatureId)
        newState = newState.updateEntity(creatureId) { c ->
            var updated = c.with(ControllerComponent(creatureOwner)).with(TappedComponent)
            if (noted.notedCounters.isNotEmpty()) {
                updated = updated.with(CountersComponent(noted.notedCounters))
            }
            updated
        }
        newState = BattlefieldEntry.place(newState, creatureOwner, creatureId)
        events.add(
            ZoneChangeEvent(
                entityId = creatureId,
                entityName = creatureName,
                fromZone = Zone.EXILE,
                toZone = Zone.BATTLEFIELD,
                ownerId = creatureOwner
            )
        )

        // 2. Return the other linked-exiled cards (Auras) attached to the creature.
        val auraIds = linked.filter { it != creatureId && inExile(it) }
        for (auraId in auraIds) {
            val auraOwner = ownerOf(newState, auraId) ?: continue
            val auraName = newState.getEntity(auraId)?.get<CardComponent>()?.name ?: "Aura"

            newState = newState.removeFromZone(ZoneKey(auraOwner, Zone.EXILE), auraId)
            newState = newState.updateEntity(auraId) { c ->
                c.with(ControllerComponent(auraOwner)).with(AttachedToComponent(creatureId))
            }
            newState = BattlefieldEntry.place(newState, auraOwner, auraId)
            // Record the reverse attachment link on the creature.
            newState = newState.updateEntity(creatureId) { c ->
                val existing = c.get<AttachmentsComponent>()?.attachedIds ?: emptyList()
                c.with(AttachmentsComponent(existing + auraId))
            }
            events.add(
                ZoneChangeEvent(
                    entityId = auraId,
                    entityName = auraName,
                    fromZone = Zone.EXILE,
                    toZone = Zone.BATTLEFIELD,
                    ownerId = auraOwner
                )
            )
            events.add(
                PermanentAttachedEvent(
                    attachmentId = auraId,
                    attachmentName = auraName,
                    attachedToId = creatureId,
                    controllerId = auraOwner
                )
            )
        }

        // The unattached-Aura SBA (CR 704.5m) sends any Aura whose attachment is illegal to its
        // owner's graveyard — the "If you don't …" fallback — so no extra handling is needed here.

        newState = clearBookkeeping(newState, sourceId)
        return EffectResult.success(newState, events)
    }

    private fun ownerOf(state: GameState, id: EntityId): EntityId? {
        val container = state.getEntity(id) ?: return null
        return container.get<OwnerComponent>()?.playerId
            ?: container.get<CardComponent>()?.ownerId
    }

    private fun clearBookkeeping(state: GameState, sourceId: EntityId): GameState =
        state.updateEntity(sourceId) { c ->
            c.without<NotedExileComponent>().without<LinkedExileComponent>()
        }
}
