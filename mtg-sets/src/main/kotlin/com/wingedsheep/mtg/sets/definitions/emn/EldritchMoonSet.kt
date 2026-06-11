package com.wingedsheep.mtg.sets.definitions.emn

import com.wingedsheep.mtg.sets.discovery.CardDiscovery
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.MtgSet
import com.wingedsheep.sdk.model.Printing

/**
 * Eldritch Moon (2016)
 *
 * mtgish-tooling auto-generated seed: only the cards relocated here as their canonical
 * earliest printing. Intentionally incomplete relative to the official set.
 *
 * Set Code: EMN
 * Release Date: 2016-07-22
 */
object EldritchMoonSet : MtgSet {

    override val code = "EMN"
    override val displayName = "Eldritch Moon"
    override val releaseDate = "2016-07-22"
    override val block = "Shadows over Innistrad"
    override val sealedSupported = false
    override val incomplete = true

    override val cards: List<CardDefinition> by lazy {
        CardDiscovery.findIn(CARDS_PACKAGE)
    }

    override val basicLands: List<CardDefinition> by lazy {
        CardDiscovery.findBasicLandsIn(CARDS_PACKAGE, code)
    }

    override val printings: List<Printing> by lazy {
        CardDiscovery.findPrintingsIn(CARDS_PACKAGE)
    }

    private const val CARDS_PACKAGE = "com.wingedsheep.mtg.sets.definitions.emn.cards"
}
