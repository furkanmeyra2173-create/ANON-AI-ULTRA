package com.anonai.ultra

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.UnknownHostException

private val Black = Color(0xFF000000)
private val Panel = Color(0xFF111111)
private val Field = Color(0xFF242424)
private val Red = Color(0xFFF04460)
private val Green = Color(0xFF16B65B)
private val White = Color(0xFFFFFFFF)
private val Muted = Color(0xFF9A9A9A)
private val Line = Color(0xFF292929)

private enum class Tab(val title: String, val glyph: String) {
    CHAT("Sohbet", "◉"), CODER("Coder", "</>"), MODELS("Modeller", "◇"), SETTINGS("Ayarlar", "⚙")
}

private data class AiModel(
    val name: String,
    val provider: String,
    val purpose: String,
    val description: String,
    val id: String
)

private val models = listOf(
    AiModel("Otomatik Ücretsiz", "OpenRouter", "Genel sohbet", "Uygun ücretsiz sohbet modelini otomatik seçer.", "openrouter/free"),
    AiModel("Particle-1", "Particle AI", "Kodlama", "Kod üretme ve yazılım görevleri için ücretsiz model.", "particle-ai/particle-1:free"),
    AiModel("Flint", "Recursive", "Kodlama ve analiz", "Kod tabanlarını inceleme, hata bulma ve test yazma.", "recursive/flint-v1.0:free"),
    AiModel("Nemotron 3.5 Lightning", "NVIDIA", "Hızlı sohbet ve kod", "Hafif ve hızlı genel amaçlı model.", "nvidia/nemotron-3.5-lightning:free"),
    AiModel("Gemma 4 26B", "Google", "Genel amaçlı", "Metin ve görsel anlayabilen açık model ailesi.", "google/gemma-4-26b-a4b:free"),
    AiModel("Ling 3.2 Flash", "inclusionAI", "Akıl yürütme", "Uzun bağlamlı ve çok yönlü model.", "inclusionai/ling-3.2-flash:free")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        setContent { AnonAiUltraApp() }
    }
}

