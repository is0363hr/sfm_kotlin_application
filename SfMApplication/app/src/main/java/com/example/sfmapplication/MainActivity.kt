package com.example.sfmapplication

import android.R
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import com.example.sfmapplication.databinding.ActivityMainBinding
import java.io.File
import java.io.FileInputStream
import java.util.ArrayList

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val exifMap = mapOf(
        "Datetime" to ExifInterface.TAG_DATETIME,
        "UserComment" to ExifInterface.TAG_USER_COMMENT,
        "Latitude" to ExifInterface.TAG_GPS_LATITUDE,
        "Longitude" to ExifInterface.TAG_GPS_LONGITUDE,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()
        binding.btnLaunchCamera.setOnClickListener {
            val nextIntent = Intent(this, CameraActivity::class.java)
            startActivity(nextIntent)
        }

        val imgFilePath = intent.getStringExtra(CameraActivity.EXTRA_MESSAGE)
        if (imgFilePath != null) {
            val inputStream = FileInputStream(imgFilePath)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            binding.cameraImage.setImageBitmap(bitmap)
            val array = createExifArrayFromInputFile(File(imgFilePath))
            binding.exifView.adapter = ArrayAdapter<String>(this, R.layout.simple_list_item_1, array)
        }
    }

    private fun createExifArrayFromInputFile(file: File) : ArrayList<String> {
        val exifInterface = android.media.ExifInterface(file.absolutePath)
        return arrayListOf<String>().also { array ->
            exifMap.forEach { item ->
                array.add("${item.key} : ${exifInterface.getAttribute(item.value)}")
            }
        }
    }


}