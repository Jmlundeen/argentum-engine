package com.wingedsheep.engine.replacement

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.mechanics.layers.SerializableModification

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.ReplacementEffectSourceComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.DoubleDamage
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.ModifyDrawAmount
import com.wingedsheep.sdk.scripting.ModifyLifeGain
import com.wingedsheep.sdk.scripting.ModifyLifeLoss
import com.wingedsheep.sdk.scripting.ModifyMillAmount
import com.wingedsheep.sdk.scripting.PreventDamage
import com.wingedsheep.sdk.scripting.PreventDraw
import com.wingedsheep.sdk.scripting.ReplaceDrawWithEffect
import com.wingedsheep.sdk.scripting.ReplacementEffect
import com.wingedsheep.sdk.scripting.conditions.Condition
import com.wingedsheep.sdk.scripting.events.RecipientFilter
import com.wingedsheep.sdk.scripting.references.Player
import java.util.UUID
import kotlin.enums.enumEntries

/**
 * Priority group for classifying replacement effects per CR 616.1a-d.
 */
enum class ReplacementPriorityGroup {
    /** Self-replacement effects (CR 614.15 / 616.1a) — must be chosen first */
    SELF_REPLACEMENT,
    /** Control-changing effects (CR 616.1b) */
    CONTROL_CHANGE,
    /** Copy effects (CR 616.1c) */
    COPY,
    /** All other replacement effects (CR 616.1d) — affected player may choose any */
    ANY
}

/**
 * Result returned by [ReplacementEffectProcessor.process].
 */
sealed interface ProcessorResult {

    /** No replacement effects match — the original action should proceed unchanged. */
    data object Pass : ProcessorResult

    /**
     * Replacements are still being resolved. The caller should return this
     * paused result to the engine so it waits for player input.
     */
    data class Paused(val state: GameState, val decision: PendingDecision) : ProcessorResult

    /**
     * All matching replacements have been applied. The outcome tells the
     * caller what to do next.
     *
     * @property executionContext Context for executing the replacement effect,
     *   built from floating-shield data when the matched replacement came from
     *   a NextUse shield. Null for battlefield-originated replacements.
     */
    data class Resolved(
        val state: GameState,
        val outcome: ReplacementOutcome,
        val executionContext: EffectContext? = null
    ) : ProcessorResult
}

/**
 * Central processor for replacement effects.
 *
 * Gathers all active replacement effects from the battlefield and granted
 * effects, matches them against a [PendingGameEvent], and handles the full
 * CR 614-616 resolution pipeline including:
 *
 * - CR 614.5: Only applies each effect once per event chain
 * - CR 616.1: Player choice between competing same-group effects
 * - CR 616.1e: Repeated application until no more effects match
 */
class ReplacementEffectProcessor {

    private val conditionEvaluator = ConditionEvaluator()

    /**
     * Process a pending game event through the replacement effect pipeline.
     *
     * @param state Current game state
     * @param event The pending event to check replacements for
     * @param context Optional execution context for condition evaluation
     * @param alreadyApplied Set of identities already applied in this chain
     * @return The processor result
     */
    fun process(
        state: GameState,
        event: PendingGameEvent,
        context: EffectContext? = null,
        alreadyApplied: Set<ReplacementEffectIdentity> = emptySet()
    ): ProcessorResult {
        // Merge the active replacement chain (CR 614.5 — effect identity chain from
        // a parent Replaced outcome) so nested executions don't re-apply effects
        // that were already consumed earlier in the same chain.
        val fullAlreadyApplied = alreadyApplied + (state.activeReplacementChain ?: emptySet())
        return processInternal(state, event, context, fullAlreadyApplied)
    }

