package com.haystudio.vehiclelab

import org.junit.Assert.*
import org.junit.Test

class ModelLoadDeadlineTest {
    @Test fun failedRecognitionDoesNotKeepShowingLoadingTitle() {
        assertEquals("正在识别车辆模型",modelIdentificationTitle(true))
        assertEquals("模型未就绪",modelIdentificationTitle(false))
    }

    @Test fun foregroundLoadExpiresButCompletionCancelsDeadline() {
        val deadline=ModelLoadDeadline(1000)
        deadline.begin(100,true)
        assertFalse(deadline.expired(1099))
        assertTrue(deadline.expired(1100))
        deadline.finish()
        assertFalse(deadline.expired(9999))
    }
    @Test fun backgroundTimeDoesNotConsumeLoadingBudget() {
        val deadline=ModelLoadDeadline(1000)
        deadline.begin(0,true)
        deadline.pause(300)
        assertFalse(deadline.expired(5000))
        deadline.resume(5000)
        assertFalse(deadline.expired(5699))
        assertTrue(deadline.expired(5700))
    }
    @Test fun newStageAndRetryHaveIndependentBudgets() {
        val deadline=ModelLoadDeadline(1000)
        deadline.begin(0,false)
        deadline.resume(5000)
        deadline.begin(5900,true)
        assertFalse(deadline.expired(6000))
        assertTrue(deadline.expired(6900))
    }
}
