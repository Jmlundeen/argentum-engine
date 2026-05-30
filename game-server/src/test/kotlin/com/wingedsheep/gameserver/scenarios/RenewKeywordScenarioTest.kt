package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * End-to-end scenario tests for the Renew keyword (Tarkir: Dragonstorm, Sultai).
 *
 * Renew is a graveyard-activated ability: "[cost], Exile this card from your graveyard:
 * [effect]. Activate only as a sorcery." It is composed entirely from existing primitives —
 * the mana cost plus [com.wingedsheep.sdk.scripting.AbilityCost.ExileSelf],
 * `activateFromZone = GRAVEYARD`, and `timing = SorcerySpeed` — via the `renew(cost) { … }`
 * DSL helper. These tests drive the full activate → pay → exile → resolve pipeline with an
 * inline test card so we don't depend on any specific TDM card being implemented yet.
 */
class RenewKeywordScenarioTest : ScenarioTestBase() {

    /** "Renew — {1}{G}, Exile this card from your graveyard: Draw a card. Activate only as a sorcery." */
    private val renewingOoze = card("Renewing Ooze") {
        manaCost = "{1}{G}"
        typeLine = "Creature — Ooze"
        power = 2
        toughness = 2
        renew("{1}{G}") {
            effect = Effects.DrawCards(1)
        }
    }

    init {
        cardRegistry.register(renewingOoze)

        context("Renew — {1}{G}, Exile this card from your graveyard: Draw a card") {

            test("activating from the graveyard at sorcery speed draws a card and exiles the card as a cost") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInGraveyard(1, "Renewing Ooze")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Two green covers {1}{G}: one green pays the colored pip, one pays the generic.
                game.state = game.state.updateEntity(game.player1Id) { c ->
                    c.with(ManaPoolComponent(green = 2))
                }

                val oozeId = game.findCardsInGraveyard(1, "Renewing Ooze").single()
                val ability = cardRegistry.getCard("Renewing Ooze")!!.script.activatedAbilities[0]
                val handBefore = game.handSize(1)

                val result = game.execute(
                    ActivateAbility(playerId = game.player1Id, sourceId = oozeId, abilityId = ability.id)
                )
                withClue("Activation should succeed: ${result.error}") { result.error shouldBe null }
                game.resolveStack()

                withClue("Renew's effect resolved — drew a card") {
                    game.handSize(1) shouldBe handBefore + 1
                }
                withClue("Card left the graveyard") {
                    game.isInGraveyard(1, "Renewing Ooze") shouldBe false
                }
                withClue("Card was exiled as part of the cost") {
                    game.state.getExile(game.player1Id).contains(oozeId) shouldBe true
                }
            }

            test("renew is not offered as a legal action during the upkeep (\"only as a sorcery\")") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInGraveyard(1, "Renewing Ooze")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withActivePlayer(1)
                    .inPhase(Phase.BEGINNING, Step.UPKEEP)
                    .build()

                val oozeId = game.findCardsInGraveyard(1, "Renewing Ooze").single()
                val enumerator = LegalActionEnumerator.create(cardRegistry)
                val renewActions = enumerator.enumerate(game.state, game.player1Id)
                    .filter { it.action is ActivateAbility && (it.action as ActivateAbility).sourceId == oozeId }

                withClue("Renew must not be activatable at upkeep") {
                    renewActions shouldBe emptyList()
                }
            }
        }
    }
}
