package com.sportsapp.application.image

data class CreatePresignedUploadUrlCommand(
    val filename: String,
    val contentType: String,
    val domain: String,
) {
    init {
        require(filename.isNotBlank()) { "filename must not be blank" }
        require(domain.isNotBlank()) { "domain must not be blank" }
    }
}
