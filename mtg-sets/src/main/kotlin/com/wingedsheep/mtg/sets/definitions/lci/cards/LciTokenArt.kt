package com.wingedsheep.mtg.sets.definitions.lci.cards

/**
 * Set-specific art for predefined tokens minted by The Lost Caverns of Ixalan cards.
 *
 * Predefined tokens (Treasure, Map, …) carry one canonical printing in `PredefinedTokens.kt`
 * shared engine-wide, but each set prints its own art. LCI cards pass these URIs to the
 * `imageUri` override on `Effects.CreateTreasure` / `Effects.CreateMapToken` so the created
 * token shows the in-set (Scryfall set `tlci`) printing.
 *
 * (Map is omitted: the predefined Map already uses the LCI printing, so no override is needed.)
 */
internal object LciTokenArt {
    /** LCI Treasure token (tlci #18). */
    const val TREASURE = "https://cards.scryfall.io/normal/front/3/d/3dfaedeb-f8ec-4f0e-b243-c850770a86f2.jpg?1783913602"
}
