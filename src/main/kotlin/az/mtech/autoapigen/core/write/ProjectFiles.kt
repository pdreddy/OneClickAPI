package az.mtech.autoapigen.core.write

/** The only door from the core to the file system. The IDE implements it on top of the VFS. */
interface ProjectFiles {
    /** Text of the file at the module-relative [path], or null if it does not exist. */
    fun read(path: String): String?

    fun exists(path: String): Boolean = read(path) != null

    /** Creates parent directories as needed. */
    fun write(path: String, content: String)
}

/** In-memory implementation used by tests and by the preview. */
class MemoryFiles(initial: Map<String, String> = emptyMap()) : ProjectFiles {
    val files: MutableMap<String, String> = initial.toMutableMap()
    override fun read(path: String): String? = files[path]
    override fun write(path: String, content: String) {
        files[path] = content
    }
}
