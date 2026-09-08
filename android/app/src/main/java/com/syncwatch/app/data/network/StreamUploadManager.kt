package com.syncwatch.app.data.network

import android.content.Context
import android.net.Uri
import com.syncwatch.app.utils.MediaUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

sealed class UploadState {
    object Idle : UploadState()
    data class Uploading(
        val progress: Float,
        val fileName: String,
        val bytesUploaded: Long,
        val totalBytes: Long
    ) : UploadState()
    data class Success(val streamUrl: String, val fileName: String) : UploadState()
    data class Error(val message: String) : UploadState()
}

object StreamUploadManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS) // No write timeout for large video uploads
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    private var activeCall: Call? = null

    fun uploadVideo(
        context: Context,
        roomCode: String,
        videoUri: Uri,
        serverHttpUrl: String,
        onSuccess: (streamUrl: String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val fileName = MediaUtils.getFileNameFromUri(context, videoUri)
        val cleanRoomCode = roomCode.uppercase().trim()
        val uploadEndpoint = "${serverHttpUrl.trimEnd('/')}/api/v1/stream/upload/$cleanRoomCode"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val contentResolver = context.contentResolver
                val fileDescriptor = try {
                    contentResolver.openAssetFileDescriptor(videoUri, "r")
                } catch (e: Exception) {
                    null
                }
                val totalBytes = fileDescriptor?.length ?: -1L
                try {
                    fileDescriptor?.close()
                } catch (e: Exception) {}

                val mimeType = contentResolver.getType(videoUri) ?: "video/mp4"

                val requestBody = object : RequestBody() {
                    override fun contentType() = mimeType.toMediaTypeOrNull()

                    override fun contentLength(): Long = totalBytes

                    override fun writeTo(sink: BufferedSink) {
                        contentResolver.openInputStream(videoUri)?.use { input ->
                            val buffer = ByteArray(128 * 1024) // 128KB buffer
                            var bytesRead: Int
                            var totalUploaded = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                sink.write(buffer, 0, bytesRead)
                                totalUploaded += bytesRead
                                if (totalBytes > 0) {
                                    val progress = (totalUploaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                    _uploadState.value = UploadState.Uploading(progress, fileName, totalUploaded, totalBytes)
                                }
                            }
                        }
                    }
                }

                val encodedFileName = try {
                    URLEncoder.encode(fileName, "UTF-8")
                } catch (e: Exception) {
                    "video.mp4"
                }

                val request = Request.Builder()
                    .url(uploadEndpoint)
                    .addHeader("X-File-Name", encodedFileName)
                    .addHeader("Content-Type", mimeType)
                    .post(requestBody)
                    .build()

                _uploadState.value = UploadState.Uploading(0f, fileName, 0L, totalBytes)

                val call = client.newCall(request)
                activeCall = call

                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (call.isCanceled()) return
                        val errorMsg = "Upload failed: ${e.message}"
                        _uploadState.value = UploadState.Error(errorMsg)
                        onError(errorMsg)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            val streamUrl = "${serverHttpUrl.trimEnd('/')}/api/v1/stream/$cleanRoomCode"
                            _uploadState.value = UploadState.Success(streamUrl, fileName)
                            onSuccess(streamUrl)
                        } else {
                            val errorMsg = "Server returned ${response.code}: ${response.message}"
                            _uploadState.value = UploadState.Error(errorMsg)
                            onError(errorMsg)
                        }
                    }
                })
            } catch (e: Exception) {
                val errorMsg = "Cannot read video file: ${e.localizedMessage}"
                _uploadState.value = UploadState.Error(errorMsg)
                onError(errorMsg)
            }
        }
    }

    fun cancelUpload() {
        activeCall?.cancel()
        activeCall = null
        _uploadState.value = UploadState.Idle
    }

    fun reset() {
        _uploadState.value = UploadState.Idle
    }
}
