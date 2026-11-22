package com.anonai.ultra
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
data class AiReply(val text:String,val model:String)
object ProviderAiClient {
 fun ask(provider:String,key:String,model:String,system:String,history:List<Pair<String,String>>):AiReply{
  require(key.isNotBlank()){ "${provider} API anahtarı Ayarlar bölümünde eksik." }
  return when(provider.lowercase()){"gemini"->gemini(key,model,system,history);"groq"->groq(key,model,system,history);else->throw IllegalArgumentException("Desteklenmeyen sağlayıcı: $provider")}
 }
 private fun post(url:String,headers:Map<String,String>,body:JSONObject):JSONObject{
  val c=URL(url).openConnection() as HttpURLConnection
  c.requestMethod="POST";c.connectTimeout=20000;c.readTimeout=90000;c.doOutput=true;c.setRequestProperty("Content-Type","application/json")
  headers.forEach{(k,v)->c.setRequestProperty(k,v)}
  try{c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
   val status=c.responseCode;val stream=if(status in 200..299)c.inputStream else c.errorStream
   val raw=BufferedReader(InputStreamReader(stream,Charsets.UTF_8)).use{it.readText()};val json=JSONObject(raw)
   if(status !in 200..299)throw IllegalStateException(json.optJSONObject("error")?.optString("message")?: "API hatası HTTP $status")
   return json
  }finally{c.disconnect()}
 }
 private fun groq(key:String,model:String,system:String,history:List<Pair<String,String>>):AiReply{
  val msgs=JSONArray().put(JSONObject().put("role","system").put("content",system))
  history.forEach{(role,text)->msgs.put(JSONObject().put("role",role).put("content",text))}
  val j=post("https://api.groq.com/openai/v1/chat/completions",mapOf("Authorization" to "Bearer $key"),JSONObject().put("model",model).put("messages",msgs).put("temperature",0.7).put("max_tokens",4096))
  val answer=j.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
  if(answer.isBlank()||answer=="null")throw IllegalStateException("Groq boş yanıt döndürdü.")
  return AiReply(answer,j.optString("model",model))
 }
 private fun gemini(key:String,model:String,system:String,history:List<Pair<String,String>>):AiReply{
  val contents=JSONArray()
  history.forEach{(role,text)->contents.put(JSONObject().put("role",if(role=="assistant")"model" else "user").put("parts",JSONArray().put(JSONObject().put("text",text))))}
  val body=JSONObject().put("system_instruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text",system)))).put("contents",contents).put("generationConfig",JSONObject().put("temperature",0.7).put("maxOutputTokens",4096))
  val j=post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",mapOf("x-goog-api-key" to key),body)
  val parts=j.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
  val answer=buildString{if(parts!=null)for(i in 0 until parts.length())append(parts.optJSONObject(i)?.optString("text").orEmpty())}
  if(answer.isBlank())throw IllegalStateException("Gemini boş yanıt döndürdü.")
  return AiReply(answer,model)
 }
}