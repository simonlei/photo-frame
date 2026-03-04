package com.photoframe.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.photoframe.R
import com.photoframe.data.Photo

class SlideShowAdapter(
    private var photos: List<Photo>,
    private var showInfo: Boolean
) : RecyclerView.Adapter<SlideShowAdapter.PhotoViewHolder>() {

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.iv_photo)
        val tvInfo: TextView = itemView.findViewById(R.id.tv_info)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        Glide.with(holder.imageView)
            .load(photo.url)
            .transition(DrawableTransitionOptions.withCrossFade(600))
            .error(android.R.color.black)
            .into(holder.imageView)

        if (showInfo) {
            val date = photo.takenAt?.take(10) ?: photo.uploadedAt.take(10)
            val uploader = photo.uploaderName.ifBlank { "未知" }
            holder.tvInfo.text = "$date · $uploader"
            holder.tvInfo.visibility = View.VISIBLE
        } else {
            holder.tvInfo.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = photos.size

    /** 使用 DiffUtil 差量更新，保持 ViewPager2 当前位置不变 */
    fun updatePhotos(newPhotos: List<Photo>) {
        val diffResult = DiffUtil.calculateDiff(PhotoDiffCallback(photos, newPhotos))
        photos = newPhotos
        diffResult.dispatchUpdatesTo(this)
    }

    /** 更新照片信息显示设置（从设置页返回时调用，不重建 Adapter） */
    fun setShowInfo(show: Boolean) {
        if (showInfo != show) {
            showInfo = show
            notifyItemRangeChanged(0, itemCount)
        }
    }

    private class PhotoDiffCallback(
        private val oldList: List<Photo>,
        private val newList: List<Photo>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
            oldList[oldPos].id == newList[newPos].id
        override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
            oldList[oldPos] == newList[newPos]
    }
}
