package com.tfcleipzig.streammanager

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerStatusTest {
    @Test
    fun `state record maps to status`() {
        assertEquals(ServerStatus.STARTED, ServerStatus.fromTxt("started"))
        assertEquals(ServerStatus.RUNNING, ServerStatus.fromTxt("running"))
        assertEquals(ServerStatus.RUNNING, ServerStatus.fromTxt("RUNNING"))
    }

    @Test
    fun `missing state record means an older server that only advertises while running`() {
        assertEquals(ServerStatus.RUNNING, ServerStatus.fromTxt(null))
    }

    @Test
    fun `unknown state is not treated as running`() {
        assertEquals(ServerStatus.STARTED, ServerStatus.fromTxt("paused"))
    }
}
