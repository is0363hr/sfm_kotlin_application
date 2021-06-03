package com.example.sfmapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.sfmapplication.databinding.CameraViewBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.properties.Delegates

class CameraActivity : AppCompatActivity(), SensorEventListener, LocationListener {
    private lateinit var binding: CameraViewBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private var pushFilePath: String? = null
    private lateinit var cameraExecutor: ExecutorService
    private var accelerateData: String = ""
    private var mManager: SensorManager by Delegates.notNull<SensorManager>()
    private var mSensor: Sensor by Delegates.notNull<Sensor>()

    // 加速度, 位置情報
    private var x = 0f
    private var y = 0f
    private var z = 0f
    private var latitude: String = ""
    private var longitude: String = ""
    private lateinit var exifLocation: ExifLocation

    private lateinit var locationManager: LocationManager
    private val exifMap = mapOf(
            "Datetime" to ExifInterface.TAG_DATETIME,
            "UserComment" to ExifInterface.TAG_USER_COMMENT,
            "Latitude" to ExifInterface.TAG_GPS_LATITUDE,
            "Longitude" to ExifInterface.TAG_GPS_LONGITUDE,
            "LatitudeRef" to ExifInterface.TAG_GPS_LATITUDE_REF,
            "LongitudeRef" to ExifInterface.TAG_GPS_LONGITUDE_REF
    )

