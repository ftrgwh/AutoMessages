package com.ftrgwh.automessages

import android.app.Application
import com.ftrgwh.automessages.util.Notify

class AutoMessagesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notify.ensureChannels(this)
    }
}
