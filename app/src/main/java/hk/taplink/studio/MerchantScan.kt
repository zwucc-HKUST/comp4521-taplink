package hk.taplink.studio

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun readMerchantImage(context: Context, uri: Uri): Pair<Merchant, String> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a readable QR image." }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: throw IllegalArgumentException("Choose a readable QR image.")
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    try { bitmap.getPixels(pixels, 0, w, 0, 0, w, h) } finally { bitmap.recycle() }
    val image = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, pixels)))
    return parseMerchantCode(QRCodeReader().decode(image).text)
}

@Composable
fun MerchantScan(onFound: (Merchant, String) -> Unit) {
    val context = LocalContext.current
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var feedback by rememberSaveable { mutableStateOf("") }
    val found by rememberUpdatedState(onFound)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pending = uri.toString()
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pending = photo
    }
    LaunchedEffect(pending) {
        val source = pending ?: return@LaunchedEffect
        try {
            val (merchant, amount) = withContext(Dispatchers.IO) { readMerchantImage(context, Uri.parse(source)) }
            found(merchant, amount)
            feedback = "QR read. Check the merchant and amount before review."
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            feedback = (e as? IllegalArgumentException)?.message ?: "QR could not be read. Try a clearer photo or enter the amount."
        }
        pending = null
    }
    Column {
        Row {
            TextButton(onClick = {
                runCatching {
                    val dir = File(context.cacheDir, "scan").apply { mkdirs() }
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".photos", File(dir, "capture.jpg"))
                    photo = uri.toString()
                    camera.launch(uri)
                }.onFailure { feedback = "Camera unavailable. Choose a QR image instead." }
            }) { Text("Photograph QR") }
            TextButton(onClick = { runCatching { picker.launch("image/*") }.onFailure { feedback = "Image picker unavailable." } }) {
                Text("Choose QR image")
            }
        }
        if (feedback.isNotEmpty()) Text(feedback, style = MaterialTheme.typography.bodySmall)
    }
}