    /**
     * Internal recursive loop. Applies one replacement at a time and re-checks
     * for additional matches until none remain or we need player input.
     *
     * @return [ProcessorResult.Pass] if no replacements matched the current event,
     *         [ProcessorResult.Resolved] if all replacements resolved (carrying the
     *         final outcome), or [ProcessorResult.Paused] if player input is needed.
     */
    private fun processInternal(
        state: GameState,
        event: PendingGameEvent,
        context: EffectContext?,
        alreadyApplied: Set<ReplacementEffectIdentity>
    ): ProcessorResult {
        // 1. Gather all active replacement effects from the battlefield
        val gathered = gatherReplacements(state, event, context)

        // 2. Filter out already-applied effects (CR 614.5)
        val fresh = gathered.filter { it.identity !in alreadyApplied }

        if (fresh.isEmpty()) {
            return ProcessorResult.Pass
        }

        // 3. If only one matches, apply it directly
        if (fresh.size == 1) {
            return applySingle(state, fresh[0], event, alreadyApplied, context)
        }

        // 4. Multiple matches — group by priority order (CR 616.1a-d)
        val byGroup = fresh.groupBy { classifyPriorityGroup(it.effect, event) }

        for (group in enumEntries<ReplacementPriorityGroup>()) {
            val groupEffects = byGroup[group] ?: continue
            if (groupEffects.isEmpty()) continue

            if (group == ReplacementPriorityGroup.ANY && groupEffects.size > 1) {
                // CR 616.1d: player chooses. However, if all effects are structurally
                // identical (same card activated multiple times, e.g. Words cycle shields),
                // auto-apply the first — there's no meaningful choice to present.
                val first = groupEffects.first()
                if (groupEffects.all { it.effect == first.effect && it.description == first.description }) {
                    return applySingle(state, first, event, alreadyApplied, context)
                }
                // Different effects — player must choose (CR 616.1d)
                return presentChoice(state, event, groupEffects, alreadyApplied, context)
            }

            // Single effect or higher-priority group — auto-apply
            return applySingle(state, groupEffects.first(), event, alreadyApplied, context)
        }

        return ProcessorResult.Pass
    }

    /**
     * Apply a single replacement effect and recursively re-check if modified
     * (CR 616.1e — after applying an effect, repeat until no more apply).
     *
     * If the outcome is [Modified], the modified event is run through the
     * pipeline again. If no further replacements match (returns [Pass]), the
     * final modified event is returned as a [Resolved] outcome.
     */
    internal fun applySingle(
        state: GameState,
        gathered: GatheredReplacement,
        event: PendingGameEvent,
        alreadyApplied: Set<ReplacementEffectIdentity>,
        context: EffectContext?
    ): ProcessorResult {
        val outcome = createOutcome(gathered.effect, event, state)

        // Build execution context — prefer floating-shield context (Words cycle),
        // fall back to any passed-in context, or build one from battlefield source data.
        val execContext = gathered.floatingEffectIndex?.let {
            buildContextFromShield(state, it, gathered.sourceControllerId)
        } ?: context ?: EffectContext(
            controllerId = gathered.sourceControllerId,
            sourceId = gathered.identity.sourceEntityId
        )

        // Consume floating-effect shield if the replacement came from one
        // (e.g. Words cycle NextUse shields). This removes the floating effect
        // from state so it won't match future events.
        val consumedState = gathered.floatingEffectIndex?.let {
            consumeFloatingEffect(state, it)
        } ?: state

        val updatedAlreadyApplied = alreadyApplied + gathered.identity

        return when (outcome) {
            is ReplacementOutcome.Modified -> {
                // Stamp the updated chain on state so subsequent iterations of the
                // per-card draw loop don't re-apply the same ModifyDrawAmount (CR 614.5).
                val stateWithChain = consumedState.copy(activeReplacementChain = updatedAlreadyApplied)
                val recurseResult = processInternal(
                    stateWithChain, outcome.modifiedEvent, execContext, updatedAlreadyApplied
                )
                when (recurseResult) {
                    is ProcessorResult.Pass -> {
                        ProcessorResult.Resolved(stateWithChain, outcome, execContext)
                    }
                    else -> recurseResult
                }
            }
            is ReplacementOutcome.Replaced -> {
                // Stamp the updated chain on the returned state so nested effect
                // execution (e.g. a DrawCardsEffect produced by the replacement)
                // does not re-trigger effects already applied in this chain.
                val stateWithChain = consumedState.copy(activeReplacementChain = updatedAlreadyApplied)
                ProcessorResult.Resolved(stateWithChain, outcome, execContext)
            }
            is ReplacementOutcome.Consumed -> {
                ProcessorResult.Resolved(consumedState, outcome, execContext)
            }
        }
    }

    /**
     * Build an [EffectContext] from a NextUse floating shield's stored data,
     * so the caller can execute the replacement effect without searching the
     * original state for shield data.
     */
    private fun buildContextFromShield(
        state: GameState,
        floatingEffectIndex: Int,
        controllerId: EntityId
    ): EffectContext? {
        val fe = state.floatingEffects.getOrNull(floatingEffectIndex) ?: return null
        return fe.effect.modification.toEffectContext(controllerId)
    }

    /**
     * Remove a floating effect by its index in [GameState.floatingEffects].
     * Used to consume NextUse shields after their replacement effect is applied.
     */
    private fun consumeFloatingEffect(state: GameState, floatingEffectIndex: Int): GameState {
        if (floatingEffectIndex < 0 || floatingEffectIndex >= state.floatingEffects.size) return state
        val updatedEffects = state.floatingEffects.toMutableList()
        updatedEffects.removeAt(floatingEffectIndex)
        return state.copy(floatingEffects = updatedEffects)
    }

