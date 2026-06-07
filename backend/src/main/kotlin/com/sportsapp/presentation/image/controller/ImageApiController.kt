package com.sportsapp.presentation.image.controller

import com.sportsapp.application.image.usecase.CreatePresignedUploadUrlUseCase
import com.sportsapp.presentation.image.dto.request.PresignedUploadRequest
import com.sportsapp.presentation.image.dto.response.CreatePresignedUploadUrlResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/images")
class ImageApiController(
    private val createPresignedUploadUrlUseCase: CreatePresignedUploadUrlUseCase,
) {
    @PostMapping("/presigned-upload")
    @ResponseStatus(HttpStatus.CREATED)
    fun createPresignedUploadUrl(
        @RequestBody request: PresignedUploadRequest,
    ): CreatePresignedUploadUrlResponse =
        CreatePresignedUploadUrlResponse.of(createPresignedUploadUrlUseCase.execute(request.toCommand()))
}
