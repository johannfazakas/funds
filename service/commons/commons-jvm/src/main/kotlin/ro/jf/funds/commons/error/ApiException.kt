package ro.jf.funds.commons.error

// TODO(Johann) should be moved to commons-api. Same for HttpClientUtils. This way client-sdk would not have to repeat some info
class ApiException(val statusCode: Int, val error: ErrorTO) : RuntimeException("ApiException: $statusCode - $error")
