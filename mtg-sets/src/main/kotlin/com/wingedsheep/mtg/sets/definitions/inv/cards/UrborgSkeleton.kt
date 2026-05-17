package com.wingedsheep.mtg.sets.definitions.inv.cards

import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.conditions.WasKicked
import com.wingedsheep.sdk.scripting.effects.RegenerateEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Urborg Skeleton
 * {B}
 * Creature — Skeleton
 * 0/1
 * Kicker {3}
 * {B}: Regenerate this creature.
 * If this creature was kicked, it enters with a +1/+1 counter on it.
 */
val UrborgSkeleton = card("Urborg Skeleton") {
    manaCost = "{B}"
    colorIdentity = "B"
    typeLine = "Creature — Skeleton"
    power = 0
    toughness = 1
    oracleText = "Kicker {3}\n{B}: Regenerate this creature.\nIf this creature was kicked, it enters with a +1/+1 counter on it."

    keywordAbility(KeywordAbility.kicker("{3}"))

    activatedAbility {
        cost = Costs.Mana("{B}")
        effect = RegenerateEffect(EffectTarget.Self)
    }

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        triggerCondition = WasKicked
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "134"
        artist = "Alan Pollack"
        imageUri = "https://cards.scryfall.io/normal/front/6/e/6e522a62-fbca-4362-9006-d4356c525704.jpg?1562917062"
    }
}
