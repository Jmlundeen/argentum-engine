package com.wingedsheep.engine.handlers.effects.player

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.LifeAuctionContinuation
import com.wingedsheep.engine.core.LifeAuctionStage
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.handlers.DecisionHandler
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.effects.LifeAuctionEffect
import com.wingedsheep.sdk.scripting.effects.LoseLifeEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import kotlin.reflect.KClass

/**
 * Executor for [LifeAuctionEffect] (Mages' Contest).
 *
 * Sets up the open life-bidding auction between the caster and the controller of the
 * targeted spell, then hands stepping/resolution to [LifeAuctionLogic] (shared with the
 * resumer). The caster opens with a bid of 1; the spell's controller is asked first.
 */
class LifeAuctionExecutor(
    private val executeEffect: (GameState, Effect, EffectContext) -> EffectResult
) : EffectExecutor<LifeAuctionEffect> {

    override val effectType: KClass<LifeAuctionEffect> = LifeAuctionEffect::class

    override fun execute(
        state: GameState,
        effect: LifeAuctionEffect,
        context: EffectContext
    ): EffectResult {
        val casterId = context.controllerId

        // The targeted spell. If it's already gone (e.g. removed from the stack), there's
        // nothing to bid over — the auction simply does nothing.
        val spellId = (context.targets.firstOrNull() as? ChosenTarget.Spell)?.spellEntityId
            ?: return EffectResult.success(state)
        if (!state.stack.contains(spellId)) return EffectResult.success(state)

        val spellController = state.getEntity(spellId)?.get<SpellOnStackComponent>()?.casterId
            ?: return EffectResult.success(state)

        val sourceName = context.sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name }

        // You open the bidding with a bid of 1.
        return EffectResult.from(
            if (spellController == casterId) {
                // You also control the targeted spell — you're the only bidder and win at 1.
                LifeAuctionLogic.resolve(
                    state, casterId, highBidder = casterId, highBid = 1,
                    onCasterWins = effect.onCasterWins, targets = context.targets,
                    sourceId = context.sourceId, executeEffect = executeEffect
                )
            } else {
                LifeAuctionLogic.advance(
                    state, casterId, spellId, highBidder = casterId, highBid = 1,
                    bidderToAsk = spellController, onCasterWins = effect.onCasterWins,
                    targets = context.targets, sourceId = context.sourceId, sourceName = sourceName,
                    executeEffect = executeEffect
                )
            }
        )
    }
}

/**
 * Shared stepping/resolution logic for the life auction, used by both [LifeAuctionExecutor]
 * (which sets up the first decision) and the continuation resumer (which drives subsequent
 * decisions and resolution).
 *
 * Bids are capped at the bidding player's current life total — a player who cannot exceed
 * the high bid simply can't top it and the auction ends.
 */
object LifeAuctionLogic {

    private val decisionHandler = DecisionHandler()

    private fun lifeOf(state: GameState, playerId: EntityId): Int =
        state.getEntity(playerId)?.get<LifeTotalComponent>()?.life ?: 0

    /**
     * Ask [bidderToAsk] whether to top the current [highBid], or resolve the auction if they
     * cannot exceed it.
     */
    fun advance(
        state: GameState,
        casterId: EntityId,
        spellId: EntityId,
        highBidder: EntityId,
        highBid: Int,
        bidderToAsk: EntityId,
        onCasterWins: Effect,
        targets: List<ChosenTarget>,
        sourceId: EntityId?,
        sourceName: String?,
        executeEffect: (GameState, Effect, EffectContext) -> EffectResult
    ): ExecutionResult {
        // A player can only top if they can bid strictly more than the high bid.
        if (lifeOf(state, bidderToAsk) <= highBid) {
            return resolve(state, casterId, highBidder, highBid, onCasterWins, targets, sourceId, executeEffect)
        }

        val decisionResult = decisionHandler.createYesNoDecision(
            state = state,
            playerId = bidderToAsk,
            sourceId = sourceId,
            sourceName = sourceName,
            prompt = "The high bid is $highBid life. Pay more life to top it?",
            yesText = "Top the bid",
            noText = "Pass"
        )

        val continuation = LifeAuctionContinuation(
            decisionId = decisionResult.pendingDecision!!.id,
            casterId = casterId,
            spellId = spellId,
            highBidder = highBidder,
            highBid = highBid,
            bidderToAsk = bidderToAsk,
            stage = LifeAuctionStage.AWAITING_TOP_DECISION,
            onCasterWins = onCasterWins,
            targets = targets,
            sourceId = sourceId,
            sourceName = sourceName
        )

        return ExecutionResult.paused(
            decisionResult.state.pushContinuation(continuation),
            decisionResult.pendingDecision,
            decisionResult.events
        )
    }

    /**
     * Ask [LifeAuctionContinuation.bidderToAsk] for the amount to top the high bid by.
     */
    fun askAmount(state: GameState, continuation: LifeAuctionContinuation): ExecutionResult {
        val maxBid = lifeOf(state, continuation.bidderToAsk)
        val decisionResult = decisionHandler.createNumberDecision(
            state = state,
            playerId = continuation.bidderToAsk,
            sourceId = continuation.sourceId,
            sourceName = continuation.sourceName,
            prompt = "Bid more than ${continuation.highBid} life (up to $maxBid)",
            minValue = continuation.highBid + 1,
            maxValue = maxBid
        )

        val newContinuation = continuation.copy(
            decisionId = decisionResult.pendingDecision!!.id,
            stage = LifeAuctionStage.AWAITING_BID_AMOUNT
        )

        return ExecutionResult.paused(
            decisionResult.state.pushContinuation(newContinuation),
            decisionResult.pendingDecision,
            decisionResult.events
        )
    }

    /**
     * End the auction: the high bidder loses [highBid] life, and if the caster is the high
     * bidder, [onCasterWins] runs against the original [targets].
     */
    fun resolve(
        state: GameState,
        casterId: EntityId,
        highBidder: EntityId,
        highBid: Int,
        onCasterWins: Effect,
        targets: List<ChosenTarget>,
        sourceId: EntityId?,
        executeEffect: (GameState, Effect, EffectContext) -> EffectResult
    ): ExecutionResult {
        val events = mutableListOf<GameEvent>()

        // The high bidder loses life equal to the high bid (routed through the life-loss
        // executor so prevention/replacement effects apply uniformly).
        val loseLifeContext = EffectContext(sourceId = sourceId, controllerId = highBidder, opponentId = null)
        val lifeResult = executeEffect(
            state,
            LoseLifeEffect(DynamicAmount.Fixed(highBid), EffectTarget.PlayerRef(Player.You)),
            loseLifeContext
        )
        if (lifeResult.error != null) return lifeResult.toExecutionResult()
        var currentState = lifeResult.state
        events.addAll(lifeResult.events)

        // If you win the bidding, apply the payoff (counter the spell) against the targets.
        if (highBidder == casterId) {
            val winContext = EffectContext(
                sourceId = sourceId,
                controllerId = casterId,
                opponentId = null,
                targets = targets
            )
            val winResult = executeEffect(currentState, onCasterWins, winContext)
            currentState = winResult.state
            events.addAll(winResult.events)
            if (winResult.pendingDecision != null) {
                return ExecutionResult.paused(currentState, winResult.pendingDecision, events)
            }
            if (winResult.error != null) return winResult.toExecutionResult()
        }

        return ExecutionResult(currentState, events)
    }
}
