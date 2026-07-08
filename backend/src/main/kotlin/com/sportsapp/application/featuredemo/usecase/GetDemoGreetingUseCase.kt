package com.sportsapp.application.featuredemo.usecase

import com.sportsapp.application.featuredemo.dto.DemoGreetingResponse
import com.sportsapp.application.featuredemo.dto.GetDemoGreetingCommand
import com.sportsapp.domain.featuredemo.service.FeatureDemoDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetDemoGreetingUseCase(
    private val featureDemoDomainService: FeatureDemoDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: GetDemoGreetingCommand): DemoGreetingResponse {
        val greeting = featureDemoDomainService.greet(command.userId)
        return DemoGreetingResponse.of(greeting, command.userId)
    }
}
