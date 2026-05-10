package com.wingedsheep.mtg.sets.definitions.eoe

import com.wingedsheep.sdk.model.Printing
import com.wingedsheep.sdk.model.Rarity

/**
 * Per-printing rows for cards that are reprinted in Edge of Eternities but whose canonical
 * [com.wingedsheep.sdk.model.CardDefinition] lives elsewhere. Pure presentation data — no
 * script, no behavior. Engine identity is the card name.
 */
internal val EOEReprints: List<Printing> = listOf(
    Printing(
        oracleId = "f28b21a6-f7ce-437a-8c5b-0423cb55cefb",
        name = "Banishing Light",
        setCode = "EOE",
        collectorNumber = "6",
        scryfallId = "c45f11cd-a0aa-4d14-aa21-57f0969f3e2b",
        artist = "Rovina Cai",
        imageUri = "https://cards.scryfall.io/normal/front/c/4/c45f11cd-a0aa-4d14-aa21-57f0969f3e2b.jpg?1752946576",
        releaseDate = "2025-08-01",
        rarity = Rarity.COMMON,
    ),
)
