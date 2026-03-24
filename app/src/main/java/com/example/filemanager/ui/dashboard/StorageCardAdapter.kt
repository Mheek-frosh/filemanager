package com.example.filemanager.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.filemanager.databinding.ItemStorageCardBinding
import com.example.filemanager.model.StorageCard

class StorageCardAdapter(
    private val onClick: (StorageCard) -> Unit
) : RecyclerView.Adapter<StorageCardAdapter.StorageViewHolder>() {
    private val items = mutableListOf<StorageCard>()

    fun submitList(list: List<StorageCard>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StorageViewHolder {
        val binding = ItemStorageCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StorageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StorageViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class StorageViewHolder(private val binding: ItemStorageCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: StorageCard) {
            binding.ivIcon.setImageResource(item.iconRes)
            binding.tvTitle.text = item.title
            binding.tvSubtitle.text = item.subtitle
            binding.progressIndicator.progress = item.progress
            binding.root.alpha = if (item.available) 1f else 0.55f
            val open: () -> Unit = {
                if (item.available && item.rootPath != null) onClick(item)
            }
            binding.root.setOnClickListener { open() }
            binding.ivMore.setOnClickListener { open() }
        }
    }
}
