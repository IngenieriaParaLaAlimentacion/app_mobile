package ar.com.ingenieriaalimentacion.data.notification.mapper

import ar.com.ingenieriaalimentacion.domain.model.notification.PushPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

class PushPlatformMapperTest {
    @Test
    fun `maps platforms to backend wire values`() {
        assertEquals("android", PushPlatform.Android.toWire())
        assertEquals("ios", PushPlatform.Ios.toWire())
    }
}
