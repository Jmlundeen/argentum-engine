package com.wingedsheep.gameserver.controller

import com.wingedsheep.gameserver.lobby.QuickGameLobby
import com.wingedsheep.gameserver.lobby.QuickGameLobbyRepository
import com.wingedsheep.gameserver.repository.GameRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public-facing browse endpoint for quick-game lobbies. Mirrors [TournamentController.listPublic]
 * so the home screen can merge tournaments and quick games into a single "Public Lobbies" list.
 *
 * AI lobbies and full lobbies are excluded — there's no second seat to join.
 */
@RestController
@RequestMapping("/api/quick-games")
class QuickGameController(
    private val lobbyRepository: QuickGameLobbyRepository,
    private val gameRepository: GameRepository,
) {

    data class PublicQuickGameDTO(
        val lobbyId: String,
        val playerCount: Int,
        val maxPlayers: Int,
        val setCode: String?,
        val hostName: String?,
        val format: String? = null
    )

    data class LiveQuickGameDTO(
        val gameSessionId: String,
        val player1Name: String,
        val player2Name: String,
        val player1Life: Int,
        val player2Life: Int,
    )

    @GetMapping("/public")
    fun listPublic(): ResponseEntity<List<PublicQuickGameDTO>> {
        val publicLobbies = lobbyRepository.findAll()
            .filter { lobby -> lobby.isPublic && !lobby.vsAi && !lobby.started && !lobby.isFull }
            .sortedBy { it.lobbyId }
            .map { lobby ->
                val host = lobby.players.firstOrNull { !it.isAi }
                PublicQuickGameDTO(
                    lobbyId = lobby.lobbyId,
                    playerCount = lobby.players.count { !it.isAi },
                    maxPlayers = QuickGameLobby.MAX_PLAYERS,
                    setCode = lobby.setCode ?: host?.setCode,
                    hostName = host?.playerName,
                    format = lobby.format?.name
                )
            }
        return ResponseEntity.ok(publicLobbies)
    }

    /**
     * In-progress public quick games. Powers the Live Games section on the landing page so
     * anonymous visitors can drop in as a spectator. Tournament matches are exposed separately
     * by [TournamentController.listLive].
     */
    @GetMapping("/live")
    fun listLive(): ResponseEntity<List<LiveQuickGameDTO>> {
        val live = gameRepository.findAll()
            .filter { it.publicSpectate && !it.isGameOver() }
            .mapNotNull { session ->
                val names = session.getPlayerNames() ?: return@mapNotNull null
                val life = session.getLifeTotals() ?: return@mapNotNull null
                LiveQuickGameDTO(
                    gameSessionId = session.sessionId,
                    player1Name = names.first,
                    player2Name = names.second,
                    player1Life = life.first,
                    player2Life = life.second,
                )
            }
            .sortedBy { it.gameSessionId }
        return ResponseEntity.ok(live)
    }
}
