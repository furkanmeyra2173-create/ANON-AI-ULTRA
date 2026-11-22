package com.anonai.ultra

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

private data class AiModel(val name: String, val provider: String, val purpose: String, val description: String)

private val models = listOf(
    AiModel("DeepSeek", "DeepSeek", "Mantık ve kod", "Genel sohbet, akıl yürütme ve kod analizi."),
    AiModel("Gemini", "Google", "Genel sohbet", "Genel amaçlı sohbet ve çok yönlü yardım."),
    AiModel("Llama", "Meta", "Genel sohbet", "Açık model ailesiyle genel amaçlı sohbet."),
    AiModel("Qwen Coder", "Qwen", "Yazılım geliştirme", "Kod üretme, açıklama ve hata ayıklama."),
    AiModel("Phi", "Microsoft", "Hafif görevler", "Daha hafif görevler için küçük model ailesi."),
    AiModel("Mistral", "Mistral AI", "Metin ve kod", "Metin üretimi, özetleme ve kod yardımı."),
    AiModel("GPT-OSS", "Open model", "Akıl yürütme", "Açık ağırlıklı akıl yürütme ve kod seçenekleri.")
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
    var chosenModel by remember { mutableStateOf("DeepSeek") }

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
                    Tab.CHAT -> ChatScreen(chosenModel) { chosenModel = it }
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
        Box(
            Modifier.size(46.dp).background(Red.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) { Text("A", color = Red, fontSize = 27.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("ANON AI ULTRA", color = White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("YOUR AI WORKSPACE", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
        }
        Pill("V1.0", Green)
    }
}

@Composable
private fun Pill(text: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.14f), shape = CircleShape) {
        Text(text, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp))
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

@Composable
private fun ChatScreen(selectedModel: String, onChoose: (String) -> Unit) {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Yapay zekân hazır.", color = White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Bir model seç ve fikirlerini konuşmaya başla.", color = Muted, fontSize = 14.sp)
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF190A0D)),
            border = BorderStroke(1.dp, Red.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(21.dp)
        ) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AI CHAT", color = Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.weight(1f))
                    Pill("ARAYÜZ HAZIR", Green)
                }
                Text("Farklı yapay zekâlar, tek platform.", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "AI servisleri henüz bağlanmadı. Bu sürümde mesaj gönderimi etkin değil; güvenli API altyapısı sonraki aşamada eklenecek.",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Heading("MODELİNİ SEÇ", "Aktif model: $selectedModel")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                models.forEach { model -> ChoiceChip(model.name, selectedModel == model.name) { onChoose(model.name) } }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Heading("MESAJIN", "Sohbet alanı")
            OutlinedTextField(
                value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Mesajını buraya yaz...", color = Muted) },
                minLines = 3, maxLines = 5, shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Field, unfocusedContainerColor = Field,
                    focusedBorderColor = Red, unfocusedBorderColor = Line,
                    focusedTextColor = White, unfocusedTextColor = White, cursorColor = Red
                )
            )
            Button(
                onClick = { Toast.makeText(context, "AI API bağlantısı henüz kurulmadı. Mesaj gönderilmedi.", Toast.LENGTH_LONG).show() },
                modifier = Modifier.fillMaxWidth().height(52.dp), enabled = prompt.isNotBlank(),
                shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Red)
            ) { Text("Gönder  ↑", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
        Text("Ücretsiz model erişimi sağlayıcıların kota ve kullanım koşullarına bağlıdır.",
            color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CoderScreen() {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("Kotlin") }
    val languages = listOf("Kotlin", "Java", "Python", "C++", "JavaScript", "HTML")
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        Text("Model: Qwen Coder", color = Muted, fontSize = 12.sp)
                    }
                    Pill("YAKINDA", Green)
                }
                Text("Kodlama asistanı arayüzü hazır. Model bağlantısı henüz etkin değil.",
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
            minLines = 5, maxLines = 8, shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Field, unfocusedContainerColor = Field,
                focusedBorderColor = Red, unfocusedBorderColor = Line,
                focusedTextColor = White, unfocusedTextColor = White, cursorColor = Red
            )
        )
        Button(
            onClick = { Toast.makeText(context, "Qwen Coder API bağlantısı sonraki aşamada eklenecek.", Toast.LENGTH_LONG).show() },
            modifier = Modifier.fillMaxWidth().height(52.dp), enabled = prompt.isNotBlank(),
            shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Red)
        ) { Text("Kod üret", fontWeight = FontWeight.Bold) }
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
            Text("Kullanmak istediğin modeli seç.", color = Muted, fontSize = 14.sp)
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
        Text("Model aileleri listelenmiştir; API bağlantıları henüz kurulmadı. Ücretsiz erişim kota ve sağlayıcı koşullarına bağlıdır.",
            color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
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
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SOSYAL MEDYA", color = Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/@anonymous.1797"))
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
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
                SettingLine("Sürüm", "1.0.0")
                SettingLine("Tema", "Siyah / Kırmızı")
                SettingLine("Durum", "Geliştirme aşamasında")
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF190A0D)),
            border = BorderStroke(1.dp, Red.copy(alpha = 0.3f)), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("GİZLİLİK VE GÜVENLİK", color = Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("API anahtarlarını uygulamanın içine koymayacağız. Gerçek AI bağlantıları güvenli bir sunucu üzerinden yapılacak.",
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
