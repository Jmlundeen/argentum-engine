package com.wingedsheep.engine.replacement

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ReplacementEffect
import kotlinx.serialization.Serializable

/**
 * Describes a game event that *would* happen, before it occurs.
 *
 * These are constructed by effect executors before performing their action,
 * then passed to [ReplacementEffectProcessor] which checks all active
 * replacement effects against this event. The processor returns an outcome
 * that either modifies the event, replaces it with a different effect, or
 * consumes it entirely.
 *
 * This is deliberately distinct from [com.wingedsheep.engine.core.GameEvent]
 * (which records what *did* happen) — pending events describe hypothetical
 * future events that may never occur if replacement effects consume them.
 */
@Serializable
sealed interface PendingGameEvent {

    /**
     * The player most affected by this event — used to determine who chooses
     * between multiple competing replacement effects (CR 616.1).
     */
    val affectedPlayerId: EntityId

    /**
     * Draw event: a player is about to draw cards from their library.
     */
    @Serializable
    data class DrawPending(
        val playerId: EntityId,
        val count: Int,
        val remainingDraws: Int = 0,
        val isDrawStep: Boolean = false
    ) : PendingGameEvent {
        override val affectedPlayerId: EntityId get() = playerId
    }
}
