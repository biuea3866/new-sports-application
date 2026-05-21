package com.sportsapp.infrastructure.storage

import com.sportsapp.domain.common.storage.ImageStorageGateway
import com.sportsapp.domain.common.storage.PresignedUpload
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.time.ZonedDateTime

@Component
class MinioImageStorageGatewayImpl(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val storageProperties: StorageProperties,
) : ImageStorageGateway {

    private val logger = LoggerFactory.getLogger(MinioImageStorageGatewayImpl::class.java)

    @PostConstruct
    fun initializeBucket() {
        try {
            ensureBucketExists()
        } catch (exception: Exception) {
            logger.warn("MinIO bucket initialization failed — will retry on first request: ${exception.message}")
        }
    }

    override fun createPresignedUpload(
        key: String,
        contentType: String,
        maxBytes: Long,
        expirySeconds: Long,
    ): PresignedUpload {
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(storageProperties.bucket)
            .key(key)
            .contentType(contentType)
            .build()

        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(expirySeconds))
            .putObjectRequest(putObjectRequest)
            .build()

        val presignedPutObjectRequest = s3Presigner.presignPutObject(presignRequest)

        return PresignedUpload(
            url = presignedPutObjectRequest.url().toString(),
            key = key,
            expiresAt = ZonedDateTime.now().plusSeconds(expirySeconds),
            requiredHeaders = presignedPutObjectRequest.signedHeaders()
                .mapValues { entry -> entry.value.joinToString(",") },
        )
    }

    override fun createPresignedDownload(key: String, expirySeconds: Long): String {
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(storageProperties.bucket)
            .key(key)
            .build()

        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(expirySeconds))
            .getObjectRequest(getObjectRequest)
            .build()

        return s3Presigner.presignGetObject(presignRequest).url().toString()
    }

    override fun publicUrl(key: String): String =
        "${storageProperties.endpoint}/${storageProperties.bucket}/$key"

    private fun ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(storageProperties.bucket).build())
        } catch (exception: NoSuchBucketException) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(storageProperties.bucket).build())
        }
    }
}