    /**
     * Present a choice between multiple competing replacement effects to
     * the affected player (CR 616.1d).
     */
    private fun presentChoice(
        state: GameState,
        event: PendingGameEvent,
        options: List<GatheredReplacement>,
        alreadyApplied: Set<ReplacementEffectIdentity>,
        context: EffectContext?
    ): ProcessorResult.Paused {
        val playerId = event.affectedPlayerId
        val decisionId = UUID.randomUUID().toString()

        val decision = ChooseOptionDecision(
            id = decisionId,
            playerId = playerId,
            prompt = "Choose which replacement effect to apply",
            context = DecisionContext(
                sourceId = context?.sourceId,
                sourceName = "Replacement effect choice",
                phase = DecisionPhase.RESOLUTION
            ),
            options = options.map { it.description },
            canCancel = false
        )

        val continuation = ReplacementChoiceContinuation(
            decisionId = decisionId,
            pendingEvent = event,
            options = options,
            alreadyApplied = alreadyApplied,
            context = context
        )

        val stateWithDecision = state.withPendingDecision(decision)
        val stateWithContinuation = stateWithDecision.pushContinuation(continuation)

        return ProcessorResult.Paused(stateWithContinuation, decision)
    }

    /**
     * Classify a replacement effect into a priority group (CR 616.1a-d).
     *
     * For Phase 1 (draw domain), all draw-related replacements are in
     * the ANY group. When expanded to other domains, self-replacement,
     * control-change, and copy groups will be classified here.
     */
    private fun classifyPriorityGroup(
        effect: ReplacementEffect,
        event: PendingGameEvent
    ): ReplacementPriorityGroup {
        return when (effect) {
            // Self-replacement effects (CR 614.15): effects on a permanent
            // that modify how that same permanent enters the battlefield.
            // Classified by checking if the effect is on the affected object
            // itself. For draw domain, this doesn't apply.
            is ReplaceDrawWithEffect,
            is PreventDraw,
            is ModifyDrawAmount -> ReplacementPriorityGroup.ANY

            // Future domains will add their own classification here
            else -> ReplacementPriorityGroup.ANY
        }
    }

    /**
     * Create a [ReplacementOutcome] by applying a single replacement effect
     * to a pending event.
     */
    private fun createOutcome(
        effect: ReplacementEffect,
        event: PendingGameEvent,
        state: GameState
    ): ReplacementOutcome {
        return when (event) {
            is PendingGameEvent.DrawPending -> applyDrawReplacement(effect, event, state)
        }
    }

    /**
     * Apply a draw-related replacement effect.
     */
    private fun applyDrawReplacement(
        effect: ReplacementEffect,
        event: PendingGameEvent.DrawPending,
        state: GameState
    ): ReplacementOutcome {
        return when (effect) {
            is ModifyDrawAmount -> {
                // Add the modifier to the remaining draws. At the per-card level
                // (checkBeforeDraw) this adds extra draws to the remaining-draws
                // continuation. At the announcement site (applyDrawAmountModifier),
                // the reader adds remainingDraws to originalCount (the event's
                // remainingDraws is 0 before modification, so it becomes `modifier`).
                val newRemaining = (event.remainingDraws + effect.modifier).coerceAtLeast(0)
                ReplacementOutcome.Modified(event.copy(remainingDraws = newRemaining))
            }
            is PreventDraw -> {
                // Consume the draw entirely
                ReplacementOutcome.Consumed
            }
            is ReplaceDrawWithEffect -> {
                // Replace with the specified effect
                ReplacementOutcome.Replaced(effect.replacementEffect)
            }
            else -> {
                // Unknown effect type for draw — pass through
                ReplacementOutcome.Modified(event)
            }
        }
    }

