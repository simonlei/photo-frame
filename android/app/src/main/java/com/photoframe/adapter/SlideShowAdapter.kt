package com.photoframe.adapter

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.photoframe.R
import com.photoframe.data.Photo

class SlideShowAdapter(
    photos: List<Photo>,
    private var showInfo: Boolean
) : RecyclerView.Adapter<SlideShowAdapter.PhotoViewHolder>() {

    // 必须持有独立副本，防止外部 MutableList 修改导致 DiffUtil 比较失效
    private var photos: List<Photo> = photos.toList()

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.iv_photo)
        val llInfo: ViewGroup = itemView.findViewById(R.id.ll_info)
        val tvTakenTime: TextView = itemView.findViewById(R.id.tv_taken_time)
        val tvLocation: TextView = itemView.findViewById(R.id.tv_location)
        val tvUploader: TextView = itemView.findViewById(R.id.tv_uploader)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        Log.d("SlideShowAdapter", "加载照片[$position] url=${photo.url}")
        Glide.with(holder.imageView)
            .load(photo.url)
            .transition(DrawableTransitionOptions.withCrossFade(600))
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?, model: Any?,
                    target: Target<Drawable>, isFirstResource: Boolean
                ): Boolean {
                    Log.e("SlideShowAdapter", "Glide 加载失败[$position] url=${photo.url}", e)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable, model: Any,
                    target: Target<Drawable>?, dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d("SlideShowAdapter", "Glide 加载成功[$position]")
                    return false
                }
            })
            .error(android.R.color.black)
            .into(holder.imageView)

        // ✨ 更新：EXIF 信息显示逻辑
        if (showInfo) {
            // 1. 拍摄时间（优先使用 takenAt，回退到 uploadedAt）
            val displayTime = photo.takenAt?.take(16)?.replace("T", " ")
                ?: photo.uploadedAt.take(10)
            holder.tvTakenTime.apply {
                text = "📸 拍摄于 $displayTime"
                visibility = View.VISIBLE
            }

            // 2. 地理位置（有值才显示）
            if (!photo.locationAddress.isNullOrEmpty()) {
                holder.tvLocation.apply {
                    text = "📍 ${photo.locationAddress}"
                    visibility = View.VISIBLE
                }
            } else {
                holder.tvLocation.visibility = View.GONE
            }

            // 3. 上传者
            val uploader = photo.uploaderName.ifBlank { "未知" }
            holder.tvUploader.apply {
                text = "👤 $uploader"
                visibility = View.VISIBLE
            }
            
            holder.llInfo.visibility = View.VISIBLE
        } else {
            holder.llInfo.visibility = View.GONE
            holder.tvTakenTime.visibility = View.GONE
            holder.tvLocation.visibility = View.GONE
            holder.tvUploader.visibility = View.GONE
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
