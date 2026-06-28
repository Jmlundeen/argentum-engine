/**
 * Full player-stats dashboard, split out from the (now lean) profile page. Everything analytic lives
 * here: win record, ranked rating over time, the colors / card-types / mana-curve / creature-types
 * you play, your most-played cards, head-to-head record, and tournament finishes — rendered with
 * recharts. Data comes from the per-user `/api/stats/me/*` endpoints; the page is account-gated.
 */
import { useEffect, useState } from 'react'
import type React from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import {
  type AccountStats,
  type CardStat,
  type HeadToHead,
  type RankedModeName,
  type RatingEntry,
  type RatingPoint,
  type StatBucket,
  type UserTournamentEntry,
  fetchCardTypes,
  fetchColorStats,
  fetchCreatureTypes,
  fetchManaCurve,
  fetchModeStats,
  fetchOpponents,
  fetchRatings,
  fetchRatingsHistory,
  fetchSetStats,
  fetchStats,
  fetchTopCards,
  fetchTournamentHistory,
} from '@/api/account'
import { colorForIdentity, colorLabel } from '@/components/admin/statFormat'
import { useAuthStore } from '@/store/authStore'

const MODE_LABELS: Record<RankedModeName, string> = {
  LIMITED: 'Limited',
  CONSTRUCTED: 'Constructed',
  COMMANDER: 'Commander',
}
const MODE_COLORS: Record<RankedModeName, string> = {
  LIMITED: '#5bd1a0',
  CONSTRUCTED: '#5b6ee1',
  COMMANDER: '#d18b5b',
}

/** Card-type slice colours, falling back to a rotating palette for anything unexpected. */
const TYPE_COLORS: Record<string, string> = {
  Creature: '#5bd1a0',
  Instant: '#5bb8d1',
  Sorcery: '#d15b9a',
  Artifact: '#b0b6c0',
  Enchantment: '#d1b85b',
  Planeswalker: '#d18b5b',
  Land: '#8a7a5b',
  Other: '#7a7f8c',
}
const PALETTE = ['#5b6ee1', '#5bd1a0', '#d15b9a', '#d1b85b', '#5bb8d1', '#d18b5b', '#9a7ad1', '#b0b6c0']

