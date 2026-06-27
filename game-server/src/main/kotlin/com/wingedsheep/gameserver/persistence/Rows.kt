package com.wingedsheep.gameserver.persistence

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

/**
 * Spring Data JDBC aggregate roots and entities backing the accounts subsystem.
 *
 * These are plain data classes — no JPA, no lazy loading, no dirty-checking. A null [Id] means
 * "new" (Spring Data JDBC inserts); a non-null id updates. Cross-aggregate references are by id
 * (e.g. [DeckRow.userId]), never an object graph; only genuinely-owned children
 * ([MatchResultRow.participants]) use @MappedCollection.
 */
@Table("users")
data class UserRow(
    @Id val id: Long? = null,
    val email: String,
    val displayName: String,
    val createdAt: Instant = Instant.now(),
)

@Table("login_tokens")
data class LoginTokenRow(
    @Id val id: Long? = null,
    val userId: Long,
    val tokenHash: String,
    val expiresAt: Instant,
    val consumedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
)

@Table("decks")
data class DeckRow(
    @Id val id: Long? = null,
    val userId: Long,
    val name: String,
    val format: String? = null,
    /** Full deck JSON (the client's SharedDeck shape). */
    val data: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

@Table("match_results")
data class MatchResultRow(
    @Id val id: Long? = null,
    val gameId: String,
    val format: String? = null,
    val tournamentName: String? = null,
    val startedAt: Instant? = null,
    val endedAt: Instant = Instant.now(),
    @MappedCollection(idColumn = "match_id")
    val participants: Set<MatchParticipantRow> = emptySet(),
)

@Table("match_participants")
data class MatchParticipantRow(
    @Id val id: Long? = null,
    /** Null for guests and AI seats. */
    val userId: Long? = null,
    val playerName: String,
    val won: Boolean,
)
