package com.example

import com.example.util.DeviceHelper
import com.example.util.OemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceHelperTest {

    @Test
    fun testDeviceDetailNotNull() {
        val detail = DeviceHelper.getDeviceDetail()
        assertNotNull(detail)
        assertNotNull(detail.oemType)
        assertTrue(detail.displayModel.isNotEmpty())
        assertTrue(detail.instructions.isNotEmpty())
        assertTrue(detail.accessMethod.isNotEmpty())
    }

    @Test
    fun testOemTypeEnum() {
        assertEquals("iQOO / Vivo", OemType.IQOO_VIVO.displayName)
        assertEquals("OnePlus / Oppo / Realme", OemType.ONEPLUS_OPPO_REALME.displayName)
        assertEquals("Infinix / Tecno", OemType.INFINIX_TECNO.displayName)
    }
}
