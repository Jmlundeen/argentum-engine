package com.wingedsheep.mtg.sets.definitions.m19.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.GameObjectFilter

/**
 * Diamond Mare
 * {2}
 * Artifact Creature — Horse
 * 1/3
 *
 * As this creature enters, choose a color.
 * Whenever you cast a spell of the chosen color, you gain 1 life.
 *
 * The chosen color is stored at ETB via [EntersWithChoice] (ChoiceType.COLOR →
 * CastChoicesComponent). The cast trigger reads it back through
 * [GameObjectFilter.sharingChosenColorWithSource] (CardPredicate.SharesChosenColorWithSource),
 * so the source permanent's chosen color gates which spells pay off.
 */
val DiamondMare = card("Diamond Mare") {
    manaCost = "{2}"
    typeLine = "Artifact Creature — Horse"
    power = 1
    toughness = 3
    oracleText = "As this creature enters, choose a color.\n" +
        "Whenever you cast a spell of the chosen color, you gain 1 life."

    replacementEffect(EntersWithChoice(ChoiceType.COLOR))

    triggeredAbility {
        trigger = Triggers.youCastSpell(
            spellFilter = GameObjectFilter.Any.sharingChosenColorWithSource()
        )
        effect = Effects.GainLife(1)
        description = "Whenever you cast a spell of the chosen color, you gain 1 life."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "231"
        artist = "Alayna Danner"
        flavorText = "When it passes, rainbows follow."
        imageUri = "https://cards.scryfall.io/normal/front/c/a/ca600b3f-2c70-489b-b218-6e3245b90114.jpg?1782709481"
    }
}
