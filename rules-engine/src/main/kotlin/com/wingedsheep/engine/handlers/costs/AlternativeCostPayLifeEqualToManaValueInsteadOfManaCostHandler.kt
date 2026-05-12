package com.wingedsheep.engine.handlers.costs

import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.LifeChangedEvent
import com.wingedsheep.engine.core.LifeChangeReason
import com.wingedsheep.engine.handlers.effects.DamageUtils
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.sdk.model.EntityId

/**
 * Reusable primitive for the alternative-cost variant where a caster pays life equal to a
 * spell's mana value rather than paying its mana cost.
 *
 * Future cards can opt into this primitive via the existing alternative-cost selection flow
 * by including [com.wingedsheep.sdk.scripting.AdditionalCost.PayLife] in their
 * [com.wingedsheep.sdk.scripting.SelfAlternativeCost.additionalCosts] (with an empty mana
 * component) or by a permanent granting an analogous cost replacement.
 */
object AlternativeCostPayLifeEqualToManaValueInsteadOfManaCostHandler {

    /**
     * Returns an error message when [playerId] cannot pay [amount] life, null when payable.
     * Life payment is illegal if it would reduce life to zero or below.
     */
    fun canPay(state: GameState, playerId: EntityId, amount: Int): String? {
        val life = state.getEntity(playerId)?.get<LifeTotalComponent>()?.life ?: 0
        return if (life <= amount) "Not enough life to pay $amount life: have $life" else null
    }

    /**
     * Deducts [amount] life from [playerId] and returns the updated state and events.
     * Uses [LifeChangeReason.PAYMENT] so "you paid life" conditions distinguish cost
     * payments from damage or regular life loss.
     */
    fun apply(state: GameState, playerId: EntityId, amount: Int): Pair<GameState, List<GameEvent>> {
        val currentLife = state.getEntity(playerId)?.get<LifeTotalComponent>()?.life ?: 0
        val newLife = currentLife - amount
        var newState = state.updateEntity(playerId) { container ->
            container.with(LifeTotalComponent(newLife))
        }
        newState = DamageUtils.markLifeLostThisTurn(newState, playerId)
        return newState to listOf(LifeChangedEvent(playerId, currentLife, newLife, LifeChangeReason.PAYMENT))
    }
}
