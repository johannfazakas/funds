package ro.jf.bk.user.service.adapter.web.mapper

import ro.jf.bk.user.api.model.CreateUserTO
import ro.jf.bk.user.api.model.UserTO
import ro.jf.bk.user.service.domain.command.CreateUserCommand
import ro.jf.bk.user.service.domain.model.User

fun User.toTO() = UserTO(id, username)

fun CreateUserTO.toCommand() = CreateUserCommand(username)