    private val timer = object: CountDownTimer(2000, 1000) {
        override fun onTick(millisUntilFinished: Long) {
            binding.countDownText.textSize = 50F
            binding.countDownText.text = ((millisUntilFinished + 1000) / 1000).toString()
        }
        override fun onFinish() {
            binding.countDownText.text = ""
            takePhoto()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding = CameraViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.countDownText.textSize = 25.0f

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this,
                    arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    ), REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listener for take photo button
        binding.cameraCaptureButton.setOnClickListener { timer.start() }
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        //センサーマネージャーを取得する
        mManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        //加速度計のセンサーを取得する
        mSensor = mManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    1000)
        } else {
            locationStart()
            if (::locationManager.isInitialized) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 50f, this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_UI)
        binding.forward.setOnClickListener {
            val frontIntent = Intent(this, MainActivity::class.java)
            Log.e("CameraActivity", "pushFilePath:" + pushFilePath)
            if (pushFilePath != null){
                frontIntent.putExtra(EXTRA_MESSAGE, pushFilePath)
            }
            startActivity(frontIntent)
        }
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return
        val timeStamp = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())

        // Create time-stamped output file to hold the image
        val photoFile = File(outputDirectory, "$timeStamp.jpg")
        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                createExifArrayFromInputFile(photoFile)
                pushFilePath = photoFile.absolutePath
                val msg = "Photo capture succeeded"
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                Log.d(TAG, msg)
            }
        })
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.viewFinder.createSurfaceProvider()) }

            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults:
            IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun createExifArrayFromInputFile(file: File) : ArrayList<String> {
        val exifInterface = ExifInterface(file.getAbsolutePath())
        Log.v("save path", file.getAbsolutePath())
        val userComment: String = accelerateData
        exifInterface.setAttribute(ExifInterface.TAG_USER_COMMENT, userComment)
//        if (exifLocation != null) {
//            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE, exifLocation.getLatitude())
//            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, exifLocation.getLongitude())
//            exifInterface.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, exifLocation.getLatitudeRef())
//            exifInterface.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, exifLocation.getLongitudeRef())
//        }
        exifInterface.saveAttributes()
        return arrayListOf<String>().also { array ->
            exifMap.forEach { item ->
                array.add("${item.key} : ${exifInterface.getAttribute(item.value)}")
            }
        }
    }

    private fun locationStart() {
        // Instances of LocationManager class must be obtained using Context.getSystemService(Class)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.d("debug", "location manager Enabled")
        } else {
            // to prompt setting up GPS
            val settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(settingsIntent)
            Log.d("debug", "not gpsEnable, startActivity")
        }
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1000)

            Log.d("debug", "checkSelfPermission false")
            return
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 50f, this)
    }

    override fun onLocationChanged(location: Location) {
//        latitude = location.latitude.toString()
//        longitude = location.longitude.toString()
        exifLocation =  encodeGpsToExifFormat(location)
        if (exifLocation != null) {
            latitude = exifLocation.getLatitude()
            longitude = exifLocation.getLongitude()
        }
        binding.latText.text = latitude
        binding.lonText.text = longitude
    }

    private fun encodeGpsToExifFormat(location: Location): ExifLocation {
        val exifLocation = ExifLocation()
        // 経度の変換(正->東, 負->西)
        // convertの出力サンプル => 73:9:57.03876
        val lonDMS = Location.convert(location.longitude, Location.FORMAT_SECONDS).split(":".toRegex()).toTypedArray()
        val lon = StringBuilder()
        // 経度の正負でREFの値を設定（経度からは符号を取り除く）
        if (lonDMS[0].contains("-")) {
            exifLocation.setLongitudeRef("W")
        } else {
            exifLocation.setLongitudeRef("E")
        }
        lon.append(lonDMS[0].replace("-", ""))
        lon.append("/1,")
        lon.append(lonDMS[1])
        lon.append("/1,")
        // 秒は小数の桁を数えて精度を求める
        var index = lonDMS[2].indexOf('.')
        if (index == -1) {
            lon.append(lonDMS[2])
            lon.append("/1")
        } else {
            val digit = lonDMS[2].substring(index + 1).length
            val second = (lonDMS[2].toDouble() * Math.pow(10.0, digit.toDouble())).toInt()
            lon.append(second.toString())
            lon.append("/1")
            for (i in 0 until digit) {
                lon.append("0")
            }
        }
        exifLocation.setLongitude(lon.toString())

        // 緯度の変換(正->北, 負->南)
        // convertの出力サンプル => 73:9:57.03876
        val latDMS = Location.convert(location.latitude, Location.FORMAT_SECONDS).split(":".toRegex()).toTypedArray()
        val lat = StringBuilder()
        // 経度の正負でREFの値を設定（経度からは符号を取り除く）
        if (latDMS[0].contains("-")) {
            exifLocation.setLatitudeRef("S")
        } else {
            exifLocation.setLatitudeRef("N")
        }
        lat.append(latDMS[0].replace("-", ""))
        lat.append("/1,")
        lat.append(latDMS[1])
        lat.append("/1,")
        // 秒は小数の桁を数えて精度を求める
        index = latDMS[2].indexOf('.')
        if (index == -1) {
            lat.append(latDMS[2])
            lat.append("/1")
        } else {
            val digit = latDMS[2].substring(index + 1).length
            val second = (latDMS[2].toDouble() * Math.pow(10.0, digit.toDouble())).toInt()
            lat.append(second.toString())
            lat.append("/1")
            for (i in 0 until digit) {
                lat.append("0")
            }
        }
        exifLocation.setLatitude(lat.toString())
        return exifLocation
    }


    override fun onSensorChanged(event: SensorEvent?) {
        val alpha = 0.90f
        if (event != null) {
            if(event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                x = (x * alpha + event.values!![0] * (1 - alpha))
                y = (y * alpha + event.values!![1] * (1 - alpha))
                z = (z * alpha + event.values!![2] * (1 - alpha))

                // 合成加速度
                var cXYZ:Float = combinedAcceleration()
                var xx:Float = (x / cXYZ)
                var yy:Float = (y / cXYZ)
                var zz:Float = (z / cXYZ)

                accelerateData = """$xx,$yy,$zz"""
                isClickBtnEnable(cXYZ)
            }
        }
    }

    private fun combinedAcceleration():Float {
        return sqrt(x.pow(2) + y.pow(2) + z.pow(2))
    }

    private fun isClickBtnEnable(cXYZ: Float) {
        if (cXYZ > 9.90 || cXYZ < 9.70){
            binding.cameraCaptureButton.isEnabled = false
            binding.cameraCaptureButton.backgroundTintList = ColorStateList.valueOf(Color.GRAY)
        }
        else{
            binding.cameraCaptureButton.isEnabled = true
            binding.cameraCaptureButton.backgroundTintList = ColorStateList.valueOf(Color.rgb(0, 230, 200))
        }
    }

    //センサー精度が変更されたときに発生するイベント
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    //アクティビティが閉じられたときにリスナーを解除する
    override fun onPause() {
        super.onPause()
        //リスナーを解除しないとバックグラウンドにいるとき常にコールバックされ続ける
        mManager.unregisterListener(this)
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        const val EXTRA_MESSAGE = "com.example.sfmapplication.MESSAGE"
    }
}