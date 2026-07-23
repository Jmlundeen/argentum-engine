import { useMemo } from 'react'
import type React from 'react'
import type { ClientPlayer } from '@/types'
import { hand } from '@/types'
import { useRevealedLibraryTopCard } from '@/store/selectors'
import { Battlefield } from './Battlefield'
import { CardRow } from './HandZone'
import { CommandZone } from './CommandZone'
import { ZonePile } from './ZonePiles'
import { styles } from './styles'

/**
 * One opponent's half of the board: hand fan (top), command zone | battlefield |
 * zone piles. This is today's 2-player opponent half, parameterized by player.
 *
 * Two layouts:
 * - `grid` — the classic 2-player placement: the hand is position:fixed at the
 *   viewport top and the board area is a direct grid child on row 2. Renders the
 *   exact markup GameBoard always had, so the 2-player game is pixel-identical.
 * - `strip` — a multiplayer slide item: the component renders a full-height
 *   strip cell; the hand is absolutely positioned inside it (so it slides with
 *   the board) above a reservation band matching grid row 1, and the board area
 *   fills the rest. Card scale machinery (slot sizing) is identical in both.
 */
export function OpponentBoardArea({
  opponent,
  layout,
  topOffset,
  handReservation = 0,
  stripWidthPct = 100,
  hideHand = false,
  spectatorMode,
  isHijacking,
  hijackedSurfaceStyle,
  isAlly = false,
  allyColor,
}: {
  opponent: ClientPlayer
  layout: 'grid' | 'strip'
  topOffset: number
  /** Strip layout only: height of the hand reservation band (grid row 1 height). */
  handReservation?: number
  /**
   * Strip layout only: this cell's share of the strip width. 100 (default) for the
   * one-board sliding camera; a fraction of 100 when several boards share the strip
   * (table overview / combat defender-focus split) — card sizing self-measures per slot.
   */
  stripWidthPct?: number
  /**
   * Strip layout only: hide the opponent hand fan and its reservation band. Used when
   * several boards share the strip — the fans would overlap across the narrow cells,
   * and the rail chips already carry every hand count.
   */
  hideHand?: boolean
  spectatorMode: boolean
  /** This opponent's seat is currently driven by this client (Mindslaver / hotseat). */
  isHijacking: boolean
  hijackedSurfaceStyle?: React.CSSProperties
  /**
   * Two-Headed Giant (CR 810): this board belongs to your teammate. You may see their hand
   * (CR 810.5b), so it renders face-up, and the cell gets an "ALLY" marker so it never reads as
   * an enemy board. You still can't act with their cards — only its controller plays from it.
   */
  isAlly?: boolean
  /** Team color for the ally marker (the viewing player's team hue). */
  allyColor?: string
}) {
  const revealedTopCard = useRevealedLibraryTopCard(opponent.playerId)
  const ghostCards = useMemo(
    () => (revealedTopCard ? [revealedTopCard] : []),
    [revealedTopCard]
  )

  /* Opponent hand — fixed at top of screen in grid layout; absolute inside the
     strip cell in strip layout (a strip cell starts at the viewport top, so the
     same `top` offset lands in the same place — but the hand travels with its
     board during slides). The face-up promotion during a Mindslaver-style hijack
     is itself the strongest signal that the controller is driving this hand. */
  const handBlock = (
    <div
      data-zone="opponent-hand"
      style={{
        position: layout === 'grid' ? 'fixed' : 'absolute',
        top: topOffset,
        left: '50%',
        transform: 'translateX(-50%)',
        zIndex: 50,
      }}
    >
      <CardRow
        zoneId={hand(opponent.playerId)}
        faceDown={!isHijacking && !isAlly}
        small
        inverted
        interactive={isHijacking}
        ghostCards={isHijacking ? [] : ghostCards}
      />
    </div>
  )

  const boardBlock = (
    <div
      style={
        layout === 'grid'
          ? styles.opponentArea
          : {
              // styles.opponentArea minus the grid-row binding — the strip cell
              // provides the vertical slot instead.
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'flex-start',
              minHeight: 0,
              overflow: 'hidden',
              flex: 1,
              width: '100%',
            }
      }
    >
      <div style={{ ...styles.playerRowWithZones, alignItems: 'flex-start' }}>
        {/* Opponent command zone (left side) — Commander format only; renders nothing otherwise. */}
        <CommandZone player={opponent} isOpponent />

        <div
          style={{
            ...styles.playerMainArea,
            ...(isHijacking ? hijackedSurfaceStyle : null),
          }}
        >
          {/* Opponent battlefield - lands first (closer to opponent), then creatures */}
          <Battlefield isOpponent playerId={opponent.playerId} spectatorMode={spectatorMode} />
        </div>

        {/* Opponent deck/graveyard (right side) */}
        <ZonePile player={opponent} isOpponent />
      </div>
    </div>
  )

  if (layout === 'grid') {
    return (
      <>
        {handBlock}
        {boardBlock}
      </>
    )
  }

  return (
    <div
      data-opponent-board={opponent.playerId}
      data-ally={isAlly || undefined}
      style={{
        flex: `0 0 ${stripWidthPct}%`,
        minWidth: `${stripWidthPct}%`,
        height: '100%',
        position: 'relative',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        transition: 'flex-basis 220ms cubic-bezier(0.4, 0, 0.2, 1), min-width 220ms cubic-bezier(0.4, 0, 0.2, 1)',
      }}
    >
      {/* Two-Headed Giant ally marker — a team-colored corner badge so a teammate's board (with
          its face-up hand) is never mistaken for an opponent's. */}
      {isAlly && (
        <div
          aria-hidden
          style={{
            position: 'absolute',
            top: topOffset + 4,
            left: 10,
            zIndex: 55,
            display: 'inline-flex',
            alignItems: 'center',
            gap: 5,
            padding: '2px 9px',
            borderRadius: 999,
            border: `1px solid ${allyColor ?? '#2FD1A4'}`,
            background: 'rgba(8, 12, 18, 0.82)',
            color: allyColor ?? '#2FD1A4',
            fontSize: 10,
            fontWeight: 800,
            letterSpacing: '0.1em',
            textTransform: 'uppercase',
            pointerEvents: 'none',
            userSelect: 'none',
          }}
        >
          <span aria-hidden style={{ width: 7, height: 7, borderRadius: '50%', background: allyColor ?? '#2FD1A4' }} />
          Ally · {opponent.name}
        </div>
      )}
      {/* A hijack-controlled hand must stay visible even in shared-strip views —
          this client is playing from it. */}
      {(!hideHand || isHijacking) && handBlock}
      {/* Reservation band mirrors grid row 1 so the board area below aligns
          exactly with the 2-player opponent area (grid row 2). With the hand
          hidden (shared-strip views) a slim margin replaces it — the board
          gets the vertical space back. */}
      <div style={{ height: !hideHand || isHijacking ? handReservation : 8, flexShrink: 0 }} aria-hidden />
      {boardBlock}
    </div>
  )
}
