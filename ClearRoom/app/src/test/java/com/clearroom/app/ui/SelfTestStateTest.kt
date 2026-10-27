package com.clearroom.app.ui

import com.clearroom.app.selftest.SelfTest
import com.clearroom.app.selftest.SelfTestResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the self-test card reads from its state. */
class SelfTestStateTest {

    private fun results(failing: Int = 0, millis: Long? = null) = SelfTest.cases.mapIndexed { i, case ->
        SelfTestResult(case, passed = i >= failing, slowestMillis = millis?.plus(i))
    }

    @Test
    fun `it has finished once every case has a result and nothing runs`() {
        assertFalse(SelfTestState().finished)
        assertFalse(SelfTestState(running = true, results = results()).finished)
        assertFalse(SelfTestState(results = results().drop(1)).finished)
        assertTrue(SelfTestState(results = results()).finished)
        assertEquals(SelfTest.cases.size, SelfTestState().total)
    }

    @Test
    fun `failed counts the cases that didn't pass`() {
        assertEquals(0, SelfTestState(results = results()).failed)
        assertEquals(2, SelfTestState(results = results(failing = 2)).failed)
    }

    @Test
    fun `the slowest clap is the longest analysis so far`() {
        assertNull(SelfTestState(results = results()).slowestClapMillis)
        assertEquals(100L + SelfTest.cases.size - 1, SelfTestState(results = results(millis = 100)).slowestClapMillis)
    }
}
