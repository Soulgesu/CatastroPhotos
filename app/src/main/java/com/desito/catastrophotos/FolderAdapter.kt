package com.desito.catastrophotos
import com.desito.catastrophotos.R

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.desito.catastrophotos.databinding.ItemFolderBinding

class FolderAdapter(
    private val isSelectionMode: () -> Boolean,
    private val isSelected: (String) -> Boolean,
    private val getThemeColor: (Int) -> Int,
    private val onClick: (String) -> Unit,
    private val onLongClick: (String) -> Unit
) : ListAdapter<FolderUIState, FolderAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val b: ItemFolderBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val folderName = item.name
        val inSelectionMode = isSelectionMode()
        val selected = isSelected(folderName)
        val resources = holder.itemView.resources

        holder.b.tvFolderName.text = folderName
        holder.b.tvPhotoCount.text = "${item.count} fotos"

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val primaryTextColor = if (isDark) Color.WHITE else getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        val modifiedColor = if (isDark) Color.parseColor("#FFCC80") else Color.parseColor("#E65100")

        holder.b.tvFolderName.setTypeface(null, Typeface.BOLD)
        holder.b.tvFolderName.alpha = 1.0f

        if (item.isDirty) {
            holder.b.tvFolderName.text = "$folderName ⚠️"
            holder.b.tvFolderName.setTextColor(modifiedColor)
        } else {
            holder.b.tvFolderName.setTextColor(primaryTextColor)
        }

        holder.b.checkBox.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        holder.b.ivArrow.visibility = if (inSelectionMode) View.GONE else View.VISIBLE
        holder.b.checkBox.isChecked = selected

        val backgroundColor = if (selected) {
            ColorStateList.valueOf(getThemeColor(com.google.android.material.R.attr.colorPrimaryContainer))
        } else {
            ColorStateList.valueOf(Color.TRANSPARENT)
        }
        holder.b.cardFolder.setCardBackgroundColor(backgroundColor)

        holder.b.root.setOnClickListener { onClick(folderName) }
        holder.b.root.setOnLongClickListener { onLongClick(folderName); true }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<FolderUIState>() {
        override fun areItemsTheSame(oldItem: FolderUIState, newItem: FolderUIState) = oldItem.name == newItem.name
        override fun areContentsTheSame(oldItem: FolderUIState, newItem: FolderUIState) = oldItem == newItem
    }
}
