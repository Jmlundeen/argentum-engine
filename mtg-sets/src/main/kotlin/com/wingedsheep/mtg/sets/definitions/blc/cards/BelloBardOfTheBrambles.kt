package com.wingedsheep.mtg.sets.definitions.blc.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantCardType
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.GrantSubtype
import com.wingedsheep.sdk.scripting.GrantTriggeredAbility
import com.wingedsheep.sdk.scripting.SetBasePowerToughnessStatic
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/**
 * Bello, Bard of the Brambles
 * {1}{R}{G}
 * Legendary Creature — Raccoon Bard
 * 3/3
 * During your turn, each non-Equipment artifact and non-Aura enchantment you control
 * with mana value 4 or greater is a 4/4 Elemental creature in addition to its other
 * types and has indestructible, haste, and "Whenever this creature deals combat
 * damage to a player, draw a card."
 *
 * Implementation: each layer of the animation (add type, add subtype, set P/T,
 * grant indestructible, grant haste) is a separate `ConditionalStaticAbility` gated
 * by `IsYourTurn`. The combat-damage trigger is granted unconditionally — it can
 * only fire when the source is a creature in combat, which only happens on Bello's
 * controller's turn (when the gating condition adds the creature type).
 */
val BelloBardOfTheBrambles = card("Bello, Bard of the Brambles") {
    manaCost = "{1}{R}{G}"
    colorIdentity = "RG"
    typeLine = "Legendary Creature — Raccoon Bard"
    power = 3
    toughness = 3
    oracleText = "During your turn, each non-Equipment artifact and non-Aura enchantment you control with mana value 4 or greater is a 4/4 Elemental creature in addition to its other types and has indestructible, haste, and \"Whenever this creature deals combat damage to a player, draw a card.\""

    val animatedFilter = GroupFilter(
        (GameObjectFilter.Artifact.notSubtype(Subtype.EQUIPMENT)
            or GameObjectFilter.Enchantment.notSubtype(Subtype.AURA))
            .manaValueAtLeast(4)
            .youControl()
    )

    staticAbility {
        ability = GrantCardType("CREATURE", animatedFilter)
        condition = Conditions.IsYourTurn
    }
    staticAbility {
        ability = GrantSubtype("Elemental", animatedFilter)
        condition = Conditions.IsYourTurn
    }
    staticAbility {
        ability = SetBasePowerToughnessStatic(4, 4, animatedFilter)
        condition = Conditions.IsYourTurn
    }
    staticAbility {
        ability = GrantKeyword(Keyword.INDESTRUCTIBLE, animatedFilter)
        condition = Conditions.IsYourTurn
    }
    staticAbility {
        ability = GrantKeyword(Keyword.HASTE, animatedFilter)
        condition = Conditions.IsYourTurn
    }
    staticAbility {
        ability = GrantTriggeredAbility(
            ability = TriggeredAbility.create(
                trigger = Triggers.DealsCombatDamageToPlayer.event,
                binding = Triggers.DealsCombatDamageToPlayer.binding,
                effect = Effects.DrawCards(1)
            ),
            filter = animatedFilter
        )
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "1"
        artist = "Slawomir Maniak"
        imageUri = "https://cards.scryfall.io/normal/front/3/1/31e4b7a1-b377-49d2-a92e-4bcb0db35f16.jpg?1721428130"
    }
}
