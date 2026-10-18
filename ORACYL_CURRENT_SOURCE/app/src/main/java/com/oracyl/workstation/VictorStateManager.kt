package com.oracyl.workstation

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Installs Victor into immutable version directories and switches only a tiny
 * atomic pointer. The protected package is never used as writable state.
 */
class VictorStateManager(private val context: Context) {
    data class ActiveState(val versionId: String, val runtimeDir: File, val sourceDir: File)

    private val root = File(context.filesDir, "victor")
    private val versions = File(root, "versions")
    private val activeFile = File(root, "active.json")
    private val lock = Any()

    companion object {
        const val BASELINE_ASSET = "VICTOR_POST104_RELATION_FORGING_CONTINUATION_20261017.zip"
        const val BASELINE_SHA256 = "0c34b8854105af6f37b705a7a9015c468185adfe5478c179dc66d1734e6652c1"
        private const val EXPORT_SCHEMA = "ORACYL_VICTOR_STATE_EXPORT_V1"
        private const val MAX_ARCHIVE_BYTES = 180_000_000L
        private const val MAX_EXPANDED_BYTES = 300_000_000L
        private const val MAX_ENTRIES = 8_000
        private val SAFE_SEGMENT = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,159}")
        private val VERSION_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    }

    init {
        require(root.mkdirs() || root.isDirectory)
        require(versions.mkdirs() || versions.isDirectory)
    }

    fun ensureInitialInstalled(): ActiveState = synchronized(lock) {
        readActiveOrNull()?.let { return it }
        val stagedArchive = File(root, ".baseline.incoming")
        if (stagedArchive.exists() && !stagedArchive.delete()) error("Cannot clear interrupted baseline stage")
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(BASELINE_ASSET).use { source ->
            DigestInputStream(BufferedInputStream(source), digest).use { input ->
                FileOutputStream(stagedArchive).use { output -> input.copyTo(output, 128 * 1024) }
            }
        }
        try {
            check(stagedArchive.length() <= MAX_ARCHIVE_BYTES) { "Victor baseline exceeds archive limit" }
            check(digest.digest().hex() == BASELINE_SHA256) { "Victor baseline digest mismatch" }
            val versionId = "baseline-${BASELINE_SHA256.take(16)}"
            val state = installBaselineArchive(stagedArchive, versionId)
            activate(versionId, previous = null)
            state
        } finally {
            stagedArchive.delete()
        }
    }

    fun active(): ActiveState = synchronized(lock) {
        readActiveOrNull() ?: error("Victor has not been installed")
    }

    fun listVersions(): List<String> = synchronized(lock) {
        versions.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.map { it.name }?.sorted() ?: emptyList()
    }

    fun importFrom(uri: Uri): ActiveState = synchronized(lock) {
        val incoming = File(root, ".import.incoming.zip")
        if (incoming.exists() && !incoming.delete()) error("Cannot clear interrupted import")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(incoming).use { output -> input.copyTo(output, 128 * 1024) }
        } ?: error("Unable to open Victor import")
        try {
            check(incoming.length() in 1..MAX_ARCHIVE_BYTES) { "Victor import size is invalid" }
            val archiveSha = sha256(incoming)
            val versionId = "import-${VERSION_TIME.format(Instant.now())}-${archiveSha.take(12)}"
            val prior = readPointer().getString("active")
            val state = installExportArchive(incoming, versionId)
            activate(versionId, previous = prior)
            state
        } finally {
            incoming.delete()
        }
    }

    fun exportTo(uri: Uri): JSONObject = synchronized(lock) {
        val state = active()
        val files = state.runtimeDir.walkTopDown().filter { it.isFile }.map {
            "runtime/${it.relativeTo(state.runtimeDir).invariantSeparatorsPath}" to it
        }.toList() + state.sourceDir.walkTopDown().filter { it.isFile }.map {
            "source/${it.relativeTo(state.sourceDir).invariantSeparatorsPath}" to it
        }.toList()
        require(files.size <= MAX_ENTRIES) { "Victor export file count exceeds limit" }
        val manifestFiles = JSONArray()
        files.sortedBy { it.first }.forEach { (path, file) ->
            manifestFiles.put(JSONObject().put("path", path).put("size", file.length()).put("sha256", sha256(file)))
        }
        val manifest = JSONObject()
            .put("schema", EXPORT_SCHEMA)
            .put("created_at", Instant.now().toString())
            .put("source_version", state.versionId)
            .put("files", manifestFiles)
        context.contentResolver.openOutputStream(uri, "w")?.use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
                files.sortedBy { it.first }.forEach { (path, file) ->
                    zip.putNextEntry(ZipEntry(path)); FileInputStream(file).use { it.copyTo(zip, 128 * 1024) }; zip.closeEntry()
                }
            }
        } ?: error("Unable to open Victor export destination")
        JSONObject().put("status", "EXPORTED").put("version_id", state.versionId)
            .put("files", files.size).put("destination", "user_selected_document")
    }

    fun rollback(): ActiveState = synchronized(lock) {
        val pointer = readPointer()
        val current = pointer.getString("active")
        val previousValue = pointer.opt("previous")
        val previous = if (previousValue == null || previousValue == JSONObject.NULL) {
            error("No previous Victor state exists")
        } else previousValue.toString()
        val restored = stateFor(previous)
        activate(previous, previous = current)
        restored
    }

    private fun installBaselineArchive(archive: File, versionId: String): ActiveState {
        val target = File(versions, versionId)
        if (target.exists()) return stateFor(versionId)
        val stage = File(versions, ".$versionId.incoming")
        stage.deleteRecursively(); check(stage.mkdirs())
        var expanded = 0L; var count = 0
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val mapping = when {
                        entry.name.startsWith("victor_world_dialogue_state/") ->
                            "runtime/" + entry.name.removePrefix("victor_world_dialogue_state/")
                        entry.name.startsWith("victor_observation_clone/SOURCE/") ->
                            "source/" + entry.name.removePrefix("victor_observation_clone/SOURCE/")
                        else -> null
                    } ?: continue
                    expanded += copySafeEntry(zip, stage, mapping); count++
                    check(expanded <= MAX_EXPANDED_BYTES && count <= MAX_ENTRIES) { "Victor baseline expansion limit exceeded" }
                }
            }
            validateInstalled(stage)
            check(stage.renameTo(target)) { "Unable to commit Victor baseline" }
            return stateFor(versionId)
        } catch (failure: Throwable) {
            stage.deleteRecursively(); throw failure
        }
    }

    private fun installExportArchive(archive: File, versionId: String): ActiveState {
        val target = File(versions, versionId)
        check(!target.exists()) { "Victor version already exists" }
        val stage = File(versions, ".$versionId.incoming")
        stage.deleteRecursively(); check(stage.mkdirs())
        try {
            ZipFile(archive).use { zip ->
                val manifestEntry = zip.getEntry("manifest.json") ?: error("Victor export manifest is missing")
                val manifest = zip.getInputStream(manifestEntry).bufferedReader().use { JSONObject(it.readText()) }
                check(manifest.optString("schema") == EXPORT_SCHEMA) { "Unsupported Victor export schema" }
                val expected = linkedMapOf<String, Pair<Long, String>>()
                val rows = manifest.getJSONArray("files")
                check(rows.length() in 1..MAX_ENTRIES)
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i); val path = row.getString("path")
                    safeRelative(path)
                    check(path.startsWith("runtime/") || path.startsWith("source/"))
                    check(expected.put(path, row.getLong("size") to row.getString("sha256")) == null)
                }
                val actualEntries = zip.entries().asSequence().filter { !it.isDirectory && it.name != "manifest.json" }.toList()
                check(actualEntries.map { it.name }.toSet() == expected.keys) { "Victor export manifest coverage mismatch" }
                var expanded = 0L
                actualEntries.forEach { entry ->
                    val pair = expected.getValue(entry.name)
                    val targetFile = safeTarget(stage, entry.name); targetFile.parentFile?.mkdirs()
                    val digest = MessageDigest.getInstance("SHA-256")
                    zip.getInputStream(entry).use { input ->
                        DigestInputStream(input, digest).use { source ->
                            FileOutputStream(targetFile).use { output -> source.copyTo(output, 128 * 1024) }
                        }
                    }
                    expanded += targetFile.length(); check(expanded <= MAX_EXPANDED_BYTES)
                    check(targetFile.length() == pair.first && digest.digest().hex() == pair.second) { "Victor export file digest mismatch" }
                }
            }
            validateInstalled(stage)
            check(stage.renameTo(target)) { "Unable to commit Victor import" }
            return stateFor(versionId)
        } catch (failure: Throwable) {
            stage.deleteRecursively(); throw failure
        }
    }

    private fun validateInstalled(base: File) {
        val runtime = File(base, "runtime")
        val source = File(base, "source")
        checkSqlite(File(runtime, "victor_state/semantic_node_index.sqlite3"))
        checkSqlite(File(runtime, "victor_cipher_v19.sqlite3"))
        check(File(runtime, "victor_state/native_gate11").isDirectory) { "Victor event state is missing" }
        check(File(source, "python/victor_mobile_bridge.py").isFile) { "Victor MobileBridge source is missing" }
        check(File(source, "python/VICTOR_V19_CORE.py").isFile) { "Victor core source is missing" }
    }

    private fun checkSqlite(file: File) {
        check(file.isFile && file.length() >= 100) { "Required Victor database is missing" }
        val header = ByteArray(16); FileInputStream(file).use { check(it.read(header) == 16) }
        check(header.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))) { "Invalid Victor database header" }
    }

    private fun copySafeEntry(input: InputStream, base: File, relative: String): Long {
        val target = safeTarget(base, relative); target.parentFile?.mkdirs()
        FileOutputStream(target).use { input.copyTo(it, 128 * 1024) }
        return target.length()
    }

    private fun safeRelative(path: String) {
        val parts = path.split('/')
        check(path.isNotBlank() && !path.startsWith('/') && parts.none { it.isBlank() || it == "." || it == ".." || !SAFE_SEGMENT.matches(it) }) {
            "Unsafe Victor archive path"
        }
    }

    private fun safeTarget(base: File, relative: String): File {
        safeRelative(relative)
        val target = File(base, relative).canonicalFile
        check(target.path.startsWith(base.canonicalPath + File.separator)) { "Victor archive path escapes staging" }
        return target
    }

    private fun activate(versionId: String, previous: String?) {
        stateFor(versionId)
        val body = JSONObject().put("schema", "ORACYL_VICTOR_ACTIVE_V1")
            .put("active", versionId).put("previous", previous ?: JSONObject.NULL)
            .put("activated_at", Instant.now().toString())
        val incoming = File(root, ".active.incoming")
        FileOutputStream(incoming).use { output ->
            output.write(body.toString().toByteArray(Charsets.UTF_8)); output.fd.sync()
        }
        try {
            Files.move(incoming.toPath(), activeFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(incoming.toPath(), activeFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun readPointer(): JSONObject {
        check(activeFile.isFile) { "Victor active pointer is missing" }
        val value = JSONObject(activeFile.readText())
        check(value.optString("schema") == "ORACYL_VICTOR_ACTIVE_V1") { "Victor active pointer is invalid" }
        return value
    }

    private fun readActiveOrNull(): ActiveState? = if (!activeFile.isFile) null else stateFor(readPointer().getString("active"))

    private fun stateFor(versionId: String): ActiveState {
        check(SAFE_SEGMENT.matches(versionId)) { "Unsafe Victor version id" }
        val base = File(versions, versionId)
        validateInstalled(base)
        return ActiveState(versionId, File(base, "runtime"), File(base, "source"))
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input -> DigestInputStream(input, digest).use { it.copyTo(java.io.OutputStream.nullOutputStream()) } }
        return digest.digest().hex()
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}