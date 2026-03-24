package com.example.filemanager.ui.files

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.example.filemanager.R
import com.example.filemanager.databinding.FragmentFileDetailBinding
import com.example.filemanager.utils.FileFormatUtils
import com.example.filemanager.utils.applySystemBarPadding

class FileDetailFragment : Fragment(R.layout.fragment_file_detail) {
    private var _binding: FragmentFileDetailBinding? = null
    private val binding get() = _binding!!
    private val args: FileDetailFragmentArgs by navArgs()

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

    private fun bindPreview() {
        val uri = args.fileUri.takeIf { it.isNotEmpty() }?.let(Uri::parse)
        val mime = args.mimeType.takeIf { it.isNotBlank() }
        when {
            args.fileType == "APP" && args.fileUri.startsWith("package:") -> {
                val pkg = args.fileUri.removePrefix("package:")
                runCatching {
                    val pm = requireContext().packageManager
                    val info = pm.getApplicationInfo(pkg, 0)
                    Glide.with(binding.ivPreview).load(pm.getApplicationIcon(info)).into(binding.ivPreview)
                }.onFailure {
                    binding.ivPreview.setImageResource(R.drawable.ic_apps)
                }
            }
            uri != null && (mime?.startsWith("image/") == true || args.fileType.contains("JPG", true) || args.fileType.contains("PNG", true)) -> {
                Glide.with(binding.ivPreview).load(uri).into(binding.ivPreview)
            }
            uri != null && mime?.startsWith("video/") == true -> {
                binding.ivPreview.setImageResource(R.drawable.ic_play_circle)
            }
            uri != null && uri.scheme == "file" -> {
                Glide.with(binding.ivPreview).load(uri).into(binding.ivPreview)
            }
            else -> binding.ivPreview.setImageResource(R.drawable.ic_description)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
