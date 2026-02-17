package ro.jf.funds.fund.service.mapper

import com.benasher44.uuid.Uuid
import ro.jf.funds.fund.api.model.LabelTO
import ro.jf.funds.fund.service.domain.LabelDomain

fun LabelDomain.toTO(): LabelTO = LabelTO(
    id = Uuid.fromString(id.toString()),
    name = name,
)
