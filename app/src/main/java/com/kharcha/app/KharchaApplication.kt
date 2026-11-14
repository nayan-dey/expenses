package com.kharcha.app

import android.app.Application

class KharchaApplication : Application() {

    /** Built once; screens reach it through `LocalContainer` (see ui/nav). */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
