package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for a batch of Wilds of Eldraine cards implemented together. All five are pure
 * composition over existing SDK primitives, so these tests pin the *composition* — the bits that
 * could plausibly be wired wrong:
 *
 *  - Witch's Mark ({1}{R} sorcery) — an optional loot half and an optional-target Wicked Role
 *    half in one spell; the two must be independent (declining the loot still makes the Role).
 *  - Return Triumphant ({1}{W} sorcery) — reanimate, then attach a Young Hero Role to *the same*
 *    card. This is the risky one: it relies on entity identity surviving the graveyard →
 *    battlefield move so the second effect finds a battlefield host.
 *  - Verdant Outrider ({2}{G} 4/2) — an activated, until-end-of-turn `CantBeBlockedBy` on itself.
 *  - Unassuming Sage ({1}{W} 2/2) — untargeted "you may pay {2}" ETB that crowns itself.
 *  - Tattered Ratter ({1}{R} 2/2) — ANY-bound "a Rat you control becomes blocked" pumping the
 *    blocked Rat, which is generally *not* the Ratter.
 */
class WoeCardsBatch6ScenarioTest : ScenarioTestBase() {

    private val outriderAbilityId by lazy {
        cardRegistry.requireCard("Verdant Outrider").activatedAbilities[0].id
    }

