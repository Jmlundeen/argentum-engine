package com.wingedsheep.sdk.scripting.effects

import com.wingedsheep.sdk.scripting.text.TextReplacer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =============================================================================
// Auction / Open-Bid Effects
// =============================================================================

/**
 * Open life-bidding auction between the caster and the controller of a targeted spell.
 *
 * Implements the Mages' Contest shape: "You and target spell's controller bid life. You
 * start the bidding with a bid of 1. In turn order, each player may top the high bid. The
 * bidding ends if the high bid stands. The high bidder loses life equal to the high bid.
 * If you win the bidding, [onCasterWins]."
 *
 * The two participants alternate topping the bid. When a player declines to top, the bid
 * stands and the high bidder loses that much life. [onCasterWins] runs **only** when the
 * caster is the high bidder, with the original targets in context (so a `CounterEffect`
 * counters the spell that was bid over).
 *
 * @property onCasterWins Effect executed if the caster wins the auction (e.g. counter the spell)
 */
@SerialName("LifeAuction")
@Serializable
data class LifeAuctionEffect(
    val onCasterWins: Effect
) : Effect {
    override val description: String =
        "You and target spell's controller bid life. You start the bidding with a bid of 1. " +
            "In turn order, each player may top the high bid. The bidding ends if the high bid stands. " +
            "The high bidder loses life equal to the high bid. If you win the bidding, " +
            "${onCasterWins.description.replaceFirstChar(Char::lowercase)}."

    override fun applyTextReplacement(replacer: TextReplacer): Effect {
        val newOnCasterWins = onCasterWins.applyTextReplacement(replacer)
        return if (newOnCasterWins !== onCasterWins) copy(onCasterWins = newOnCasterWins) else this
    }
}
