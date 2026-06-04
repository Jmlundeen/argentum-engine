package com.wingedsheep.engine.handlers.effects.player

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.FlashGrantsThisTurnComponent
import com.wingedsheep.engine.state.components.player.PlayerEffectRemoval
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.effects.GrantFlashToSpellsEffect
import kotlin.reflect.KClass

/**
 * Records a turn-scoped flash permission on the target player.
 *
 * Adds an entry to the target's [FlashGrantsThisTurnComponent]; if the component is already
 * present (e.g. a second Borne Upon a Wind resolves the same turn), the new filter is appended
 * — multiple grants stack additively and all expire together at the duration's end.
 *
 * Sibling of [GrantSpellsCantBeCounteredExecutor].
 */
class GrantFlashToSpellsExecutor : EffectExecutor<GrantFlashToSpellsEffect> {

    override val effectType: KClass<GrantFlashToSpellsEffect> = GrantFlashToSpellsEffect::class

    override fun execute(
        state: GameState,
        effect: GrantFlashToSpellsEffect,
        context: EffectContext
    ): EffectResult {
        val targetId = context.resolvePlayerTarget(effect.target)
            ?: return EffectResult.error(state, "No valid target for grant-flash-to-spells effect")

        if (!state.turnOrder.contains(targetId)) {
            return EffectResult.error(state, "Target is not a player")
        }

        val removeOn = when (effect.duration) {
            is Duration.Permanent -> PlayerEffectRemoval.Permanent
            else -> PlayerEffectRemoval.EndOfTurn
        }

        val newState = state.updateEntity(targetId) { container ->
            val existing = container.get<FlashGrantsThisTurnComponent>()
            val mergedFilters = (existing?.filters ?: emptyList()) + effect.spellFilter
            container.with(FlashGrantsThisTurnComponent(filters = mergedFilters, removeOn = removeOn))
        }

        return EffectResult.success(newState)
    }
}
