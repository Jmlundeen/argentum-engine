package com.wingedsheep.gameserver.stats

import com.wingedsheep.gameserver.persistence.MatchParticipantRow
import com.wingedsheep.gameserver.persistence.MatchResultRepository
import com.wingedsheep.gameserver.persistence.MatchResultRow
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant

/** A finished game ready to be recorded for stats. */
data class RecordedMatch(
    val gameId: String,
    val format: String?,
    val tournamentName: String?,
    val startedAt: Instant?,
    val endedAt: Instant,
    val participants: List<RecordedParticipant>,
)

/** One seat in a finished game. [userId] is null for guests and AI. */
data class RecordedParticipant(
    val userId: Long?,
    val playerName: String,
    val won: Boolean,
)

/**
 * Records finished games for durable stats. The game-over path calls this unconditionally; which
 * implementation is wired depends on whether accounts are enabled, so [GamePlayHandler] stays
 * decoupled from the persistence layer.
 */
interface MatchResultSink {
    fun record(match: RecordedMatch)
}

/** Default: accounts disabled — stats are not persisted. */
@Component
@ConditionalOnProperty(name = ["accounts.enabled"], havingValue = "false", matchIfMissing = true)
class NoOpMatchResultSink : MatchResultSink {
    override fun record(match: RecordedMatch) = Unit
}

/**
 * Accounts enabled: persist the match, but only when at least one seat is a signed-in account —
 * guest-only and AI-only games (e.g. the LLM tournament) would otherwise flood the table without
 * contributing to any user's stats.
 */
@Component
@ConditionalOnProperty(name = ["accounts.enabled"], havingValue = "true")
class JdbcMatchResultSink(private val matchResults: MatchResultRepository) : MatchResultSink {
    private val logger = LoggerFactory.getLogger(JdbcMatchResultSink::class.java)

    override fun record(match: RecordedMatch) {
        if (match.participants.none { it.userId != null }) return
        matchResults.save(
            MatchResultRow(
                gameId = match.gameId,
                format = match.format,
                tournamentName = match.tournamentName,
                startedAt = match.startedAt,
                endedAt = match.endedAt,
                participants = match.participants.map {
                    MatchParticipantRow(userId = it.userId, playerName = it.playerName, won = it.won)
                }.toSet(),
            )
        )
        logger.debug("Recorded match {} for stats ({} seats)", match.gameId, match.participants.size)
    }
}
