package com.wingedsheep.mtg.sets.definitions.blc.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Karplusan Forest
 * Land
 *
 * {T}: Add {C}.
 * {T}: Add {R} or {G}. This land deals 1 damage to you.
 */
val KarplusanForest = card("Karplusan Forest") {
    typeLine = "Land"
    colorIdentity = "RG"
    oracleText = "{T}: Add {C}.\n{T}: Add {R} or {G}. This land deals 1 damage to you."

    activatedAbility {
        cost = AbilityCost.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = AbilityCost.Tap
        effect = Effects.AddMana(Color.RED)
            .then(Effects.DealDamage(1, EffectTarget.PlayerRef(Player.You)))
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = AbilityCost.Tap
        effect = Effects.AddMana(Color.GREEN)
            .then(Effects.DealDamage(1, EffectTarget.PlayerRef(Player.You)))
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "314"
        artist = "Sam Burley"
        flavorText = "Between jagged, snowcapped peaks, the dense bands of hardy evergreens provide the perfect cover for roving bands of orc and goblin raiders."
        imageUri = "https://cards.scryfall.io/normal/front/b/f/bf999ad0-ec64-4bbc-b0c3-fc0946cf65de.jpg?1721429765"
    }
}
