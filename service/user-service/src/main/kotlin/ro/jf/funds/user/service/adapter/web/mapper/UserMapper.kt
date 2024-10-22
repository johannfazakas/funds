package ro.jf.funds.user.service.adapter.web.mapper

import ro.jf.funds.user.api.model.CreateUserTO
import ro.jf.funds.user.api.model.UserTO
import ro.jf.funds.user.service.domain.command.CreateUserCommand
import ro.jf.funds.user.service.domain.model.User

fun User.toTO() = UserTO(id, username)

fun CreateUserTO.toCommand() = CreateUserCommand(username)
