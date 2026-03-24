package com.example.filemanager.ui.files

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.MediaController
import android.widget.SeekBar
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.filemanager.R
import com.example.filemanager.databinding.FragmentFileDetailBinding
import com.example.filemanager.utils.FileFormatUtils
import com.example.filemanager.utils.applySystemBarPadding
import com.google.android.material.snackbar.Snackbar
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import java.io.File

class FileDetailFragment : Fragment(R.layout.fragment_file_detail) {
    private var _binding: FragmentFileDetailBinding? = null
    private val binding get() = _binding!!
    private val args: FileDetailFragmentArgs by navArgs()
    private var mediaController: MediaController? = null

    private var audioPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioSeekHandler = Handler(Looper.getMainLooper())
    private var audioSeekRunnable: Runnable? = null
    private var userSeekingAudio = false
    private var audioRepeatOne = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pauseAudioPlayer()
                _binding?.let { updateAudioPlayPauseIcon(false) }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                audioPlayer?.setVolume(0.25f, 0.25f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                audioPlayer?.setVolume(1f, 1f)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFileDetailBinding.bind(view)
        binding.root.applySystemBarPadding(alsoBottom = true)
        binding.ivBack.setOnClickListener { findNavController().popBackStack() }
        binding.tvName.text = args.fileName
        binding.tvSize.text = FileFormatUtils.sizeToDisplay(args.fileSize)
        binding.tvType.text = args.fileType
        binding.tvDate.text = getString(R.string.today_label)
        bindPreview()
    }

    override fun onPause() {
        super.onPause()
        _binding?.videoView?.pause()
        pauseAudioPlayer()
    }

    override fun onDestroyView() {
        mediaController?.hide()
        mediaController = null
        releaseAudioPlayer()
        _binding?.videoView?.stopPlayback()
        super.onDestroyView()
        _binding = null
    }

    private fun bindPreview() {
        hideAllPreview()
        if (args.fileType.startsWith("APP") && args.fileUri.startsWith("package:")) {
            binding.detailMetadataSection.visibility = View.VISIBLE
            val pkg = args.fileUri.removePrefix("package:")
            binding.ivPreview.visibility = View.VISIBLE
            runCatching {
                val pm = requireContext().packageManager
                val info = pm.getApplicationInfo(pkg, 0)
                Glide.with(binding.ivPreview).load(pm.getApplicationIcon(info)).into(binding.ivPreview)
            }.onFailure {
                binding.ivPlaceholder.visibility = View.VISIBLE
                binding.ivPlaceholder.setImageResource(R.drawable.ic_apps)
            }
            return
        }

        val uri = args.fileUri.takeIf { it.isNotEmpty() }?.let(Uri::parse)
        if (uri == null) {
            showFallbackIcon()
            return
        }

        val mime = effectiveMime(uri)
        when {
            mime.startsWith("image/") -> showImage(uri)
            mime.startsWith("video/") -> showVideo(uri)
            mime.startsWith("audio/") -> showAudio(uri)
            looksLikeImagePath(args.fileName) -> showImage(uri)
            looksLikeVideoPath(args.fileName) -> showVideo(uri)
            looksLikeAudioPath(args.fileName) -> showAudio(uri)
            else -> showFallbackIcon()
        }
    }

    private fun uriForMedia(uri: Uri): Uri {
        if (uri.scheme != "file") return uri
        val path = uri.path ?: return uri
        val file = File(path)
        if (!file.exists()) return uri
        return runCatching {
            FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
        }.getOrElse { uri }
    }

    private fun hideAllPreview() {
        binding.ivPreview.visibility = View.GONE
        binding.videoView.visibility = View.GONE
        binding.audioPlayerPanel.visibility = View.GONE
        binding.ivPlaceholder.visibility = View.GONE
    }

