package com.wingedsheep.sdk.scripting.effects

import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.text.TextReplacer
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =============================================================================
// Power/Toughness Modification Effects
// =============================================================================

/**
 * Modify power/toughness effect.
 * "Target creature gets +X/+Y until end of turn"
 *
 * Supports both fixed and dynamic amounts via [DynamicAmount].
 */
@SerialName("ModifyStats")
@Serializable
data class ModifyStatsEffect(
    val powerModifier: DynamicAmount,
    val toughnessModifier: DynamicAmount,
    val target: EffectTarget,
    val duration: Duration = Duration.EndOfTurn
) : Effect {
    constructor(powerModifier: Int, toughnessModifier: Int, target: EffectTarget, duration: Duration = Duration.EndOfTurn) :
        this(DynamicAmount.Fixed(powerModifier), DynamicAmount.Fixed(toughnessModifier), target, duration)

    override val description: String = buildString {
        append("${target.description} gets ")
        val pDesc = powerModifier.let {
            if (it is DynamicAmount.Fixed) {
                if (it.amount >= 0) "+${it.amount}" else "${it.amount}"
            } else {
                it.description
            }
        }
        val tDesc = toughnessModifier.let {
            if (it is DynamicAmount.Fixed) {
                if (it.amount >= 0) "+${it.amount}" else "${it.amount}"
            } else {
                it.description
            }
        }
        append("$pDesc/$tDesc")
        if (duration.description.isNotEmpty()) append(" ${duration.description}")
    }

    override fun runtimeDescription(resolver: (DynamicAmount) -> Int): String = buildString {
        append("${target.description} gets ")
        fun fmt(v: Int) = if (v >= 0) "+$v" else "$v"
        append("${fmt(resolver(powerModifier))}/${fmt(resolver(toughnessModifier))}")
        if (duration.description.isNotEmpty()) append(" ${duration.description}")
    }

    override fun applyTextReplacement(replacer: TextReplacer): Effect {
        val newPower = powerModifier.applyTextReplacement(replacer)
        val newToughness = toughnessModifier.applyTextReplacement(replacer)
        return if (newPower !== powerModifier || newToughness !== toughnessModifier)
            copy(powerModifier = newPower, toughnessModifier = newToughness) else this
    }
}

/**
 * Set a creature's base power and/or toughness to specific values via a one-shot floating
 * continuous effect at Layer.POWER_TOUGHNESS, Sublayer.SET_VALUES (CR 613.4b, layer 7b),
 * evaluated at resolution time.
 *
 * A `null` [power] or [toughness] leaves that stat unchanged, so this single atom expresses every
 * shape the engine needs:
 *  - power only ("change this creature's base power to target creature's power") — toughness null,
 *  - both ("has base power and toughness 2/2 until your next turn", Azure Beastbinder),
 *  - toughness only.
 * Both are [DynamicAmount] (the asymmetry the two predecessors carried — power-only was dynamic,
 * power-and-toughness was fixed Int — is gone), so either value can be read from game state.
 *
 * This is the one-shot, resolution-time *set*. It is deliberately distinct from:
 *  - [ModifyStatsEffect] — a +N/+N *modifier* (layer 7c), not a set.
 *  - the `SetBasePowerToughness*Static` characteristic-defining abilities — applied for as long as
 *    a static ability is active, not a one-shot floating effect.
 *  - the projector's `SetPowerToughnessDynamic` modification — re-evaluated per affected entity at
 *    projection time (mass animate), not once at resolution.
 *
 * Reach it through the [com.wingedsheep.sdk.dsl.Effects] `SetBasePower` / `SetBasePowerAndToughness`
 * facades rather than constructing it directly.
 *
 * @property target The creature whose base stats are being set
 * @property power The value to set base power to (evaluated at resolution time), or null to leave it
 * @property toughness The value to set base toughness to, or null to leave it
 * @property duration How long the effect lasts (typically Permanent for indefinite effects)
 */
@SerialName("SetBaseStats")
@Serializable
data class SetBaseStatsEffect(
    val target: EffectTarget,
    val power: DynamicAmount? = null,
    val toughness: DynamicAmount? = null,
    val duration: Duration = Duration.Permanent
) : Effect {
    override val description: String = buildString {
        when {
            power != null && toughness != null ->
                append("${target.description} has base power and toughness ${power.description}/${toughness.description}")
            power != null ->
                append("Change ${target.description}'s base power to ${power.description}")
            toughness != null ->
                append("Change ${target.description}'s base toughness to ${toughness.description}")
            else -> append("Set ${target.description}'s base stats")
        }
        if (duration.description.isNotEmpty()) append(" ${duration.description}")
    }

    override fun applyTextReplacement(replacer: TextReplacer): Effect {
        val newPower = power?.applyTextReplacement(replacer)
        val newToughness = toughness?.applyTextReplacement(replacer)
        return if (newPower !== power || newToughness !== toughness)
            copy(power = newPower, toughness = newToughness) else this
    }
}
