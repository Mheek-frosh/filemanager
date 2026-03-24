package com.example.filemanager.ui.files

import android.app.Activity
import android.content.IntentSender
import android.os.Bundle
import android.view.View
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.filemanager.R
import com.example.filemanager.databinding.FragmentFileListBinding
import com.example.filemanager.model.FileItem
import com.example.filemanager.utils.FileMenuHelper
import com.example.filemanager.utils.applySystemBarPadding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Category or folder listing: list, photo grid, video grid, or apps list depending on navigation args.
 * Shares the same MediaStore recoverable flow as the dashboard for overflow menu actions.
 */
@AndroidEntryPoint
class FileListFragment : Fragment(R.layout.fragment_file_list) {
    private var _binding: FragmentFileListBinding? = null
    private val binding get() = _binding!!
    private val args: FileListFragmentArgs by navArgs()
    private val viewModel: FilesViewModel by viewModels()

    private var pendingRecoverableRetry: (() -> Unit)? = null // paired with FileMenuHelper MediaStore writes

    private val recoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingRecoverableRetry?.invoke()
        }
        pendingRecoverableRetry = null
    }

    private fun launchRecoverable(sender: IntentSender, retry: () -> Unit) {
        pendingRecoverableRetry = retry
        recoverableLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    private val onReload: () -> Unit = {
        viewModel.load(args.categoryId, args.storageRootPath)
    }

    private fun navigateToDetail(file: FileItem) {
        val action = FileListFragmentDirections.actionFileListFragmentToFileDetailFragment(
            fileName = file.name,
            fileSize = file.sizeBytes,
            fileType = file.type,
            fileUri = file.contentUri?.toString().orEmpty(),
            mimeType = file.mimeType.orEmpty()
        )
        findNavController().navigate(action)
    }

    private val fileAdapter = FileAdapter(
        onClick = { file ->
            if (file.isDirectory && file.localPath != null) {
                findNavController().navigate(
                    FileListFragmentDirections.actionFileListFragmentSelf(
                        categoryId = "browse",
                        categoryTitle = file.name,
                        storageRootPath = file.localPath!!
                    )
                )
            } else {
                navigateToDetail(file)
            }
        },
        onMoreClick = { item, anchor ->
            FileMenuHelper.show(
                this,
                anchor,
                item,
                onChanged = onReload,
                launchRecoverable = { sender, retry -> launchRecoverable(sender, retry) }
            )
        }
    )

    private val photoAdapter = MediaGridAdapter(
        isVideo = false,
        onOpen = { navigateToDetail(it) },
        onMore = { item, anchor ->
            FileMenuHelper.show(
                this,
                anchor,
                item,
                onChanged = onReload,
                launchRecoverable = { sender, retry -> launchRecoverable(sender, retry) }
            )
        }
    )

    private val videoAdapter = MediaGridAdapter(
        isVideo = true,
        onOpen = { navigateToDetail(it) },
        onMore = { item, anchor ->
            FileMenuHelper.show(
                this,
                anchor,
                item,
                onChanged = onReload,
                launchRecoverable = { sender, retry -> launchRecoverable(sender, retry) }
            )
        }
    )

    private val appsAdapter = AppsListAdapter(
        onOpen = { navigateToDetail(it) },
        onMore = { item, anchor ->
            FileMenuHelper.show(
                this,
                anchor,
                item,
                onChanged = onReload,
                launchRecoverable = { sender, retry -> launchRecoverable(sender, retry) }
            )
        }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFileListBinding.bind(view)
        binding.root.applySystemBarPadding(alsoBottom = true)
        binding.tvTitle.text = args.categoryTitle.ifBlank { getString(R.string.app_name) }
        binding.tvSubtitle.text = getString(R.string.loading_files)

        binding.ivBack.setOnClickListener { findNavController().popBackStack() }

        when (listMode()) {
            ListMode.PHOTOS -> {
                binding.rvFiles.layoutManager = GridLayoutManager(requireContext(), 3)
                binding.rvFiles.adapter = photoAdapter
                binding.tvSubtitle.text = getString(R.string.photos_subtitle)
            }
            ListMode.VIDEOS -> {
                binding.rvFiles.layoutManager = GridLayoutManager(requireContext(), 2)
                binding.rvFiles.adapter = videoAdapter
                binding.tvSubtitle.text = getString(R.string.videos_subtitle)
            }
            ListMode.APPS -> {
                binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
                binding.rvFiles.adapter = appsAdapter
                binding.tvSubtitle.text = getString(R.string.apps_subtitle)
            }
            ListMode.FILES -> {
                binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
                binding.rvFiles.adapter = fileAdapter
            }
        }

        viewModel.files.observe(viewLifecycleOwner) { list ->
            when (listMode()) {
                ListMode.PHOTOS -> photoAdapter.submitList(list)
                ListMode.VIDEOS -> videoAdapter.submitList(list)
                ListMode.APPS -> appsAdapter.submitList(list)
                ListMode.FILES -> fileAdapter.submitList(list)
            }
            binding.tvSubtitle.text = when (listMode()) {
                ListMode.FILES -> if (args.storageRootPath.isNotBlank()) {
                    "${args.storageRootPath}\n${getString(R.string.items_count, list.size)}"
                } else {
                    getString(R.string.items_count, list.size)
                }
                ListMode.PHOTOS -> getString(R.string.photos_count, list.size)
                ListMode.VIDEOS -> getString(R.string.videos_count, list.size)
                ListMode.APPS -> getString(R.string.apps_count, list.size)
            }
        }
        viewModel.load(args.categoryId, args.storageRootPath)
    }

    private fun listMode(): ListMode {
        if (args.storageRootPath.isNotBlank()) return ListMode.FILES
        return when (args.categoryId) {
            "pictures" -> ListMode.PHOTOS
            "videos" -> ListMode.VIDEOS
            "apps" -> ListMode.APPS
            else -> ListMode.FILES
        }
    }

    private enum class ListMode {
        FILES, PHOTOS, VIDEOS, APPS
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
