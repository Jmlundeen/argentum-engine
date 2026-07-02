package com.wingedsheep.mtg.sets.definitions.akh.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CharacteristicValue
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Enigma Drake
 * {1}{U}{R}
 * Creature — Drake
 * Power * / Toughness 4
 *
 * Flying
 * Enigma Drake's power is equal to the number of instant and sorcery cards in your graveyard.
 *
 * The power is a characteristic-defining ability (CR 604.3): only [dynamicPower] is set, so
 * toughness stays a fixed 4. The count uses [DynamicAmount.Count] over the controller's
 * graveyard filtered to [GameObjectFilter.InstantOrSorcery].
 */
val EnigmaDrake = card("Enigma Drake") {
    manaCost = "{1}{U}{R}"
    colorIdentity = "UR"
    typeLine = "Creature — Drake"
    toughness = 4
    oracleText = "Flying\n" +
        "Enigma Drake's power is equal to the number of instant and sorcery cards in your graveyard."

    keywords(Keyword.FLYING)

    dynamicPower = CharacteristicValue.dynamic(
        DynamicAmount.Count(Player.You, Zone.GRAVEYARD, GameObjectFilter.InstantOrSorcery)
    )

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "198"
        artist = "Steve Argyle"
        flavorText = "Many initiates believe it possesses secrets known only to Kefnet himself. " +
            "Many have become meals trying to learn them."
        imageUri = "https://cards.scryfall.io/normal/front/6/6/66286631-c16e-410c-b963-25cfe8005d8f.jpg?1782711097"
    }
}
