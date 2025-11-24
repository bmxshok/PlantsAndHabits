package com.example.plantsandhabits

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PlantAdapter(
    private val plants: List<Plant>,
    private val onItemClick: (Plant) -> Unit
) : RecyclerView.Adapter<PlantAdapter.PlantViewHolder>() {

    inner class PlantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgPlant: ImageView = itemView.findViewById(R.id.imgPlant)
        private val tvPlantName: TextView = itemView.findViewById(R.id.tvPlantName)
        private val tvPlantScienceName: TextView = itemView.findViewById(R.id.tvScienceName)
        private val imgArrow: ImageView = itemView.findViewById(R.id.imgArrow)

        fun bind(plant: Plant) {
            imgPlant.setImageResource(plant.imageRes)
            tvPlantName.text = plant.name
            tvPlantScienceName.text = plant.scienceName

            itemView.setOnClickListener {
                onItemClick(plant)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlantViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plant, parent, false)
        return PlantViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlantViewHolder, position: Int) {
        holder.bind(plants[position])
    }

    override fun getItemCount(): Int = plants.size
}