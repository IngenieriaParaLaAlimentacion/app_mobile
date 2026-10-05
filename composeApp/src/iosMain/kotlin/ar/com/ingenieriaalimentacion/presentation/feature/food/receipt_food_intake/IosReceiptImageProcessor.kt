package ar.com.ingenieriaalimentacion.presentation.feature.food.receipt_food_intake

import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptError
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptFailure
import ar.com.ingenieriaalimentacion.domain.model.food.ReceiptPhoto
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryGetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFNumberGetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSURL
import platform.Foundation.CFBridgingRetain
import platform.Foundation.CFBridgingRelease
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithURL
import platform.ImageIO.kCGImagePropertyPixelHeight
import platform.ImageIO.kCGImagePropertyPixelWidth
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext

@OptIn(ExperimentalForeignApi::class)
internal fun processReceiptFile(url: NSURL): Result<ReceiptPhoto> = runCatching {
    val size = url.path?.let { path ->
        NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? Number
    }
    if (size != null && size.toLong() > MAX_SOURCE_BYTES)
        throw ReceiptFailure(ReceiptError.SourceTooLarge)
    val urlRef = CFBridgingRetain(url)
    try {
        val source = CGImageSourceCreateWithURL(urlRef?.reinterpret(), null)
            ?: throw ReceiptFailure(ReceiptError.InvalidImage)
        try {
            val properties = CGImageSourceCopyPropertiesAtIndex(source, 0u, null)
                ?: throw ReceiptFailure(ReceiptError.InvalidImage)
            val width: Int
            val height: Int
            try {
                width = propertyPixels(properties, kCGImagePropertyPixelWidth)
                height = propertyPixels(properties, kCGImagePropertyPixelHeight)
            } finally { CFRelease(properties) }
            try { receiptTargetSize(width, height) }
            catch (_: IllegalArgumentException) { throw ReceiptFailure(ReceiptError.SourceTooLarge) }
            val options = CFDictionaryCreateMutable(null, 3.convert(), null, null)
                ?: throw ReceiptFailure(ReceiptError.InvalidImage)
            try {
                CFDictionaryAddValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
                CFDictionaryAddValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
                val maxEdge = memScoped {
                    val value = alloc<IntVar>(); value.value = RECEIPT_MAX_EDGE
                    CFNumberCreate(null, kCFNumberIntType, value.ptr)
                } ?: throw ReceiptFailure(ReceiptError.InvalidImage)
                try {
                    CFDictionaryAddValue(options, kCGImageSourceThumbnailMaxPixelSize, maxEdge)
                    val thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0u, options)
                        ?: throw ReceiptFailure(ReceiptError.InvalidImage)
                    try { encodeWebp(UIImage(cGImage = thumbnail)) }
                    finally { CFRelease(thumbnail) }
                } finally { CFRelease(maxEdge) }
            } finally { CFRelease(options) }
        } finally { CFRelease(source) }
    } finally { CFBridgingRelease(urlRef) }
}

@OptIn(ExperimentalForeignApi::class)
private fun propertyPixels(properties: platform.CoreFoundation.CFDictionaryRef, key: platform.CoreFoundation.CFStringRef?): Int {
    val number = CFDictionaryGetValue(properties, key) ?: return 0
    return memScoped {
        val value = alloc<IntVar>()
        if (CFNumberGetValue(number.reinterpret(), kCFNumberIntType, value.ptr)) value.value else 0
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun processReceiptCameraImage(image: UIImage): Result<ReceiptPhoto> = runCatching {
    val pixelWidth = image.CGImage?.let { CGImageGetWidth(it).toInt() }
        ?: image.size.useContents { (width * image.scale).toInt() }
    val pixelHeight = image.CGImage?.let { CGImageGetHeight(it).toInt() }
        ?: image.size.useContents { (height * image.scale).toInt() }
    try { receiptTargetSize(pixelWidth, pixelHeight) }
    catch (_: IllegalArgumentException) { throw ReceiptFailure(ReceiptError.SourceTooLarge) }
    val (sourceWidth, sourceHeight) = image.size.useContents {
        (width * image.scale).toInt() to (height * image.scale).toInt()
    }
    val (width, height) = try { receiptTargetSize(sourceWidth, sourceHeight) }
        catch (_: IllegalArgumentException) { throw ReceiptFailure(ReceiptError.SourceTooLarge) }
    UIGraphicsBeginImageContextWithOptions(
        CGSizeMake(width.toDouble(), height.toDouble()), true, 1.0,
    )
    try {
        image.drawInRect(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
        encodeWebp(UIGraphicsGetImageFromCurrentImageContext()
            ?: throw ReceiptFailure(ReceiptError.InvalidImage))
    } finally { UIGraphicsEndImageContext() }
}

@OptIn(ExperimentalForeignApi::class)
private fun encodeWebp(image: UIImage): ReceiptPhoto {
    // UIKit's small upright image crosses into Skiko through an in-memory PNG.
    val png = UIImagePNGRepresentation(image) ?: throw ReceiptFailure(ReceiptError.InvalidImage)
    val pixels = png.bytes?.readBytes(png.length.toInt())
        ?: throw ReceiptFailure(ReceiptError.InvalidImage)
    val skiaImage = Image.makeFromEncoded(pixels)
    try {
        val encoded = skiaImage.encodeToData(EncodedImageFormat.WEBP, RECEIPT_WEBP_QUALITY)
            ?: throw ReceiptFailure(ReceiptError.InvalidImage)
        try { return validateReceiptPhoto(processedReceiptPhoto(encoded.bytes)).getOrThrow() }
        finally { encoded.close() }
    } finally { skiaImage.close() }
}