    private fun showImage(uri: Uri) {
        hideAllPreview()
        binding.detailMetadataSection.visibility = View.VISIBLE
        binding.ivPreview.visibility = View.VISIBLE
        binding.ivPreview.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        val loadUri = uriForMedia(uri)
        Glide.with(this)
            .load(loadUri)
            .apply(
                RequestOptions()
                    .fitCenter()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .error(R.drawable.ic_image)
            )
            .into(binding.ivPreview)
    }

    private fun showVideo(uri: Uri) {
        hideAllPreview()
        binding.detailMetadataSection.visibility = View.VISIBLE
        binding.videoView.visibility = View.VISIBLE
        val mc = MediaController(requireContext())
        mediaController = mc
        mc.setAnchorView(binding.videoView)
        binding.videoView.setMediaController(mc)
        binding.videoView.setOnPreparedListener { mp ->
            mp.isLooping = false
        }
        binding.videoView.setOnErrorListener { _, _, _ ->
            Snackbar.make(binding.root, R.string.video_playback_failed, Snackbar.LENGTH_LONG).show()
            true
        }
        binding.videoView.setVideoURI(uriForMedia(uri))
        binding.videoView.requestFocus()
    }

    private fun showAudio(uri: Uri) {
        hideAllPreview()
        binding.detailMetadataSection.visibility = View.GONE
        binding.audioPlayerPanel.visibility = View.VISIBLE
        releaseAudioPlayer()
        audioRepeatOne = false
        updateRepeatButtonUi()
        userSeekingAudio = false
        binding.tvAudioTitle.text = args.fileName
        binding.tvAudioTitle.isSelected = true
        binding.tvAudioMeta.text = audioMetaLine()
        binding.tvAudioCurrent.text = formatMs(0)
        binding.tvAudioDuration.text = formatMs(0)
        binding.sbAudio.max = 1000
        binding.sbAudio.progress = 0
        updateAudioPlayPauseIcon(false)

        if (!requestAudioFocus()) {
            Snackbar.make(binding.root, R.string.audio_playback_failed, Snackbar.LENGTH_LONG).show()
            return
        }

        val playUri = uriForMedia(uri)
        val mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setOnPreparedListener { p ->
                val b = _binding ?: return@setOnPreparedListener
                val dur = p.duration.coerceAtLeast(0)
                b.sbAudio.max = dur.coerceAtLeast(1)
                b.tvAudioDuration.text = formatMs(dur)
                p.isLooping = audioRepeatOne
                updateAudioPlayPauseIcon(true)
                p.start()
                startAudioSeekUpdates()
            }
            setOnCompletionListener {
                stopAudioSeekUpdates()
                val b = _binding ?: return@setOnCompletionListener
                audioPlayer?.seekTo(0)
                b.sbAudio.progress = 0
                b.tvAudioCurrent.text = formatMs(0)
                updateAudioPlayPauseIcon(false)
            }
            setOnErrorListener { _, _, _ ->
                Snackbar.make(binding.root, R.string.audio_playback_failed, Snackbar.LENGTH_LONG).show()
                releaseAudioPlayer()
                true
            }
            setDataSource(requireContext(), playUri)
            prepareAsync()
        }
        audioPlayer = mp

