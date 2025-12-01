package ro.jf.funds.reporting.service.utils

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import java.math.BigDecimal as JavaBigDecimal

fun JavaBigDecimal.toKmpBigDecimal(): BigDecimal = BigDecimal.parseString(this.toPlainString())
