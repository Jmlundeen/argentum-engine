package com.wingedsheep.sdk.core

import kotlinx.serialization.Serializable

/**
 * Game-mode configuration the engine reads at runtime.
 *
 * Distinct from [DeckFormat], which is a deck-construction concept (Scryfall-sourced legality +
 * singleton/size rules enforced by the deck validator). [Format] tells the engine how to *run*
 * the game: starting life, hand size, win conditions, and any zone setup that depends on format.
 *
 * Adding a new format becomes a config variant rather than a new code path: Brawl, Oathbreaker,
 * Pauper Commander, and 1v1 Commander are all (or will be) `Commander`-shaped data with different
 * field values.
 */
@Serializable
sealed interface Format {

    @Serializable
    data object Standard : Format

    /**
     * 1v1 Commander (Phase 1). Multiplayer (3-4 free-for-all) is its own project.
     *
     * @property commanderDamageThreshold Cumulative single-source combat damage that loses the
     *   game (CR 903.10a). Standard Commander is 21.
     * @property deckSize Total deck size including the commander; the validator enforces this.
     * @property startingLife Each player's starting life total.
     * @property startingHandSize Cards drawn for the opening hand.
     * @property alwaysDivertToCommand Bypass the CR 903.9a player choice and unconditionally
     *   divert a commander leaving for graveyard / exile / hand / library to the command zone.
     *   Off by default — the SBA pauses with a yes/no decision so the owner can choose to leave
     *   the commander in the destination zone (e.g. to keep recursion targets in graveyard or
     *   keep linked-exile abilities tracking it). AI/headless tooling can flip this on to skip
     *   the prompt.
     */
    @Serializable
    data class Commander(
        val commanderDamageThreshold: Int = 21,
        val deckSize: Int = 100,
        val startingLife: Int = 40,
        val startingHandSize: Int = 7,
        val alwaysDivertToCommand: Boolean = false,
    ) : Format

    /**
     * Momir Basic — the classic Vanguard format (<https://mtg.fandom.com/wiki/Momir>).
     *
     * Each player's deck is 60 basic lands and every player begins the game with the avatar
     * [avatarCardName] in the command zone. The avatar grants the activated ability
     * "{X}, Discard a card: Create a token that's a copy of a randomly chosen creature card
     * with mana value X. Activate only as a sorcery and only once each turn."
     *
     * Like [Commander], this is runtime config, not a code path: the engine reads it at game
     * init to set life / hand size and to place the avatar in the command zone, and the
     * random-creature-token effect reads [eligibleCreatureNames] as its candidate pool.
     *
     * @property startingLife 24 — Vanguard's base 20 plus Momir Vig's printed Life Modifier of +4.
     * @property eligibleCreatureNames The pool the random copy is drawn from — every creature card
     *   name across all sets. **Stored pre-sorted** so the engine's seeded `GameRng.pick` is
     *   replay-stable: the effect filters this list by mana value and picks, never re-collecting
     *   from the card registry (whose map order is unspecified). The engine treats it as opaque,
     *   deterministic data; the server computes it at match start.
     */
    @Serializable
    data class MomirBasic(
        val startingLife: Int = 24,
        val startingHandSize: Int = 7,
        val avatarCardName: String = "Momir Vig, Simic Visionary",
        val eligibleCreatureNames: List<String> = emptyList(),
    ) : Format

    /**
     * Two-Headed Giant — the 2v2 team variant (CR 810). Two teams of two players each; a team
     * shares one life total and one pool of poison counters, takes a shared turn (CR 805), fights
     * combined combat, and wins or loses together (CR 810.8a).
     *
     * Like [Commander] this is runtime config, not a code path: the engine reads it at game init
     * to set the shared starting life and (via [com.wingedsheep.engine.core.GameConfig.teams]) to
     * stamp team membership on the player entities.
     *
     * **Phasing note.** This Phase 1 type only carries the format config + drives team membership.
     * The behaviours it *describes* — shared life total (CR 810.4 / 810.9), shared turns and
     * priority (CR 805), combined combat (CR 805.10 / 810.7), and team win/loss (CR 810.8) — are
     * implemented in later phases. Until then a 2HG game boots with the right teams and life values
     * but otherwise runs the standard per-player rules.
     *
     * @property startingLife The team's shared starting life total. Regular two-player 2HG is 30
     *   (CR 810.4). In Phase 1 (no shared pool yet) each player is initialized to this value.
     * @property startingHandSize Cards drawn for each player's opening hand.
     * @property poisonThreshold Poison counters at which a team loses the game (CR 810.8d). 2HG
     *   raises the normal 10 to 15.
     */
    @Serializable
    data class TwoHeadedGiant(
        val startingLife: Int = 30,
        val startingHandSize: Int = 7,
        val poisonThreshold: Int = 15,
    ) : Format
}

/**
 * Preset shapes for drafted/sealed 1v1 commander formats. Each preset selects a
 * [Format.Commander] configuration tuned for 60-card limited play (paper Brawl life vs. classic
 * Commander life). The lobby host picks one of these when creating a Commander Draft or Sealed
 * lobby; the match builder converts it to a [Format.Commander] instance at game start.
 */
@Serializable
enum class CommanderPreset(
    val deckSize: Int,
    val startingLife: Int,
    val commanderDamage: Int,
) {
    /** Paper Brawl life total, faster 1v1 games. */
    BRAWL(deckSize = 60, startingLife = 25, commanderDamage = 16),

    /** Closer to Commander Legends' template — slower, more recursive games. */
    COMMANDER(deckSize = 60, startingLife = 30, commanderDamage = 21);

    fun toFormat(): Format.Commander = Format.Commander(
        commanderDamageThreshold = commanderDamage,
        deckSize = deckSize,
        startingLife = startingLife,
    )
}
