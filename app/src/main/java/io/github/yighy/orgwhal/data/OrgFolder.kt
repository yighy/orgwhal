package io.github.yighy.orgwhal.data

import java.io.IOException
import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/** What is on disk after a save is not what was written. */
class FileNotSavedException : IOException("The file was not saved correctly")

/** Reads and writes .org files in a folder picked by the user (Storage Access Framework). */
class OrgFolder(private val resolver: ContentResolver, val treeUri: Uri) {

    data class FileRef(val uri: Uri, val name: String, val path: String, val lastModified: Long)

    /**
     * All .org files in the folder and its subfolders, and their archives (.org_archive), skipping
     * hidden ones (.git, .stfolder…).
     */
    fun listOrgFiles(): List<FileRef> {
        val result = mutableListOf<FileRef>()
        collect(DocumentsContract.getTreeDocumentId(treeUri), "", result)
        return result.sortedBy { it.path.lowercase() }
    }

    private fun collect(parentId: String, prefix: String, out: MutableList<FileRef>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
        )
        resolver.query(childrenUri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue
                val path = prefix + name
                if (c.getString(2) == Document.MIME_TYPE_DIR) {
                    collect(id, "$path/", out)
                } else if (name.endsWith(".org", ignoreCase = true) || name.endsWith(".org_archive", ignoreCase = true)) {
                    out += FileRef(DocumentsContract.buildDocumentUriUsingTree(treeUri, id), name, path, c.getLong(3))
                }
            }
        }
    }

    fun lastModified(uri: Uri): Long =
        resolver.query(uri, arrayOf(Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        } ?: 0L

    /** The file just written and checked, so reading it right after needs no second read. */
    private var justWritten: Pair<Uri, String>? = null

    fun read(uri: Uri): String {
        justWritten?.let { (writtenUri, text) ->
            justWritten = null
            if (writtenUri == uri) return text
        }
        return resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Cannot open $uri")
    }

    /**
     * Replaces the file's content with [text], then reads it back: what is on disk must be
     * exactly what was written, or the save fails.
     */
    fun write(uri: Uri, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        // The file is also cut to the new length after writing: emptying it on opening is up to
        // the provider, and if it did not, the end of a longer previous version would stay behind
        val descriptor = runCatching { resolver.openFileDescriptor(uri, "rwt") }.getOrNull()
        if (descriptor != null) {
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { out ->
                out.write(bytes)
                // A provider may hand out a pipe, which cannot be cut (nor left longer)
                runCatching { out.channel.truncate(bytes.size.toLong()) }
                runCatching { out.fd.sync() }
            }
        } else {
            resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Cannot write $uri")
        }
        val written = resolver.openInputStream(uri)?.use { it.readBytes() }
        if (written == null || !written.contentEquals(bytes)) throw FileNotSavedException()
        justWritten = uri to text
    }

    /** Deletes the file from the folder. */
    fun delete(uri: Uri) {
        if (!DocumentsContract.deleteDocument(resolver, uri)) error("Cannot delete $uri")
    }

    /** The document at [relativePath] ("Download/document.txt") in the folder, or null. */
    fun find(relativePath: String): Uri? {
        var id = DocumentsContract.getTreeDocumentId(treeUri)
        for (part in relativePath.split('/').filter { it.isNotEmpty() && it != "." }) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, id)
            id = resolver.query(children, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                var found: String? = null
                while (found == null && c.moveToNext()) if (c.getString(1) == part) found = c.getString(0)
                found
            } ?: return null
        }
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
    }

    fun type(uri: Uri): String? = resolver.getType(uri)

    /** Renames the file in its folder; returns its new URI. */
    fun rename(uri: Uri, name: String): Uri =
        DocumentsContract.renameDocument(resolver, uri, name) ?: error("Cannot rename to $name")

    /** Creates an empty file at [relativePath] ("notes/gtd.org_archive"), named as given, in a folder that exists. */
    fun createFileAt(relativePath: String): Uri {
        val dir = relativePath.substringBeforeLast('/', "")
        val parent = if (dir.isEmpty()) {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        } else {
            find(dir) ?: error("No folder $dir")
        }
        val name = relativePath.substringAfterLast('/')
        // Generic MIME type so the provider keeps the name as given
        return DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name) ?: error("Cannot create $name")
    }

    /** Creates an empty .org file at the root of the folder. */
    fun createFile(name: String): Uri {
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val fileName = if (name.endsWith(".org", ignoreCase = true)) name else "$name.org"
        // Generic MIME type so the provider keeps the .org extension as given
        return DocumentsContract.createDocument(resolver, parent, "application/octet-stream", fileName)
            ?: error("Cannot create $fileName")
    }
}
