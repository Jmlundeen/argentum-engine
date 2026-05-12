package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffects
import com.wingedsheep.engine.mechanics.layers.createFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.effects.BecomesALandAndNotACreatureUntilYourNextTurnEffect
import kotlin.reflect.KClass

class BecomesALandAndNotACreatureUntilYourNextTurnHandler : EffectExecutor<BecomesALandAndNotACreatureUntilYourNextTurnEffect> {

    override val effectType: KClass<BecomesALandAndNotACreatureUntilYourNextTurnEffect> =
        BecomesALandAndNotACreatureUntilYourNextTurnEffect::class

    override fun execute(
        state: GameState,
        effect: BecomesALandAndNotACreatureUntilYourNextTurnEffect,
        context: EffectContext
    ): EffectResult {
        val targetId = context.resolveTarget(effect.target)
            ?: return EffectResult.success(state)

        if (targetId !in state.getBattlefield()) {
            return EffectResult.success(state)
        }

        val affected = setOf(targetId)

        val addLand = state.createFloatingEffect(
            layer = Layer.TYPE,
            modification = SerializableModification.AddType("LAND"),
            affectedEntities = affected,
            duration = Duration.UntilYourNextTurn,
            context = context
        )

        val removeCreature = state.createFloatingEffect(
            layer = Layer.TYPE,
            modification = SerializableModification.RemoveType("CREATURE"),
            affectedEntities = affected,
            duration = Duration.UntilYourNextTurn,
            context = context
        )

        return EffectResult.success(state.addFloatingEffects(listOf(addLand, removeCreature)))
    }
}
