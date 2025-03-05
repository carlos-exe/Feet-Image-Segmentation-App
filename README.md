# Feet Segmentation Android App

## Overview
This repository contains the source code for the "Feet Segmentation" Android application, developed using Kotlin and TensorFlow Lite. The app performs real-time semantic segmentation of thermal foot images, leveraging a pre-trained U-Net model enhanced with Convolutional Random Fourier Feature (ConvRFF) layers. It offers two inference options: loading images from the gallery or capturing thermal images via the Fluke iSee app. The project is part of a broader research effort to improve medical diagnostics through mobile technology, specifically targeting foot ulcer detection.

## Features
- **Gallery Image Segmentation**: Select and segment existing images from the device.
- **Thermal Image Capture**: Integrate with Fluke iSee to process newly captured thermal images.
- **Real-Time Inference**: Utilizes a TensorFlow Lite model (`model.tflite`) for efficient on-device processing.

## Prerequisites
- Android Studio (latest version)
- Android device or emulator (API level 21 or higher)
- Fluke iSee app installed (for thermal capture functionality)
- TensorFlow Lite support library

## Instructions to use another model
- Open app/src/main/ml and load your tflite model here
- Open app/src/main/java/com/carlos_ordonez/feet_mamitas/MainActivity.kt and replace in line 152, on the function "loadModelFile", "model.tflite" for the name of your model.

## Setup Instructions
1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/feet-segmentation-app.git
