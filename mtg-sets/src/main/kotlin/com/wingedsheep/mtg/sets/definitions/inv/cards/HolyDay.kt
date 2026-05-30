package com.wingedsheep.mtg.sets.definitions.inv.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Holy Day
 * {W}
 * Instant
 * Prevent all combat damage that would be dealt this turn.
 *
 * Canonical [com.wingedsheep.sdk.model.CardDefinition] placed in Invasion: the earliest
 * printing is Legends (leg), which is not scaffolded in the repo, and scaffolding it is out
 * of scope for this change. Invasion is the earliest scaffolded printing.
 */
val HolyDay = card("Holy Day") {
    manaCost = "{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText = "Prevent all combat damage that would be dealt this turn."

    spell {
        effect = Effects.PreventAllCombatDamage()
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "20"
        artist = "Pete Venters"
        imageUri = "https://cards.scryfall.io/normal/front/a/a/aa91fd4e-4e1f-4cfa-b10f-456bd875238f.jpg?1562929372"
    }
}
