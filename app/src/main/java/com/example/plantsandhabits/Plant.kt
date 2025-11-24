package com.example.plantsandhabits

data class Plant (
    val id: Int,
    val name: String,
    val scienceName: String,
    val imageRes: Int = R.drawable.sample_category
)