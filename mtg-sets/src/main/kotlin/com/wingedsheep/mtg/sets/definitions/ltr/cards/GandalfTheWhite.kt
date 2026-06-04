package com.wingedsheep.mtg.sets.definitions.ltr.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantFlashToSpellType

/**
 * Gandalf the White
 * {3}{W}{W}
 * Legendary Creature — Avatar Wizard
 * 4/5
 *
 * Flash
 * You may cast legendary spells and artifact spells as though they had flash.
 * If a legendary permanent or an artifact entering or leaving the battlefield causes a
 * triggered ability of a permanent you control to trigger, that ability triggers an
 * additional time.
 *
 * **PARTIAL — third clause deferred.**
 *
 * The Flash keyword (CR 702.8) and the flash-permission static
 * ([GrantFlashToSpellType] with filter `Artifact ∨ Legendary`, controller-only) are
 * implemented. The third clause — "if X causes a triggered ability of a permanent you
 * control to trigger, that ability triggers an additional time" — is a *trigger
 * replacement* (CR 614.5-style "triggers an additional time") and shares no clean reusable
 * primitive with anything else in LTR yet. It is tracked in `TODO.md` as a follow-up gap.
 * The card stays unchecked in `cards.md` until that gap lands so the backlog correctly
 * reflects which clauses are live.
 */
val GandalfTheWhite = card("Gandalf the White") {
    manaCost = "{3}{W}{W}"
    colorIdentity = "W"
    typeLine = "Legendary Creature — Avatar Wizard"
    power = 4
    toughness = 5
    oracleText = "Flash\n" +
        "You may cast legendary spells and artifact spells as though they had flash.\n" +
        "If a legendary permanent or an artifact entering or leaving the battlefield causes " +
        "a triggered ability of a permanent you control to trigger, that ability triggers an " +
        "additional time."

    keywords(Keyword.FLASH)

    staticAbility {
        ability = GrantFlashToSpellType(
            filter = GameObjectFilter.Artifact or GameObjectFilter.Any.legendary(),
            controllerOnly = true
        )
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "19"
        artist = "Magali Villeneuve"
        imageUri = "https://cards.scryfall.io/normal/front/e/3/e384c20b-d0c1-4781-9d11-e89e5a6bf3fc.jpg?1686967821"
    }
}
