package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptError
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptFailure
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSOperationQueue
import platform.darwin.NSObject
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext

@Composable
actual fun rememberReceiptImagePicker(onResult: (Result<ReceiptPhoto>?) -> Unit): ReceiptImagePicker {
    val callback = rememberUpdatedState(onResult)
    return remember { IosReceiptImagePicker { callback.value(it) } }
}

private class IosReceiptImagePicker(
    private val callback: (Result<ReceiptPhoto>?) -> Unit,
) : ReceiptImagePicker {
    private val cameraDelegate = CameraDelegate(callback)
    private val galleryDelegate = GalleryDelegate(callback)

    override fun launchCamera() {
        if (!UIImagePickerController.isSourceTypeAvailable(
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            callback(Result.failure(ReceiptFailure(ReceiptError.InvalidImage)))
            return
        }
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        picker.delegate = cameraDelegate
        val presenter = visibleController() ?: run {
            callback(Result.failure(ReceiptFailure(ReceiptError.Retryable))); return
        }
        presenter.presentViewController(picker, animated = true, completion = null)
    }

    override fun launchGallery() {
        val config = PHPickerConfiguration()
        config.filter = PHPickerFilter.imagesFilter
        config.selectionLimit = 1
        val picker = PHPickerViewController(configuration = config)
        picker.delegate = galleryDelegate
        val presenter = visibleController() ?: run {
            callback(Result.failure(ReceiptFailure(ReceiptError.Retryable))); return
        }
        presenter.presentViewController(picker, animated = true, completion = null)
    }
}

private fun visibleController(): UIViewController? {
    val window = UIApplication.sharedApplication.windows.firstOrNull {
        (it as? UIWindow)?.isKeyWindow() == true
    } as? UIWindow
    var controller = (window ?: UIApplication.sharedApplication.keyWindow)?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}

private class CameraDelegate(
    private val callback: (Result<ReceiptPhoto>?) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        val result = image?.let(::encodeJpeg) ?: Result.failure(ReceiptFailure(ReceiptError.InvalidImage))
        picker.dismissViewControllerAnimated(true) { callback(result) }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true) { callback(null) }
    }
}

private class GalleryDelegate(
    private val callback: (Result<ReceiptPhoto>?) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
        picker.dismissViewControllerAnimated(true) {
            if (provider == null) {
                callback(null)
            } else {
                val concreteType = provider.registeredTypeIdentifiers.mapNotNull { it as? String }
                    .firstOrNull { it in setOf("public.jpeg", "public.png", "public.heic",
                        "public.heif", "org.webmproject.webp") }
                if (concreteType == null) {
                    callback(Result.failure(ReceiptFailure(ReceiptError.InvalidImage)))
                    return@dismissViewControllerAnimated
                }
                provider.loadDataRepresentationForTypeIdentifier(concreteType) { data, _ ->
                    val result = if (data != null && data.length.toLong() > 40L * 1024 * 1024) {
                        Result.failure(ReceiptFailure(ReceiptError.TooLarge))
                    } else {
                        val image = data?.let { UIImage(data = it) }
                        image?.let(::encodeJpeg)
                            ?: Result.failure(ReceiptFailure(ReceiptError.InvalidImage))
                    }
                    NSOperationQueue.mainQueue.addOperationWithBlock { callback(result) }
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun encodeJpeg(image: UIImage): Result<ReceiptPhoto> = runCatching {
    // UIKit draws the image using its orientation, producing upright JPEG pixels.
    val (width, height) = image.size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0 || width * height > 50_000_000.0) {
        throw ReceiptFailure(ReceiptError.TooLarge)
    }
    UIGraphicsBeginImageContextWithOptions(image.size, false, 1.0)
    image.drawInRect(CGRectMake(0.0, 0.0, width, height))
    val upright = UIGraphicsGetImageFromCurrentImageContext() ?: image
    UIGraphicsEndImageContext()
    val data = listOf(0.85, 0.7).asSequence().mapNotNull { UIImageJPEGRepresentation(upright, it) }
        .firstOrNull { it.length.toLong() <= MAX_RECEIPT_BYTES } ?: throw ReceiptFailure(ReceiptError.TooLarge)
    val bytes = data.bytes?.readBytes(data.length.toInt()) ?: throw ReceiptFailure(ReceiptError.InvalidImage)
    ReceiptPhoto(bytes, "image/jpeg", "recibo.jpg")
}
