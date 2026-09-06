package com.wlc.news

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.File
import java.util.Vector

/**
 * Thin wrapper around JSch (SFTP). All calls must run on a background thread.
 */
class SftpClient(
    private val host: String,
    private val port: Int,
    private val user: String,
    private val password: String,
) : AutoCloseable {

    private var session: Session? = null
    private var channel: ChannelSftp? = null

    private fun ensureConnected() {
        if (channel != null && session != null && session!!.isConnected && channel!!.isConnected) return
        val jsch = JSch()
        val s = jsch.getSession(user, host, port)
        s.setPassword(password)
        s.setConfig("StrictHostKeyChecking", "no")
        s.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        s.timeout = 20_000
        s.setServerAliveInterval(15_000)
        s.connect(20_000)
        val c = s.openChannel("sftp") as ChannelSftp
        c.connect(20_000)
        session = s
        channel = c
    }

    data class RemoteEntry(val name: String, val size: Long, val isDir: Boolean)

    /** Lists the entries of [remoteDir]. Returns null if the folder does not exist. */
    fun list(remoteDir: String): List<RemoteEntry>? {
        ensureConnected()
        val entries = ArrayList<RemoteEntry>()
        try {
            @Suppress("UNCHECKED_CAST")
            val vector = channel!!.ls(remoteDir) as Vector<ChannelSftp.LsEntry>
            for (e in vector) {
                val name = e.filename
                if (name == "." || name == "..") continue
                entries.add(RemoteEntry(name, e.attrs.size, e.attrs.isDir))
            }
        } catch (e: com.jcraft.jsch.SftpException) {
            // no such file/dir
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) return null
            throw e
        }
        return entries
    }

    /** Downloads one file. [onProgress] receives (bytesCopied, totalBytes). */
    fun download(remotePath: String, localFile: File, onProgress: (Long, Long) -> Unit) {
        ensureConnected()
        localFile.parentFile?.mkdirs()
        val attrs = channel!!.stat(remotePath)
        val total = attrs.size
        val input = channel!!.get(remotePath)
        try {
            localFile.outputStream().use { out ->
                val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    copied += n
                    onProgress(copied, total)
                }
            }
        } finally {
            try { input.close() } catch (_: Exception) {}
        }
    }

    override fun close() {
        try { channel?.disconnect() } catch (_: Exception) {}
        try { session?.disconnect() } catch (_: Exception) {}
        channel = null
        session = null
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 32 * 1024

        /** Convenience: build client from saved preferences. */
        fun fromPrefs(context: android.content.Context): SftpClient =
            SftpClient(
                host = Prefs.host(context),
                port = Prefs.port(context),
                user = Prefs.user(context),
                password = Prefs.password(context),
            )
    }
}