export function StatsPage() {
  const navigate = useNavigate()
  const status = useAuthStore((s) => s.status)
  const init = useAuthStore((s) => s.init)

  const [stats, setStats] = useState<AccountStats | null>(null)
  const [colors, setColors] = useState<StatBucket[]>([])
  const [cardTypes, setCardTypes] = useState<StatBucket[]>([])
  const [curve, setCurve] = useState<StatBucket[]>([])
  const [creatureTypes, setCreatureTypes] = useState<StatBucket[]>([])
  const [sets, setSets] = useState<StatBucket[]>([])
  const [modes, setModes] = useState<StatBucket[]>([])
  const [opponents, setOpponents] = useState<HeadToHead[]>([])
  const [topCards, setTopCards] = useState<CardStat[]>([])
  const [tournaments, setTournaments] = useState<UserTournamentEntry[]>([])
  const [ratings, setRatings] = useState<RatingEntry[]>([])
  const [ratingHistory, setRatingHistory] = useState<RatingPoint[]>([])

  useEffect(() => {
    if (status === 'idle') void init()
  }, [status, init])

  useEffect(() => {
    if (status !== 'authenticated') return
    void fetchStats().then(setStats).catch(() => setStats(null))
    void fetchColorStats().then(setColors).catch(() => setColors([]))
    void fetchCardTypes().then(setCardTypes).catch(() => setCardTypes([]))
    void fetchManaCurve().then(setCurve).catch(() => setCurve([]))
    void fetchCreatureTypes(12).then(setCreatureTypes).catch(() => setCreatureTypes([]))
    void fetchSetStats().then(setSets).catch(() => setSets([]))
    void fetchModeStats().then(setModes).catch(() => setModes([]))
    void fetchOpponents().then(setOpponents).catch(() => setOpponents([]))
    void fetchTopCards(24).then(setTopCards).catch(() => setTopCards([]))
    void fetchTournamentHistory(15).then(setTournaments).catch(() => setTournaments([]))
    void fetchRatings().then(setRatings).catch(() => setRatings([]))
    void fetchRatingsHistory().then(setRatingHistory).catch(() => setRatingHistory([]))
  }, [status])

  if (status !== 'authenticated') {
    return (
      <div style={styles.wrap}>
        <div style={styles.container}>
          <button type="button" style={styles.link} onClick={() => navigate('/profile')}>
            ← Profile
          </button>
          <h1 style={styles.title}>Your stats</h1>
          <p style={styles.muted}>
            {status === 'idle' || status === 'loading' ? 'Loading…' : 'Sign in to see your stats.'}
          </p>
        </div>
      </div>
    )
  }

  const hasData = (stats?.games ?? 0) > 0

  return (
    <div style={styles.wrap}>
      <div style={styles.container}>
        <div style={styles.header}>
          <button type="button" style={styles.link} onClick={() => navigate('/profile')}>
            ← Profile
          </button>
          <button type="button" style={styles.link} onClick={() => navigate('/')}>
            Home
          </button>
        </div>
        <h1 style={styles.title}>Your stats</h1>

        {!hasData && <p style={styles.muted}>Play some games to start building your stats.</p>}

        {/* Overview: record + win-rate donut */}
        <div style={styles.overviewRow}>
          <div style={styles.tiles}>
            <Stat label="Games" value={stats?.games ?? 0} />
            <Stat label="Wins" value={stats?.wins ?? 0} />
            <Stat label="Losses" value={stats?.losses ?? 0} />
            <Stat label="Win rate" value={stats ? `${Math.round(stats.winRate * 100)}%` : '—'} />
          </div>
          {hasData && stats && (
            <div style={styles.donutCard}>
              <ResponsiveContainer width="100%" height={150}>
                <PieChart>
                  <Pie
                    data={[
                      { name: 'Wins', value: stats.wins },
                      { name: 'Losses', value: stats.losses },
                    ]}
                    dataKey="value"
                    innerRadius={45}
                    outerRadius={64}
                    startAngle={90}
                    endAngle={-270}
                    stroke="none"
                  >
                    <Cell fill="#5bd16e" />
                    <Cell fill="#e15b6e" />
                  </Pie>
                  <Tooltip contentStyle={tooltipStyle} />
                </PieChart>
              </ResponsiveContainer>
              <div style={styles.donutCenter}>
                <div style={styles.donutPct}>{Math.round(stats.winRate * 100)}%</div>
                <div style={styles.donutLabel}>win rate</div>
              </div>
            </div>
          )}
        </div>

        {/* Ranked rating */}
        {ratings.some((r) => r.gamesPlayed > 0) && (
          <Panel title="Ranked rating" wide>
            <div style={styles.ratingRow}>
              {ratings.map((r) => (
                <RatingCard key={r.mode} rating={r} />
              ))}
            </div>
            <RatingChart points={ratingHistory} />
          </Panel>
        )}

        <div style={styles.grid}>
          {colors.length > 0 && (
            <Panel title="Colors you play">
              <ResponsiveContainer width="100%" height={Math.max(140, colors.length * 30)}>
                <BarChart
                  data={colors.map((c) => ({ label: colorLabel(c.label), count: c.count, fill: colorForIdentity(c.label) }))}
                  layout="vertical"
                  margin={{ top: 4, right: 16, bottom: 4, left: 8 }}
                >
                  <XAxis type="number" stroke="#666" fontSize={11} allowDecimals={false} />
                  <YAxis type="category" dataKey="label" stroke="#aaa" fontSize={11} width={92} />
                  <Tooltip contentStyle={tooltipStyle} cursor={{ fill: '#ffffff10' }} />
                  <Bar dataKey="count" radius={[0, 4, 4, 0]}>
                    {colors.map((c) => (
                      <Cell key={c.label} fill={colorForIdentity(c.label)} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </Panel>
          )}

          {cardTypes.length > 0 && (
            <Panel title="Card types">
              <ResponsiveContainer width="100%" height={Math.max(160, cardTypes.length * 24)}>
                <PieChart>
                  <Pie data={cardTypes} dataKey="count" nameKey="label" outerRadius={70} stroke="none" label={pieLabel}>
                    {cardTypes.map((t, i) => (
                      <Cell key={t.label} fill={TYPE_COLORS[t.label] ?? PALETTE[i % PALETTE.length]} />
                    ))}
                  </Pie>
                  <Tooltip contentStyle={tooltipStyle} />
                </PieChart>
              </ResponsiveContainer>
            </Panel>
          )}

          {curve.some((c) => c.count > 0) && (
            <Panel title="Mana curve">
              <ResponsiveContainer width="100%" height={180}>
                <BarChart data={curve} margin={{ top: 8, right: 8, bottom: 4, left: -16 }}>
                  <CartesianGrid stroke="#1f1f2e" vertical={false} />
                  <XAxis dataKey="label" stroke="#888" fontSize={11} />
                  <YAxis stroke="#666" fontSize={11} allowDecimals={false} />
                  <Tooltip contentStyle={tooltipStyle} cursor={{ fill: '#ffffff10' }} />
                  <Bar dataKey="count" fill="#5b6ee1" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </Panel>
          )}

          {creatureTypes.length > 0 && (
            <Panel title="Creature types you play most">
              <ResponsiveContainer width="100%" height={Math.max(160, creatureTypes.length * 26)}>
                <BarChart
                  data={creatureTypes}
                  layout="vertical"
                  margin={{ top: 4, right: 16, bottom: 4, left: 8 }}
                >
                  <XAxis type="number" stroke="#666" fontSize={11} allowDecimals={false} />
                  <YAxis type="category" dataKey="label" stroke="#aaa" fontSize={11} width={92} />
                  <Tooltip contentStyle={tooltipStyle} cursor={{ fill: '#ffffff10' }} />
                  <Bar dataKey="count" fill="#5bd1a0" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </Panel>
          )}

          {modes.length > 0 && (
            <Panel title="Game modes">
              <ChipList items={modes.map((m) => `${prettyMode(m.label)} · ${m.count}`)} />
            </Panel>
          )}

          {sets.length > 0 && (
            <Panel title="Sets you play">
              <ChipList items={sets.map((sb) => `${sb.label} · ${sb.count}`)} />
            </Panel>
          )}

          {opponents.length > 0 && (
            <Panel title="Head to head">
              <p style={styles.subtle}>Most-played human opponents (AI excluded).</p>
              <SimpleTable head={['Opponent', 'W', 'L']}>
                {opponents.map((o, i) => (
                  <tr key={`${o.opponent}-${i}`}>
                    <td style={styles.td}>{o.opponent}</td>
                    <td style={styles.tdNum}>{o.wins}</td>
                    <td style={styles.tdNum}>{o.losses}</td>
                  </tr>
                ))}
              </SimpleTable>
            </Panel>
          )}

          {topCards.length > 0 && (
            <Panel title="Most-played cards">
              <div style={styles.cardChips}>
                {topCards.map((c) => (
                  <span key={c.cardName} style={styles.cardChip}>
                    <span style={styles.cardChipCount}>{c.copies}×</span>
                    {c.cardName}
                  </span>
                ))}
              </div>
            </Panel>
          )}

          {tournaments.length > 0 && (
            <Panel title="Tournaments">
              <SimpleTable head={['Date', 'Tournament', 'Place']}>
                {tournaments.map((t, i) => (
                  <tr key={`${t.endedAt}-${i}`}>
                    <td style={styles.td}>{t.endedAt.slice(0, 10)}</td>
                    <td style={styles.td}>{t.name ?? '—'}</td>
                    <td style={styles.tdNum}>
                      {t.placement}/{t.playerCount}
                    </td>
                  </tr>
                ))}
              </SimpleTable>
            </Panel>
          )}
        </div>
      </div>
    </div>
  )
}

/** Show a slice label only when it's big enough to not collide with its neighbours. */
function pieLabel(entry: { label: string; percent?: number }): string {
  return (entry.percent ?? 0) >= 0.08 ? entry.label : ''
}

function Stat({ label, value }: { label: string; value: number | string }) {
  return (
    <div style={styles.tile}>
      <div style={styles.tileValue}>{value}</div>
      <div style={styles.tileLabel}>{label}</div>
    </div>
  )
}

function tierColor(tier: string): string {
  switch (tier) {
    case 'Mythic':
      return '#e15bd1'
    case 'Diamond':
      return '#5bd1d1'
    case 'Platinum':
      return '#9ad1e1'
    case 'Gold':
      return '#e1c45b'
    case 'Silver':
      return '#c0c4cc'
    case 'Bronze':
      return '#c08a5b'
    default:
      return '#888'
  }
}

function RatingCard({ rating }: { rating: RatingEntry }) {
  const games = rating.gamesPlayed
  const record =
    games === 0
      ? 'Unrated'
      : rating.provisional
        ? `${games}/10 placement`
        : `${rating.wins}–${rating.losses}${rating.draws ? `–${rating.draws}` : ''}`
  return (
    <div style={{ ...styles.ratingCard, borderColor: `${MODE_COLORS[rating.mode]}55` }}>
      <div style={styles.ratingMode}>{MODE_LABELS[rating.mode]}</div>
      <div style={styles.ratingValue}>{rating.rating}</div>
      <div style={{ ...styles.ratingTier, color: tierColor(rating.tier) }}>{rating.tier}</div>
      <div style={styles.ratingRecord}>{record}</div>
    </div>
  )
}

/** Rating over time, one filled line per mode (each connected across its own games). */
function RatingChart({ points }: { points: RatingPoint[] }) {
  if (points.length === 0) {
    return <p style={styles.muted}>Play ranked games to see your rating over time.</p>
  }
  const sorted = [...points].sort((a, b) => a.endedAt.localeCompare(b.endedAt))
  const data = sorted.map((p, i) => ({ idx: i, label: p.endedAt.slice(0, 10), [p.mode]: p.ratingAfter }))
  const modes = Array.from(new Set(points.map((p) => p.mode)))
  return (
    <div style={{ marginTop: 14 }}>
      <ResponsiveContainer width="100%" height={240}>
        <AreaChart data={data} margin={{ top: 8, right: 16, bottom: 4, left: -8 }}>
          <defs>
            {modes.map((m) => (
              <linearGradient key={m} id={`grad-${m}`} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={MODE_COLORS[m]} stopOpacity={0.4} />
                <stop offset="100%" stopColor={MODE_COLORS[m]} stopOpacity={0} />
              </linearGradient>
            ))}
          </defs>
          <CartesianGrid stroke="#1f1f2e" />
          <XAxis dataKey="label" stroke="#666" fontSize={11} minTickGap={32} />
          <YAxis stroke="#666" fontSize={11} domain={['dataMin - 30', 'dataMax + 30']} allowDecimals={false} width={44} />
          <Tooltip contentStyle={tooltipStyle} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          {modes.map((m) => (
            <Area
              key={m}
              type="monotone"
              dataKey={m}
              name={MODE_LABELS[m]}
              stroke={MODE_COLORS[m]}
              strokeWidth={2}
              fill={`url(#grad-${m})`}
              connectNulls
              dot={false}
            />
          ))}
        </AreaChart>
      </ResponsiveContainer>
    </div>
  )
}

function Panel({ title, children, wide }: { title: string; children: React.ReactNode; wide?: boolean }) {
  return (
    <div style={{ ...styles.panel, ...(wide ? styles.panelWide : {}) }}>
      <h2 style={styles.panelTitle}>{title}</h2>
      {children}
    </div>
  )
}

function ChipList({ items }: { items: string[] }) {
  return (
    <div style={styles.chipRow}>
      {items.map((it) => (
        <span key={it} style={styles.chip}>
          {it}
        </span>
      ))}
    </div>
  )
}

function SimpleTable({ head, children }: { head: string[]; children: React.ReactNode }) {
  return (
    <div style={styles.tableWrap}>
      <table style={styles.table}>
        <thead>
          <tr>
            {head.map((h, i) => (
              <th key={h} style={i === 0 ? styles.th : styles.thNum}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  )
}

function prettyMode(mode: string | null): string {
  if (!mode) return '—'
  return mode
    .toLowerCase()
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ')
}

const tooltipStyle: React.CSSProperties = {
  backgroundColor: '#12121e',
  border: '1px solid #2a2a3e',
  borderRadius: 6,
  color: '#ddd',
  fontSize: 12,
}

const styles: Record<string, React.CSSProperties> = {
  wrap: { height: '100vh', overflowY: 'auto', backgroundColor: '#0a0a15', padding: '32px 16px' },
  container: { maxWidth: 960, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 14 },
  header: { display: 'flex', justifyContent: 'space-between' },
  link: { background: 'none', border: 'none', color: '#8b9bff', cursor: 'pointer', fontSize: 14, padding: 0 },
  title: { margin: '4px 0 0', color: '#fff', fontSize: 28 },
  muted: { margin: 0, color: '#888', fontSize: 14 },
  subtle: { margin: '0 0 8px', color: '#777', fontSize: 12 },
  overviewRow: { display: 'flex', gap: 14, flexWrap: 'wrap', alignItems: 'center' },
  tiles: { display: 'flex', gap: 12, flexWrap: 'wrap', flex: '2 1 360px' },
  tile: {
    flex: '1 1 110px',
    backgroundColor: '#14141f',
    border: '1px solid #2a2a3e',
    borderRadius: 12,
    padding: '16px 12px',
    textAlign: 'center',
  },
  tileValue: { color: '#fff', fontSize: 26, fontWeight: 700 },
  tileLabel: { color: '#888', fontSize: 12, marginTop: 4, textTransform: 'uppercase', letterSpacing: 0.5 },
  donutCard: {
    position: 'relative',
    flex: '1 1 180px',
    minWidth: 180,
    backgroundColor: '#14141f',
    border: '1px solid #2a2a3e',
    borderRadius: 12,
    padding: '10px 8px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  donutCenter: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    pointerEvents: 'none',
  },
  donutPct: { color: '#fff', fontSize: 24, fontWeight: 800, lineHeight: 1 },
  donutLabel: { color: '#888', fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.5, marginTop: 2 },
  grid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 14 },
  panel: {
    backgroundColor: '#14141f',
    border: '1px solid #2a2a3e',
    borderRadius: 12,
    padding: '16px 18px',
  },
  panelWide: { gridColumn: '1 / -1' },
  panelTitle: { margin: '0 0 12px', color: '#fff', fontSize: 16 },
  ratingRow: { display: 'flex', gap: 12, flexWrap: 'wrap' },
  ratingCard: {
    flex: '1 1 140px',
    backgroundColor: '#1a1a28',
    border: '1px solid #2a2a3e',
    borderRadius: 12,
    padding: '14px 12px',
    textAlign: 'center',
  },
  ratingMode: { color: '#9aa', fontSize: 12, textTransform: 'uppercase', letterSpacing: 0.5 },
  ratingValue: { color: '#fff', fontSize: 30, fontWeight: 800, lineHeight: 1.1, marginTop: 4 },
  ratingTier: { fontSize: 14, fontWeight: 700, marginTop: 2 },
  ratingRecord: { color: '#888', fontSize: 12, marginTop: 4 },
  chipRow: { display: 'flex', flexWrap: 'wrap', gap: 8 },
  chip: {
    backgroundColor: '#1d1d2e',
    border: '1px solid #2a2a3e',
    borderRadius: 999,
    padding: '4px 12px',
    color: '#cdd',
    fontSize: 13,
  },
  cardChips: { display: 'flex', flexWrap: 'wrap', gap: 8 },
  cardChip: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    backgroundColor: '#1d1d2e',
    border: '1px solid #2a2a3e',
    borderRadius: 8,
    padding: '4px 10px',
    color: '#cdd',
    fontSize: 13,
  },
  cardChipCount: { color: '#8b9bff', fontWeight: 700, fontVariantNumeric: 'tabular-nums' },
  tableWrap: { overflowX: 'auto' },
  table: { width: '100%', borderCollapse: 'collapse', fontSize: 13 },
  th: { textAlign: 'left', color: '#888', fontWeight: 600, padding: '6px 8px', borderBottom: '1px solid #2a2a3e' },
  thNum: { textAlign: 'right', color: '#888', fontWeight: 600, padding: '6px 8px', borderBottom: '1px solid #2a2a3e' },
  td: { textAlign: 'left', color: '#ccc', padding: '6px 8px', borderBottom: '1px solid #1f1f2e' },
  tdNum: { textAlign: 'right', color: '#ccc', padding: '6px 8px', borderBottom: '1px solid #1f1f2e' },
}
