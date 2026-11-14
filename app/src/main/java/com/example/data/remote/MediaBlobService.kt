package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

data class UploadedMediaResult(
    val blobId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)

class MediaBlobService(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    companion object {
        private const val CHUNK_SIZE_BYTES = 500_000
        private const val MAX_INLINE_BYTES = 500_000
        private const val MAX_TOTAL_BYTES = 6_000_000
    }

    private fun mediaCacheDir(context: Context): File {
        val dir = File(context.filesDir, "septis_media")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCachedFile(context: Context, blobId: String, fileName: String): File? {
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(mediaCacheDir(context), "${blobId}_$safeName")
        return if (file.exists() && file.length() > 0) file else null
    }

    suspend fun uploadImageUri(
        context: Context,
        uri: Uri,
        scope: String,
        allowedUids: List<String>,
        onProgress: (Float) -> Unit = {}
    ): Result<UploadedMediaResult> = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Cannot read selected image.")
            val compressed = compressImageBytes(rawBytes)
            val fileName = queryFileName(context, uri) ?: "photo_${System.currentTimeMillis()}.jpg"
            uploadBytes(
                bytes = compressed,
                fileName = fileName.substringBeforeLast('.') + ".jpg",
                mimeType = "image/jpeg",
                scope = scope,
                allowedUids = allowedUids,
                onProgress = onProgress
            ).getOrThrow()
        }
    }

    suspend fun uploadFileUri(
        context: Context,
        uri: Uri,
        fallbackMime: String,
        scope: String,
        allowedUids: List<String>,
        onProgress: (Float) -> Unit = {}
    ): Result<UploadedMediaResult> = withContext(Dispatchers.IO) {
        runCatching {
            val mimeType = context.contentResolver.getType(uri) ?: fallbackMime
            val fileName = queryFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Unable to read selected file.")
            if (mimeType.startsWith("image/")) {
                val compressed = compressImageBytes(bytes)
                return@runCatching uploadBytes(
                    bytes = compressed,
                    fileName = fileName,
                    mimeType = "image/jpeg",
                    scope = scope,
                    allowedUids = allowedUids,
                    onProgress = onProgress
                ).getOrThrow()
            }
            uploadBytes(
                bytes = bytes,
                fileName = fileName,
                mimeType = mimeType,
                scope = scope,
                allowedUids = allowedUids,
                onProgress = onProgress
            ).getOrThrow()
        }
    }

    suspend fun uploadBytes(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        scope: String,
        allowedUids: List<String>,
        onProgress: (Float) -> Unit = {}
    ): Result<UploadedMediaResult> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Authentication required.")
            require(bytes.isNotEmpty()) { "Selected file is empty." }
            require(bytes.size <= MAX_TOTAL_BYTES) {
                "File exceeds 6 MB upload limit (${bytes.size / 1024} KB)."
            }

            val blobId = "blob_${UUID.randomUUID().toString().replace("-", "").take(20)}"
            val blobRef = db.collection("media_blobs").document(blobId)
            val safeAllowed = (allowedUids + uid).distinct().take(100)
            val safeFileName = fileName.take(180).ifBlank { "attachment" }
            val safeMime = mimeType.take(96).ifBlank { "application/octet-stream" }

            onProgress(0.08f)

            if (bytes.size <= MAX_INLINE_BYTES) {
                val payload = mapOf(
                    "ownerUid" to uid,
                    "scope" to scope,
                    "allowedUids" to safeAllowed,
                    "mimeType" to safeMime,
                    "fileName" to safeFileName,
                    "sizeBytes" to bytes.size.toLong(),
                    "chunkCount" to 0L,
                    "payload" to Blob.fromBytes(bytes),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                try {
                    blobRef.set(payload).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.CREATE, blobRef.path)
                    throw e
                }
                onProgress(1f)
            } else {
                val chunks = bytes.toList().chunked(CHUNK_SIZE_BYTES).map { it.toByteArray() }
                val metaPayload = mapOf(
                    "ownerUid" to uid,
                    "scope" to scope,
                    "allowedUids" to safeAllowed,
                    "mimeType" to safeMime,
                    "fileName" to safeFileName,
                    "sizeBytes" to bytes.size.toLong(),
                    "chunkCount" to chunks.size.toLong(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                try {
                    blobRef.set(metaPayload).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.CREATE, blobRef.path)
                    throw e
                }
                chunks.forEachIndexed { index, chunkBytes ->
                    val chunkRef = blobRef.collection("chunks").document("c_$index")
                    val chunkPayload = mapOf(
                        "ownerUid" to uid,
                        "scope" to scope,
                        "allowedUids" to safeAllowed,
                        "chunkIndex" to index.toLong(),
                        "payload" to Blob.fromBytes(chunkBytes)
                    )
                    try {
                        chunkRef.set(chunkPayload).await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        handleFirestoreError(e, OperationType.CREATE, chunkRef.path)
                        throw e
                    }
                    onProgress(((index + 1).toFloat() / chunks.size).coerceIn(0.1f, 1f))
                }
            }

            UploadedMediaResult(
                blobId = blobId,
                fileName = safeFileName,
                mimeType = safeMime,
                sizeBytes = bytes.size.toLong()
            )
        }
    }

    suspend fun downloadToCache(
        context: Context,
        blobId: String,
        fallbackFileName: String = "media"
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cached = getCachedFile(context, blobId, fallbackFileName)
            if (cached != null) return@runCatching cached

            val blobRef = db.collection("media_blobs").document(blobId)
            val snap = try {
                blobRef.get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.GET, blobRef.path)
                throw e
            }
            if (!snap.exists()) throw IllegalStateException("Media blob not found.")

            val fileName = snap.getString("fileName") ?: fallbackFileName
            getCachedFile(context, blobId, fileName)?.let { return@runCatching it }

            val chunkCount = snap.getLong("chunkCount")?.toInt() ?: 0
            val bytes = if (chunkCount == 0) {
                snap.getBlob("payload")?.toBytes()
                    ?: throw IllegalStateException("Media payload missing.")
            } else {
                val chunksSnap = try {
                    blobRef.collection("chunks")
                        .orderBy("chunkIndex", Query.Direction.ASCENDING)
                        .get()
                        .await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.LIST, "${blobRef.path}/chunks")
                    throw e
                }
                val out = ByteArrayOutputStream()
                chunksSnap.documents.forEach { doc ->
                    doc.getBlob("payload")?.toBytes()?.let { out.write(it) }
                }
                out.toByteArray()
            }

            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(mediaCacheDir(context), "${blobId}_$safeName")
            targetFile.writeBytes(bytes)
            targetFile
        }
    }

    fun openCachedFileWithIntent(context: Context, file: File, mimeType: String): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    }

    private fun compressImageBytes(rawBytes: ByteArray): ByteArray {
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: return rawBytes
        val maxDim = 1080
        val ratio = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height, 1f)
        val target = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }
        val out = ByteArrayOutputStream()
        var quality = 78
        target.compress(Bitmap.CompressFormat.JPEG, quality, out)
        while (out.size() > 450_000 && quality > 35) {
            out.reset()
            quality -= 12
            target.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        if (target !== decoded) decoded.recycle()
        return out.toByteArray()
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
