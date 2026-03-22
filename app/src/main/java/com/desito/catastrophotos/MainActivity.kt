package com.desito.catastrophotos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.desito.catastrophotos.databinding.ActivityMainBinding
import com.google.android.material.color.DynamicColors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // En orientación vertical, viewPager es un ViewPager2.
        // En orientación horizontal, es el root ConstraintLayout.
        val viewPager = binding.viewPager
        if (viewPager is ViewPager2) {
            viewPager.adapter = object : FragmentStateAdapter(this) {
                override fun getItemCount(): Int = 2
                override fun createFragment(position: Int): Fragment {
                    return if (position == 0) CameraFragment() else GalleryFragment()
                }
            }
        }
    }
}