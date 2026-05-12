package com.wingedsheep.mtg.sets.definitions.spm.cards

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Living Brain, Mechanical Marvel
 * {4}
 * Legendary Artifact Creature — Robot Villain
 * 3/3
 *
 * At the beginning of combat on your turn, target non-Equipment artifact you control
 * becomes an artifact creature with base power and toughness 3/3 until end of turn. Untap it.
 */
val LivingBrainMechanicalMarvel = card("Living Brain, Mechanical Marvel") {
    manaCost = "{4}"
    colorIdentity = ""
    typeLine = "Legendary Artifact Creature — Robot Villain"
    power = 3
    toughness = 3
    oracleText = "At the beginning of combat on your turn, target non-Equipment artifact you control becomes an artifact creature with base power and toughness 3/3 until end of turn. Untap it."

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "167"
        artist = "Nathaniel Himawan"
        flavorText = "\"This was cutting edge when I was a kid!\"\n—Spider-Man, Peter Parker"
        imageUri = "https://cards.scryfall.io/normal/front/2/6/26833b64-2e6d-4977-9a6e-6fe73c54d671.jpg?1757378042"
    }
}
