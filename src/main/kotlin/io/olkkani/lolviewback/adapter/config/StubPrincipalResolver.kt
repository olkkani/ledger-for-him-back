package io.olkkani.lolviewback.adapter.config

import org.springframework.stereotype.Component

/**
 * Temporary stand-in for a real authenticated principal. No Spring Security config,
 * JWT filter, or `users` table exist in this codebase yet — replace [currentUserId]'s
 * body with a real principal lookup once auth is built, and every caller of this class
 * keeps working unchanged.
 */
@Component
class StubPrincipalResolver {
    fun currentUserId(): Long = STUB_USER_ID

    companion object {
        const val STUB_USER_ID = 1L
    }
}
