package com.carlos_ordonez.feet_mamitas

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MainActivity : ComponentActivity() {

    private lateinit var imageView: ImageView // Lo que vamos a poner en el viewContent y es un widget tipo ImageView
    private lateinit var tflite: Interpreter // Interpreter de tflite

    // Registrar la actividad para obtener contenido desde la galería
    private val selectImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                // Convertir el URI a Bitmap
                val bitmap = uriToBitmap(it)
                // Redimensionar la imagen a 224x224 (requerido por el modelo)
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
                // Ejecutar la inferencia y obtener la máscara resultante
                val maskBitmap = runInference(resizedBitmap)
                // Mostrar la máscara en el ImageView
                imageView.setImageBitmap(maskBitmap)
            }
        }

    //starts the application
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)  // Activate the frontend

        imageView = findViewById(R.id.imageToLoad) // takes the widget ImageView
        val loadImageButton = findViewById<Button>(R.id.loadImageButton) // Value of Button
        loadImageButton.setOnClickListener { // If clicked, then
            openGallery() // Open Gallery
        }

        // Cargar el modelo TFLite desde la carpeta assets
        try {
            val model = loadModelFile()  // Basically, returns the model
            tflite = Interpreter(model)  // set the interpreter
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openGallery() {
        selectImageLauncher.launch("image/*")
    }

    // Función para convertir un URI a Bitmap
    private fun uriToBitmap(uri: Uri): Bitmap {
        val inputStream = contentResolver.openInputStream(uri)
        return BitmapFactory.decodeStream(inputStream)
    }

    // Función que ejecuta la inferencia con el modelo
    private fun runInference(bitmap: Bitmap): Bitmap {
        // Preprocesar la imagen: convertirla a ByteBuffer (entrada del modelo)
        val inputBuffer = convertBitmapToByteBuffer(bitmap)

        // Preparar un buffer para la salida del modelo (forma: [1, 224, 224, 1])
        val outputBuffer = ByteBuffer.allocateDirect(1 * 224 * 224 * 4) // 4 bytes por float
        outputBuffer.order(ByteOrder.nativeOrder())

        // Ejecutar la inferencia
        tflite.run(inputBuffer, outputBuffer)

        // Postprocesar la salida: convertirla en un arreglo de float
        outputBuffer.rewind()
        val outputArray = FloatArray(224 * 224)
        outputBuffer.asFloatBuffer().get(outputArray)

        // Crear un Bitmap para la máscara de salida
        val maskBitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        for (y in 0 until 224) {
            for (x in 0 until 224) {
                // Obtener el valor (asumido en rango [0,1]) y mapearlo a [0,255]
                val value = outputArray[y * 224 + x]
                val gray = (value * 255).toInt().coerceIn(0, 255)
                // Crear un color gris y asignarlo al píxel
                val color = Color.rgb(gray, gray, gray)
                maskBitmap.setPixel(x, y, color)
            }
        }
        return maskBitmap
    }

    // Función para convertir un Bitmap a un ByteBuffer, aplicando conversión a escala de grises y normalización
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputSize = 224
        val bytesPerChannel = 4 // float = 4 bytes
        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 1 * bytesPerChannel)
        inputBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixelValue in intValues) {
            // Extraer los componentes RGB (el modelo espera 1 canal, por lo que se convierte a escala de grises)
            val r = (pixelValue shr 16 and 0xFF).toFloat()
            val g = (pixelValue shr 8 and 0xFF).toFloat()
            val b = (pixelValue and 0xFF).toFloat()
            // Conversión a escala de grises usando la fórmula de luminosidad
            val gray = (0.2989f * r + 0.5870f * g + 0.1140f * b) / 255.0f
            inputBuffer.putFloat(gray)
        }
        inputBuffer.rewind()
        return inputBuffer
    }

    // Función para cargar el modelo TFLite desde assets
    @Throws(Exception::class)
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = assets.openFd("model.tflite")  // Abre el modelo desde assets
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}
