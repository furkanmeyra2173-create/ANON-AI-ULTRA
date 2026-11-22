package com.anonai.ultra

import com.google.firebase.appcheck.FirebaseAppCheck

object AppCheckDebugToken {
    fun request(onResult: (String) -> Unit) {
        FirebaseAppCheck.getInstance().getAppCheckToken(false)
            .addOnSuccessListener { onResult(it.token) }
            .addOnFailureListener { onResult("Token alınamadı: ${it.message}") }
    }
}
