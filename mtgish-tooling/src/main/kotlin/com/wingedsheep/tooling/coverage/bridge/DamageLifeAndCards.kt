package com.wingedsheep.tooling.coverage.bridge

/** Direct one-to-one effects: damage, life, card draw, and the common single-permanent verbs. */
internal fun BridgeBuilder.damageLifeAndCards() {
    effects(
        "SpellDealsDamage", "PermanentDealsDamage",
        // "this spell deals N damage … that can't be prevented" (Pinpoint Avalanche) and "have <permanent>
        // deal N damage to <recipient>" (Skirk Commando, Snapping Thragg, Aether Charge) — both realise as
        // a DealDamageEffect, so they carry the same DealDamage capability as the plain spell/permanent forms.
        "SpellDealsDamageCantBePrevented", "HavePermanentDealDamage",
        tag = "DealDamage",
    )
    effect("SpellDealsDistributedDamage", "DividedDamage")

    effects("DrawNumberCards", "DrawACard", tag = "DrawCards")
    effect("DrawUptoNumberCards", "DrawUpTo")
    // "The next time you would draw a card this turn, [do X] instead" (the Onslaught Words cycle); the
    // replacement action's own capability is surfaced via the `_ReplacementActionWouldDraw` discriminator.
    effect("CreateFutureReplaceWouldDraw", "ReplaceNextDrawWith")
    composed("GainLifeForEach", "GainLife + DynamicAmount", composes = listOf("GainLife"))
    effect("GainLife", "GainLife")
    effect("LoseLife", "LoseLife")

    effect("SacrificePermanent", "Sacrifice")
    effect("CounterSpell", "Counter")
    effect("TakeAnExtraTurn", "TakeExtraTurn")
    effect("LoseTheGame", "LoseGame")
    effect("Shuffle", "ShuffleLibrary")
    effect("RevealHand", "RevealHand")
    effect("LookAtPlayersHand", "LookAtTargetHand")

    effects("TapPermanent", "UntapPermanent", tag = "TapUntap")
    composed("TapEachPermanent", composes = listOf("TapUntap"))
    composed("UntapEachPermanent", composes = listOf("TapUntap"))

    // Plain ±P/T and the two dynamic-amount variants (AdjustPTX uses a ±X modifier over a game number —
    // Wirewood Pride / Feeding Frenzy / Tribal Unity; AdjustPTForEach scales a fixed base by a count —
    // Goblin Piledriver / Shaleskin Bruiser) all realise as a ModifyStatsEffect.
    effects("AdjustPT", "AdjustPTX", "AdjustPTForEach", tag = "ModifyStats")
    effect("AddAbility", "GrantKeyword")
}
