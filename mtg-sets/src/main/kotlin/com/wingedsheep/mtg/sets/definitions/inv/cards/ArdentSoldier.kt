package com.wingedsheep.mtg.sets.definitions.inv.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.conditions.WasKicked
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Ardent Soldier
 * {1}{W}
 * Creature — Human Soldier
 * 1/2
 * Kicker {2}
 * Vigilance
 * If this creature was kicked, it enters with a +1/+1 counter on it.
 */
val ArdentSoldier = card("Ardent Soldier") {
    manaCost = "{1}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Human Soldier"
    power = 1
    toughness = 2
    oracleText = "Kicker {2} (You may pay an additional {2} as you cast this spell.)\n" +
        "Vigilance\n" +
        "If this creature was kicked, it enters with a +1/+1 counter on it."

    keywords(Keyword.VIGILANCE)
    keywordAbility(KeywordAbility.kicker("{2}"))

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        triggerCondition = WasKicked
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "3"
        artist = "Paolo Parente"
        imageUri = "https://cards.scryfall.io/normal/front/3/9/39dce974-846f-4365-b0a5-851e38668e7d.jpg?1562906683"
    }
}
