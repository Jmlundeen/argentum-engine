package com.wingedsheep.mtg.sets.definitions.dft

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Aetherdrift (2025)
 *
 * Set Code: DFT
 * Release Date: February 14, 2025
 */
object AetherdriftSet : MtgSet {

    override val code = "DFT"
    override val displayName = "Aetherdrift"

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.dft.cards"
}
