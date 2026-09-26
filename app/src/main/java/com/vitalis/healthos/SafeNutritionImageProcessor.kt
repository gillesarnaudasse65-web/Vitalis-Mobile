package com.vitalis.healthos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

data class NormalizedNutritionImage(
    val dataUrl: String,
    val metadata: NormalizedImageMetadata
)

data class NutritionImageProcessResult(
    val image: NormalizedNutritionImage? = null,
    val errorCode: String? = null,
    val userMessage: String? = null
) {
    val success: Boolean get() = image != null && errorCode == null
}

class SafeNutritionImageProcessor(private val context: Context) {
    fun process(uri: Uri): NutritionImageProcessResult {
        if (uri.scheme != "content") return failure(
            "invalid_uri",
            "La photo doit provenir du sélecteur Android sécurisé."
        )
        val declaredMime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use(::readBounded)
                ?: return failure("unreadable_uri", "La photo sélectionnée n’est plus accessible.")
        } catch (_: SourceTooLargeException) {
            return failure("source_too_large", "L’image dépasse la limite de 12 Mo.")
        } catch (_: Exception) {
            return failure("unreadable_uri", "La photo sélectionnée ne peut pas être lue.")
        }
        return processBytes(bytes, declaredMime)
    }

    internal fun processBytes(bytes: ByteArray, declaredMime: String?): NutritionImageProcessResult {
        val detected = NutritionImageFormat.detect(bytes)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val validation = NutritionImagePolicy.validate(
            NutritionImageCandidate(
                declaredMimeType = declaredMime,
                detectedFormat = detected,
                sourceBytes = bytes.size.toLong(),
                width = bounds.outWidth,
                height = bounds.outHeight
            )
        )
        if (!validation.accepted) return failure(
            validation.errorCode ?: "invalid_image",
            validation.userMessage ?: "L’image n’est pas valide."
        )
        val plan = NutritionImagePolicy.plan(bounds.outWidth, bounds.outHeight)
        val decoded = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                decoder.setTargetSize(plan.targetWidth, plan.targetHeight)
            }
        } catch (_: Exception) {
            return failure("decode_failed", "L’image est corrompue ou illisible.")
        }
        val opaque = flattenOnWhite(decoded)
        if (opaque !== decoded) decoded.recycle()
        val encoded = encodeBounded(opaque)
        opaque.recycle()
        if (encoded == null) return failure(
            "normalized_too_large",
            "La photo reste trop volumineuse après normalisation."
        )
        val base64 = Base64.encodeToString(encoded.bytes, Base64.NO_WRAP)
        if (base64.length > NutritionImagePolicy.MAX_BASE64_CHARACTERS) return failure(
            "payload_too_large",
            "La photo normalisée dépasse la limite d’analyse."
        )
        return NutritionImageProcessResult(
            image = NormalizedNutritionImage(
                dataUrl = "data:image/jpeg;base64,$base64",
                metadata = NormalizedImageMetadata(
                    sourceMimeType = detected!!.mimeType,
                    sourceBytes = bytes.size.toLong(),
                    sourceWidth = bounds.outWidth,
                    sourceHeight = bounds.outHeight,
                    normalizedMimeType = "image/jpeg",
                    normalizedBytes = encoded.bytes.size,
                    normalizedWidth = encoded.width,
                    normalizedHeight = encoded.height,
                    orientationApplied = true
                )
            )
        )
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > NutritionImagePolicy.MAX_SOURCE_BYTES) throw SourceTooLargeException()
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun flattenOnWhite(source: Bitmap): Bitmap {
        if (!source.hasAlpha()) return source
        return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { target ->
            Canvas(target).apply {
                drawColor(Color.WHITE)
                drawBitmap(source, 0f, 0f, null)
            }
        }
    }

    private fun encodeBounded(source: Bitmap): EncodedBitmap? {
        var working = source
        var ownsWorking = false
        try {
            repeat(5) { attempt ->
                val quality = (NutritionImagePolicy.OUTPUT_QUALITY - attempt * 8).coerceAtLeast(55)
                val output = ByteArrayOutputStream()
                if (!working.compress(Bitmap.CompressFormat.JPEG, quality, output)) return null
                val bytes = output.toByteArray()
                if (bytes.size <= NutritionImagePolicy.MAX_NORMALIZED_BYTES) {
                    return EncodedBitmap(bytes, working.width, working.height)
                }
                val nextWidth = (working.width * 0.8).toInt().coerceAtLeast(1)
                val nextHeight = (working.height * 0.8).toInt().coerceAtLeast(1)
                val smaller = Bitmap.createScaledBitmap(working, nextWidth, nextHeight, true)
                if (ownsWorking) working.recycle()
                working = smaller
                ownsWorking = true
            }
            return null
        } finally {
            if (ownsWorking) working.recycle()
        }
    }

    private fun failure(code: String, message: String) =
        NutritionImageProcessResult(errorCode = code, userMessage = message)

    private data class EncodedBitmap(val bytes: ByteArray, val width: Int, val height: Int)

    private class SourceTooLargeException : IllegalStateException()
}
