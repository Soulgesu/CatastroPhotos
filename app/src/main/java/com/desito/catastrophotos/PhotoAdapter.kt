package com.desito.catastrophotos
import com.desito.catastrophotos.R

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.desito.catastrophotos.databinding.ItemPhotoBinding

class PhotoAdapter(
    private val getThemeColor: (Int) -> Int,
    private val onClick: (Uri?, String) -> Unit,
    private val onDelete: (Uri?, String) -> Unit
) : ListAdapter<PhotoUIState, PhotoAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(val b: ItemPhotoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val fileName = item.name
        val resources = holder.itemView.resources

        if (item.isDeleted) {
            holder.b.ivPhoto.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            holder.b.ivPhoto.alpha = 0.3f
            holder.b.tvPhotoName.text = "${fileName.substringBeforeLast(".")} (Eliminada)"
            holder.b.btnDelete.visibility = View.GONE
            holder.b.root.strokeWidth = 0
        } else {
            holder.b.ivPhoto.alpha = 1.0f
            holder.b.ivPhoto.load(item.uri)
            holder.b.tvPhotoName.text = fileName.substringBeforeLast(".")
            holder.b.btnDelete.visibility = View.VISIBLE

            val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val highlightColor = if (isDark) Color.parseColor("#FFCC80") else Color.parseColor("#FF9800")

            if (item.isNew) {
                holder.b.root.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, resources.displayMetrics).toInt()
                holder.b.root.strokeColor = highlightColor
            } else {
                holder.b.root.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics).toInt()
                holder.b.root.strokeColor = getThemeColor(com.google.android.material.R.attr.colorOutlineVariant)
            }
        }

        holder.b.root.setOnClickListener { onClick(item.uri, item.name) }
        holder.b.btnDelete.setOnClickListener { onDelete(item.uri, item.name) }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<PhotoUIState>() {
        override fun areItemsTheSame(oldItem: PhotoUIState, newItem: PhotoUIState) = oldItem.name == newItem.name
        override fun areContentsTheSame(oldItem: PhotoUIState, newItem: PhotoUIState) = oldItem == newItem
    }
}
