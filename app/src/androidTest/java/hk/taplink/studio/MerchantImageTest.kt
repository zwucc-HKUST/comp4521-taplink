package hk.taplink.studio

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MerchantImageTest {
    @Test fun everyHandoffImageDecodesThroughTheProductionReader() {
        val runner = InstrumentationRegistry.getInstrumentation()
        val expected = mapOf("PIER" to "18.50", "FERRY" to "8.20", "BOOKS" to "42.00")
        expected.forEach { (code, amount) ->
            val file = File.createTempFile("merchant", ".png", runner.targetContext.cacheDir)
            try {
                runner.context.assets.open("$code.png").use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                val (merchant, decoded) = readMerchantImage(runner.targetContext, Uri.fromFile(file))
                assertEquals(code, merchant.code)
                assertEquals(amount, decoded)
            } finally { file.delete() }
        }
    }
}
