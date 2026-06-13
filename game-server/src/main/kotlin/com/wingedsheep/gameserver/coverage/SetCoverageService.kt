package com.wingedsheep.gameserver.coverage

import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.model.MtgSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

/**
 * Computes per-set card-implementation coverage for the Set Completion view.
 *
 * The denominator — how many cards a set canonically *has* — is not knowable at
 * runtime: it lives only in the local Scryfall cache that `scripts/card-status`
 * populates. `scripts/gen-set-totals` bakes those canonical card names into the
 * committed `coverage/set-totals.json` resource, split into **draft** (booster-relevant,
 * Scryfall `booster: true`) and **extra** (completionist exclusives), same partitioning
 * as `card-status` so the numbers match the mtgish coverage TUI.
 *
 * At request time this service joins that static denominator with the *live*
 * [MtgSetCatalog] numerator: a set's implemented count is the number of its canonical
 * names we've actually authored (`cards` + `basicLands` + reprint `printings`). Because
 * coverage is an intersection against the canonical name set, `implemented` can never
 * exceed the total — a name we author that Scryfall doesn't list for the set simply
 * doesn't count, rather than pushing the bar past 100%.
 *
 * The headline percentage is over the **booster (draft)** cards only — a set reads 100%
 * once every boosterable card is implemented; the completionist extras are reported
 * separately. Sets with no booster at all (Commander decks, supplemental products) have
 * every card flagged `booster: false`, so there the whole set *is* the main pool and there
 * are no separate extras — otherwise the headline would read a useless 0/0.
 */
@Service
class SetCoverageService {

    /** One canonical card with its set-specific Scryfall art, as baked by `scripts/gen-set-totals`. */
    @Serializable
    private data class CanonicalCard(val name: String, val img: String? = null)

    /** One catalogued set's canonical card universe, as baked by `scripts/gen-set-totals`. */
    @Serializable
    private data class CanonicalSet(
        val code: String,
        val name: String,
        val releaseDate: String? = null,
        val setType: String? = null,
        val draft: List<CanonicalCard> = emptyList(),
        val extra: List<CanonicalCard> = emptyList(),
    ) {
        /**
         * Cards that drive the headline %: the booster (draft) pool when the set has one,
         * otherwise the whole set (Commander / supplemental products have no booster).
         */
        val mainCards: List<CanonicalCard> get() = draft.ifEmpty { extra }

        /** Completionist extras reported separately — only when the set also has a booster. */
        val secondaryCards: List<CanonicalCard> get() = if (draft.isEmpty()) emptyList() else extra
    }

    /** Per-set coverage row served to the Set Completion grid. */
    data class SetCoverageDTO(
        val code: String,
        val name: String,
        val releaseDate: String?,
        val setType: String?,
        val block: String?,
        /** Booster (draft) cards we've authored. Always `<= total`; drives the headline %. */
        val implemented: Int,
        /** Booster (draft) canonical card count — the headline denominator. */
        val total: Int,
        /** Completionist extras we've authored (starter exclusives, bonus sheets, Special Guests). */
        val extraImplemented: Int,
        /** Completionist extra canonical card count. */
        val extraTotal: Int,
        /** `implemented / total * 100` (booster cards), one decimal. `0.0` when [total] is `0`. */
        val percent: Double,
    )

    /** One canonical card and whether we've implemented it — for the per-set detail view. */
    data class CardCoverageDTO(
        val name: String,
        val implemented: Boolean,
        /** Set-specific Scryfall art (direct CDN URL, normal size); null if Scryfall had none. */
        val imageUri: String?,
    )

    /** A single set's full canonical card list, split into booster + extra, each marked. */
    data class SetDetailDTO(
        val code: String,
        val name: String,
        val releaseDate: String?,
        val block: String?,
        val implemented: Int,
        val total: Int,
        val extraImplemented: Int,
        val extraTotal: Int,
        val percent: Double,
        /** Booster (draft) cards, A→Z. */
        val draft: List<CardCoverageDTO>,
        /** Completionist extras, A→Z. Empty if the set has none. */
        val extra: List<CardCoverageDTO>,
    )

    private val canonical: List<CanonicalSet> =
        ClassPathResource(RESOURCE_PATH).inputStream.bufferedReader().use {
            JSON.decodeFromString<List<CanonicalSet>>(it.readText())
        }
    private val byCode: Map<String, CanonicalSet> = canonical.associateBy { it.code }

    /**
     * Coverage for every catalogued set, newest release first (then by code) —
     * mirroring the mtgish dashboard ordering.
     */
    fun coverage(): List<SetCoverageDTO> =
        canonical
            .map { c ->
                val set = MtgSetCatalog.byCode(c.code)
                val authored = authoredNames(set)
                val implemented = c.mainCards.count { frontFace(it.name) in authored }
                val extraImplemented = c.secondaryCards.count { frontFace(it.name) in authored }
                SetCoverageDTO(
                    code = c.code,
                    name = c.name,
                    releaseDate = c.releaseDate,
                    setType = c.setType,
                    block = set?.block,
                    implemented = implemented,
                    total = c.mainCards.size,
                    extraImplemented = extraImplemented,
                    extraTotal = c.secondaryCards.size,
                    percent = percent(implemented, c.mainCards.size),
                )
            }
            .sortedWith(compareByDescending<SetCoverageDTO> { it.releaseDate ?: "" }.thenBy { it.code })

    /** Full canonical card list for one set, each card marked implemented / missing. Null if unknown. */
    fun detail(code: String): SetDetailDTO? {
        val c = byCode[code.uppercase()] ?: return null
        val set = MtgSetCatalog.byCode(c.code)
        val authored = authoredNames(set)
        fun mark(cards: List<CanonicalCard>) =
            cards.map { CardCoverageDTO(it.name, frontFace(it.name) in authored, it.img) }
        val draft = mark(c.mainCards)
        val extra = mark(c.secondaryCards)
        return SetDetailDTO(
            code = c.code,
            name = c.name,
            releaseDate = c.releaseDate,
            block = set?.block,
            implemented = draft.count { it.implemented },
            total = draft.size,
            extraImplemented = extra.count { it.implemented },
            extraTotal = extra.size,
            percent = percent(draft.count { it.implemented }, draft.size),
            draft = draft,
            extra = extra,
        )
    }

    /**
     * Names we've authored for a set, matching `scripts/card-status`: every `card(...)`,
     * `basicLand(...)`, and reprint `Printing` row, reduced to front-face names.
     */
    private fun authoredNames(set: MtgSet?): Set<String> =
        set
            ?.let {
                it.cards.asSequence().map { cd -> cd.name } +
                    it.basicLands.asSequence().map { cd -> cd.name } +
                    it.printings.asSequence().map { p -> p.name }
            }
            ?.map(::frontFace)
            ?.toSet()
            ?: emptySet()

    private companion object {
        const val RESOURCE_PATH = "coverage/set-totals.json"
        val JSON = Json { ignoreUnknownKeys = true }

        fun percent(implemented: Int, total: Int): Double =
            if (total == 0) 0.0 else Math.round(implemented * 1000.0 / total) / 10.0

        /** Strip a ` // back` suffix so DFC / adventure names match canonical front-faces. */
        fun frontFace(name: String): String = name.substringBefore(" // ").trim()
    }
}
