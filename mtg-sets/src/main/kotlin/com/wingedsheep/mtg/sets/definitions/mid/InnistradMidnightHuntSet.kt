package com.wingedsheep.mtg.sets.definitions.mid

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Innistrad: Midnight Hunt (2021)
 *
 * Set Code: MID
 * Release Date: September 24, 2021
 */
object InnistradMidnightHuntSet : MtgSet {

    override val code = "MID"
    override val displayName = "Innistrad: Midnight Hunt"

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.mid.cards"
}
