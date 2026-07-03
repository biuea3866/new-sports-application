package com.sportsapp.presentation.goods.worker

import com.sportsapp.application.goods.usecase.ReconcileLimitedDropsUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class DropReconciliationWorkerTest : BehaviorSpec({

    Given("스케줄 주기가 도래한 상황") {
        val reconcileLimitedDropsUseCase = mockk<ReconcileLimitedDropsUseCase>()
        val worker = DropReconciliationWorker(reconcileLimitedDropsUseCase)

        every { reconcileLimitedDropsUseCase.execute() } returns Unit

        When("reconcile을 호출하면") {
            worker.reconcile()

            Then("ReconcileLimitedDropsUseCase를 위임 호출한다") {
                verify(exactly = 1) { reconcileLimitedDropsUseCase.execute() }
            }
        }
    }
})
