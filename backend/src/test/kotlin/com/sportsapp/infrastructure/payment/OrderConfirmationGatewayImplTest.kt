package com.sportsapp.infrastructure.payment

import com.sportsapp.domain.ticketing.TicketOrderResult
import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingDomainService
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.GoodsOrder
import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.ticketing.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class OrderConfirmationGatewayImplTest : BehaviorSpec({

    Given("OrderType 이 BOOKING 인 confirm 요청") {
        val bookingDomainService = mockk<BookingDomainService>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val ticketingDomainService = mockk<TicketingDomainService>()
        val gateway = OrderConfirmationGatewayImpl(
            bookingDomainService = bookingDomainService,
            goodsDomainService = goodsDomainService,
            ticketingDomainService = ticketingDomainService,
        )
        val booking = mockk<Booking>()
        every { bookingDomainService.confirmBooking(bookingId = 10L, paymentId = 100L) } returns booking

        When("confirm 을 호출하면") {
            gateway.confirm(orderType = OrderType.BOOKING, orderId = 10L, paymentId = 100L)

            Then("BookingDomainService.confirmBooking 이 1회 호출된다") {
                verify(exactly = 1) { bookingDomainService.confirmBooking(bookingId = 10L, paymentId = 100L) }
            }

            Then("GoodsDomainService 와 TicketingDomainService 는 호출되지 않는다") {
                verify(exactly = 0) { goodsDomainService.markPaid(any(), any()) }
                verify(exactly = 0) { ticketingDomainService.confirmOrder(any(), any()) }
            }
        }
    }

    Given("OrderType 이 GOODS 인 confirm 요청") {
        val bookingDomainService = mockk<BookingDomainService>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val ticketingDomainService = mockk<TicketingDomainService>()
        val gateway = OrderConfirmationGatewayImpl(
            bookingDomainService = bookingDomainService,
            goodsDomainService = goodsDomainService,
            ticketingDomainService = ticketingDomainService,
        )
        val goodsOrder = mockk<GoodsOrder>()
        every { goodsDomainService.markPaid(orderId = 20L, paymentId = 200L) } returns goodsOrder

        When("confirm 을 호출하면") {
            gateway.confirm(orderType = OrderType.GOODS, orderId = 20L, paymentId = 200L)

            Then("GoodsDomainService.markPaid 가 1회 호출된다") {
                verify(exactly = 1) { goodsDomainService.markPaid(orderId = 20L, paymentId = 200L) }
            }

            Then("BookingDomainService 와 TicketingDomainService 는 호출되지 않는다") {
                verify(exactly = 0) { bookingDomainService.confirmBooking(any(), any()) }
                verify(exactly = 0) { ticketingDomainService.confirmOrder(any(), any()) }
            }
        }
    }

    Given("OrderType 이 TICKETING 인 confirm 요청") {
        val bookingDomainService = mockk<BookingDomainService>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val ticketingDomainService = mockk<TicketingDomainService>()
        val gateway = OrderConfirmationGatewayImpl(
            bookingDomainService = bookingDomainService,
            goodsDomainService = goodsDomainService,
            ticketingDomainService = ticketingDomainService,
        )
        val ticketOrderResult = mockk<TicketOrderResult>()
        every { ticketingDomainService.confirmOrder(orderId = 30L, paymentId = 300L) } returns ticketOrderResult

        When("confirm 을 호출하면") {
            gateway.confirm(orderType = OrderType.TICKETING, orderId = 30L, paymentId = 300L)

            Then("TicketingDomainService.confirmOrder 가 1회 호출되고 반환값은 무시된다") {
                verify(exactly = 1) { ticketingDomainService.confirmOrder(orderId = 30L, paymentId = 300L) }
            }

            Then("BookingDomainService 와 GoodsDomainService 는 호출되지 않는다") {
                verify(exactly = 0) { bookingDomainService.confirmBooking(any(), any()) }
                verify(exactly = 0) { goodsDomainService.markPaid(any(), any()) }
            }
        }
    }

    Given("OrderType 이 BOOKING 인 cancel 요청") {
        val bookingDomainService = mockk<BookingDomainService>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val ticketingDomainService = mockk<TicketingDomainService>()
        val gateway = OrderConfirmationGatewayImpl(
            bookingDomainService = bookingDomainService,
            goodsDomainService = goodsDomainService,
            ticketingDomainService = ticketingDomainService,
        )
        justRun { bookingDomainService.cancelPending(bookingId = 10L) }

        When("cancel 을 호출하면") {
            gateway.cancel(orderType = OrderType.BOOKING, orderId = 10L, paymentId = 100L)

            Then("BookingDomainService.cancelPending 이 1회 호출된다") {
                verify(exactly = 1) { bookingDomainService.cancelPending(bookingId = 10L) }
            }

            Then("GoodsDomainService 와 TicketingDomainService 는 호출되지 않는다") {
                verify(exactly = 0) { goodsDomainService.cancelPendingOrder(any()) }
                verify(exactly = 0) { ticketingDomainService.cancelOrder(any()) }
            }
        }
    }

    Given("OrderType 이 GOODS 인 cancel 요청") {
        val bookingDomainService = mockk<BookingDomainService>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val ticketingDomainService = mockk<TicketingDomainService>()
        val gateway = OrderConfirmationGatewayImpl(
            bookingDomainService = bookingDomainService,
            goodsDomainService = goodsDomainService,
            ticketingDomainService = ticketingDomainService,
        )
        justRun { goodsDomainService.cancelPendingOrder(orderId = 20L) }

        When("cancel 을 호출하면") {
            gateway.cancel(orderType = OrderType.GOODS, orderId = 20L, paymentId = 200L)

            Then("GoodsDomainService.cancelPendingOrder 가 1회 호출된다") {
                verify(exactly = 1) { goodsDomainService.cancelPendingOrder(orderId = 20L) }
            }

            Then("BookingDomainService 와 TicketingDomainService 는 호출되지 않는다") {
                verify(exactly = 0) { bookingDomainService.cancelPending(any()) }
                verify(exactly = 0) { ticketingDomainService.cancelOrder(any()) }
            }
        }
    }

    Given("OrderType 이 TICKETING 인 cancel 요청") {
        val bookingDomainService = mockk<BookingDomainService>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val ticketingDomainService = mockk<TicketingDomainService>()
        val gateway = OrderConfirmationGatewayImpl(
            bookingDomainService = bookingDomainService,
            goodsDomainService = goodsDomainService,
            ticketingDomainService = ticketingDomainService,
        )
        justRun { ticketingDomainService.cancelOrder(orderId = 30L) }

        When("cancel 을 호출하면") {
            gateway.cancel(orderType = OrderType.TICKETING, orderId = 30L, paymentId = 300L)

            Then("TicketingDomainService.cancelOrder 가 1회 호출된다") {
                verify(exactly = 1) { ticketingDomainService.cancelOrder(orderId = 30L) }
            }

            Then("BookingDomainService 와 GoodsDomainService 는 호출되지 않는다") {
                verify(exactly = 0) { bookingDomainService.cancelPending(any()) }
                verify(exactly = 0) { goodsDomainService.cancelPendingOrder(any()) }
            }
        }
    }
})
