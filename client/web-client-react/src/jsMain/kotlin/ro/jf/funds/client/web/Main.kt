package ro.jf.funds.client.web

import kotlinx.browser.window
import ro.jf.funds.client.web.api.AccountApi
import ro.jf.funds.client.web.api.FundApi
import ro.jf.funds.client.web.api.ReportingApi
import ro.jf.funds.client.web.api.TransactionApi
import ro.jf.funds.client.web.api.UserApi

fun main() {
    val ro = js("{}")
    ro.jf = js("{}")
    ro.jf.funds = js("{}")
    ro.jf.funds.client = js("{}")
    ro.jf.funds.client.web = js("{}")
    ro.jf.funds.client.web.UserApi = UserApi
    ro.jf.funds.client.web.FundApi = FundApi
    ro.jf.funds.client.web.AccountApi = AccountApi
    ro.jf.funds.client.web.TransactionApi = TransactionApi
    ro.jf.funds.client.web.ReportingApi = ReportingApi
    window.asDynamic().ro = ro
}
