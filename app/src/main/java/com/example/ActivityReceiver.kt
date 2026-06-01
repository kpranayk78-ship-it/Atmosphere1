package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import kotlinx.coroutines.flow.MutableStateFlow

object ActivityGlobalState {
    val detectedActivity = MutableStateFlow(4) // 4 is DetectedActivity.UNKNOWN
}

class ActivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            val mostProbable = result?.mostProbableActivity
            if (mostProbable != null) {
                ActivityGlobalState.detectedActivity.value = mostProbable.type
            }
        }
    }
}
