package androidx.documentfile.provider

import android.net.Uri

/**
 * Test-only [DocumentFile] wrapper that can refuse renames to chosen names.
 *
 * Lives in `androidx.documentfile.provider` because `DocumentFile`'s constructor is
 * package-private — there is no other way to subclass it. Used by
 * `com.tendril.app.sync.SafWriteFailureInstrumentedTest` to force a rename failure in the
 * exact window where the old SAF write sequence lost data.
 */
class RenameGateDocumentFile(
    private val delegate: DocumentFile,
    /**
     * `(currentName, targetName) -> refuse?`. Both names matter: refusing purely on the target
     * would also block the `<name>.bak` → `<name>` restore, which is the recovery step the
     * write sequence depends on — so a gate that ignored the source would report a failure the
     * production code is specifically designed to survive.
     */
    private val refuseRename: (String?, String) -> Boolean,
) : DocumentFile(null) {

    private fun wrap(f: DocumentFile?): DocumentFile? =
        f?.let { RenameGateDocumentFile(it, refuseRename) }

    override fun createFile(mimeType: String, displayName: String): DocumentFile? =
        wrap(delegate.createFile(mimeType, displayName))

    override fun createDirectory(displayName: String): DocumentFile? =
        wrap(delegate.createDirectory(displayName))

    override fun getUri(): Uri = delegate.uri
    override fun getName(): String? = delegate.name
    override fun getType(): String? = delegate.type
    override fun isDirectory(): Boolean = delegate.isDirectory
    override fun isFile(): Boolean = delegate.isFile
    override fun isVirtual(): Boolean = delegate.isVirtual
    override fun lastModified(): Long = delegate.lastModified()
    override fun length(): Long = delegate.length()
    override fun canRead(): Boolean = delegate.canRead()
    override fun canWrite(): Boolean = delegate.canWrite()
    override fun delete(): Boolean = delegate.delete()
    override fun exists(): Boolean = delegate.exists()

    override fun listFiles(): Array<DocumentFile> =
        delegate.listFiles().mapNotNull { wrap(it) }.toTypedArray()

    override fun renameTo(displayName: String): Boolean =
        if (refuseRename(delegate.name, displayName)) false else delegate.renameTo(displayName)
}
