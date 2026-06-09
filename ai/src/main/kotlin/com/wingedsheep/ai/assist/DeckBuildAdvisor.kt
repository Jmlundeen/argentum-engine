package com.wingedsheep.ai.assist

import com.wingedsheep.sdk.model.CardDefinition

/**
 * A pluggable engine that builds (or completes) a deck from a card pool. The heuristic engine
 * ([HeuristicDeckBuildAdvisor]) ships today; LLM- ([com.wingedsheep.ai.llm.AiDeckBuilder]) and
 * MCTS-backed engines can be added later by implementing this interface and registering them in
 * [AdvisorCatalog].
 *
 * Operates on resolved [CardDefinition]s (the server resolves pool card names against the registry),
 * because building needs the full card — colors, types, mana cost, and the effect tree the heuristic
 * rater walks.
 */
interface DeckBuildAdvisor {
    /** Stable id used by the client to select this engine (e.g. "heuristic"). */
    val id: String

    /** Human-readable name shown in the engine dropdown. */
    val displayName: String

    fun buildDeck(request: DeckBuildRequest): DeckBuildResult
}

data class DeckBuildRequest(
    /** The player's full card pool, one entry per physical copy. */
    val pool: List<CardDefinition>,
    /** Basic lands available to the build, by name (Plains/Island/…). */
    val availableBasics: List<CardDefinition> = emptyList(),
    /**
     * Cards already in the in-progress deck (name → count). Empty = build fresh; non-empty = keep
     * these and only fill the rest ("complete partial").
     */
    val locked: Map<String, Int> = emptyMap(),
    /** Total deck size to reach (40 for limited, higher for commander). */
    val targetSize: Int = 40,
    /**
     * The lobby's set code(s), so set-specific engines (e.g. Draftsim) load the right ratings /
     * removal / archetype tables. Empty for engines that don't need it (the heuristic).
     */
    val setCodes: List<String> = emptyList(),
)

data class DeckBuildResult(
    val advisorId: String,
    /** Full decklist (spells + lands, including basics) as name → count. */
    val deckList: Map<String, Int>,
    /** Intrinsic quality estimate (sum of card ratings), or null if the engine doesn't score. */
    val score: Double? = null,
    /** Detected/targeted archetype label, if the engine identifies one. */
    val archetype: String? = null,
)
