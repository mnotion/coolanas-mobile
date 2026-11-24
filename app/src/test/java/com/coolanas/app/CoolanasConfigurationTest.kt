package com.coolanas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CoolanasConfigurationTest {
    @Test
    fun applicationIdentityAndVersionAreConfigured() {
        assertEquals("com.coolanas.app", BuildConfig.APPLICATION_ID)
        assertEquals("0.1.0", BuildConfig.VERSION_NAME)
    }
}
