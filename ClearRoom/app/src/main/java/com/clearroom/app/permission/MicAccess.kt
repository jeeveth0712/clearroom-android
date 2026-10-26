package com.clearroom.app.permission

/** Where the app stands with the RECORD_AUDIO permission, and what the screen should offer. */
enum class MicAccess {
    /** Never asked in this session; asking will show the system dialog. */
    NOT_ASKED,
    GRANTED,
    /** Denied once; the system will still show its dialog if we ask again. */
    DENIED_CAN_ASK_AGAIN,
    /** The system won't show its dialog any more; only the app's Settings page can grant it. */
    DENIED_PERMANENTLY;

    companion object {
        /**
         * Android exposes no "permanently denied" flag. When the permission isn't granted and no
         * rationale should be shown, that means either "never asked" or "denied for good"; only
         * [hasAsked] tells them apart. A dismissed dialog can look like a permanent denial on some
         * versions, which is harmless: the user is offered the Settings page instead.
         */
        fun from(isGranted: Boolean, shouldShowRationale: Boolean, hasAsked: Boolean): MicAccess = when {
            isGranted -> GRANTED
            shouldShowRationale -> DENIED_CAN_ASK_AGAIN
            hasAsked -> DENIED_PERMANENTLY
            else -> NOT_ASKED
        }
    }
}
