package com.example.plantsandhabits

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CategoryAdapter(
    private val categories: List<Category>,
    private val onItemClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgCategory: ImageView = itemView.findViewById(R.id.imgCategory)
        private val tvCategoryName: TextView = itemView.findViewById(R.id.tvCategoryName)
        private val imgArrow: ImageView = itemView.findViewById(R.id.imgArrow)

        fun bind(category: Category) {
            imgCategory.setImageResource(category.imageRes)
            tvCategoryName.text = category.name

            itemView.setOnClickListener {
                onItemClick(category)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size
}