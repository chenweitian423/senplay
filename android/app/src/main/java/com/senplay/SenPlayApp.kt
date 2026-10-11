package com.senplay

import android.app.Application
import com.senplay.core.LibraryStore

class SenPlayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LibraryStore.get(this)
    }
}
