package com.wingedsheep.mtg.sets.definitions.inv.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

/**
 * Tranquility
 * {2}{G}
 * Sorcery
 * Destroy all enchantments.
 */
val Tranquility = card("Tranquility") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Sorcery"
    oracleText = "Destroy all enchantments."

    spell {
        effect = Effects.DestroyAll(GameObjectFilter.Enchantment)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "217"
        artist = "Rob Alexander"
        flavorText = "The plagues robbed Dominaria of all but its dreams. Eladamri hoped dreams were enough."
        imageUri = "https://cards.scryfall.io/normal/front/9/7/97019ba5-ce2a-460c-8a4e-2b22053ced65.jpg?1562925426"
    }
}
