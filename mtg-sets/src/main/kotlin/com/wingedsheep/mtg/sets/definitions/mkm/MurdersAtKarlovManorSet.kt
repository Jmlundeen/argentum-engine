package com.wingedsheep.mtg.sets.definitions.mkm

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Murders at Karlov Manor (2024)
 *
 * Set Code: MKM
 * Release Date: February 9, 2024
 */
object MurdersAtKarlovManorSet : MtgSet {

    override val code = "MKM"
    override val displayName = "Murders at Karlov Manor"

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.mkm.cards"
}
