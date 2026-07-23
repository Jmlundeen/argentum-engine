import { useState, useEffect, useRef } from 'react'
import { useGameStore } from '@/store/gameStore.ts'
import type { DecisionSelectionState } from '@/store/slices'
import type { EntityId, ChooseTargetsDecision } from '@/types'
import { useResponsive } from '@/hooks/useResponsive.ts'
import { getCardImageUrl } from '@/utils/cardImages.ts'
import { DecisionCardPreview } from './DecisionComponents'
import { DraggableBanner } from './DraggableBanner'
import styles from './DecisionUI.module.css'

/**
 * Battlefield targeting UI for ChooseTargetsDecision (non-player, non-graveyard targets).
 * Shows a side banner with Confirm/Decline buttons, uses decisionSelectionState for toggle-to-select.
 */
export function BattlefieldTargetingUI({
  decision,
}: {
  decision: ChooseTargetsDecision
}) {
  const startDecisionSelection = useGameStore((s) => s.startDecisionSelection)
  const decisionSelectionState = useGameStore((s) => s.decisionSelectionState)
  const cancelDecisionSelection = useGameStore((s) => s.cancelDecisionSelection)
  const submitTargetsDecision = useGameStore((s) => s.submitTargetsDecision)
  const submitCancelDecision = useGameStore((s) => s.submitCancelDecision)
  const gameState = useGameStore((s) => s.gameState)
  const [isHoveringSource, setIsHoveringSource] = useState(false)
  const responsive = useResponsive()

  // Multi-requirement state: track which requirement we're on and accumulated targets
  const [currentReqIndex, setCurrentReqIndex] = useState(0)
  const [collectedTargets, setCollectedTargets] = useState<Record<number, readonly EntityId[]>>({})
  // Picks to pre-select when the requirement changes because the player stepped Back;
  // consumed (and cleared) by the selection-state effect below.
  const restoredSelectionRef = useRef<readonly EntityId[] | null>(null)

  const totalRequirements = decision.targetRequirements.length
  const targetReq = decision.targetRequirements[currentReqIndex]
  const minTargets = targetReq?.minTargets ?? 1
  const maxTargets = targetReq?.maxTargets ?? 1
  const legalTargets = decision.legalTargets[currentReqIndex] ?? []

  // For multi-requirement, exclude already-selected targets from valid options
  const alreadySelected = Object.values(collectedTargets).flat()
  const filteredLegalTargets = legalTargets.filter((id) => !alreadySelected.includes(id))

  // Look up source card image from game state
  const sourceId = decision.context.sourceId
  const sourceCard = sourceId ? gameState?.cards[sourceId] : undefined
  const sourceImageUrl = sourceCard ? getCardImageUrl(sourceCard.name, sourceCard.imageUri) : undefined

  // Start decision selection state when this component mounts or requirement changes
  useEffect(() => {
    const selectionState: DecisionSelectionState = {
      decisionId: decision.id,
      validOptions: [...filteredLegalTargets],
      selectedOptions: restoredSelectionRef.current ? [...restoredSelectionRef.current] : [],
      minSelections: minTargets,
      maxSelections: maxTargets,
      prompt: targetReq?.description ?? decision.prompt,
    }
    restoredSelectionRef.current = null
    startDecisionSelection(selectionState)

    return () => {
      cancelDecisionSelection()
    }
  }, [decision.id, currentReqIndex])

  const selectedCount = decisionSelectionState?.selectedOptions.length ?? 0
  const canConfirm = selectedCount >= minTargets && selectedCount <= maxTargets
  const canDecline = minTargets === 0

  const handleConfirm = () => {
    if (canConfirm && decisionSelectionState) {
      const updatedTargets = { ...collectedTargets, [currentReqIndex]: decisionSelectionState.selectedOptions }

      if (currentReqIndex + 1 < totalRequirements) {
        // More requirements — advance to the next one
        setCollectedTargets(updatedTargets)
        cancelDecisionSelection()
        setCurrentReqIndex(currentReqIndex + 1)
      } else {
        // All requirements satisfied — submit
        submitTargetsDecision(updatedTargets)
        cancelDecisionSelection()
      }
    }
  }

  const handleDecline = () => {
    const updatedTargets = { ...collectedTargets, [currentReqIndex]: [] as EntityId[] }
    if (currentReqIndex + 1 < totalRequirements) {
      setCollectedTargets(updatedTargets)
      cancelDecisionSelection()
      setCurrentReqIndex(currentReqIndex + 1)
    } else {
      submitTargetsDecision(updatedTargets)
      cancelDecisionSelection()
    }
  }

  const handleCancel = () => {
    cancelDecisionSelection()
    submitCancelDecision()
  }

  const handleBack = () => {
    // Step back to the previous requirement, restoring its confirmed picks so the
    // player can revise them. The current requirement's in-progress picks are
    // discarded; its pool is recomputed on re-confirm against the revised selection.
    if (currentReqIndex === 0) return
    const prevIndex = currentReqIndex - 1
    restoredSelectionRef.current = collectedTargets[prevIndex] ?? []
    const remaining = { ...collectedTargets }
    delete remaining[prevIndex]
    setCollectedTargets(remaining)
    cancelDecisionSelection()
    setCurrentReqIndex(prevIndex)
  }

  const requirementLabel = totalRequirements > 1
    ? `Choose Target (${currentReqIndex + 1}/${totalRequirements})`
    : 'Choose Target'

  const promptText = targetReq?.description ?? decision.prompt

  return (
    <DraggableBanner className={styles.sideBannerSelection}>
      {sourceImageUrl && (
        <img
          src={sourceImageUrl}
          alt={`Source: ${decision.context.sourceName ?? 'card'}`}
          className={styles.bannerCardImage}
          onMouseEnter={() => setIsHoveringSource(true)}
          onMouseLeave={() => setIsHoveringSource(false)}
        />
      )}
      {isHoveringSource && sourceCard && !responsive.isMobile && (
        <DecisionCardPreview cardName={sourceCard.name} imageUri={sourceCard.imageUri} />
      )}
      <div className={styles.bannerTitleSelection}>
        {requirementLabel}
      </div>
      {decision.context.effectHint && (
        <div className={styles.effectHint}>
          {decision.context.effectHint}
        </div>
      )}
      <div className={styles.hint}>
        {promptText}
      </div>
      <div className={styles.hint}>
        {`${selectedCount} / ${maxTargets} selected`}
      </div>

      <div className={styles.buttonContainerSmall}>
        {currentReqIndex > 0 && (
          <button onClick={handleBack} className={`${styles.confirmButton} ${styles.confirmButtonSmall}`}>
            ← Back
          </button>
        )}
        {canDecline && selectedCount === 0 && (
          <button onClick={handleDecline} className={`${styles.confirmButton} ${styles.confirmButtonSmall}`}>
            Decline
          </button>
        )}
        {selectedCount > 0 && (
          <button
            onClick={handleConfirm}
            disabled={!canConfirm}
            className={`${styles.confirmButton} ${styles.confirmButtonSmall}`}
          >
            Confirm ({selectedCount})
          </button>
        )}
        {decision.canCancel && (
          <button onClick={handleCancel} className={`${styles.confirmButton} ${styles.confirmButtonSmall}`}>
            Cancel
          </button>
        )}
      </div>
    </DraggableBanner>
  )
}
