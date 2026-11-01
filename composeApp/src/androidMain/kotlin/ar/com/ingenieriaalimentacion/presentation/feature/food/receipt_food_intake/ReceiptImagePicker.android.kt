package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberReceiptImagePicker(onResult: (Result<ReceiptPhoto>?) -> Unit): ReceiptImagePicker {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) callback(null)
        else scope.launch { callback(withContext(Dispatchers.IO) {
            processReceiptUri(context.contentResolver, uri)
        }) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pending?.let(Uri::parse)
        if (!ok || uri == null) {
            try { callback(null) } finally {
                pending = null
                context.receiptCameraFile().delete()
            }
        } else scope.launch {
            try {
                callback(withContext(Dispatchers.IO) {
                    processReceiptUri(context.contentResolver, uri)
                })
            } finally {
                pending = null
                context.receiptCameraFile().delete()
            }
        }
    }
    return remember(context, gallery, camera) {
        object : ReceiptImagePicker {
            override fun launchGallery() = gallery.launch("image/*")
            override fun launchCamera() {
                runCatching {
                    val file = context.receiptCameraFile()
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                    val uri = FileProvider.getUriForFile(context,
                        "${context.packageName}.receipt.fileprovider", file)
                    pending = uri.toString()
                    camera.launch(uri)
                }.onFailure {
                    pending = null
                    context.receiptCameraFile().delete()
                    callback(Result.failure(it))
                }
            }
        }
    }
}

private fun Context.receiptCameraFile() = File(cacheDir, "receipts/camera.jpg")
