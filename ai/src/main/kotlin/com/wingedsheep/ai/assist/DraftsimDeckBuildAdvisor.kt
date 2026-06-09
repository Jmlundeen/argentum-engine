package com.wingedsheep.ai.assist

import com.wingedsheep.ai.draftsim.DraftsimData
import com.wingedsheep.ai.draftsim.DraftsimDeckBuilder
import com.wingedsheep.ai.draftsim.DraftsimPoolCard
import com.wingedsheep.ai.draftsim.toScorerCard

/**
 * Deckbuild engine backed by the ported Draftsim autobuilder ([DraftsimDeckBuilder]): it ranks
 * archetypes (`kf`), greedily builds + refines each (`vX`/`ek`), and returns the highest-scoring
 * 23-nonland + 17-land deck.
 *
 * Unlike the heuristic engine this is a **full rebuild from the pool** — it does not honor
 * [DeckBuildRequest.locked] or [DeckBuildRequest.targetSize] (the bundle's Auto-Build always
 * replaces the deck with a fresh 40-card limited build). Basics are reported by name so the client
 * splits them out like any other deckbuild result.
 */
object DraftsimDeckBuildAdvisor : DeckBuildAdvisor {
    override val id = "draftsim"
    override val displayName = "Draftsim"

    private val COLOR_TO_BASIC = mapOf("W" to "Plains", "U" to "Island", "B" to "Swamp", "R" to "Mountain", "G" to "Forest")

    override fun buildDeck(request: DeckBuildRequest): DeckBuildResult {
        val builder = DraftsimDeckBuilder(DraftsimData.tablesFor(request.setCodes))
        val pool = request.pool.mapIndexed { i, def -> DraftsimPoolCard(def.toScorerCard(), "pool-$i") }

        val builds = builder.buildDecks(pool, mode = "sealed")
        val best = builds.firstOrNull()
            ?: return DeckBuildResult(advisorId = id, deckList = emptyMap(), score = null)

        val byId = pool.associateBy { it.instanceId }
        val deckList = LinkedHashMap<String, Int>()
        for (instanceId in best.deckInstanceIds) {
            val name = byId[instanceId]?.card?.name ?: continue
            deckList[name] = (deckList[name] ?: 0) + 1
        }
        for ((color, count) in best.basicsNeeded) {
            val name = COLOR_TO_BASIC[color] ?: continue
            if (count > 0) deckList[name] = (deckList[name] ?: 0) + count
        }
        return DeckBuildResult(advisorId = id, deckList = deckList, score = best.score, archetype = best.name)
    }
}
