package com.wingedsheep.mtg.sets.definitions.fdn.cards

import com.wingedsheep.sdk.model.Printing
import com.wingedsheep.sdk.model.Rarity

/**
 * Diamond Mare reprint in FDN.
 *
 * The canonical [com.wingedsheep.sdk.model.CardDefinition] lives in M19's `cards/` package
 * (the card's earliest real printing). This file contributes only the FDN presentation row.
 */
val DiamondMareReprint = Printing(
    oracleId = "9a440122-a015-4af2-b270-37f019884458",
    name = "Diamond Mare",
    setCode = "FDN",
    collectorNumber = "672",
    scryfallId = "721463ad-9531-487f-bd82-5e638c4fc35f",
    artist = "Alayna Danner",
    imageUri = "https://cards.scryfall.io/normal/front/7/2/721463ad-9531-487f-bd82-5e638c4fc35f.jpg?1782688683",
    releaseDate = "2024-11-15",
    rarity = Rarity.UNCOMMON,
)
