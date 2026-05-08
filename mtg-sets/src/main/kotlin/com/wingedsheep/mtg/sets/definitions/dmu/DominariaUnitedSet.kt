package com.wingedsheep.mtg.sets.definitions.dmu

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet

/**
 * Dominaria United (2022)
 *
 * Set Code: DMU
 * Release Date: September 9, 2022
 */
object DominariaUnitedSet : MtgSet {

    override val code = "DMU"
    override val displayName = "Dominaria United"

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.dmu.cards"
}
