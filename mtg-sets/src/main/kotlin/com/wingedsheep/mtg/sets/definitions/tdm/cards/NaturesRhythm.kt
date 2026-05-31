package com.wingedsheep.mtg.sets.definitions.tdm.cards

import com.wingedsheep.sdk.dsl.EffectPatterns
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.SearchDestination

/**
 * Nature's Rhythm — Tarkir: Dragonstorm #150
 * {X}{G}{G} · Sorcery
 *
 * Search your library for a creature card with mana value X or less, put it onto the
 * battlefield, then shuffle.
 * Harmonize {X}{G}{G}{G}{G} (You may cast this card from your graveyard for its harmonize
 * cost. You may tap a creature you control to reduce that cost by an amount of generic
 * mana equal to its power. Then exile this spell.)
 *
 * The primary cast (`{X}{G}{G}` from hand) and the search-with-MV-X effect are fully
 * supported. The Harmonize alternative cost here is itself an {X} cost: the cast handler
 * pays whatever `xValue` the action carries and the search reads the same X, so resolution
 * is faithful. However, [enumerateHarmonize] in CastFromZoneEnumerator does not yet set
 * `hasXCost`/`maxAffordableX` on its `CastWithHarmonize` legal action, so the client UI
 * won't prompt for X when casting this from the graveyard (X would default to 0). That is a
 * pre-existing engine/enumerator UX gap for X-cost Harmonize — out of scope for this card.
 */
val NaturesRhythm = card("Nature's Rhythm") {
    manaCost = "{X}{G}{G}"
    colorIdentity = "G"
    typeLine = "Sorcery"
    oracleText = "Search your library for a creature card with mana value X or less, put it onto the battlefield, then shuffle.\n" +
        "Harmonize {X}{G}{G}{G}{G} (You may cast this card from your graveyard for its harmonize cost. " +
        "You may tap a creature you control to reduce that cost by an amount of generic mana equal to its power. Then exile this spell.)"

    spell {
        effect = EffectPatterns.searchLibrary(
            filter = GameObjectFilter.Creature.manaValueAtMostX(),
            count = 1,
            destination = SearchDestination.BATTLEFIELD,
            shuffleAfter = true
        )
    }

    keywordAbility(KeywordAbility.harmonize("{X}{G}{G}{G}{G}"))

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "150"
        artist = "Liiga Smilshkalne"
        imageUri = "https://cards.scryfall.io/normal/front/1/3/1397d904-c51d-451e-8505-7f3118acc1f6.jpg?1743204565"
    }
}
