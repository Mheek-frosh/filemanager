package com.example.filemanager.ui.dashboard

import android.Manifest
import android.app.Activity
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.filemanager.MainActivity
import com.example.filemanager.R
import com.example.filemanager.databinding.FragmentDashboardBinding
import com.example.filemanager.ui.files.FileAdapter
import com.example.filemanager.utils.FileMenuHelper
import com.example.filemanager.utils.applySystemBarPadding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    private var pendingRecoverableRetry: (() -> Unit)? = null

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

    private val storageAdapter = StorageCardAdapter { card ->
        if (!card.available || card.rootPath == null) return@StorageCardAdapter
        findNavController().navigate(
            DashboardFragmentDirections.actionDashboardFragmentToFileListFragment(
                categoryId = "browse",
                categoryTitle = card.title,
                storageRootPath = card.rootPath
            )
        )
    }
    private val categoryAdapter = CategoryAdapter { category ->
        findNavController().navigate(
            DashboardFragmentDirections.actionDashboardFragmentToFileListFragment(
                categoryId = category.id,
                categoryTitle = category.title,
                storageRootPath = ""
            )
        )
    }
    private val fileAdapter = FileAdapter(
        onClick = { file ->
            val action = DashboardFragmentDirections.actionDashboardFragmentToFileDetailFragment(
                fileName = file.name,
                fileSize = file.sizeBytes,
                fileType = file.type,
                fileUri = file.contentUri?.toString().orEmpty(),
                mimeType = file.mimeType.orEmpty()
            )
            findNavController().navigate(action)
        },
        onMoreClick = { item, anchor ->
            FileMenuHelper.show(
                this,
                anchor,
                item,
                onChanged = { viewModel.loadRecentFiles() },
                launchRecoverable = { sender, retry -> launchRecoverable(sender, retry) }
            )
        }
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.loadRecentFiles()
        viewModel.refreshStorage()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDashboardBinding.bind(view)
        binding.root.applySystemBarPadding(alsoBottom = true)

        binding.ivMenu.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        binding.rvStorage.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvStorage.adapter = storageAdapter
        binding.rvCategories.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.rvCategories.adapter = categoryAdapter
        binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFiles.adapter = fileAdapter

        viewModel.storageCards.observe(viewLifecycleOwner) { storageAdapter.submitList(it) }
        categoryAdapter.submitList(viewModel.categories)
        viewModel.recentFiles.observe(viewLifecycleOwner) { fileAdapter.submitList(it.take(3)) }

        requestMediaPermissionsIfNeeded()
    }

    private fun requestMediaPermissionsIfNeeded() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val granted = permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) {
            viewModel.loadRecentFiles()
            viewModel.refreshStorage()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
