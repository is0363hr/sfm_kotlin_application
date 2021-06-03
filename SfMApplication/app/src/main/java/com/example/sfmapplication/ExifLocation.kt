package com.example.sfmapplication

class ExifLocation{
    private var longitudeRef: String = ""
    private var longitude: String = ""
    private var latitudeRef: String = ""
    private var latitude: String = ""

    fun setLongitude(longitude:String) {
        this.longitude = longitude
    }

    fun setLatitude(latitude:String) {
        this.latitude = latitude
    }

    fun setLongitudeRef(longitudeRef:String) {
        this.longitudeRef = longitudeRef
    }

    fun setLatitudeRef(latitudeRef:String) {
        this.latitudeRef = latitudeRef
    }

    fun getLongitude(): String {
        return this.longitude
    }

    fun getLongitudeRef(): String {
        return this.longitudeRef
    }

    fun getLatitude(): String {
        return this.latitude
    }

    fun getLatitudeRef(): String {
        return this.latitudeRef
    }
}