package ro.jf.funds.commons.model

import java.math.BigDecimal

@JvmInline
value class Amount(val amount: String) {
    // TODO(Johann) use this instead of BigDecimal, validate and add utilitary methods
    fun asBigDecimal(): BigDecimal = BigDecimal(amount)
}
