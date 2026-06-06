package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.amountNode
import com.wingedsheep.tooling.coverage.findInteger
import com.wingedsheep.tooling.coverage.jsonContains
import com.wingedsheep.tooling.coverage.strField

/** Direct effects on life totals, damage, and the player's own cards (draw / discard / look). */
internal val damageDrawLifeHandlers: Map<String, ActionHandler> = buildMap {
    fun reg(vararg keys: String, h: ActionHandler) = keys.forEach { put(it, h) }

    reg("SpellDealsDamage", "PermanentDealsDamage") { _, args, tvar ->
        val amt = amount(args) ?: dynamicAmount(amountNode(args)) ?: return@reg null
        if (jsonContains(args, "_DamageRecipient", "EachPermanent")) {  // mass: deal N to each creature
            used.addAll(listOf("ForEachInGroupEffect", "DealDamageEffect", "EffectTarget"))
            return@reg "ForEachInGroupEffect(${groupFilterDsl(args)}, DealDamageEffect($amt, EffectTarget.Self))"
        }
        val tgt = refTarget(args, tvar) ?: return@reg null
        used.add("DealDamageEffect")
        "DealDamageEffect($amt, $tgt)"
    }

    reg("SpellDealsDistributedDamage") { _, args, _ ->
        val total = findInteger(args)
        if (total !is Int) return@reg null
        used.add("DividedDamageEffect")
        "DividedDamageEffect(totalDamage = $total)"
    }

    reg("GainLifeForEach") { _, args, _ ->
        val dyn = dynamicAmount(gainForEachAmount(args)) ?: return@reg null
        used.add("GainLifeEffect")
        "GainLifeEffect($dyn)"
    }

    reg("DrawNumberCards", "DrawACard") { node, args, _ ->
        used.add("DrawCardsEffect")
        val amt = if (node.strField("_Action") == "DrawACard") "1" else (amount(args) ?: dynamicAmount(amountNode(args)))
        if (amt != null) "DrawCardsEffect($amt)" else null
    }

    reg("CounterSpell") { _, _, _ -> used.add("CounterEffect"); "CounterEffect()" }
    reg("Shuffle") { _, _, _ -> used.add("ShuffleLibraryEffect"); "ShuffleLibraryEffect()" }

    reg("GainLife") { _, args, _ ->
        val amt = amount(args) ?: return@reg null
        used.add("GainLifeEffect")
        "GainLifeEffect($amt)"
    }
    reg("LoseLife") { _, args, _ ->
        val amt = amount(args) ?: dynamicAmount(amountNode(args)) ?: return@reg null
        used.addAll(listOf("LoseLifeEffect", "EffectTarget"))
        "LoseLifeEffect($amt, EffectTarget.Controller)"
    }

    reg("DiscardACard", "DiscardNumberCards", "DiscardAnyNumberOfCards") { _, args, _ ->
        used.add("Patterns")
        "Patterns.Hand.discardCards(${(findInteger(args) as? Int) ?: 1})"
    }
    reg("DiscardACardAtRandom") { _, _, _ -> used.add("Patterns"); "Patterns.Hand.discardRandom(1)" }

    reg("LookAtPlayersHand") { _, args, tvar ->
        val tgt = refTarget(args, tvar)
        used.add("LookAtTargetHandEffect")
        if (tgt != null) "LookAtTargetHandEffect($tgt)" else "LookAtTargetHandEffect()"
    }
    reg("TakeAnExtraTurn") { _, _, _ -> used.add("TakeExtraTurnEffect"); "TakeExtraTurnEffect()" }
}
