package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.stack.CopyTargetSpellExecutor
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Supertype
import com.wingedsheep.sdk.core.TypeLine
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.CopyTargetSpellEffect
import com.wingedsheep.sdk.scripting.effects.DrawCardsEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class CopyTargetSpellWithModificationCopyIsnTLegendaryTest : FunSpec({

    val cardRegistry = CardRegistry()
    val executor = CopyTargetSpellExecutor(cardRegistry)

    test("copying a legendary creature spell with stripSupertypes=true produces a non-legendary copy on the stack") {
        // GIVEN a legendary creature spell on the stack
        val playerId = EntityId.generate()
        val opponentId = EntityId.generate()
        val spellId = EntityId.generate()

        val legendaryCreatureCard = CardComponent(
            cardDefinitionId = "Test Legendary Creature",
            name = "Test Legendary Creature",
            manaCost = ManaCost(emptyList()),
            typeLine = TypeLine(
                supertypes = setOf(Supertype.LEGENDARY),
                cardTypes = setOf(CardType.CREATURE)
            ),
            ownerId = playerId,
            spellEffect = DrawCardsEffect(0)
        )

        val spellContainer = ComponentContainer()
            .with(legendaryCreatureCard)
            .with(SpellOnStackComponent(casterId = playerId))

        val state = GameState()
            .withEntity(playerId, ComponentContainer())
            .withEntity(opponentId, ComponentContainer())
            .withEntity(spellId, spellContainer)
            .pushToStack(spellId)

        // AND an engine-level CopyTargetSpell effect with stripSupertypes=true
        val effect = CopyTargetSpellEffect(
            target = EffectTarget.ContextTarget(0),
            stripSupertypes = true
        )
        val context = EffectContext(
            sourceId = null,
            controllerId = playerId,
            opponentId = opponentId,
            targets = listOf(ChosenTarget.Spell(spellId))
        )
        val stackBefore = state.stack.toSet()

        // WHEN the CopyTargetSpell effect resolves
        val result = executor.execute(state, effect, context)

        // THEN execution succeeds
        result.isSuccess shouldBe true

        val newState = result.state
        val copyId = newState.stack.find { it !in stackBefore }
        copyId shouldNotBe null

        // AND the copy has a type line (i.e. is a full spell entity, not a mere triggered ability)
        val copyCard = newState.getEntity(copyId!!)!!.get<CardComponent>()
        copyCard shouldNotBe null

        // AND the copy's type line does NOT contain Legendary
        copyCard!!.typeLine.supertypes shouldNotContain Supertype.LEGENDARY

        // AND the copy retains Creature as a card type
        copyCard.typeLine.cardTypes shouldContain CardType.CREATURE

        // AND the original legendary spell on the stack is unchanged
        val originalCard = newState.getEntity(spellId)!!.get<CardComponent>()!!
        originalCard.typeLine.supertypes shouldContain Supertype.LEGENDARY
    }
})
