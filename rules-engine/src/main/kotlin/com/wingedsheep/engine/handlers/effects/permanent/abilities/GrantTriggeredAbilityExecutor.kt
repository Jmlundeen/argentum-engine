package com.wingedsheep.engine.handlers.effects.permanent.abilities

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.event.GrantedTriggeredAbility
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.effects.GrantTriggeredAbilityEffect
import kotlin.reflect.KClass

/**
 * Executor for GrantTriggeredAbilityEffect.
 * "Target creature gains '[triggered ability]' until end of turn"
 *
 * Adds the triggered ability to GameState.grantedTriggeredAbilities,
 * where TriggerDetector will find it when checking for triggers on
 * that entity.
 */
class GrantTriggeredAbilityExecutor : EffectExecutor<GrantTriggeredAbilityEffect> {

    override val effectType: KClass<GrantTriggeredAbilityEffect> =
        GrantTriggeredAbilityEffect::class

    override fun execute(
        state: GameState,
        effect: GrantTriggeredAbilityEffect,
        context: EffectContext
    ): EffectResult {
        val targetId = context.resolveTarget(effect.target)
            ?: return EffectResult.error(state, "No valid target for triggered ability grant")

        val targetContainer = state.getEntity(targetId)
            ?: return EffectResult.error(state, "Target no longer exists")
        targetContainer.get<CardComponent>()
            ?: return EffectResult.error(state, "Target is not a card")
        if (!state.getBattlefield().contains(targetId)) {
            return EffectResult.error(state, "Target is not on the battlefield")
        }
        // Read projected state so type-changing floating effects earlier in the same
        // composite (e.g., AnimateLand on a land becoming a creature) are visible here.
        if (!state.projectedState.isCreature(targetId)) {
            return EffectResult.error(state, "Target is not a creature")
        }

        val grant = GrantedTriggeredAbility(
            entityId = targetId,
            ability = effect.ability,
            duration = effect.duration
        )

        val newState = state.copy(
            grantedTriggeredAbilities = state.grantedTriggeredAbilities + grant
        )

        return EffectResult.success(newState)
    }
}