    /**
     * Gather all active replacement effects that match the given event.
     */
    fun gatherReplacements(
        state: GameState,
        event: PendingGameEvent,
        context: EffectContext? = null
    ): List<GatheredReplacement> {
        val results = mutableListOf<GatheredReplacement>()

        for (entityId in state.getBattlefield()) {
            val container = state.getEntity(entityId) ?: continue
            val replacementSource = container.get<ReplacementEffectSourceComponent>() ?: continue
            val controllerId = container.get<ControllerComponent>()?.playerId ?: continue

            for ((index, effect) in replacementSource.replacementEffects.withIndex()) {
                // Skip optional ReplaceDrawWithEffect from battlefield permanents —
                // these are handled by DrawReplacementDispatcher.checkStaticDrawReplacement
                // which presents the yes/no prompt to the player (Parallel Thoughts style).
                if (effect is ReplaceDrawWithEffect && effect.optional) continue

                val evalContext = context ?: EffectContext(
                    controllerId = controllerId,
                    sourceId = entityId
                )
                if (!matchesEvent(effect, event, controllerId, state, evalContext)) continue

                results.add(
                    GatheredReplacement(
                        identity = ReplacementEffectIdentity(
                            sourceEntityId = entityId,
                            effectIndex = index
                        ),
                        effect = effect,
                        sourceControllerId = controllerId,
                        description = effect.description
                    )
                )
            }
        }

        // Also scan floating effects for NextUse replacement shields
        // (Words cycle activated-ability shields are stored here, not on battlefield permanents).
        if (event is PendingGameEvent.DrawPending) {
            for ((index, fe) in state.floatingEffects.withIndex()) {
                if (fe.duration !is Duration.NextUse) continue
                if (event.playerId !in fe.effect.affectedEntities) continue
                val mod = fe.effect.modification
                val drawMod = mod as? SerializableModification.ReplaceDrawWithEffect ?: continue

                val sdkEffect = ReplaceDrawWithEffect(
                    replacementEffect = drawMod.replacementEffect,
                    appliesTo = EventPattern.DrawEvent()
                )
                val desc = (drawMod.sourceName ?: "").ifBlank { "Replace draw" }
                val sourceId = drawMod.sourceId ?: EntityId("")

                results.add(
                    GatheredReplacement(
                        identity = ReplacementEffectIdentity(
                            sourceEntityId = sourceId,
                            effectIndex = -(index + 1000)  // negative range to avoid collision
                        ),
                        effect = sdkEffect,
                        sourceControllerId = event.playerId,
                        description = desc,
                        floatingEffectIndex = index
                    )
                )
            }
        }

        return results
    }

    /**
     * Check whether a replacement effect matches a pending event using its
     * [ReplacementEffect.appliesTo] pattern and any [ReplacementEffect.restrictions].
     */
    private fun matchesEvent(
        effect: ReplacementEffect,
        event: PendingGameEvent,
        sourceControllerId: EntityId,
        state: GameState,
        context: EffectContext? = null
    ): Boolean {
        val appliesTo = effect.appliesTo

        val baseMatch = when (event) {
            is PendingGameEvent.DrawPending -> {
                when (appliesTo) {
                    is EventPattern.DrawEvent -> {
                        val condition = appliesTo.condition
                        if (condition != null) {
                            if (!conditionEvaluator.evaluate(state, condition, context ?: EffectContext(sourceId = null, controllerId = sourceControllerId))) {
                                return false
                            }
                        }
                        matchesPlayerFilter(appliesTo.player, event.playerId, sourceControllerId)
                    }
                    else -> false
                }
            }
        }

        if (!baseMatch) return false

        // Evaluate restrictions if present (e.g., ModifyDrawAmount.restrictions)
        val restrictions = getRestrictions(effect)
        if (restrictions.isNotEmpty()) {
            val evalContext = context ?: EffectContext(
                sourceId = null,
                controllerId = sourceControllerId
            )
            return restrictions.all { condition ->
                conditionEvaluator.evaluate(state, condition, evalContext)
            }
        }

        return true
    }

    /**
     * Check whether a player filter matches the drawing player relative to
     * the replacement source's controller.
     */
    private fun matchesPlayerFilter(
        player: Player,
        affectedPlayerId: EntityId,
        sourceControllerId: EntityId
    ): Boolean {
        return when (player) {
            Player.Each -> true
            Player.You -> affectedPlayerId == sourceControllerId
            Player.EachOpponent -> affectedPlayerId != sourceControllerId
            else -> false
        }
    }

    /**
     * Extract the [Condition] restrictions from a [ReplacementEffect], if any.
     *
     * Not all replacement effects have restrictions — only those that refine their
     * applicability with additional conditions (e.g., [ModifyDrawAmount.restrictions],
     * [com.wingedsheep.sdk.scripting.PreventDamage.restrictions]). This helper
     * provides a uniform interface for the matching pipeline.
     */
    private fun getRestrictions(effect: ReplacementEffect): List<Condition> {
        return when (effect) {
            is ModifyDrawAmount -> effect.restrictions
            is PreventDamage -> effect.restrictions
            is DoubleDamage -> effect.restrictions
            is ModifyLifeGain -> effect.restrictions
            is ModifyLifeLoss -> effect.restrictions
            is ModifyMillAmount -> effect.restrictions
            else -> emptyList()
        }
    }
}
