package com.zkwokleung.backchannel

import android.app.Application

class BackchannelApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
