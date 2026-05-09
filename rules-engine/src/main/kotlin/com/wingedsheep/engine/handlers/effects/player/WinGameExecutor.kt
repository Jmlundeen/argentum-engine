package com.wingedsheep.engine.handlers.effects.player

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.GameEndReason
import com.wingedsheep.engine.core.PlayerLostEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.GrantsCantLoseGameComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.player.LossReason
import com.wingedsheep.engine.state.components.player.PlayerLostComponent
import com.wingedsheep.sdk.scripting.effects.WinGameEffect
import kotlin.reflect.KClass

/**
 * Executor for [WinGameEffect].
 *
 * Marks every opponent of the target as having lost the game; [GameEndCheck]
 * then resolves `gameOver` with the target as the surviving (winning) player.
 * Skips opponents who already lost or who control a permanent that grants
 * "can't lose the game".
 */
class WinGameExecutor : EffectExecutor<WinGameEffect> {

    override val effectType: KClass<WinGameEffect> = WinGameEffect::class

    override fun execute(
        state: GameState,
        effect: WinGameEffect,
        context: EffectContext
    ): EffectResult {
        val winnerId = context.resolvePlayerTarget(effect.target)
            ?: return EffectResult.error(state, "No target player for WinGameEffect")

        val opponentIds = state.turnOrder.filter { it != winnerId }
        if (opponentIds.isEmpty()) return EffectResult.success(state)

        var newState = state
        val events = mutableListOf<com.wingedsheep.engine.core.GameEvent>()

        for (opponentId in opponentIds) {
            val container = newState.getEntity(opponentId) ?: continue
            if (container.has<PlayerLostComponent>()) continue

            val cantLose = newState.getBattlefield().any { entityId ->
                val c = newState.getEntity(entityId) ?: return@any false
                c.has<GrantsCantLoseGameComponent>() &&
                    c.get<ControllerComponent>()?.playerId == opponentId
            }
            if (cantLose) continue

            newState = newState.updateEntity(opponentId) { c ->
                c.with(PlayerLostComponent(LossReason.CARD_EFFECT))
            }
            events.add(PlayerLostEvent(opponentId, GameEndReason.CARD_EFFECT, effect.message))
        }

        return EffectResult.success(newState, events)
    }
}