    init {
        context("Witch's Mark — loot half and Role half are independent") {
            test("accepting the discard draws two and still creates the Wicked Role") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withCardInHand(1, "Witch's Mark")
                    .withCardInHand(1, "Mountain")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bear = game.findPermanent("Grizzly Bears")!!
                val spareMountain = game.findCardsInHand(1, "Mountain").first()

                // Hand after casting: the spare Mountain only (Witch's Mark goes to the stack).
                game.castSpell(1, "Witch's Mark", bear)
                game.resolveStack()

                // "You may discard a card." — say yes; with a single card in hand the engine
                // auto-selects it, so only prompt-driven cases need an explicit selection.
                game.getPendingDecision() shouldNotBe null
                game.answerYesNo(true)
                if (game.isInHand(1, "Mountain")) game.selectCards(listOf(spareMountain))
                game.resolveStack()

                withClue("discarded 1, drew 2 -> net +1 card in hand") {
                    game.handSize(1) shouldBe 2
                }
                withClue("Mountain went to the graveyard") {
                    game.isInGraveyard(1, "Mountain") shouldBe true
                }
                val role = game.findPermanent("Wicked Role")
                withClue("the Wicked Role token was created") { role shouldNotBe null }
                withClue("the Role is attached to the targeted Bear") {
                    game.state.getEntity(role!!)?.get<AttachedToComponent>()?.targetId shouldBe bear
                }
                withClue("2/2 Bear + Wicked Role's +1/+1 = 3/3") {
                    game.state.projectedState.getPower(bear) shouldBe 3
                    game.state.projectedState.getToughness(bear) shouldBe 3
                }
            }

            test("declining the discard skips the draw but still creates the Wicked Role") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withCardInHand(1, "Witch's Mark")
                    .withCardInHand(1, "Mountain")
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bear = game.findPermanent("Grizzly Bears")!!
                game.castSpell(1, "Witch's Mark", bear)
                game.resolveStack()

                game.answerYesNo(false)
                game.resolveStack()

                withClue("no discard and no draw — the spare Mountain is untouched") {
                    game.handSize(1) shouldBe 1
                    game.isInHand(1, "Mountain") shouldBe true
                }
                withClue("the Role half is independent of the loot half") {
                    game.findPermanent("Wicked Role") shouldNotBe null
                    game.state.projectedState.getPower(bear) shouldBe 3
                }
            }
        }

        context("Return Triumphant — reanimate, then crown the same creature") {
            test("the returned creature enters and gets a Young Hero Role attached to it") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInGraveyard(1, "Grizzly Bears")
                    .withCardInHand(1, "Return Triumphant")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellTargetingGraveyardCard(1, "Return Triumphant", 1, "Grizzly Bears")
                game.resolveStack()

                val bear = game.findPermanent("Grizzly Bears")
                withClue("mana value 2 <= 3, so the Bears come back") { bear shouldNotBe null }
                withClue("it left the graveyard") { game.isInGraveyard(1, "Grizzly Bears") shouldBe false }

                val role = game.findPermanent("Young Hero Role")
                withClue("the Role token was created") { role shouldNotBe null }
                withClue("attached to the creature this spell just reanimated") {
                    game.state.getEntity(role!!)?.get<AttachedToComponent>()?.targetId shouldBe bear
                }
                withClue("the Young Hero Role is stats-neutral — the Bears are still 2/2") {
                    game.state.projectedState.getPower(bear!!) shouldBe 2
                    game.state.projectedState.getToughness(bear) shouldBe 2
                }
            }
        }

        context("Verdant Outrider — activated blocking restriction") {
            test("after activating, a power-2 creature can't block it") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Verdant Outrider", summoningSickness = false)
                    .withCardOnBattlefield(2, "Grizzly Bears", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val outrider = game.findPermanent("Verdant Outrider")!!

                game.execute(
                    ActivateAbility(playerId = game.player1Id, sourceId = outrider, abilityId = outriderAbilityId)
                ).error shouldBe null
                game.resolveStack()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Verdant Outrider" to 2)).error shouldBe null
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

                withClue("the 2/2 Bears has power 2, so the restriction forbids the block") {
                    game.declareBlockers(
                        mapOf("Grizzly Bears" to listOf("Verdant Outrider"))
                    ).error shouldNotBe null
                }
            }

            test("without activating, the same creature blocks fine") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Verdant Outrider", summoningSickness = false)
                    .withCardOnBattlefield(2, "Grizzly Bears", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Verdant Outrider" to 2)).error shouldBe null
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)

                game.declareBlockers(
                    mapOf("Grizzly Bears" to listOf("Verdant Outrider"))
                ).error shouldBe null
            }
        }

        context("Unassuming Sage — optional {2} for a self-attached Sorcerer Role") {
            test("paying {2} crowns the Sage itself") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Unassuming Sage")
                    .withLandsOnBattlefield(1, "Plains", 4)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Unassuming Sage")
                game.resolveStack()

                // The ETB trigger offers the {2}: say yes, then auto-tap for it.
                game.answerYesNo(true)
                if (game.getPendingDecision() != null) game.submitManaSourcesAutoPay()
                game.resolveStack()

                val sage = game.findPermanent("Unassuming Sage")!!
                val role = game.findPermanent("Sorcerer Role")
                withClue("the Sorcerer Role token was created") { role shouldNotBe null }
                withClue("\"attached to it\" means the Sage itself, not a target") {
                    game.state.getEntity(role!!)?.get<AttachedToComponent>()?.targetId shouldBe sage
                }
                withClue("2/2 base + the Sorcerer Role's +1/+1 = 3/3") {
                    game.state.projectedState.getPower(sage) shouldBe 3
                    game.state.projectedState.getToughness(sage) shouldBe 3
                }
            }

            test("declining the {2} leaves the Sage a plain 2/2") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Unassuming Sage")
                    .withLandsOnBattlefield(1, "Plains", 4)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Unassuming Sage")
                game.resolveStack()

                game.answerYesNo(false)
                game.resolveStack()

                val sage = game.findPermanent("Unassuming Sage")!!
                withClue("declining skips the Role entirely") {
                    game.findPermanent("Sorcerer Role") shouldBe null
                    game.state.projectedState.getPower(sage) shouldBe 2
                    game.state.projectedState.getToughness(sage) shouldBe 2
                }
            }
        }

        context("Tattered Ratter — pumps whichever Rat you control got blocked") {
            test("a blocked Rat gets +2/+0, not the Ratter") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Tattered Ratter", summoningSickness = false)
                    .withCardOnBattlefield(1, "Mind Drill Assailant", summoningSickness = false)
                    .withCardOnBattlefield(2, "Grizzly Bears", summoningSickness = false)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Mind Drill Assailant is a 2/5 Rat Warlock; the Ratter itself is a Human Peasant.
                val rat = game.findPermanent("Mind Drill Assailant")!!
                val ratter = game.findPermanent("Tattered Ratter")!!

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Mind Drill Assailant" to 2, "Tattered Ratter" to 2)).error shouldBe null
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareBlockers(
                    mapOf("Grizzly Bears" to listOf("Mind Drill Assailant"))
                ).error shouldBe null
                game.resolveStack()

                withClue("the blocked Rat gets +2/+0 (2/5 -> 4/5)") {
                    game.state.projectedState.getPower(rat) shouldBe 4
                    game.state.projectedState.getToughness(rat) shouldBe 5
                }
                withClue("the unblocked, non-Rat Ratter is untouched") {
                    game.state.projectedState.getPower(ratter) shouldBe 2
                }
            }

            test("a blocked non-Rat doesn't trigger it") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Tattered Ratter", summoningSickness = false)
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withCardOnBattlefield(2, "Craw Wurm", summoningSickness = false)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val attackingBear = game.findPermanent("Grizzly Bears")!!

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Grizzly Bears" to 2)).error shouldBe null
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareBlockers(mapOf("Craw Wurm" to listOf("Grizzly Bears"))).error shouldBe null
                game.resolveStack()

                withClue("Bears aren't Rats — no pump") {
                    game.state.projectedState.getPower(attackingBear) shouldBe 2
                }
            }
        }
    }
}
