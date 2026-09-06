package com.wlc.news

import android.app.DatePickerDialog
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

class PlayerActivity : AppCompatActivity() {

    private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val episodes = mutableListOf<Episode>()
    private lateinit var adapter: EpisodeAdapter

    private var currentDate: String = ""

    private var mediaPlayer: MediaPlayer? = null
    private var playingIndex = -1
    private var seekLoop: Runnable? = null
    private var userSeeking = false
    private var busy = false

    private lateinit var btnDate: Button
    private lateinit var btnRefresh: com.google.android.material.button.MaterialButton
    private lateinit var tvRemoteDir: TextView
    private lateinit var panelProgress: LinearLayout
    private lateinit var pb: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var playbackBar: LinearLayout
    private lateinit var tvNowPlaying: TextView
    private lateinit var tvTime: TextView
    private lateinit var sbSeek: SeekBar
    private lateinit var btnPlayPause: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.source_npr)

        btnDate = findViewById(R.id.btnDate)
        btnRefresh = findViewById(R.id.btnRefresh)
        tvRemoteDir = findViewById(R.id.tvRemoteDir)
        panelProgress = findViewById(R.id.panelProgress)
        pb = findViewById(R.id.pb)
        tvStatus = findViewById(R.id.tvStatus)
        tvEmpty = findViewById(R.id.tvEmpty)
        playbackBar = findViewById(R.id.playbackBar)
        tvNowPlaying = findViewById(R.id.tvNowPlaying)
        tvTime = findViewById(R.id.tvTime)
        sbSeek = findViewById(R.id.sbSeek)
        btnPlayPause = findViewById(R.id.btnPlayPause)

        adapter = EpisodeAdapter { pos -> onEpisodeClicked(pos) }
        findViewById<RecyclerView>(R.id.rvEpisodes).apply {
            layoutManager = LinearLayoutManager(this@PlayerActivity)
            adapter = this@PlayerActivity.adapter
        }

        btnRefresh.setOnClickListener { startRefresh() }

        findViewById<ImageButton>(R.id.btnPrevDay).setOnClickListener { changeDateBy(-1) }
        findViewById<ImageButton>(R.id.btnNextDay).setOnClickListener { changeDateBy(1) }
        btnDate.setOnClickListener { pickDate() }

        btnPlayPause.setOnClickListener { togglePlayPause() }
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { playRelative(-1) }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { playRelative(1) }

        sbSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && userSeeking) {
                    mediaPlayer?.seekTo(progress)
                    updateTimeLabel()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { userSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar?) { userSeeking = false }
        })

        // Default to "yesterday" (today - 1), e.g. 2026-09-05 for today 2026-09-06.
        val stored = Prefs.lastDate(this)
        currentDate = if (stored != null) stored else shiftDate(today(), -1)
        applyDateUi()
    }

    override fun onResume() {
        super.onResume()
        // Refresh labels in case the base folder changed in Settings, but
        // NEVER stop playback or reload the list (that would kill the MP3
        // when the user comes back from the home screen).
        btnDate.text = "VOICE_$currentDate"
        tvRemoteDir.text = remoteDirOf(currentDate)
    }

    // ---------------------------------------------------------------- dates

    private fun today(): String = dateFormat.format(Calendar.getInstance().time)

    private fun shiftDate(date: String, delta: Int): String {
        return try {
            val c = Calendar.getInstance()
            c.time = dateFormat.parse(date)!!
            c.add(Calendar.DAY_OF_YEAR, delta)
            dateFormat.format(c.time)
        } catch (e: Exception) {
            today()
        }
    }

    private fun remoteDirOf(date: String): String =
        Prefs.basePath(this) + "/VOICE_" + date + Prefs.dateSuffix(this)

    private fun dateDir(date: String): File = File(File(filesDir, "voice"), date)

    private fun changeDateBy(delta: Int) {
        if (busy) return
        currentDate = shiftDate(currentDate, delta)
        applyDateUi()
    }

    private fun pickDate() {
        if (busy) return
        val c = try {
            dateFormat.parse(currentDate)?.let { Calendar.getInstance().apply { time = it } }
        } catch (e: Exception) { null } ?: Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                currentDate = String.format("%04d%02d%02d", y, m + 1, d)
                applyDateUi()
            },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun applyDateUi() {
        Prefs.setLastDate(this, currentDate)
        btnDate.text = "VOICE_$currentDate"
        tvRemoteDir.text = remoteDirOf(currentDate)
        stopPlayback()
        loadLocalDate(currentDate)
    }

    /** Loads whatever was already downloaded for [date] (works offline). */
    private fun loadLocalDate(date: String) {
        val dir = dateDir(date)
        val local = mutableListOf<Episode>()
        dir.listFiles()?.filter { it.isFile && it.name.lowercase().endsWith(".mp3") }
            ?.sortedBy { it.name }
            ?.forEach { f ->
                local.add(Episode(f.name, "", f.length(), f, date))
            }
        episodes.clear()
        episodes.addAll(local)
        adapter.submit(episodes)
        setEmptyVisible(false)
        if (episodes.isEmpty()) {
            showEmpty("Nothing here yet.\nTap “Refresh / Download” to fetch\nVOICE_$date from the server.")
        }
    }

    // ---------------------------------------------------------------- refresh

    private fun startRefresh() {
        if (busy) return
        if (!Prefs.configured(this)) {
            Toast.makeText(this, getString(R.string.need_settings), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        if (!Prefs.hasPassword(this)) {
            promptForServerPassword { startRefresh() }
            return
        }
        setBusy(true)
        setStatus("Connecting to ${Prefs.host(this)}…")
        val dir = remoteDirOf(currentDate)
        val date = currentDate

        executor.execute {
            var error: String? = null
            try {
                SftpClient.fromPrefs(this@PlayerActivity).use { client ->
                    val entries = client.list(dir)

                    val mp3s = (entries ?: emptyList())
                        .filter { !it.isDir && it.name.lowercase().endsWith(".mp3") }
                        .sortedBy { it.name }

                    onUi {
                        if (entries == null) {
                            tvRemoteDir.text = dir
                            setStatus("Folder not found on server")
                            setBusy(false)
                            showEmpty("Folder not found:\n$dir\n\nCheck the date or edit the base folder in Settings.")
                        } else if (mp3s.isEmpty()) {
                            setStatus("0 MP3 files in this folder")
                            setBusy(false)
                            showEmpty("No MP3 found in:\n$dir")
                        }
                    }

                    if (mp3s.isEmpty()) return@use

                    // Update the list with the remote files we found.
                    val built = mp3s.map {
                        Episode(it.name, "$dir/${it.name}", it.size, File(dateDir(date), it.name), date)
                    }
                    onUi {
                        episodes.clear()
                        episodes.addAll(built)
                        adapter.submit(built)
                        setEmptyVisible(false)
                    }

                    var index = 0
                    var newCount = 0
                    var autoPlayed = false
                    for (ep in built) {
                        index++
                        val label = ep.filename
                        if (!ep.localFile.exists() || ep.localFile.length() != ep.sizeBytes) {
                            onUi { setStatus("Downloading $index/${built.size}: $label") }
                            var lastUpdate = 0L
                            client.download(ep.remotePath, ep.localFile) { copied, total ->
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastUpdate >= 150 || copied == total) {
                                    lastUpdate = now
                                    val pct = if (total <= 0) 0 else (copied * 100 / total).toInt()
                                    onUi { setStatus("Downloading $index/${built.size}: $label ($pct%)") }
                                }
                            }
                            newCount++
                            // Auto-play this file the moment it lands; the rest keep
                            // downloading in the background on this same worker.
                            if (!autoPlayed) {
                                autoPlayed = true
                                val pos = index - 1
                                val name = label
                                onUi {
                                    adapter.notifyDataSetChanged()
                                    if (mediaPlayer == null) {
                                        setStatus("Playing $name — downloading the rest…")
                                        playFile(pos)
                                    }
                                }
                            } else {
                                onUi { adapter.notifyDataSetChanged() }
                            }
                        } else {
                            onUi { setStatus("Already downloaded ($index/${built.size})") }
                        }
                    }
                    val newDown = newCount
                    onUi {
                        adapter.notifyDataSetChanged()
                        setBusy(false)
                        if (newDown > 0) {
                            Toast.makeText(this@PlayerActivity, "Downloaded $newDown file(s)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            }
            if (error != null) {
                val msg = error
                onUi {
                    setStatus("Error: $msg")
                    setBusy(false)
                }
                val isAuthProblem = msg.contains("Auth fail", ignoreCase = true) ||
                        msg.contains("authenticate", ignoreCase = true) ||
                        msg.contains("permission denied", ignoreCase = true)
                if (isAuthProblem) {
                    onUi { promptForServerPassword { startRefresh() } }
                }
            }
        }
    }

    // ------------------------------------------------------------- playing

    private fun onEpisodeClicked(pos: Int) {
        val ep = episodes.getOrNull(pos) ?: return
        if (ep.downloaded) {
            playFile(pos)
        } else if (busy) {
            Toast.makeText(this, "Still downloading — wait for it to finish", Toast.LENGTH_SHORT).show()
        } else {
            downloadSingleThenPlay(pos)
        }
    }

    private fun downloadSingleThenPlay(pos: Int) {
        val ep = episodes[pos]
        setBusy(true)
        setStatus("Downloading ${ep.filename}…")
        executor.execute {
            var error: String? = null
            try {
                SftpClient.fromPrefs(this@PlayerActivity).use { client ->
                    var last = 0L
                    client.download(ep.remotePath, ep.localFile) { copied, total ->
                        val now = SystemClock.elapsedRealtime()
                        if (now - last >= 150 || copied == total) {
                            last = now
                            val pct = if (total <= 0) 0 else (copied * 100 / total).toInt()
                            onUi { setStatus("Downloading ${ep.filename} ($pct%)") }
                        }
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            }
            if (error != null) {
                onUi {
                    setStatus("Error: $error")
                    setBusy(false)
                }
            } else {
                onUi {
                    setBusy(false)
                    adapter.notifyDataSetChanged()
                    playFile(pos)
                }
            }
        }
    }

    private fun playFile(pos: Int) {
        val ep = episodes.getOrNull(pos) ?: return
        if (!ep.localFile.exists()) return

        stopPlayback()
        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
        try {
            mp.setDataSource(ep.localFile.absolutePath)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot read file", Toast.LENGTH_SHORT).show()
            return
        }
        mp.setOnPreparedListener {
            playingIndex = pos
            adapter.playingIndex = pos
            tvNowPlaying.text = ep.filename
            tvNowPlaying.isSelected = true
            playbackBar.visibility = LinearLayout.VISIBLE
            btnPlayPause.setImageResource(R.drawable.ic_pause)
            sbSeek.max = it.duration.coerceAtLeast(0)
            sbSeek.progress = 0
            it.start()
            updateTimeLabel()
            startSeekLoop()
        }
        mp.setOnErrorListener { _, _, _ ->
            onUi { setStatus("Playback error") }
            playbackStopped()
            true
        }
        mp.setOnCompletionListener {
            playRelative(1, auto = true)
        }
        mediaPlayer = mp
        try {
            mp.prepareAsync()
        } catch (e: Exception) {
            playbackStopped()
        }
    }

    private fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            btnPlayPause.setImageResource(R.drawable.ic_play)
        } else {
            mp.start()
            btnPlayPause.setImageResource(R.drawable.ic_pause)
            startSeekLoop()
        }
    }

    private fun playRelative(delta: Int, auto: Boolean = false) {
        if (playingIndex < 0) return
        val next = playingIndex + delta
        if (next !in episodes.indices) {
            if (auto) playbackStopped()
            return
        }
        val ep = episodes[next]
        if (!ep.downloaded) {
            if (auto) playbackStopped()
            return
        }
        playFile(next)
    }

    private fun playbackStopped() {
        playingIndex = -1
        adapter.playingIndex = -1
        btnPlayPause.setImageResource(R.drawable.ic_play)
        stopSeekLoop()
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {
        }
        mediaPlayer = null
        playingIndex = -1
        adapter.playingIndex = -1
        btnPlayPause.setImageResource(R.drawable.ic_play)
        playbackBar.visibility = LinearLayout.GONE
        stopSeekLoop()
        sbSeek.progress = 0
        tvTime.text = "0:00 / 0:00"
    }

    private fun startSeekLoop() {
        stopSeekLoop()
        seekLoop = object : Runnable {
            override fun run() {
                val mp = mediaPlayer ?: return
                val dur = mp.duration
                if (dur > 0) {
                    sbSeek.max = dur
                    if (!userSeeking) sbSeek.progress = mp.currentPosition
                    updateTimeLabel()
                }
                mainHandler.postDelayed(this, 500)
            }
        }
        mainHandler.post(seekLoop!!)
    }

    private fun stopSeekLoop() {
        seekLoop?.let { mainHandler.removeCallbacks(it) }
        seekLoop = null
    }

    private fun updateTimeLabel() {
        val mp = mediaPlayer ?: return
        val pos = mp.currentPosition
        val dur = mp.duration
        tvTime.text = formatMs(pos) + " / " + formatMs(dur)
    }

    private fun formatMs(ms: Int): String {
        if (ms < 0) return "0:00"
        val s = ms / 1000
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    // ---------------------------------------------------------------- ui helpers

    private fun promptForServerPassword(onEntered: () -> Unit) {
        if (isFinishing || isDestroyed) return
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.setText(Prefs.password(this))
        input.hint = "SFTP password"

        val dialog = AlertDialog.Builder(this)
            .setTitle("Server password")
            .setMessage("Password for ${Prefs.user(this)}@${Prefs.host(this)}")
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                val pass = input.text.toString()
                if (pass.isEmpty()) {
                    Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Prefs.setPassword(this, pass)
                onEntered()
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            input.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                ?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        dialog.show()
    }

    private fun onUi(action: () -> Unit) {
        if (isFinishing || isDestroyed) return
        runOnUiThread(action)
    }

    private fun setBusy(value: Boolean) {
        busy = value
        btnRefresh.isEnabled = !value
        panelProgress.visibility = if (value) LinearLayout.VISIBLE else LinearLayout.GONE
        pb.isIndeterminate = true
    }

    private fun hideProgress() {
        panelProgress.visibility = LinearLayout.GONE
    }

    private fun setStatus(text: String) {
        tvStatus.text = text
    }

    private fun showEmpty(text: String) {
        tvEmpty.text = text
        tvEmpty.visibility = TextView.VISIBLE
    }

    private fun setEmptyVisible(visible: Boolean) {
        tvEmpty.visibility = if (visible) TextView.VISIBLE else TextView.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSeekLoop()
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        executor.shutdownNow()
    }
}
