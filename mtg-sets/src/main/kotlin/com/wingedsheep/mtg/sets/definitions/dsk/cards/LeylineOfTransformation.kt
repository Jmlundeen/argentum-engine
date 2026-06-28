package com.wingedsheep.mtg.sets.definitions.dsk.cards

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.leyline
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ChoiceType
import com.wingedsheep.sdk.scripting.EntersWithChoice
import com.wingedsheep.sdk.scripting.GrantChosenSubtype
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/**
 * Leyline of Transformation (DSK #63)
 * {2}{U}{U}  Enchantment
 *
 * If this card is in your opening hand, you may begin the game with it on the battlefield.
 * As this enchantment enters, choose a creature type.
 * Creatures you control are the chosen type in addition to their other types. The same is
 * true for creature spells you control and creature cards you own that aren't on the
 * battlefield.
 *
 * `leyline()` adds the "begin the game on the battlefield" opening-hand marker (CR 103.6).
 * [EntersWithChoice] locks in the chosen creature type (CR 614 as-it-enters choice), and
 * [GrantChosenSubtype] is the Layer 4 (type-changing) static ability that adds that chosen
 * type to creatures you control — the Conspiracy / Xenograft mechanic.
 *
 * Limitation: the "creature spells you control and creature cards you own that aren't on the
 * battlefield are the chosen type" clause is NOT modeled. The engine projects type-changing
 * continuous effects only onto battlefield permanents (StateProjector iterates the
 * battlefield), so a chosen-type grant cannot reach the stack, hand, graveyard, library, or
 * exile. Implementing that would require type projection across every non-battlefield zone —
 * a broad, cross-cutting capability that no card in the engine currently exercises. The
 * battlefield clause (by far the dominant gameplay function) is fully faithful.
 */
val LeylineOfTransformation = card("Leyline of Transformation") {
    manaCost = "{2}{U}{U}"
    colorIdentity = "U"
    typeLine = "Enchantment"
    oracleText = "If this card is in your opening hand, you may begin the game with it on the battlefield.\n" +
        "As this enchantment enters, choose a creature type.\n" +
        "Creatures you control are the chosen type in addition to their other types. The same is true for creature spells you control and creature cards you own that aren't on the battlefield."

    leyline()

    replacementEffect(EntersWithChoice(ChoiceType.CREATURE_TYPE))

    staticAbility {
        ability = GrantChosenSubtype(filter = GroupFilter.AllCreaturesYouControl)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "63"
        artist = "Sergey Glushakov"
        imageUri = "https://cards.scryfall.io/normal/front/4/b/4bd941ca-f3d2-44c1-8df3-851362f6b848.jpg?1726286087"
    }
}
