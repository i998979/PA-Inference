package com.example.gpt_sovits_demo

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.experimental.and

class MainActivity : ComponentActivity() {
    companion object {
        init {
            System.loadLibrary("gpt_sovits_demo_jni")
        }
    }

    external fun initModel(
        g2pWPath: String,
        vitsPath: String,
        sslPath: String,
        t2sEncoderPath: String,
        t2sFsDecoderPath: String,
        t2sSDecoderPath: String,
        bertPath: String,
        maxLength: Int
    ): Long

    external fun processReferenceSync(
        modelHandle: Long,
        refAudioPath: String,
        refText: String
    ): Boolean

    external fun runInferenceSync(modelHandle: Long, text: String): FloatArray?
    external fun freeModel(modelHandle: Long)

    private lateinit var mediaPlayer: MediaPlayer
    private val audioHistory = mutableStateListOf<AudioEntry>()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var modelHandle by mutableStateOf(0L)
    private var selectedModelFolder by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaPlayer = MediaPlayer()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TextToSpeechScreen(
                        onGenerateClick = { text -> runInferenceAsync(text) },
                        onLoadClick = { loadModelAsync() },
                        onSelectFolderClick = { folderPickerLauncher.launch(null) },
                        audioHistory = audioHistory,
                        onReplayClick = { audioPath -> playAudioFromFile(audioPath) },
                        selectedFolder = selectedModelFolder,
                    )
                }
            }
        }
    }

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { folderUri ->
            // Persist permissions for the selected folder
            contentResolver.takePersistableUriPermission(
                folderUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            selectedModelFolder = folderUri.toString()
            Toast.makeText(this, "Model folder selected", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(this, "No folder selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadModelAsync() {
        coroutineScope.launch {
            try {
                var loadingProgress by mutableStateOf(0f)
                var isLoading by mutableStateOf(true)
                try {
                    withContext(Dispatchers.IO) {
                        if (selectedModelFolder == null) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Please select a model folder first",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            return@withContext
                        }
                        val modelFiles = getModelsFromFolder(selectedModelFolder!!) { progress ->
                            loadingProgress = progress
                        }
                        if (modelFiles.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Failed to load models from folder",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            return@withContext
                        }
                        loadingProgress = 0.8f // After accessing files, 80% done
                        modelHandle = initModel(
                            modelFiles["g2pW"]!!,
                            modelFiles["vits"]!!,
                            modelFiles["ssl"]!!,
                            modelFiles["t2s_encoder"]!!,
                            modelFiles["t2s_fs_decoder"]!!,
                            modelFiles["t2s_s_decoder"]!!,
                            modelFiles["bert"]!!,
                            24
                        )
                        if (modelHandle == 0L) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Model initialization failed",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            return@withContext
                        }
                        loadingProgress = 0.9f // After init, 90% done
                        val refAudioPath = modelFiles["ref"]!!
                        val refText = "格式化，可以给自家的奶带来大量的"
                        val refSuccess = processReferenceSync(modelHandle, refAudioPath, refText)
                        if (!refSuccess) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Reference audio processing failed",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            freeModel(modelHandle)
                            modelHandle = 0L
                            return@withContext
                        }
                        loadingProgress = 1.0f // Loading complete
                    }
                    Toast.makeText(
                        this@MainActivity,
                        "Model loaded successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("MainActivity", "Error loading model", e)
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    private fun getModelsFromFolder(folderUriString: String, onProgressUpdate: (Float) -> Unit): Map<String, String> {
        val modelFiles = mapOf(
            "g2pW" to "g2pW.onnx",
            "vits" to "custom_vits.onnx",
            "ssl" to "ssl.onnx",
            "t2s_encoder" to "custom_t2s_encoder.onnx",
            "t2s_fs_decoder" to "custom_t2s_fs_decoder.onnx",
            "t2s_s_decoder" to "custom_t2s_s_decoder.onnx",
            "bert" to "bert.onnx",
            "ref" to "ref.wav"
        )
        val outputFiles = mutableMapOf<String, String>()
        try {
            val folderUri = folderUriString.toUri()
            val documentFile = DocumentFile.fromTreeUri(this, folderUri)
                ?: throw IllegalStateException("Invalid folder URI")
            val totalFiles = modelFiles.size
            var filesProcessed = 0
            for ((key, fileName) in modelFiles) {
                val file = documentFile.findFile(fileName)
                if (file == null || !file.exists()) {
                    Log.e("MainActivity", "Model file $fileName not found in selected folder")
                    return emptyMap()
                }
                val fileUri = file.uri
                // Copy file to cache to ensure accessibility
                val cacheFile = File(cacheDir, fileName)
                contentResolver.openInputStream(fileUri)?.use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (!cacheFile.exists()) {
                    Log.e("MainActivity", "Failed to copy $fileName to cache")
                    return emptyMap()
                }
                outputFiles[key] = cacheFile.absolutePath
                filesProcessed++
                onProgressUpdate(filesProcessed / totalFiles.toFloat() * 0.8f) // Up to 80% for copying
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error accessing folder: ${e.message}", e)
            return emptyMap()
        }
        return outputFiles
    }

    private fun runInferenceAsync(text: String) {
        if (modelHandle == 0L) {
            Toast.makeText(this, "Please load model first", Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    val samples = runInferenceSync(modelHandle, text)
                    if (samples != null) {
                        val wavFile = File(cacheDir, "output_${UUID.randomUUID()}.wav")
                        writeWavFile(wavFile, WavSpec(32000, 16, 1), samples)
                        withContext(Dispatchers.Main) {
                            val inferenceTime = System.currentTimeMillis() - startTime
                            playAudioFromFile(wavFile.absolutePath)
                            audioHistory.add(
                                0,
                                AudioEntry(text, wavFile.absolutePath, inferenceTime)
                            )
                            Toast.makeText(
                                this@MainActivity,
                                "Audio generated in ${inferenceTime}ms",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Inference failed", Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("MainActivity", "Error during inference", e)
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    @Composable
    fun TextToSpeechScreen(
        onGenerateClick: (String) -> Unit,
        onLoadClick: () -> Unit,
        onSelectFolderClick: () -> Unit, // New callback for folder selection
        audioHistory: List<AudioEntry>,
        onReplayClick: (String) -> Unit,
        selectedFolder: String?
    ) {
        var textInput by remember { mutableStateOf(TextFieldValue("")) }
        var isProcessing by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(false) }
        var loadingProgress by remember { mutableStateOf(0f) }
        val modelLoaded by remember { derivedStateOf { modelHandle != 0L } }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onSelectFolderClick() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        ) {
                            Text("Select Model Folder")
                        }
                        Text(
                            text = selectedFolder?.let { "Selected: ${it.toUri().lastPathSegment}" } ?: "No folder selected",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Button(
                            onClick = {
                                if (!isLoading) {
                                    isLoading = true
                                    onLoadClick()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading && selectedFolder != null
                        ) {
                            Text("Load Model")
                        }
                        if (isLoading && loadingProgress < 1.0f) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LinearProgressIndicator(
                                    progress = { loadingProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                )
                                Text(
                                    text = "Loading model: ${(loadingProgress * 100).toInt()}%",
                                    fontSize = 14.sp
                                )
                            }
                        }
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Enter text to synthesize") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isProcessing && modelLoaded
                        )
                        Button(
                            onClick = {
                                if (!isProcessing && modelLoaded) {
                                    isProcessing = true
                                    val text = textInput.text.ifEmpty { "Hello, this is a test." }
                                    onGenerateClick(text)
                                    isProcessing = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isProcessing && modelLoaded
                        ) {
                            Text("Generate Audio")
                        }
                        if (isProcessing) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generating audio...", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
            // [Rest of the Composable remains unchanged]
            item {
                Text(
                    text = "Demo Texts",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                DemoCard("你好，欢迎来到小鱼的TTS测试。", onGenerateClick, isProcessing || !modelLoaded)
                DemoCard("小鱼想成为你的好朋友，而不仅仅是一个可爱的AI助理", onGenerateClick, isProcessing || !modelLoaded)
                DemoCard("呀！忘了说，希望你能喜欢小鱼！", onGenerateClick, isProcessing || !modelLoaded)
            }
            item {
                Text(
                    text = "Audio History",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(audioHistory) { entry ->
                AudioHistoryItem(
                    text = entry.text,
                    inferenceTime = entry.inferenceTime,
                    onClick = { onReplayClick(entry.audioPath) }
                )
            }
        }
    }

    @Composable
    fun DemoCard(text: String, onGenerateClick: (String) -> Unit, isDisabled: Boolean) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(enabled = !isDisabled) { onGenerateClick(text) },
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDisabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
            )
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    @Composable
    fun AudioHistoryItem(text: String, inferenceTime: Long, onClick: () -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onClick() },
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Inference time: ${inferenceTime}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onClick() }) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Replay Audio"
                    )
                }
            }
        }
    }

    private fun verifyFileIntegrity(file: File, assetName: String): Boolean {
        return try {
            val assetSize = assets.open(assetName).use { it.available().toLong() }
            file.exists() && file.length() == assetSize
        } catch (e: Exception) {
            Log.e("MainActivity", "Error verifying $assetName: ${e.message}")
            false
        }
    }

    private fun playAudioFromFile(audioPath: String) {
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    mediaPlayer.stop()
                    mediaPlayer.reset()
                    mediaPlayer.release()
                }
                mediaPlayer = MediaPlayer().apply {
                    setOnErrorListener { _, what, extra ->
                        Log.e("MediaPlayer", "Error: what=$what, extra=$extra")
                        coroutineScope.launch {
                            Toast.makeText(
                                this@MainActivity,
                                "MediaPlayer error: $what, $extra",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        true
                    }
                    setOnInfoListener { _, what, extra ->
                        if (what == 211) {
                            Log.w("MediaPlayer", "Unrecognized message (211, $extra) - ignoring")
                            true
                        } else {
                            Log.i("MediaPlayer", "Info: what=$what, extra=$extra")
                            false
                        }
                    }
                    setOnCompletionListener {
                        Log.i("MediaPlayer", "Playback completed")
                        reset()
                    }
                    setDataSource(audioPath)
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error playing audio: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error playing audio: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun writeWavFile(file: File, spec: WavSpec, samples: FloatArray) {
        FileOutputStream(file).use { outputStream ->
            val bitsPerSample = 16
            val pcmSamples = ShortArray(samples.size) { i ->
                (samples[i] * 32767.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort()
            }
            val totalAudioLen = pcmSamples.size * 2
            val totalDataLen = totalAudioLen + 36

            outputStream.write("RIFF".toByteArray())
            outputStream.write(intToByteArray(totalDataLen))
            outputStream.write("WAVE".toByteArray())
            outputStream.write("fmt ".toByteArray())
            outputStream.write(intToByteArray(16))
            outputStream.write(shortToByteArray(1))
            outputStream.write(shortToByteArray(spec.channels.toShort()))
            outputStream.write(intToByteArray(spec.sampleRate))
            outputStream.write(intToByteArray(spec.sampleRate * spec.channels * bitsPerSample / 8))
            outputStream.write(shortToByteArray((spec.channels * bitsPerSample / 8).toShort()))
            outputStream.write(shortToByteArray(bitsPerSample.toShort()))
            outputStream.write("data".toByteArray())
            outputStream.write(intToByteArray(totalAudioLen))

            val buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            pcmSamples.forEach { sample ->
                buffer.clear()
                buffer.putShort(sample)
                outputStream.write(buffer.array())
            }
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value and 0xFF.toShort()).toByte(),
            ((value.toInt() shr 8).toShort() and 0xFF.toShort()).toByte()
        )
    }

    data class WavSpec(val sampleRate: Int, val bitsPerSample: Int, val channels: Int)
    data class AudioEntry(val text: String, val audioPath: String, val inferenceTime: Long = 0)

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        if (modelHandle != 0L) {
            freeModel(modelHandle)
            modelHandle = 0L
        }
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.release()
        }
    }
}