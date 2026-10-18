package com.oracyl.workstation

import com.chaquo.python.android.PyApplication
import org.mozilla.geckoview.GeckoRuntime

class OracylApplication : PyApplication() {
    val geckoRuntime: GeckoRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { GeckoRuntime.create(this) }
}