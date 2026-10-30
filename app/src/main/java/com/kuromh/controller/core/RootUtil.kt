package com.kuromh.controller.core

object RootUtil {

    /** Cek akses root. Memblokir sampai dialog root dijawab, jadi panggil dari background thread. */
    fun isRootAvailable(): Boolean = execute("id").contains("uid=0")

    /** Jalankan [command] sebagai root dan kembalikan stdout (sudah di-trim). Panggil dari background thread. */
    fun execute(command: String): String {
        var process: Process? = null
        return try {
            val p = ProcessBuilder("su")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            process = p
            p.outputStream.bufferedWriter().use { writer ->
                writer.write(command)
                writer.write("\nexit\n")
            }
            val output = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor()
            output.trim()
        } catch (e: Exception) {
            ""
        } finally {
            process?.destroy()
        }
    }
}
