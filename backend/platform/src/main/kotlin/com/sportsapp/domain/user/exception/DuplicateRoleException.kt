package com.sportsapp.domain.user.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class DuplicateRoleException(roleId: Long) : BusinessException(
    errorCode = "DUPLICATE_ROLE",
    message = "Role $roleId is already assigned to this user"
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
