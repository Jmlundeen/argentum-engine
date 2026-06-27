package com.wingedsheep.gameserver.controller

import com.wingedsheep.gameserver.auth.AuthSupport
import com.wingedsheep.gameserver.persistence.MatchResultRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Win/loss stats for the signed-in user, computed on demand from the match-history tables (no
 * denormalized stats table). Only mounted when accounts are enabled.
 */
@RestController
@RequestMapping("/api/account/stats")
@ConditionalOnProperty(name = ["accounts.enabled"], havingValue = "true")
class AccountStatsController(
    private val matchResults: MatchResultRepository,
    private val authSupport: AuthSupport,
) {
    data class StatsDto(val games: Long, val wins: Long, val losses: Long, val winRate: Double)

    @GetMapping("/me")
    fun me(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?): StatsDto {
        val userId = authSupport.requireUser(auth).uid
        val games = matchResults.countGamesForUser(userId)
        val wins = matchResults.countWinsForUser(userId)
        return StatsDto(
            games = games,
            wins = wins,
            losses = games - wins,
            winRate = if (games > 0) wins.toDouble() / games else 0.0,
        )
    }
}
