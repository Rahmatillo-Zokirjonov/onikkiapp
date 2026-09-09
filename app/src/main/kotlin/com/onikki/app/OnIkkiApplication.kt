package com.onikki.app

import android.app.Application
import com.onikki.app.data.db.AppDatabase

class OnIkkiApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
}
