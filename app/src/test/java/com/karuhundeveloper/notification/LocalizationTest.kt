package com.karuhundeveloper.notification

import android.content.Context
import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalizationTest {
    @Test
    fun languageResourcesAndEnglishFallback() {
        val english = contextFor("en")
        val indonesian = contextFor("id")
        val chinese = contextFor("zh")

        assertEquals("Add rule", english.getString(R.string.add_rule))
        assertEquals("Tambah aturan", indonesian.getString(R.string.add_rule))
        assertEquals(english.getString(R.string.add_rule), chinese.getString(R.string.add_rule))
        assertEquals(
            "Hapus \"Demo\"? Tindakan ini tidak dapat dibatalkan.",
            indonesian.getString(R.string.delete_rule_confirmation, "Demo")
        )
    }

    private fun contextFor(languageTag: String): Context {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(languageTag))
        return application.createConfigurationContext(configuration)
    }
}
