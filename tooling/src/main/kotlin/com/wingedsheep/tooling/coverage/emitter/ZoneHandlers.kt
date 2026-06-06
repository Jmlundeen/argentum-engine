package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.compact
import com.wingedsheep.tooling.coverage.findInteger
import com.wingedsheep.tooling.coverage.jsonContains
import com.wingedsheep.tooling.coverage.strField
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Zone movement: destroy / bounce / reanimate / search / look / mill. Argentum has no leaf
 *  destroy/discard verb — they compose from MoveToZone (single) / MoveCollection (mass). */
internal val zoneHandlers: Map<String, ActionHandler> = buildMap {
    fun reg(vararg keys: String, h: ActionHandler) = keys.forEach { put(it, h) }

    reg("PutEachPermanentIntoItsOwnersHand") { node, _, _ ->  // bounce each chosen target
        if (jsonContains(node, "_Permanents", "Ref_TargetPermanents")) {
            used.addAll(listOf("ForEachTargetEffect", "MoveToZoneEffect", "Zone", "EffectTarget"))
            "ForEachTargetEffect(listOf(MoveToZoneEffect(EffectTarget.ContextTarget(0), Zone.HAND)))"
        } else null
    }

    reg("DestroyPermanent") { _, args, tvar ->
        val tgt = refTarget(args, tvar) ?: return@reg null
        used.addAll(listOf("MoveToZoneEffect", "Zone"))
        "MoveToZoneEffect($tgt, Zone.GRAVEYARD, byDestruction = true)"
    }
    reg("DestroyEachPermanent", "DestroyEachPermanentNoRegen") { node, args, _ ->
        used.addAll(listOf("ForEachInGroupEffect", "MoveToZoneEffect", "Zone", "EffectTarget"))
        val noregen = if (node.strField("_Action") == "DestroyEachPermanentNoRegen") "true" else "false"
        "ForEachInGroupEffect(${groupFilterDsl(args)}, MoveToZoneEffect(EffectTarget.Self, " +
            "Zone.GRAVEYARD, byDestruction = true), noRegenerate = $noregen)"
    }

    reg("PutPermanentIntoItsOwnersHand") { _, args, tvar ->  // bounce
        val tgt = refTarget(args, tvar) ?: return@reg null
        used.addAll(listOf("MoveToZoneEffect", "Zone"))
        "MoveToZoneEffect($tgt, Zone.HAND)"
    }

    reg("ShuffleGraveyardCardIntoLibrary") { _, args, tvar ->  // e.g. Alabaster Dragon
        val tgt = refTarget(args, tvar) ?: "EffectTarget.Self"
        used.addAll(listOf("MoveToZoneEffect", "Zone", "ZonePlacement", "EffectTarget"))
        "MoveToZoneEffect($tgt, Zone.LIBRARY, ZonePlacement.Shuffled)"
    }

    reg("SearchLibrary") { _, args, _ -> renderSearch(args) }
    reg("LookAtTheTopNumberCardsOfLibrary", "LookAtTheTopNumberCardsOfPlayersLibrary") { node, _, _ -> renderLook(node) }

    reg("PutGraveyardCardOntoBattlefield", "PutGraveyardCardIntoHand",
        "ReturnDeadGraveyardCardToTopOfLibrary", "PutPermanentOnTopOfOwnersLibrary") { node, args, tvar ->
        val a = node.strField("_Action")
        // ReturnDead… ("return this card from the graveyard") often has no ref -> Self
        var tgt = refTarget(args, tvar)
        if (tgt == null) {
            if (a == "ReturnDeadGraveyardCardToTopOfLibrary") { used.add("EffectTarget"); tgt = "EffectTarget.Self" } else return@reg null
        }
        val zone = mapOf(
            "PutGraveyardCardOntoBattlefield" to "BATTLEFIELD", "PutGraveyardCardIntoHand" to "HAND",
            "ReturnDeadGraveyardCardToTopOfLibrary" to "LIBRARY", "PutPermanentOnTopOfOwnersLibrary" to "LIBRARY",
        )[a]
        used.addAll(listOf("MoveToZoneEffect", "Zone"))
        "MoveToZoneEffect($tgt, Zone.$zone)"
    }
}

internal fun EmitCtx.renderSearch(args: JsonElement?): String? {
    used.add("Patterns")
    val blob = compact(args)
    val dest = when {
        "PutFoundCardsOntoBattlefield" in blob -> "BATTLEFIELD"
        "PutFoundCardsIntoHand" in blob -> "HAND"
        "PutSetAsideCardsOnTopOfLibrary" in blob || "OnTopOfLibrary" in blob -> "TOP_OF_LIBRARY"
        else -> "HAND"
    }
    used.add("SearchDestination")
    val filt = landSearchFilterDsl(args)
    val count = findInteger(args)
    val parts = mutableListOf("filter = $filt")
    if (count is Int && count != 1) parts.add("count = $count")
    parts.add("destination = SearchDestination.$dest")
    if ("RevealFoundCards" in blob) parts.add("reveal = true")
    return "Patterns.Library.searchLibrary(${parts.joinToString(", ")})"
}

internal fun EmitCtx.renderLook(node: JsonObject): String? {
    used.add("Patterns")
    val look = findInteger(node) ?: return null
    val blob = compact(node)
    var keep: Int? = null
    for (m in Regex(""""PutNumber\w*IntoHand".*?"args":\s*(\d+)""").findAll(blob)) keep = m.groupValues[1].toInt()
    if (keep != null) return "Patterns.Library.lookAtTopAndKeep(count = $look, keepCount = $keep)"
    if ("PutTheRemainingCardsOnTopOfLibraryInAnyOrder" in blob) return "Patterns.Library.lookAtTopAndReorder(count = $look)"
    return null
}
