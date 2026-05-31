package com.wingedsheep.engine.handlers.continuations

import com.wingedsheep.engine.core.AmassContinuation
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.EffectContinuation
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.handlers.effects.player.AmassResolution
import com.wingedsheep.engine.state.GameState

/**
 * Resumes "amass [subtype] N" after the controller picks which Army to amass (CR 701.47a).
 *
 * Reached only when the controller has more than one Army. The chosen Army gets the +1/+1 counters
 * and becomes the subtype if it isn't already, via the shared [AmassResolution].
 */
class AmassContinuationResumer(
    private val services: EngineServices
) : ContinuationResumerModule {

    override fun resumers(): List<ContinuationResumer<*>> = listOf(
        resumer(AmassContinuation::class, ::resumeAmass)
    )

    private fun resumeAmass(
        state: GameState,
        continuation: AmassContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is CardsSelectedResponse) {
            return ExecutionResult.error(state, "Expected an Army selection for amass")
        }

        val chosen = response.selectedCards.firstOrNull { it in continuation.candidates }
            ?: continuation.candidates.first()

        val result = AmassResolution.applyToArmy(
            state = state,
            armyId = chosen,
            controllerId = continuation.controllerId,
            opponentId = continuation.opponentId,
            subtype = continuation.subtype,
            amount = continuation.amount,
            sourceId = continuation.sourceId,
            executeEffect = services.effectExecutorRegistry::execute
        )

        // The synchronous Amass paths thread `updatedCollections` (the AmassedArmy pipeline
        // slot) back through the composite executor. The multi-Army resume path runs after
        // the parent composite has already pushed its EffectContinuation, so we inject the
        // slot into that pending frame directly — same pattern as LibraryAndZoneContinuationResumer.
        val stateWithArmyExposed = if (result.updatedCollections.isNotEmpty()) {
            val nextFrame = result.state.peekContinuation()
            if (nextFrame is EffectContinuation) {
                val (_, stateAfterPop) = result.state.popContinuation()
                stateAfterPop.pushContinuation(
                    nextFrame.copy(
                        effectContext = nextFrame.effectContext.copy(
                            pipeline = nextFrame.effectContext.pipeline.copy(
                                storedCollections = nextFrame.effectContext.pipeline.storedCollections + result.updatedCollections
                            )
                        )
                    )
                )
            } else {
                result.state
            }
        } else {
            result.state
        }

        return checkForMore(stateWithArmyExposed, result.events)
    }
}
