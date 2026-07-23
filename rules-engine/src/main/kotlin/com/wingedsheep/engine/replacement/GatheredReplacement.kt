package com.wingedsheep.engine.replacement

import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ReplacementEffect
import kotlinx.serialization.Serializable

/**
 * A replacement effect that has been matched to a [PendingGameEvent] and
 * gathered from the battlefield or granted effects.
 *
 * @property identity Unique identity for tracking (CR 614.5 — prevent double-application)
 * @property effect The replacement effect that matched
 * @property sourceControllerId The controller of the source permanent
 * @property description Human-readable description of why this replacement applies
 */
@Serializable
data class GatheredReplacement(
    val identity: ReplacementEffectIdentity,
    @kotlinx.serialization.Contextual
    val effect: ReplacementEffect,
    val sourceControllerId: EntityId,
    val description: String,
    val floatingEffectIndex: Int? = null
)
