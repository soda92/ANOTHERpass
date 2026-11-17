package de.jepfa.yapm.desktop

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.io.File
import javax.imageio.ImageIO

/**
 * Decodes a QR code from an image file. Camera capture can be added later; the
 * import flow only needs the EMP QR, which is usually stored as an image.
 */
object QrDecoder {

    fun decode(file: File): String {
        val image = ImageIO.read(file)
            ?: throw IllegalArgumentException("Unsupported image file: ${file.name}")
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)

        val source = RGBLuminanceSource(width, height, pixels)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return QRCodeReader().decode(bitmap).text
    }
}
