package com.anonai.ultra
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureApiKeyStore {
 private const val PREFS="anon_ai_secure"
 private fun secretKey(alias:String):SecretKey {
  val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  (ks.getKey(alias,null) as? SecretKey)?.let{return it}
  val gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
  gen.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
   .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
   .setRandomizedEncryptionRequired(true).build())
  return gen.generateKey()
 }
 fun save(c:android.content.Context,provider:String,value:String){
  val alias="anon_ai_${provider}_key"; val clean=value.trim()
  if(clean.isEmpty()){clear(c,provider);return}
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,secretKey(alias))
  val bytes=cipher.iv+cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
  c.getSharedPreferences(PREFS,0).edit().putString(alias,Base64.encodeToString(bytes,Base64.NO_WRAP)).apply()
 }
 fun read(c:android.content.Context,provider:String):String?{
  val alias="anon_ai_${provider}_key"
  val encoded=c.getSharedPreferences(PREFS,0).getString(alias,null)?:return null
  return try{
   val bytes=Base64.decode(encoded,Base64.NO_WRAP);require(bytes.size>12)
   val cipher=Cipher.getInstance("AES/GCM/NoPadding")
   cipher.init(Cipher.DECRYPT_MODE,secretKey(alias),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
   String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
  }catch(_:Exception){null}
 }
 fun clear(c:android.content.Context,provider:String){
  val alias="anon_ai_${provider}_key";c.getSharedPreferences(PREFS,0).edit().remove(alias).apply()
 }
}
