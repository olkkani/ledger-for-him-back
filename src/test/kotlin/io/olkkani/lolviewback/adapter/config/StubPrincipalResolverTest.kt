package io.olkkani.lolviewback.infastructure.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StubPrincipalResolverTest {
    @Test
    fun `returns a fixed stub user id`() {
        val resolver = StubPrincipalResolver()
        assertEquals(StubPrincipalResolver.STUB_USER_ID, resolver.currentUserId())
    }
}
