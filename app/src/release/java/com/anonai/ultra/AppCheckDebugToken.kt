package com.anonai.ultra

object AppCheckDebugToken {
    fun request(onResult: (String) -> Unit) {
        onResult("Üretim sürümünde Play Integrity kullanılır.")
    }
}
