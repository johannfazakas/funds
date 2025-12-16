package ro.jf.funds.platform.jvm.error

class ApiException(val statusCode: Int, val error: ErrorTO) : RuntimeException("ApiException: $statusCode - $error")
