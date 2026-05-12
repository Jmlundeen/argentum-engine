package com.wingedsheep.mtg.sets.definitions.spm.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.TimingRule

/**
 * Hot Dog Cart
 * {3}
 * Artifact
 * {T}: Add one mana of any color.
 * {2}, {T}: Create a Food token.
 */
val HotDogCart = card("Hot Dog Cart") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "{T}: Add one mana of any color.\n{2}, {T}: Create a Food token."

    activatedAbility {
        cost = AbilityCost.Tap
        effect = Effects.AddAnyColorMana()
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{2}"), Costs.Tap)
        effect = Effects.CreateFood()
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "240"
        artist = "Jodie Muir"
        imageUri = "https://cards.scryfall.io/normal/front/9/e/9e3c4ae4-c4a6-4f33-9a53-dd79beae9b55.jpg?1757376788"
    }
}
