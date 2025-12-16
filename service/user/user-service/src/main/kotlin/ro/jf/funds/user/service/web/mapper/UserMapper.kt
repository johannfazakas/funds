package ro.jf.funds.user.service.web.mapper

import ro.jf.funds.user.api.model.CreateUserTO
import ro.jf.funds.user.api.model.UserTO
import ro.jf.funds.user.service.domain.CreateUserCommand
import ro.jf.funds.user.service.domain.User

fun User.toTO() = UserTO(id, username)

fun CreateUserTO.toCommand() = CreateUserCommand(username)
