package com.humberto.gestorfinanceiro.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.coroutines.resume

private const val TAG = "ImageTextExtractor"

class ImageTextExtractor(private val context: Context) {
    
    private val textRecognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    /**
     * Extrai texto de uma imagem usando Google ML Kit
     * @param imageUri URI da imagem a ser processada
     * @return Texto extraído da imagem, ou null em caso de erro
     */
    suspend fun extractTextFromImage(imageUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Iniciando extração de texto da imagem: $imageUri")
            
            // Carregar imagem do URI
            val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
            if (inputStream == null) {
                Log.e(TAG, "Não foi possível abrir o stream da imagem")
                return@withContext null
            }
            
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (bitmap == null) {
                Log.e(TAG, "Não foi possível decodificar a imagem")
                return@withContext null
            }
            
            // Criar InputImage para o ML Kit
            val image = InputImage.fromBitmap(bitmap, 0)
            
            // Processar imagem com ML Kit
            val extractedText = suspendCancellableCoroutine<String?> { continuation ->
                textRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val fullText = visionText.text
                        Log.d(TAG, "Texto extraído com sucesso: ${fullText.take(100)}...")
                        continuation.resume(fullText)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Erro ao processar imagem com ML Kit", e)
                        continuation.resume(null)
                    }
            }
            
            extractedText
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao extrair texto da imagem", e)
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Fecha o reconhecedor de texto quando não for mais necessário
     */
    fun close() {
        textRecognizer.close()
    }
}

