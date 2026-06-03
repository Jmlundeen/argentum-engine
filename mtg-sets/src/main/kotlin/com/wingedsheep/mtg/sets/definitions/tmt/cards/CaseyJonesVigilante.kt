package com.wingedsheep.mtg.sets.definitions.tmt.cards

import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.ConditionalOnCollectionEffect
import com.wingedsheep.sdk.scripting.effects.CreateDelayedTriggerEffect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.MoveType
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Casey Jones, Vigilante
 * {1}{R}{R}
 * Legendary Creature — Human Berserker
 * 4/3
 *
 * When Casey Jones enters, draw three cards. At the beginning of your
 * next upkeep, discard three cards at random.
 */
val CaseyJonesVigilante = card("Casey Jones, Vigilante") {
    manaCost = "{1}{R}{R}"
    colorIdentity = "R"
    typeLine = "Legendary Creature — Human Berserker"
    oracleText = "When Casey Jones enters, draw three cards. At the beginning of your next upkeep, discard three cards at random."
    power = 4
    toughness = 3

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = CompositeEffect(
            listOf(
                Effects.DrawCards(3),
                CreateDelayedTriggerEffect(
                    step = Step.UPKEEP,
                    fireOnlyOnControllersTurn = true,
                    effect = CompositeEffect(
                        listOf(
                            GatherCardsEffect(
                                source = CardSource.FromZone(Zone.HAND, Player.You),
                                storeAs = "hand"
                            ),
                            ConditionalOnCollectionEffect(
                                collection = "hand",
                                ifNotEmpty = CompositeEffect(
                                    listOf(
                                        SelectFromCollectionEffect(
                                            from = "hand",
                                            selection = SelectionMode.Random(DynamicAmount.Fixed(3)),
                                            storeSelected = "discarded"
                                        ),
                                        MoveCollectionEffect(
                                            from = "discarded",
                                            destination = CardDestination.ToZone(Zone.GRAVEYARD, Player.You),
                                            moveType = MoveType.Discard
                                        )
                                    )
                                ),
                                ifEmpty = CompositeEffect(listOf())
                            )
                        )
                    )
                )
            )
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "88"
        artist = "Xavier Ribeiro"
        flavorText = "\"Goongala!\""
        imageUri = "https://cards.scryfall.io/normal/front/a/6/a6a3258d-2e9b-4862-b2e3-bbfae9bd4d33.jpg?1769005927"
    }
}
