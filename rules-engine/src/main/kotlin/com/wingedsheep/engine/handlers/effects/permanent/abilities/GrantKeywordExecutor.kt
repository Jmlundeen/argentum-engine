package com.wingedsheep.engine.handlers.effects.permanent.abilities

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.KeywordGrantedEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.effects.GrantKeywordEffect
import kotlin.reflect.KClass

/**
 * Executor for GrantKeywordEffect.
 * "Target creature gains [keyword] until end of turn"
 *
 * Works for any battlefield permanent, not just creatures: combat keywords (flying, trample,
 * …) are no-ops on noncreatures, but ability flags such as `DOESNT_UNTAP` are meaningful on a
 * noncreature artifact (Phyrexian Gremlins taps a target artifact and keeps it from untapping).
 * The granted keyword lands in the projected keyword set either way; the layer that consumes it
 * decides whether it does anything. So the executor only requires that the target be a permanent
 * on the battlefield, not specifically a creature.
 */
class GrantKeywordExecutor : EffectExecutor<GrantKeywordEffect> {

    override val effectType: KClass<GrantKeywordEffect> = GrantKeywordEffect::class

    override fun execute(
        state: GameState,
        effect: GrantKeywordEffect,
        context: EffectContext
    ): EffectResult {
        // Resolve the target permanent
        val targetId = context.resolveTarget(effect.target, state)
            ?: return EffectResult.error(state, "No valid target for keyword grant")

        // Verify target still exists as a battlefield permanent
        val targetContainer = state.getEntity(targetId)
            ?: return EffectResult.error(state, "Target permanent no longer exists")
        val cardComponent = targetContainer.get<CardComponent>()
            ?: return EffectResult.error(state, "Target is not a card")
        if (targetId !in state.getBattlefield()) {
            return EffectResult.error(state, "Target is no longer on the battlefield")
        }

        // Create a floating effect for the keyword grant
        val newState = state.addFloatingEffect(
            layer = Layer.ABILITY,
            modification = SerializableModification.GrantKeyword(effect.keyword),
            affectedEntities = setOf(targetId),
            duration = effect.duration,
            context = context
        )

        // Emit event for visualization
        val sourceName = context.sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name } ?: "Unknown"
        val events = listOf(
            KeywordGrantedEvent(
                targetId = targetId,
                targetName = cardComponent.name,
                keyword = effect.keyword.lowercase().replace('_', ' '),
                sourceName = sourceName
            )
        )

        return EffectResult.success(newState, events)
    }
}
