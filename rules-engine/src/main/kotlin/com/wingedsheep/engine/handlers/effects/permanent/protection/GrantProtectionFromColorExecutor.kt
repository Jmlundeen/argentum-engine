package com.wingedsheep.engine.handlers.effects.permanent.protection

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.KeywordGrantedEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.effects.GrantProtectionFromColorEffect
import kotlin.reflect.KClass

/**
 * Executor for [GrantProtectionFromColorEffect].
 *
 * "{W}: Target creature gains protection from red until end of turn." (Crimson Acolyte)
 *
 * The color is fixed at authoring time, so unlike the choose-a-color variant this executor
 * applies the protection immediately via a Layer 6 (ABILITY) floating effect carrying
 * [SerializableModification.GrantProtectionFromColor].
 */
class GrantProtectionFromColorExecutor : EffectExecutor<GrantProtectionFromColorEffect> {

    override val effectType: KClass<GrantProtectionFromColorEffect> =
        GrantProtectionFromColorEffect::class

    override fun execute(
        state: GameState,
        effect: GrantProtectionFromColorEffect,
        context: EffectContext
    ): EffectResult {
        val targetId = context.resolveTarget(effect.target, state)
            ?: return EffectResult.error(state, "No valid target for protection grant")

        val targetContainer = state.getEntity(targetId)
            ?: return EffectResult.error(state, "Target no longer exists")
        val cardComponent = targetContainer.get<CardComponent>()
            ?: return EffectResult.error(state, "Target is not a card")
        if (!state.getBattlefield().contains(targetId)) {
            return EffectResult.error(state, "Target is not on the battlefield")
        }

        val newState = state.addFloatingEffect(
            layer = Layer.ABILITY,
            modification = SerializableModification.GrantProtectionFromColor(effect.color.name),
            affectedEntities = setOf(targetId),
            duration = effect.duration,
            context = context
        )

        val sourceName = context.sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name } ?: "Unknown"
        val events = listOf(
            KeywordGrantedEvent(
                targetId = targetId,
                targetName = cardComponent.name,
                keyword = "Protection from ${effect.color.displayName.lowercase()}",
                sourceName = sourceName
            )
        )

        return EffectResult.success(newState, events)
    }
}
