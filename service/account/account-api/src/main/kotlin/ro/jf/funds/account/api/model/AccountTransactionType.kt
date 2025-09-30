package ro.jf.funds.account.api.model

enum class AccountTransactionType {
    SINGLE_RECORD,
    TRANSFER,
    EXCHANGE,
    OPEN_POSITION,
    CLOSE_POSITION,
}