package com.mhstackdevelop.dndx

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.PhoneNumberUtils

class DndCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        val active = prefs.getBoolean(MainActivity.KEY_ACTIVE, false)
        val screenNonExempt = prefs.getBoolean(MainActivity.KEY_SCREEN_NON_EXEMPT, true)
        val number = callDetails.handle?.schemeSpecificPart

        val exempt = number != null && prefs.getStringSet(MainActivity.KEY_EXEMPTIONS, emptySet())
            .orEmpty()
            .any { entry ->
                val savedNumber = entry.substringAfter('|', "")
                savedNumber.isNotBlank() && PhoneNumberUtils.compare(number, savedNumber)
            }
        val reject = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING &&
            active && screenNonExempt && !exempt
        val response = CallResponse.Builder()
            .setDisallowCall(reject)
            .setRejectCall(reject)
            .setSkipCallLog(false)
            .setSkipNotification(reject)
            .build()
        respondToCall(callDetails, response)
    }
}
