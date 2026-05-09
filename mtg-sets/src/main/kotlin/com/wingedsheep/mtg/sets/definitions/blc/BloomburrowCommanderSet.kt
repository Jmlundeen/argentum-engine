package com.wingedsheep.mtg.sets.definitions.blc

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Bloomburrow Commander Set (2024)
 *
 * Commander preconstructed decks released alongside the Bloomburrow main set.
 *
 * Set Code: BLC
 * Release Date: August 2, 2024
 * Card Count: 312
 */
object BloomburrowCommanderSet : MtgSet {

    override val code = "BLC"
    override val displayName = "Bloomburrow Commander"
    override val sealedSupported = false

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val basicLands: List<CardDefinition> by lazy {
        CardDiscovery.findBasicLandsIn(CARDS_PACKAGE).map { it.copy(setCode = code) }
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.blc.cards"
}
