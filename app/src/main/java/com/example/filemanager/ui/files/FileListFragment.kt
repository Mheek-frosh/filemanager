package com.example.filemanager.ui.files

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.filemanager.R
import com.example.filemanager.databinding.FragmentFileListBinding
import com.example.filemanager.ui.dashboard.FileAdapter
import com.example.filemanager.utils.FileMenuHelper
import com.example.filemanager.utils.applySystemBarPadding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FileListFragment : Fragment(R.layout.fragment_file_list) {
    private var _binding: FragmentFileListBinding? = null
    private val binding get() = _binding!!
    private val args: FileListFragmentArgs by navArgs()
    private val viewModel: FilesViewModel by viewModels()
    private val adapter = FileAdapter(
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
                val action = FileListFragmentDirections.actionFileListFragmentToFileDetailFragment(
                    fileName = file.name,
                    fileSize = file.sizeBytes,
                    fileType = file.type,
                    fileUri = file.contentUri?.toString().orEmpty(),
                    mimeType = file.mimeType.orEmpty()
                )
                findNavController().navigate(action)
            }
        },
        onMoreClick = { item, anchor ->
            FileMenuHelper.show(this, anchor, item) {
                viewModel.load(args.categoryId, args.storageRootPath)
            }
        }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFileListBinding.bind(view)
        binding.root.applySystemBarPadding(alsoBottom = true)
        binding.tvTitle.text = args.categoryTitle.ifBlank { getString(R.string.app_name) }
        binding.tvSubtitle.text = if (args.storageRootPath.isNotBlank()) {
            args.storageRootPath
        } else {
            getString(R.string.loading_files)
        }
        binding.ivBack.setOnClickListener { findNavController().popBackStack() }
        binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFiles.adapter = adapter
        viewModel.files.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            binding.tvSubtitle.text = if (args.storageRootPath.isNotBlank()) {
                "${args.storageRootPath}\n${getString(R.string.items_count, list.size)}"
            } else {
                getString(R.string.items_count, list.size)
            }
        }
        viewModel.load(args.categoryId, args.storageRootPath)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
