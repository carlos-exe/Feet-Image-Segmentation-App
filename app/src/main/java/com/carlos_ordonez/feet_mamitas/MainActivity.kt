package com.carlos_ordonez.feet_mamitas

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private lateinit var imageView: ImageView

    // Registrar la actividad para obtener contenido desde la galería
    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // Actualizar la imagen seleccionada en el ImageView
            imageView.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageToLoad)

        val loadImageButton = findViewById<Button>(R.id.loadImageButton)
        loadImageButton.setOnClickListener {
            // Llamar al lanzador para abrir la galería
            openGallery()
        }
    }

    private fun openGallery() {
        // Usar el lanzador para seleccionar contenido del tipo "image/*"
        selectImageLauncher.launch("image/*")
    }
}
