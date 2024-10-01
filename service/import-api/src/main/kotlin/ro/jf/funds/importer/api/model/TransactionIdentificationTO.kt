package ro.jf.funds.importer.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionIdentificationTO(
    val conditions: List<TransactionConditionTO>,
)

@Serializable
sealed class TransactionConditionTO {
    @Serializable
    @SerialName("column_equality")
    data class ColumnEquality(
        val column: String,
    ) : TransactionConditionTO()

    @Serializable
    @SerialName("constant")
    data class ColumnConstant(
        val column: String,
        val value: String,
    ) : TransactionConditionTO()

    @Serializable
    @SerialName("numerical_absolute_equality")
    data class NumericalAbsoluteEquality(
        val column: String
    ) : TransactionConditionTO()
}