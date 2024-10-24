package ro.jf.funds.commons.error

class ApiException(val statusCode: Int, val error: ErrorTO) : RuntimeException()