@Composable
private fun AnonAiUltraApp() {
    var tab by remember { mutableStateOf(Tab.CHAT) }
    var chosenModel by remember { mutableStateOf(models.first().name) }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Red, secondary = Green, background = Black, surface = Panel,
        onPrimary = White, onBackground = White, onSurface = White
    )) {
        Scaffold(
            containerColor = Black,
            bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = White) {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Text(item.glyph, fontSize = 19.sp, fontWeight = FontWeight.Bold) },
                            label = { Text(item.title, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Red, selectedTextColor = White,
                                unselectedIconColor = Muted, unselectedTextColor = Muted,
                                indicatorColor = Red.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).background(Black)) {
                AppHeader()
                when (tab) {
                    Tab.CHAT -> ChatScreen(chosenModel, onChoose = { chosenModel = it })
                    Tab.CODER -> CoderScreen()
                    Tab.MODELS -> ModelsScreen(chosenModel) {
                        chosenModel = it
                        tab = Tab.CHAT
                    }
                    Tab.SETTINGS -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).background(Red.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center) {
            Text("A", color = Red, fontSize = 27.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("ANON AI ULTRA", color = White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("YOUR AI WORKSPACE", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
        }
        Pill("AI ONLINE READY", Green)
    }
}

@Composable
private fun Pill(text: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.14f), shape = CircleShape) {
        Text(text, color = tint, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) Red.copy(alpha = 0.18f) else Panel,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (selected) Red else Line)
    ) {
        Text(label, color = if (selected) White else Muted, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
    }
}

private fun runAiRequest(
    context: android.content.Context,
    modelId: String,
    systemPrompt: String,
    history: List<Pair<String, String>>,
    onStart: () -> Unit,
    onFinish: (String) -> Unit
) {
    val apiKey = SecureApiKeyStore.read(context)
    if (apiKey.isNullOrBlank()) {
        onFinish("API anahtarı bulunamadı. Ayarlar bölümünden ücretsiz OpenRouter anahtarını ekle.")
        return
    }
    onStart()
    Thread {
        val result = try {
            OpenRouterClient.ask(apiKey, modelId, systemPrompt, history).text
        } catch (e: Exception) {
            when (e) {
                is UnknownHostException -> "İnternet bağlantısı kurulamadı. Bağlantını kontrol et."
                else -> e.message ?: "Bilinmeyen bir bağlantı hatası oluştu."
            }
        }
        Handler(Looper.getMainLooper()).post { onFinish(result) }
    }.start()
}

@Composable
private fun ChatScreen(selectedModel: String, onChoose: (String) -> Unit) {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<Pair<String, String>>() }
    val model = models.firstOrNull { it.name == selectedModel } ?: models.first()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Yapay zekân hazır.", color = White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Ücretsiz modellerle sohbet et.", color = Muted, fontSize = 14.sp)
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF190A0D)),
            border = BorderStroke(1.dp, Red.copy(alpha = 0.4f)), shape = RoundedCornerShape(21.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AI CHAT", color = Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.weight(1f))
                    Pill(if (loading) "YANITLANIYOR" else "ÜCRETSİZ MODEL", Green)
                }
                Text("Tek uygulamada farklı yapay zekâlar.", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Aktif: ${model.name} • ${model.provider}", color = Muted, fontSize = 12.sp)
            }
        }
        Heading("MODELİNİ SEÇ", "Aktif model: ${model.name}")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            models.forEach { item -> ChoiceChip(item.name, selectedModel == item.name) { onChoose(item.name) } }
        }
        if (messages.isNotEmpty()) {
            Heading("SOHBET", "${messages.size} mesaj")
            messages.forEach { (role, text) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (role == "assistant") Panel else Color(0xFF190A0D)),
                    border = BorderStroke(1.dp, if (role == "assistant") Line else Red.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (role == "assistant") "ANON AI" else "SEN", color = if (role == "assistant") Green else Red,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(text, color = White, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
        Heading("MESAJIN", "Sorunu veya fikrini yaz")
        OutlinedTextField(
            value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Bana bir şey sor...", color = Muted) },
            minLines = 3, maxLines = 6, shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Field, unfocusedContainerColor = Field,
                focusedBorderColor = Red, unfocusedBorderColor = Line,
                focusedTextColor = White, unfocusedTextColor = White, cursorColor = Red
            )
        )
        Button(
            onClick = {
                val text = prompt.trim()
                if (text.isNotEmpty() && !loading) {
                    messages.add("user" to text)
                    prompt = ""
                    runAiRequest(context, model.id,
                        "Sen ANON AI ULTRA adlı uygulamanın Türkçe konuşan yardımcı asistanısın. Açık, doğru ve faydalı cevap ver.",
                        messages.toList(),
                        onStart = { loading = true },
                        onFinish = { answer -> messages.add("assistant" to answer); loading = false }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = prompt.isNotBlank() && !loading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Red)
        ) { Text(if (loading) "Yanıt hazırlanıyor..." else "Gönder  ↑", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        if (messages.isNotEmpty()) {
            OutlinedButton(onClick = { messages.clear() }, modifier = Modifier.fillMaxWidth()) {
                Text("Sohbeti temizle", color = Muted)
            }
        }
        Text("Ücretsiz modellerin hız ve kullanım kotası sağlayıcıya göre değişebilir.",
            color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CoderScreen() {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("Kotlin") }
    var answer by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val languages = listOf("Kotlin", "Java", "Python", "C++", "JavaScript", "HTML")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("ANON CODER", color = White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Kod yaz, analiz et ve hataları bul.", color = Muted, fontSize = 14.sp)
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel),
            border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("</>", color = Red, fontSize = 23.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("AI Coding Assistant", color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Model: Particle-1 / Flint", color = Muted, fontSize = 12.sp)
                    }
                    Pill(if (loading) "ÇALIŞIYOR" else "READY", Green)
                }
                Text("Kod üretimi, açıklama, hata analizi ve örnek proje yardımı.",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        Heading("PROGRAMLAMA DİLİ", "Çalışma alanını seç")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            languages.forEach { item -> ChoiceChip(item, language == item) { language = item } }
        }
        Heading("GÖREVİN", "Ne kodlamak istiyorsun?")
        OutlinedTextField(
            value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("$language için bir proje oluştur...", color = Muted) },
            minLines = 5, maxLines = 9, shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Field, unfocusedContainerColor = Field,
                focusedBorderColor = Red, unfocusedBorderColor = Line,
                focusedTextColor = White, unfocusedTextColor = White, cursorColor = Red
            )
        )
        Button(
            onClick = {
                val task = prompt.trim()
                if (task.isNotEmpty() && !loading) {
                    answer = ""
                    runAiRequest(context, "particle-ai/particle-1:free",
                        "Sen deneyimli bir yazılım geliştiricisisin. Kullanıcıya Türkçe cevap ver. Seçilen dil: $language. Kod istediğinde çalışan, eksiksiz kod blokları üret; varsayımları belirt ve hataları açıkla. İstek: $task",
                        listOf("user" to "Bu görevi $language dilinde gerçekleştir: $task"),
                        onStart = { loading = true },
                        onFinish = { result -> answer = result; loading = false }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = prompt.isNotBlank() && !loading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Red)
        ) { Text(if (loading) "Kod hazırlanıyor..." else "Kod üret  </>", fontWeight = FontWeight.Bold) }
        if (answer.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = Panel),
                border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ANON CODER YANITI", color = Green, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(answer, color = White, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ModelsScreen(selected: String, onSelect: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("AI MODELLERİ", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Ücretsiz erişilebilen modeller arasından seç.", color = Muted, fontSize = 14.sp)
        }
        models.forEach { model ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(model.name) },
                colors = CardDefaults.cardColors(containerColor = Panel),
                border = BorderStroke(1.dp, if (selected == model.name) Red else Line),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(Red.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center) {
                        Text(model.name.take(1), color = Red, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(model.name, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(model.provider + "  •  " + model.purpose, color = Muted, fontSize = 11.sp)
                        Text(model.description, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(if (selected == model.name) "SEÇİLİ" else "SEÇ",
                        color = if (selected == model.name) Green else Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("Model listesi ve ücretsiz erişim zamanla değişebilir. Bazı modeller yoğunluk nedeniyle geçici olarak yanıt vermeyebilir.",
            color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(SecureApiKeyStore.read(context).orEmpty()) }
    var saved by remember { mutableStateOf(SecureApiKeyStore.read(context).isNullOrBlank().not()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("AYARLAR", color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Uygulamanı ve profil bağlantılarını yönet.", color = Muted, fontSize = 14.sp)
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel),
            border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ÜCRETSİZ AI BAĞLANTISI", color = Red, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text("OpenRouter API anahtarı", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Ücretsiz modelleri kullanmak için ücretsiz bir OpenRouter hesabı ve API anahtarı gerekir. Kredi satın almak zorunda değilsin; ücretsiz model kotası sınırlıdır.",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; saved = false },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-or-v1-...", color = Muted) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Field, unfocusedContainerColor = Field,
                        focusedBorderColor = Red, unfocusedBorderColor = Line,
                        focusedTextColor = White, unfocusedTextColor = White, cursorColor = Red
                    )
                )
                Button(
                    onClick = {
                        if (apiKey.trim().startsWith("sk-or-")) {
                            SecureApiKeyStore.save(context, apiKey)
                            saved = true
                            Toast.makeText(context, "API anahtarı şifreli olarak kaydedildi.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Geçerli bir OpenRouter anahtarı gir.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text("API anahtarını kaydet", fontWeight = FontWeight.Bold) }
                if (saved) Text("API anahtarı bu cihazda şifreli saklanıyor.", color = Green, fontSize = 11.sp)
                TextButton(onClick = {
                    SecureApiKeyStore.clear(context)
                    apiKey = ""
                    saved = false
                    Toast.makeText(context, "API anahtarı silindi.", Toast.LENGTH_SHORT).show()
                }) { Text("Anahtarı sil", color = Muted) }
                Text("Anahtar oluşturmak için OpenRouter sitesini aç:", color = Muted, fontSize = 11.sp)
                Text("openrouter.ai/keys", color = Red, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://openrouter.ai/keys")))
                    })
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel),
            border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SOSYAL MEDYA", color = Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/@anonymous.1797"))
                        try { context.startActivity(intent) }
                        catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "TikTok bağlantısı açılamadı.", Toast.LENGTH_SHORT).show()
                        }
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(48.dp).background(Red.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text("♪", color = Red, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("TikTok", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("@anonymous.1797", color = Muted, fontSize = 13.sp)
                        Text("Profili aç", color = Red, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("↗", color = Red, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel),
            border = BorderStroke(1.dp, Line), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text("ANON AI ULTRA", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                SettingLine("Sürüm", "1.1.0")
                SettingLine("Tema", "Siyah / Kırmızı")
                SettingLine("AI altyapısı", "OpenRouter • ücretsiz modeller")
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF190A0D)),
            border = BorderStroke(1.dp, Red.copy(alpha = 0.3f)), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("GİZLİLİK VE GÜVENLİK", color = Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Anahtar Android Keystore ile cihazında şifrelenerek saklanır. Bu, sunucu taraflı koruma kadar güçlü değildir; cihazına erişimi olan veya cihazı ele geçirilmiş biri anahtarı çıkarabilir. İleride anahtarları sunucu tarafına taşıyacağız.",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.8.sp)
        Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SettingLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 13.sp)
        Text(value, color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
