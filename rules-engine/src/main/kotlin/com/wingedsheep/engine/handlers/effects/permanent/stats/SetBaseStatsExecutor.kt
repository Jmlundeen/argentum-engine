package com.wingedsheep.engine.handlers.effects.permanent.stats

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.DynamicAmountEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.Sublayer
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.scripting.effects.SetBaseStatsEffect
import kotlin.reflect.KClass

/**
 * Executor for [SetBaseStatsEffect].
 *
 * Evaluates the dynamic power/toughness amounts and creates a floating effect at
 * Layer.POWER_TOUGHNESS, Sublayer.SET_VALUES that sets whichever stats are non-null:
 *  - both     -> [SerializableModification.SetPowerToughness]
 *  - power    -> [SerializableModification.SetPower]    (toughness unchanged)
 *  - toughness-> [SerializableModification.SetToughness] (power unchanged)
 *
 * "Change this creature's base power to target creature's power." / "It has base power and
 * toughness 2/2 until your next turn."
 */
class SetBaseStatsExecutor(
    private val amountEvaluator: DynamicAmountEvaluator = DynamicAmountEvaluator()
) : EffectExecutor<SetBaseStatsEffect> {

    override val effectType: KClass<SetBaseStatsEffect> = SetBaseStatsEffect::class

    override fun execute(
        state: GameState,
        effect: SetBaseStatsEffect,
        context: EffectContext
    ): EffectResult {
        val targetId = context.resolveTarget(effect.target, state)
            ?: return EffectResult.success(state)

        // Verify target is on the battlefield
        if (targetId !in state.getBattlefield()) {
            return EffectResult.success(state)
        }

        val power = effect.power?.let { amountEvaluator.evaluate(state, it, context) }
        val toughness = effect.toughness?.let { amountEvaluator.evaluate(state, it, context) }

        val modification = when {
            power != null && toughness != null -> SerializableModification.SetPowerToughness(power, toughness)
            power != null -> SerializableModification.SetPower(power)
            toughness != null -> SerializableModification.SetToughness(toughness)
            else -> return EffectResult.success(state) // nothing to set
        }

        val newState = state.addFloatingEffect(
            layer = Layer.POWER_TOUGHNESS,
            modification = modification,
            affectedEntities = setOf(targetId),
            duration = effect.duration,
            context = context,
            sublayer = Sublayer.SET_VALUES
        )

        return EffectResult.success(newState)
    }
}
