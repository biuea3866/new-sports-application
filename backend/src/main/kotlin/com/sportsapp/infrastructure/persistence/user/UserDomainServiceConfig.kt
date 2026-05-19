package com.sportsapp.infrastructure.persistence.user

import com.sportsapp.domain.user.RoleRepository
import com.sportsapp.domain.user.UserDomainService
import com.sportsapp.domain.user.UserRepository
import com.sportsapp.domain.user.UserRoleRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UserDomainServiceConfig {

    @Bean
    fun userDomainService(
        userRepository: UserRepository,
        roleRepository: RoleRepository,
        userRoleRepository: UserRoleRepository,
    ): UserDomainService = UserDomainService(userRepository, roleRepository, userRoleRepository)
}
