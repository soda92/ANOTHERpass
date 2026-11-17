package de.jepfa.yapm.desktop

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class QrDecoderTest {

    @Test
    fun `decodes a generated QR image`() {
        val text = "EMP#protected:AAAA:BBBB:0"
        val size = 400
        val matrix = MultiFormatWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 3),
        )
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until size) {
            for (y in 0 until size) {
                image.setRGB(x, y, if (matrix[x, y]) 0x000000 else 0xFFFFFF)
            }
        }

        val file = Files.createTempFile("anotherpass-qr", ".png").toFile()
        try {
            ImageIO.write(image, "png", file)
            assertEquals(text, QrDecoder.decode(file))
        } finally {
            file.delete()
        }
    }
}
