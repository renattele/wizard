package ${Package}.core.testing

import app.cash.turbine.ReceiveTurbine

object TurbineSupport {
    fun <T> label(turbine: ReceiveTurbine<T>): String = turbine.toString()
}
