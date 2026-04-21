package ro.jf.funds.fund.service.mapper

import com.benasher44.uuid.Uuid
import ro.jf.funds.fund.api.model.CategoryTO
import ro.jf.funds.fund.service.domain.Category

fun Category.toTO(): CategoryTO = CategoryTO(
    id = Uuid.fromString(id.toString()),
    name = name,
)
