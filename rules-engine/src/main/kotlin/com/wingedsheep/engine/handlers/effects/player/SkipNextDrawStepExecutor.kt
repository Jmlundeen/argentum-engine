package com.wingedsheep.engine.handlers.effects.player

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.SkipDrawStepComponent
import com.wingedsheep.sdk.scripting.effects.SkipNextDrawStepEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import kotlin.reflect.KClass

/**
 * Executor for [SkipNextDrawStepEffect].
 * Adds [SkipDrawStepComponent] to the target player so their next draw step is skipped.
 * The component is consumed by [com.wingedsheep.engine.core.DrawPhaseManager.performDrawStep].
 */
class SkipNextDrawStepExecutor : EffectExecutor<SkipNextDrawStepEffect> {

    override val effectType: KClass<SkipNextDrawStepEffect> = SkipNextDrawStepEffect::class

    override fun execute(
        state: GameState,
        effect: SkipNextDrawStepEffect,
        context: EffectContext
    ): EffectResult {
        val target = effect.target
        val targetPlayerId = when (target) {
            is EffectTarget.Controller -> context.controllerId
            is EffectTarget.PlayerRef -> {
                when (target.player) {
                    Player.You -> context.controllerId
                    Player.Opponent, Player.TargetOpponent -> context.opponentId
                        ?: return EffectResult.error(state, "No opponent found")
                    else -> return EffectResult.error(state, "Unsupported player reference for SkipNextDrawStepEffect")
                }
            }
            else -> return EffectResult.error(state, "Unsupported target for SkipNextDrawStepEffect")
        }

        val newState = state.updateEntity(targetPlayerId) { container ->
            container.with(SkipDrawStepComponent)
        }

        return EffectResult.success(newState)
    }
}
