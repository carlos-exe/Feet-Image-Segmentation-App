package com.carlos_ordonez.feet_mamitas

import android.Manifest
import android.content.ContentUris
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MainActivity : ComponentActivity() {

    private lateinit var imageView: ImageView
    private lateinit var tflite: Interpreter

    // Bandera para saber si se lanzó Fluke iSee
    private var launchedFluke = false

    //Function to launch the permission request
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                loadLastImageFromGallery()
            } else {
                Toast.makeText(this, "Permiso denegado para leer imágenes", Toast.LENGTH_SHORT).show()
            }
        }

    // Launcher to select the image from the gallery
    private val selectImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val bitmap = uriToBitmap(it)
                //Resize for our goals
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
                val maskBitmap = runInference(resizedBitmap)
                imageView.setImageBitmap(maskBitmap)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageToLoad) //Load the first image
        val loadImageButton = findViewById<Button>(R.id.loadImageButton)
        loadImageButton.setOnClickListener { //Load an image from gallery
            openGallery()
        }

        //Then we do the same for the other button
        val captureThermalButton = findViewById<Button>(R.id.captureThermalButton)
        captureThermalButton.setOnClickListener {
            launchFlukeISeeApp()
        }

        //Load model from the assets folder
        try {
            val model = loadModelFile()
            //Initialize the model
            tflite = Interpreter(model)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    //Function to check if Fluke iSee button was pressed
    override fun onResume() {
        super.onResume()
        if (launchedFluke) {
            //Verify the permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                //Ask for the permission if the user didn't accepted
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                // Any case, if it's guaranteed, run the loadlastImage function to get the last image
                // from gallery
                loadLastImageFromGallery()
            }
            launchedFluke = false
        }
    }

    //This function will open the gallery and let the user select the image
    private fun openGallery() {
        selectImageLauncher.launch("image/*")
    }

    //Function to transform the image into a Bitmap so we can use it
    private fun uriToBitmap(uri: Uri): Bitmap {
        val inputStream = contentResolver.openInputStream(uri)
        return BitmapFactory.decodeStream(inputStream)
    }

    //Function to run the inference on the bitmap made with the image selected
    private fun runInference(bitmap: Bitmap): Bitmap {
        val inputBuffer = convertBitmapToByteBuffer(bitmap)
        val outputBuffer = ByteBuffer.allocateDirect(1 * 224 * 224 * 4)
        outputBuffer.order(ByteOrder.nativeOrder())
        tflite.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()
        val outputArray = FloatArray(224 * 224)
        outputBuffer.asFloatBuffer().get(outputArray)
        val maskBitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        for (y in 0 until 224) {
            for (x in 0 until 224) {
                val value = outputArray[y * 224 + x]
                val gray = (value * 255).toInt().coerceIn(0, 255)
                val color = Color.rgb(gray, gray, gray)
                maskBitmap.setPixel(x, y, color)
            }
        }
        return maskBitmap
    }

    //Function to preprocess the image in order to make it readable for the model.
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputSize = 224
        val bytesPerChannel = 4
        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 1 * bytesPerChannel)
        inputBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixelValue in intValues) {
            val r = (pixelValue shr 16 and 0xFF).toFloat()
            val g = (pixelValue shr 8 and 0xFF).toFloat()
            val b = (pixelValue and 0xFF).toFloat()
            val gray = (0.2989f * r + 0.5870f * g + 0.1140f * b) / 255.0f
            inputBuffer.putFloat(gray)
        }
        inputBuffer.rewind()
        return inputBuffer
    }

    //Function to load Model
    @Throws(Exception::class)
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = assets.openFd("model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    //Function to launch the app Fluke iSee
    private fun launchFlukeISeeApp() {
        val flukePackageName = "com.fluke.erlang"
        val launchIntent = packageManager.getLaunchIntentForPackage(flukePackageName)
        if (launchIntent != null) {
            launchedFluke = true
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "La aplicación Fluke iSee no tiene una actividad lanzable.", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to get the last image from gallery
    private fun loadLastImageFromGallery() {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val query = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        query?.use {
            if (it.moveToFirst()) {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val imageId = it.getLong(idColumn)
                val imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId)
                val bitmap = uriToBitmap(imageUri)
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
                val maskBitmap = runInference(resizedBitmap)
                imageView.setImageBitmap(maskBitmap)
            } else {
                Toast.makeText(this, "No se encontró ninguna imagen en la galería.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
