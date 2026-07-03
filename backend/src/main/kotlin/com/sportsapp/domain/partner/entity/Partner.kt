package com.sportsapp.domain.partner.entity
import com.sportsapp.domain.partner.exception.PartnerSuspendedException

class Partner private constructor(
    val id: Long?,
    val name: String,
    initialStatus: PartnerStatus,
    val linkedUserId: Long,
) {

    var status: PartnerStatus = initialStatus
        private set

    fun suspend() {
        if (status == PartnerStatus.SUSPENDED) return
        check(status.canTransitTo(PartnerStatus.SUSPENDED)) {
            "Cannot suspend Partner(id=$id): current status=$status"
        }
        status = PartnerStatus.SUSPENDED
    }

    fun activate() {
        if (status == PartnerStatus.ACTIVE) return
        check(status.canTransitTo(PartnerStatus.ACTIVE)) {
            "Cannot activate Partner(id=$id): current status=$status"
        }
        status = PartnerStatus.ACTIVE
    }

    fun validateActive() {
        if (status == PartnerStatus.SUSPENDED) {
            throw PartnerSuspendedException(id)
        }
    }

    companion object {
        fun create(name: String, linkedUserId: Long): Partner {
            require(name.isNotBlank()) { "name must not be blank" }
            return Partner(
                id = null,
                name = name,
                initialStatus = PartnerStatus.ACTIVE,
                linkedUserId = linkedUserId,
            )
        }

        fun reconstitute(
            id: Long,
            name: String,
            status: PartnerStatus,
            linkedUserId: Long,
        ): Partner = Partner(
            id = id,
            name = name,
            initialStatus = status,
            linkedUserId = linkedUserId,
        )
    }
}
