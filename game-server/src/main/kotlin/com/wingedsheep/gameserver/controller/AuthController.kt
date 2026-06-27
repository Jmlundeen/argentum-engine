package com.wingedsheep.gameserver.controller

import com.wingedsheep.gameserver.auth.AuthSupport
import com.wingedsheep.gameserver.auth.InvalidLoginTokenException
import com.wingedsheep.gameserver.auth.MagicLinkService
import com.wingedsheep.gameserver.persistence.UserRow
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Passwordless magic-link auth. Only mounted when accounts are enabled.
 *
 *  - POST /api/auth/request-login  { email }            → emails a sign-in link (always 200, even if
 *                                                          the email is unknown, to avoid leaking who
 *                                                          has an account)
 *  - POST /api/auth/verify         { token }            → { authToken, user }
 *  - GET  /api/auth/me             (Bearer authToken)   → { user }
 */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = ["accounts.enabled"], havingValue = "true")
class AuthController(
    private val magicLinkService: MagicLinkService,
    private val authSupport: AuthSupport,
) {
    data class RequestLoginBody(val email: String)
    data class VerifyBody(val token: String)
    data class UserDto(val id: Long, val email: String, val displayName: String)
    data class LoginResponse(val authToken: String, val user: UserDto)

    @PostMapping("/request-login")
    fun requestLogin(@RequestBody body: RequestLoginBody): ResponseEntity<Any> {
        return try {
            magicLinkService.requestLogin(body.email)
            ResponseEntity.ok(mapOf("status" to "sent"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid email")))
        }
    }

    @PostMapping("/verify")
    fun verify(@RequestBody body: VerifyBody): ResponseEntity<Any> {
        return try {
            val result = magicLinkService.verify(body.token)
            ResponseEntity.ok(LoginResponse(result.authToken, result.user.toDto()))
        } catch (e: InvalidLoginTokenException) {
            ResponseEntity.status(401).body(mapOf("error" to (e.message ?: "Invalid sign-in link")))
        }
    }

    @GetMapping("/me")
    fun me(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?): ResponseEntity<Any> {
        val claims = authSupport.requireUser(authorization)
        val user = magicLinkService.findUser(claims.uid)
            ?: return ResponseEntity.status(401).body(mapOf("error" to "Account no longer exists"))
        return ResponseEntity.ok(user.toDto())
    }

    private fun UserRow.toDto() = UserDto(id = id!!, email = email, displayName = displayName)
}