        binding.fabAudioPlayPause.setOnClickListener { toggleAudioPlayback() }
        binding.btnAudioSkipBack.setOnClickListener { seekAudioBy(-10_000) }
        binding.btnAudioSkipForward.setOnClickListener { seekAudioBy(10_000) }
        binding.btnAudioRepeat.setOnClickListener {
            audioRepeatOne = !audioRepeatOne
            audioPlayer?.isLooping = audioRepeatOne
            updateRepeatButtonUi()
        }
        binding.sbAudio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvAudioCurrent.text = formatMs(progress)
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                userSeekingAudio = true
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                userSeekingAudio = false
                audioPlayer?.seekTo(sb?.progress ?: 0)
            }
        })
    }

    private fun requestAudioFocus(): Boolean {
        val am = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager = am
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
        audioFocusRequest = null
        audioManager = null
    }

    private fun startAudioSeekUpdates() {
        stopAudioSeekUpdates()
        val r = object : Runnable {
            override fun run() {
                val mp = audioPlayer ?: return
                val b = _binding ?: return
                if (!userSeekingAudio && mp.isPlaying) {
                    b.sbAudio.progress = mp.currentPosition
                    b.tvAudioCurrent.text = formatMs(mp.currentPosition)
                }
                audioSeekHandler.postDelayed(this, 400L)
            }
        }
        audioSeekRunnable = r
        audioSeekHandler.post(r)
    }

    private fun stopAudioSeekUpdates() {
        audioSeekRunnable?.let { audioSeekHandler.removeCallbacks(it) }
        audioSeekRunnable = null
    }

    private fun pauseAudioPlayer() {
        val mp = audioPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            stopAudioSeekUpdates()
            updateAudioPlayPauseIcon(false)
        }
    }

    private fun toggleAudioPlayback() {
        val mp = audioPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            stopAudioSeekUpdates()
            updateAudioPlayPauseIcon(false)
        } else {
            if (!requestAudioFocus()) return
            mp.start()
            startAudioSeekUpdates()
            updateAudioPlayPauseIcon(true)
        }
    }

    private fun seekAudioBy(deltaMs: Int) {
        val mp = audioPlayer ?: return
        val dur = mp.duration.takeIf { it > 0 } ?: return
        val newPos = (mp.currentPosition + deltaMs).coerceIn(0, dur)
        mp.seekTo(newPos)
        _binding?.let { b ->
            b.sbAudio.progress = newPos
            b.tvAudioCurrent.text = formatMs(newPos)
        }
    }

    private fun updateRepeatButtonUi() {
        val b = _binding ?: return
        b.btnAudioRepeat.setImageResource(
            if (audioRepeatOne) R.drawable.ic_repeat_one else R.drawable.ic_repeat
        )
    }

    private fun audioMetaLine(): String {
        val type = args.fileType.trim().ifEmpty { getString(R.string.audio_playing_badge) }
        val size = FileFormatUtils.sizeToDisplay(args.fileSize)
        val date = getString(R.string.today_label)
        return "$type · $size · $date"
    }

    private fun releaseAudioPlayer() {
        stopAudioSeekUpdates()
        userSeekingAudio = false
        audioRepeatOne = false
        audioPlayer?.let { p ->
            runCatching {
                if (p.isPlaying) p.stop()
                p.release()
            }
        }
        audioPlayer = null
        abandonAudioFocus()
        _binding?.let { updateRepeatButtonUi() }
    }

    private fun updateAudioPlayPauseIcon(playing: Boolean) {
        val b = _binding ?: return
        if (playing) {
            b.fabAudioPlayPause.setImageResource(R.drawable.ic_pause)
        } else {
            b.fabAudioPlayPause.setImageResource(R.drawable.ic_play_circle)
        }
    }

    private fun formatMs(ms: Int): String {
        if (ms < 0) return "0:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%d:%02d", m, s)
        }
    }

    private fun showFallbackIcon() {
        hideAllPreview()
        binding.detailMetadataSection.visibility = View.VISIBLE
        binding.ivPlaceholder.visibility = View.VISIBLE
        binding.ivPlaceholder.setImageResource(R.drawable.ic_description)
    }

    private fun effectiveMime(uri: Uri): String {
        val m = args.mimeType.trim()
        if (m.isNotEmpty()) return m
        val fromResolver = runCatching { requireContext().contentResolver.getType(uri) }.getOrNull()
        if (!fromResolver.isNullOrBlank()) return fromResolver
        return inferMimeFromFileName(args.fileName)
    }

    private fun inferMimeFromFileName(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heic"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wma" -> "audio/x-ms-wma"
            else -> ""
        }
    }

    private fun looksLikeImagePath(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    }

    private fun looksLikeVideoPath(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp4", "mkv", "webm", "3gp", "avi", "mov", "m4v")
    }

    private fun looksLikeAudioPath(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma")
    }
}
