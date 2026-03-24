package com.example.filemanager

import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.example.filemanager.data.StorageVolumeProvider
import com.example.filemanager.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var storageVolumeProvider: StorageVolumeProvider

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        binding.navView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_home -> {
                    val popped = navController.popBackStack(R.id.dashboardFragment, false)
                    if (!popped && navController.currentDestination?.id != R.id.dashboardFragment) {
                        navController.navigate(R.id.dashboardFragment)
                    }
                }
                R.id.nav_pictures -> openFileList("pictures", getString(R.string.drawer_pictures))
                R.id.nav_videos -> openFileList("videos", getString(R.string.drawer_videos))
                R.id.nav_music -> openFileList("music", getString(R.string.drawer_music))
                R.id.nav_documents -> openFileList("documents", getString(R.string.drawer_documents))
                R.id.nav_downloads -> openFileList("downloads", getString(R.string.drawer_downloads))
                R.id.nav_zip -> openFileList("zip", getString(R.string.drawer_zip))
                R.id.nav_apps -> openFileList("apps", getString(R.string.drawer_apps))
                R.id.nav_internal_storage -> {
                    val path = android.os.Environment.getExternalStorageDirectory()?.absolutePath.orEmpty()
                    openFileList("browse", getString(R.string.drawer_internal), path)
                }
                R.id.nav_sd_card -> {
                    val cards = storageVolumeProvider.getStorageCards()
                    val sd = cards.find { it.id == StorageVolumeProvider.ID_SD }
                    if (sd?.rootPath != null && sd.available) {
                        openFileList("browse", getString(R.string.drawer_sd), sd.rootPath!!)
                    } else {
                        Toast.makeText(this, R.string.sd_not_available, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            true
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.loginFragment, R.id.signUpFragment -> {
                    binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    binding.navView.visibility = View.GONE
                }
                else -> {
                    binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    binding.navView.visibility = View.VISIBLE
                }
            }
            when (destination.id) {
                R.id.dashboardFragment -> {
                    clearMenuChecks(binding.navView.menu)
                    binding.navView.setCheckedItem(R.id.nav_home)
                }
                R.id.fileListFragment, R.id.fileDetailFragment -> clearMenuChecks(binding.navView.menu)
            }
        }
    }

    private fun clearMenuChecks(menu: Menu) {
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            item.isChecked = false
            if (item.hasSubMenu()) {
                clearMenuChecks(item.subMenu!!)
            }
        }
    }

    private fun openFileList(categoryId: String, title: String, rootPath: String = "") {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        val bundle = Bundle().apply {
            putString("categoryId", categoryId)
            putString("categoryTitle", title)
            putString("storageRootPath", rootPath)
        }
        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .build()
        navController.navigate(R.id.fileListFragment, bundle, options)
    }

    fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }
}
