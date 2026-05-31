package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.layers.ActiveFloatingEffect
import com.wingedsheep.engine.mechanics.layers.FloatingEffectData
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.mechanics.layers.addFloatingEffects
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.SuspendedComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.Duration
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Mechanic-level tests for Suspend (CR 702.62), driven by the [SuspendedComponent] marker
 * the engine reads off an exiled card (set by the [com.wingedsheep.sdk.dsl.Effects.Suspend]
 * chain). Unlike Impending / Vanishing, suspend lives in **exile**: a time counter is removed
 * at the beginning of the owner's upkeep, and when the last is gone the card is played for
 * free — with haste if it's a creature.
 *
 * The setup places a creature card directly into exile with the marker + N time counters
 * (the state a `Suspend` chain produces) and verifies the countdown-and-cast the engine
 * synthesizes from that marker.
 */
class SuspendMechanicTest : FunSpec({

    val suspendedBear = card("Suspended Bear") {
        manaCost = "{1}{G}"
        typeLine = "Creature — Bear"
        power = 2
        toughness = 2
    }

    val projector = StateProjector()

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(suspendedBear))
        return driver
    }

    fun timeCounters(driver: GameTestDriver, perm: EntityId): Int =
        driver.state.getEntity(perm)?.get<CountersComponent>()?.getCount(CounterType.TIME) ?: 0

    /**
     * Put [cardName] into [owner]'s exile in the state a real [com.wingedsheep.sdk.dsl.Effects.Suspend]
     * produces: the suspended marker, [timeCounters] time counters, and the dormant permanent
     * haste effect (which `GrantSuspendExecutor` arms; verified independently in the Taigam
     * scenario test). The haste effect lies inert in exile and should apply once the card is
     * played onto the battlefield as the same entity.
     */
    fun suspendInExile(driver: GameTestDriver, owner: EntityId, cardName: String, timeCounters: Int): EntityId {
        val cardId = driver.putCardInGraveyard(owner, cardName)
        var s = driver.state
            .removeFromZone(ZoneKey(owner, Zone.GRAVEYARD), cardId)
            .addToZone(ZoneKey(owner, Zone.EXILE), cardId)
            .updateEntity(cardId) { container ->
                container
                    .with(SuspendedComponent)
                    .with(CountersComponent().withAdded(CounterType.TIME, timeCounters))
            }
        s = s.addFloatingEffects(
            listOf(
                ActiveFloatingEffect(
                    id = EntityId.generate(),
                    effect = FloatingEffectData(
                        layer = Layer.ABILITY,
                        modification = SerializableModification.GrantKeyword(Keyword.HASTE.name),
                        affectedEntities = setOf(cardId),
                    ),
                    duration = Duration.Permanent,
                    sourceId = cardId,
                    sourceName = cardName,
                    controllerId = owner,
                    timestamp = s.timestamp,
                )
            )
        )
        driver.replaceState(s)
        return cardId
    }

    /**
     * Advance to the owner's next upkeep — passing through any intervening turns — and resolve
     * the suspend countdown trigger that fires there. Steps off the current upkeep first (via
     * the precombat main) so consecutive calls keep moving forward.
     */
    fun resolveNextOwnerUpkeep(driver: GameTestDriver, owner: EntityId) {
        do {
            driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
            driver.passPriorityUntil(Step.UPKEEP)
        } while (driver.activePlayer != owner)
        driver.bothPass() // resolve the suspend countdown trigger
    }

    test("a suspended card sits in exile with its time counters") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val player = driver.activePlayer!!
        val cardId = suspendInExile(driver, player, "Suspended Bear", timeCounters = 2)

        driver.getExile(player).contains(cardId) shouldBe true
        timeCounters(driver, cardId) shouldBe 2
        driver.state.getEntity(cardId)?.has<SuspendedComponent>() shouldBe true
    }

    test("a time counter is removed at the beginning of the owner's upkeep") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val player = driver.activePlayer!!
        val cardId = suspendInExile(driver, player, "Suspended Bear", timeCounters = 2)

        // Opponent's upkeep first — the owner's countdown must NOT fire on it.
        driver.passPriorityUntil(Step.UPKEEP)
        driver.activePlayer shouldNotBe player
        timeCounters(driver, cardId) shouldBe 2

        // The owner's next upkeep removes one.
        resolveNextOwnerUpkeep(driver, player)
        timeCounters(driver, cardId) shouldBe 1
        driver.getExile(player).contains(cardId) shouldBe true
    }

    test("when the last time counter is removed the card is played for free with haste") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40))
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val player = driver.activePlayer!!
        val cardId = suspendInExile(driver, player, "Suspended Bear", timeCounters = 2)

        // First owner upkeep: 2 -> 1, still in exile.
        resolveNextOwnerUpkeep(driver, player)
        timeCounters(driver, cardId) shouldBe 1
        driver.getExile(player).contains(cardId) shouldBe true

        // Second owner upkeep: 1 -> 0, the suspend trigger offers to play it for free.
        resolveNextOwnerUpkeep(driver, player)
        // CR 702.62f — "they may play it." Accept, then resolve the cast.
        driver.submitYesNo(player, true)
        driver.bothPass()

        driver.getExile(player).contains(cardId) shouldBe false
        val perm = driver.findPermanent(player, "Suspended Bear")
        perm shouldNotBe null
        // CR 702.62g — a creature played via suspend has haste.
        projector.project(driver.state).hasKeyword(perm!!, Keyword.HASTE) shouldBe true
        // The marker is gone once it leaves exile.
        driver.state.getEntity(perm)?.has<SuspendedComponent>() shouldBe false
    }
})
