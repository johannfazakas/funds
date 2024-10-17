package ro.jf.bk.commons.model

sealed class ResponseTO<T>() {
    data class Success<T>(val value: T) : ResponseTO<T>()
    data class Problem<T>(val value: ProblemTO) : ResponseTO<T>()
}

