package com.example.filemanager.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.filemanager.databinding.ItemFileBinding
import com.example.filemanager.model.FileItem
import com.example.filemanager.utils.FileFormatUtils

class FileAdapter(
    private val onClick: (FileItem) -> Unit,
    private val onMoreClick: (FileItem, View) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {
    private val items = mutableListOf<FileItem>()

    fun submitList(list: List<FileItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class FileViewHolder(private val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FileItem) {
            binding.tvName.text = item.name
            val time = if (item.dateAddedSeconds > 0) {
                FileFormatUtils.dateTimeFromEpochSeconds(item.dateAddedSeconds)
            } else {
                "--:--"
            }
            binding.tvMeta.text = when {
                item.isDirectory -> "Folder"
                item.type == "APP" -> "Application"
                else -> "${item.type} • ${FileFormatUtils.sizeToDisplay(item.sizeBytes)} • $time"
            }
            when {
                item.type == "APP" && item.localPath != null -> {
                    binding.ivFileType.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    runCatching {
                        val pm = binding.root.context.packageManager
                        val info = pm.getApplicationInfo(item.localPath!!, 0)
                        Glide.with(binding.ivFileType).load(pm.getApplicationIcon(info)).into(binding.ivFileType)
                    }.onFailure {
                        binding.ivFileType.setImageResource(item.iconRes)
                    }
                }
                item.contentUri != null && item.mimeType?.startsWith("image/") == true -> {
                    binding.ivFileType.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    Glide.with(binding.ivFileType).load(item.contentUri).into(binding.ivFileType)
                }
                else -> {
                    binding.ivFileType.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    binding.ivFileType.setImageResource(item.iconRes)
                }
            }
            binding.root.setOnClickListener { onClick(item) }
            binding.ivMore.setOnClickListener { v -> onMoreClick(item, v) }
        }
    }
}
