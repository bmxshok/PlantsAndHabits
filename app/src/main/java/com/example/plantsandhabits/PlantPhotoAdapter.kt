package com.example.plantsandhabits

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Size
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PlantPhotoAdapter(
    private val photos: MutableList<PlantPhoto>,
    private val onDeleteClick: (PlantPhoto) -> Unit
) : RecyclerView.Adapter<PlantPhotoAdapter.PhotoViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgPhoto: ImageView = itemView.findViewById(R.id.imgPhoto)
        val tvPhotoDate: TextView = itemView.findViewById(R.id.tvPhotoDate)
        val btnDeletePhoto: ImageView = itemView.findViewById(R.id.btnDeletePhoto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plant_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]

        // Загружаем изображение с помощью Coil (асинхронно, с кэшированием и оптимизацией размера)
        val photoFile = File(photo.photoPath)
        if (photoFile.exists()) {
            // Получаем размеры ImageView для оптимизации
            val density = holder.itemView.context.resources.displayMetrics.density
            val targetWidth = (120 * density).toInt() // 120dp в пикселях
            val targetHeight = (140 * density).toInt() // 140dp в пикселях
            
            holder.imgPhoto.load(photoFile) {
                // Оптимизируем размер - загружаем изображение под размер ImageView
                // Это значительно уменьшает использование памяти и ускоряет загрузку
                size(Size(targetWidth, targetHeight))
                crossfade(true)
                placeholder(R.drawable.sample_category)
                error(R.drawable.sample_category)
                // Отключаем анимацию для лучшей производительности при прокрутке
                allowHardware(true)
            }
        } else {
            holder.imgPhoto.setImageResource(R.drawable.sample_category)
        }

        // Форматируем дату
        val date = Date(photo.dateAdded)
        holder.tvPhotoDate.text = dateFormat.format(date)

        // Обработчик удаления
        holder.btnDeletePhoto.setOnClickListener {
            onDeleteClick(photo)
        }
    }

    override fun getItemCount(): Int = photos.size

    fun updatePhotos(newPhotos: List<PlantPhoto>) {
        photos.clear()
        photos.addAll(newPhotos)
        notifyDataSetChanged()
    }
}

