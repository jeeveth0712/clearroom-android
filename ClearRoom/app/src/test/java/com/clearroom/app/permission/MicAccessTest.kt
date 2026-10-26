package com.clearroom.app.permission

import kotlin.test.Test
import kotlin.test.assertEquals

class MicAccessTest {

    @Test
    fun `every combination of permission signals maps to the right access state`() {
        // isGranted, shouldShowRationale, hasAsked -> expected
        val truthTable = listOf(
            Triple(true, false, false) to MicAccess.GRANTED,
            Triple(true, false, true) to MicAccess.GRANTED,
            Triple(true, true, false) to MicAccess.GRANTED,
            Triple(true, true, true) to MicAccess.GRANTED,
            Triple(false, true, false) to MicAccess.DENIED_CAN_ASK_AGAIN,
            Triple(false, true, true) to MicAccess.DENIED_CAN_ASK_AGAIN,
            Triple(false, false, true) to MicAccess.DENIED_PERMANENTLY,
            Triple(false, false, false) to MicAccess.NOT_ASKED,
        )
        truthTable.forEach { (signals, expected) ->
            val (granted, rationale, asked) = signals
            assertEquals(
                expected,
                MicAccess.from(isGranted = granted, shouldShowRationale = rationale, hasAsked = asked),
                "granted=$granted rationale=$rationale asked=$asked",
            )
        }
    }
}
