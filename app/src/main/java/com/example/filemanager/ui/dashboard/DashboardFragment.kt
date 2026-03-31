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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.filemanager.MainActivity
import com.example.filemanager.R
import com.example.filemanager.databinding.FragmentDashboardBinding
import com.example.filemanager.model.FileItem
import com.example.filemanager.ui.files.FileAdapter
import com.example.filemanager.utils.FileMenuHelper
import com.example.filemanager.utils.applySystemBarPadding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

/**
 * Home dashboard: storage cards, category grid, recent files list, pull-to-refresh, and overflow actions
 * that may require a system IntentSender step for MediaStore rename/delete (see `FileMenuHelper`).
 */
@AndroidEntryPoint
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    // MediaStore rename/delete can require a one-shot system permission; retry runs after user approves.
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
    private val stopRefreshRunnable = Runnable {
        _binding?.swipeRefresh?.isRefreshing = false
    }
    private var selectedTab = DashboardTab.RECENT
    private var selectedFilter = DashboardFilter.ALL
    private var sourceFiles: List<FileItem> = emptyList()

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
        binding.ivSectionMenu.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        binding.rvStorage.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvStorage.adapter = storageAdapter
        binding.rvCategories.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.rvCategories.adapter = categoryAdapter
        binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFiles.adapter = fileAdapter
        setupTabs()
        setupFilters()
        setupBottomNav()
        binding.fabAdd.setOnClickListener {
            val action = DashboardFragmentDirections.actionDashboardFragmentToFileListFragment(
                categoryId = "all",
                categoryTitle = getString(R.string.dashboard_nav_files),
                storageRootPath = ""
            )
            findNavController().navigate(action)
        }

        // Pull-to-refresh: reload recent files + storage stats (DashboardViewModel).
        binding.swipeRefresh.setColorSchemeResources(R.color.primary, R.color.primary_dark)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.surface)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadRecentFiles()
            viewModel.refreshStorage()
            binding.swipeRefresh.removeCallbacks(stopRefreshRunnable)
            binding.swipeRefresh.postDelayed(stopRefreshRunnable, 380)
        }

        viewModel.storageCards.observe(viewLifecycleOwner) { storageAdapter.submitList(it) }
        categoryAdapter.submitList(viewModel.categories)
        viewModel.recentFiles.observe(viewLifecycleOwner) {
            sourceFiles = it
            renderFiles()
        }

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
        _binding?.swipeRefresh?.removeCallbacks(stopRefreshRunnable)
        super.onDestroyView()
        _binding = null
    }

    private fun setupTabs() {
        binding.tabRecent.setOnClickListener {
            selectedTab = DashboardTab.RECENT
            renderFiles()
        }
        binding.tabStarred.setOnClickListener {
            selectedTab = DashboardTab.STARRED
            renderFiles()
        }
        binding.tabOffline.setOnClickListener {
            selectedTab = DashboardTab.OFFLINE
            renderFiles()
        }
    }

    private fun setupFilters() {
        binding.chipAll.setOnClickListener { selectedFilter = DashboardFilter.ALL; renderFiles() }
        binding.chipViewed.setOnClickListener { selectedFilter = DashboardFilter.VIEWED; renderFiles() }
        binding.chipSaved.setOnClickListener { selectedFilter = DashboardFilter.SAVED; renderFiles() }
        binding.chipUploaded.setOnClickListener { selectedFilter = DashboardFilter.UPLOADED; renderFiles() }
        binding.chipScanned.setOnClickListener { selectedFilter = DashboardFilter.SCANNED; renderFiles() }
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_bottom_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_bottom_home -> true
                R.id.nav_bottom_files -> {
                    val action = DashboardFragmentDirections.actionDashboardFragmentToFileListFragment(
                        categoryId = "all",
                        categoryTitle = getString(R.string.dashboard_nav_files),
                        storageRootPath = ""
                    )
                    findNavController().navigate(action)
                    true
                }
                R.id.nav_bottom_share -> {
                    Snackbar.make(binding.root, R.string.dashboard_share_hint, Snackbar.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_bottom_my -> {
                    (activity as? MainActivity)?.openDrawer()
                    true
                }
                else -> false
            }
        }
    }

    private fun renderFiles() {
        updateTabUi()
        updateFilterUi()
        val byTab = when (selectedTab) {
            DashboardTab.RECENT -> sourceFiles
            DashboardTab.STARRED -> sourceFiles.filter { it.name.contains("star", true) }.ifEmpty { sourceFiles.take(10) }
            DashboardTab.OFFLINE -> sourceFiles.filter { it.localPath != null }.ifEmpty { sourceFiles }
        }
        val byFilter = when (selectedFilter) {
            DashboardFilter.ALL -> byTab
            DashboardFilter.VIEWED -> byTab.filter { it.mimeType?.startsWith("image/") == true || it.mimeType?.startsWith("video/") == true }
            DashboardFilter.SAVED -> byTab.filter { it.name.endsWith(".pdf", true) || it.name.endsWith(".zip", true) }
            DashboardFilter.UPLOADED -> byTab.filter { it.name.contains("upload", true) }.ifEmpty { byTab }
            DashboardFilter.SCANNED -> byTab.filter { it.name.contains("scan", true) || it.mimeType?.contains("pdf", true) == true }
        }
        fileAdapter.submitList(byFilter)
        binding.tvEmptyState.isVisible = byFilter.isEmpty()
    }

    private fun updateTabUi() {
        val active = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val inactive = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        binding.tabRecent.setTextColor(if (selectedTab == DashboardTab.RECENT) active else inactive)
        binding.tabStarred.setTextColor(if (selectedTab == DashboardTab.STARRED) active else inactive)
        binding.tabOffline.setTextColor(if (selectedTab == DashboardTab.OFFLINE) active else inactive)
        binding.indicatorRecent.isVisible = selectedTab == DashboardTab.RECENT
        binding.indicatorStarred.isVisible = selectedTab == DashboardTab.STARRED
        binding.indicatorOffline.isVisible = selectedTab == DashboardTab.OFFLINE
    }

    private fun updateFilterUi() {
        val selectedBg = R.drawable.bg_dashboard_chip_selected
        val normalBg = R.drawable.bg_dashboard_chip
        val selectedText = ContextCompat.getColor(requireContext(), R.color.white)
        val normalText = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val chips = listOf(
            binding.chipAll to DashboardFilter.ALL,
            binding.chipViewed to DashboardFilter.VIEWED,
            binding.chipSaved to DashboardFilter.SAVED,
            binding.chipUploaded to DashboardFilter.UPLOADED,
            binding.chipScanned to DashboardFilter.SCANNED
        )
        chips.forEach { (chip, value) ->
            val active = selectedFilter == value
            chip.setBackgroundResource(if (active) selectedBg else normalBg)
            chip.setTextColor(if (active) selectedText else normalText)
        }
    }

    private enum class DashboardTab { RECENT, STARRED, OFFLINE }
    private enum class DashboardFilter { ALL, VIEWED, SAVED, UPLOADED, SCANNED }
}
