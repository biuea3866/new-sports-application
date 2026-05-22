package com.sportsapp.presentation.mcp

import com.sportsapp.application.booking.BookingResponse
import com.sportsapp.application.booking.ListBookingsCommand
import com.sportsapp.application.booking.ListBookingsResponse
import com.sportsapp.application.booking.ListMyBookingsUseCase
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.presentation.mcp.response.McpResponseStatus
import com.sportsapp.presentation.mcp.toolregistry.McpBookingTools
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.ZonedDateTime

class McpBookingToolsTest : BehaviorSpec({

    val listMyBookingsUseCase = mockk<ListMyBookingsUseCase>()
    val mcpBookingTools = McpBookingTools(listMyBookingsUseCase)

    Given("getBookings tool") {
        val bookingResponse = BookingResponse(
            id = 1L,
            slotId = 100L,
            userId = 42L,
            status = BookingStatus.CONFIRMED,
            paymentId = 200L,
            paymentStatus = null,
            createdAt = ZonedDateTime.now(),
            updatedAt = ZonedDateTime.now(),
        )
        val listResponse = ListBookingsResponse(
            bookings = listOf(bookingResponse),
            totalElements = 1L,
            totalPages = 1,
            page = 0,
            size = 20,
        )

        When("[U-04] userId로 getBookings를 호출하면") {
            val commandSlot = slot<ListBookingsCommand>()
            every { listMyBookingsUseCase.execute(capture(commandSlot)) } returns listResponse

            val result = mcpBookingTools.getBookings(userId = 42L, status = null, page = 0, size = 20)

            Then("[U-04] OK 상태와 예약 목록이 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                result.data shouldNotBe null
                val data = requireNotNull(result.data)
                data.size shouldBe 1
                data[0].id shouldBe 1L
                data[0].userId shouldBe 42L
            }
        }

        When("[U-05] status 필터를 지정해 getBookings를 호출하면") {
            val commandSlot = slot<ListBookingsCommand>()
            every { listMyBookingsUseCase.execute(capture(commandSlot)) } returns listResponse

            mcpBookingTools.getBookings(userId = 42L, status = "CONFIRMED", page = 0, size = 20)

            Then("[U-05] ListBookingsCommand에 status 값이 전달된다") {
                commandSlot.captured.userId shouldBe 42L
                commandSlot.captured.status shouldBe BookingStatus.CONFIRMED
            }
        }

        When("[U-06] 결과가 없으면") {
            every { listMyBookingsUseCase.execute(any()) } returns ListBookingsResponse(
                bookings = emptyList(),
                totalElements = 0L,
                totalPages = 0,
                page = 0,
                size = 20,
            )

            val result = mcpBookingTools.getBookings(userId = 99L, status = null, page = 0, size = 20)

            Then("[U-06] OK 상태와 빈 목록이 반환된다") {
                result.status shouldBe McpResponseStatus.OK
                val data = requireNotNull(result.data)
                data.size shouldBe 0
            }
        }
    }
})
