package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Tweeze ({2}{R}, Instant):
 *   "Tweeze deals 3 damage to any target. You may discard a card. If you do, draw a card."
 *
 * Regression: the discard-then-draw used to be a `MayEffect(CompositeEffect[discard, draw])`,
 * which would still draw a card when the discard portion silently produced no movement
 * (e.g. empty hand). Switched to `MayEffect(IfYouDoEffect(discard, draw))` so the draw is
 * properly gated on the discard succeeding.
 */
class TweezeScenarioTest : ScenarioTestBase() {

    init {
        context("Tweeze — discard-then-draw rider") {

            test("declining the discard does not draw a card") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardInHand(1, "Tweeze")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellTargetingPlayer(1, "Tweeze", 2)

                // Tweeze resolves → 3 damage to Bob → "may discard?" yes/no decision
                game.resolveStack()

                withClue("Tweeze should present a may-discard yes/no decision") {
                    game.hasPendingDecision() shouldBe true
                }

                game.answerYesNo(false)
                game.resolveStack()

                withClue("Hand should be empty — declining the discard must not draw") {
                    game.handSize(1) shouldBe 0
                }
                withClue("Graveyard should hold only Tweeze (no discard happened)") {
                    game.graveyardSize(1) shouldBe 1
                }
            }

            test("accepting the discard discards one card and draws one") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardInHand(1, "Tweeze")
                    .withCardInHand(1, "Forest") // discard fodder
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellTargetingPlayer(1, "Tweeze", 2)
                game.resolveStack()

                game.answerYesNo(true)
                game.resolveStack()

                withClue("Hand size unchanged: discarded Forest, drew Plains") {
                    game.handSize(1) shouldBe 1
                }
                withClue("Forest should be in graveyard after discard") {
                    game.isInGraveyard(1, "Forest") shouldBe true
                }
            }

            test("can target a planeswalker — damage removes loyalty counters") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardInHand(1, "Tweeze")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withCardOnBattlefield(2, "Sarkhan, the Dragonspeaker")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val sarkhanId = game.findPermanent("Sarkhan, the Dragonspeaker")!!
                game.state = game.state.updateEntity(sarkhanId) { c ->
                    val counters = c.get<CountersComponent>() ?: CountersComponent()
                    c.with(counters.withAdded(CounterType.LOYALTY, 4))
                }

                // Legal-action enumeration must offer the planeswalker as a Tweeze target
                // (regression for AnyTarget enumeration omitting planeswalkers).
                val enumerator = LegalActionEnumerator.create(cardRegistry)
                val actions = enumerator.enumerate(game.state, game.player1Id, EnumerationMode.FULL)
                val tweezeAction = actions.firstOrNull {
                    val a = it.action
                    a is CastSpell && game.state.getEntity(a.cardId)?.get<CardComponent>()?.name == "Tweeze"
                }
                withClue("Tweeze should be enumerable as a cast action") {
                    tweezeAction shouldNotBe null
                }
                withClue("Tweeze's 'any target' must include the planeswalker") {
                    tweezeAction!!.validTargets!! shouldContain sarkhanId
                }

                val castResult = game.castSpell(1, "Tweeze", sarkhanId)
                withClue("Casting Tweeze targeting the planeswalker should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                game.resolveStack()
                // Decline the may-discard rider so the test focuses on the damage redirect.
                game.answerYesNo(false)
                game.resolveStack()

                val counters = game.state.getEntity(sarkhanId)?.get<CountersComponent>()
                withClue("Sarkhan should have 1 loyalty after 3 damage from Tweeze (4 - 3)") {
                    counters?.getCount(CounterType.LOYALTY) shouldBe 1
                }
                withClue("Sarkhan should still be on the battlefield with 1 loyalty") {
                    game.isOnBattlefield("Sarkhan, the Dragonspeaker") shouldBe true
                }
            }

            test("accepting with empty hand: nothing discarded, no draw") {
                // Regression for the original bug: with the old
                // MayEffect(CompositeEffect[discard, draw]) wiring, saying yes still
                // drew a card even though no card was discarded.
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardInHand(1, "Tweeze")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Hand before cast: [Tweeze]; after cast: []
                game.castSpellTargetingPlayer(1, "Tweeze", 2)
                game.resolveStack()

                // Accept the may, but no card is available to discard.
                game.answerYesNo(true)
                game.resolveStack()

                withClue("No draw when nothing was actually discarded") {
                    game.handSize(1) shouldBe 0
                }
                withClue("Graveyard should hold only Tweeze (nothing else moved)") {
                    game.graveyardSize(1) shouldBe 1
                }
            }
        }
    }
}
