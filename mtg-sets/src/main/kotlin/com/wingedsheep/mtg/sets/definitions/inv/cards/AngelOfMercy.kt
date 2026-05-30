package com.wingedsheep.mtg.sets.definitions.inv.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Angel of Mercy
 * {4}{W}
 * Creature — Angel
 * 3/3
 * Flying
 * When this creature enters, you gain 3 life.
 *
 * Canonical [com.wingedsheep.sdk.model.CardDefinition] placed in Invasion: the earlier
 * Portal Second Age (p02) and Starter 1999 (s99) starter-set printings are not scaffolded
 * in the repo, and scaffolding them is out of scope for this change. Invasion is the
 * earliest scaffolded printing.
 */
val AngelOfMercy = card("Angel of Mercy") {
    manaCost = "{4}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Angel"
    power = 3
    toughness = 3
    oracleText = "Flying\nWhen this creature enters, you gain 3 life."

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.GainLife(3)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "2"
        artist = "Mark Tedin"
        imageUri = "https://cards.scryfall.io/normal/front/5/b/5b6de688-685f-4389-be35-a472ada988e1.jpg?1562913560"
    }
}
