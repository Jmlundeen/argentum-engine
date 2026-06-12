package com.wingedsheep.sdk.dsl

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.effects.AddManaEffect
import com.wingedsheep.sdk.scripting.effects.ManaExpiry

/**
 * Add Firebending N (Avatar: The Last Airbender, CR 702.189) — keyword ability +
 * triggered ability.
 *
 * "Whenever this creature attacks, add N {R}. Until end of combat, you don't lose this
 * mana as steps and phases end." The keyword ability is display-only (no separate
 * Firebending handler exists); the behavior lives entirely in the attack-triggered
 * ability wired here — an [AddManaEffect] producing N red mana with
 * [ManaExpiry.END_OF_COMBAT], which the pool keeps through combat and discards once
 * combat ends. It is an ordinary triggered ability (not a mana ability): it uses the
 * stack and can be responded to.
 */
fun CardBuilder.firebending(n: Int) {
    keywordAbilityList.add(KeywordAbility.firebending(n))
    triggeredAbilities.add(
        TriggeredAbility.create(
            trigger = Triggers.Attacks.event,
            binding = Triggers.Attacks.binding,
            effect = AddManaEffect(Color.RED, n, expiry = ManaExpiry.END_OF_COMBAT),
            descriptionOverride = "Whenever this creature attacks, add ${"{R}".repeat(n)}. " +
                "Until end of combat, you don't lose this mana as steps and phases end."
        )
    )
}
