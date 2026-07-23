package com.wingedsheep.engine.replacement

import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/**
 * Uniquely identifies a specific replacement effect instance on the battlefield.
 *
 * Replacement effects are tracked by this identity to ensure each effect
 * only applies once per event chain (CR 614.5 — a replacement effect doesn't
 * invoke itself repeatedly). This is particularly important when an entity
 * has two copies of the same ability (e.g., two Doubling Seasons), each of
 * which should apply exactly once.
 *
 * @property sourceEntityId The permanent that grants this replacement effect
 * @property sourceAbilityIndex Which ability on the permanent (0-based, or
 *           null for class-level abilities)
 * @property effectIndex Which replacement effect within that ability (0-based)
 */
@Serializable
data class ReplacementEffectIdentity(
    val sourceEntityId: EntityId,
    val sourceAbilityIndex: Int? = null,
    val effectIndex: Int = 0
)
