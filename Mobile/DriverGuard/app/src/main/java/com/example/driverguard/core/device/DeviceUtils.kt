package com.example.driverguard.core.device

import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

object DeviceUtils {

    /** Tự động nhận diện Tên thiết bị thật từ phần cứng máy (Ví dụ: "Google Pixel 8", "Samsung Galaxy S23") */
    val deviceName: String
        get() {
            val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
            val model = Build.MODEL.orEmpty()
            return if (model.startsWith(manufacturer, ignoreCase = true)) {
                model
            } else {
                "$manufacturer $model".trim().ifBlank { "Thiết bị di động" }
            }
        }

    /** Tạo Mã thiết bị (Device Code) duy nhất theo Model phần cứng + UID người dùng */
    val deviceCode: String
        get() {
            val modelClean = Build.MODEL.orEmpty().filter { it.isLetterOrDigit() }.takeLast(4).uppercase()
            val uidClean = FirebaseAuth.getInstance().currentUser?.uid?.take(4)?.uppercase() ?: "0001"
            return "DG-$modelClean-$uidClean"
        }
}
