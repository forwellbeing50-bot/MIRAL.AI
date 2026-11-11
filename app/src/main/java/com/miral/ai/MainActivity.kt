package com.miral.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var voice: VoiceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        voice = VoiceManager(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MiralApp(voice)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        voice.stopConversation()
    }

    override fun onDestroy() {
        super.onDestroy()
        voice.shutdown()
    }
}

data class ChatMessage(val text: String, val fromMiral: Boolean)

data class Reply(val roman: String, val urdu: String)

fun miralReply(input: String): Reply {
    val t = input.trim().lowercase()
    return when {
        t.contains("salam") || t.contains("assalam") || t.contains("سلام") ->
            Reply(
                "Walaikum Assalam! Aap ka kya haal hai?",
                "وعلیکم السلام! آپ کا کیا حال ہے؟"
            )

        t.contains("time") || t.contains("waqt") || t.contains("baje") || t.contains("وقت") -> {
            val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            Reply("Abhi waqt hai $now.", "ابھی وقت ہے $now۔")
        }

        t.contains("date") || t.contains("tareekh") || t.contains("tarikh") || t.contains("تاریخ") -> {
            val today = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())
            Reply("Aaj ki tareekh hai $today.", "آج کی تاریخ ہے $today۔")
        }

        t.contains("naam") || t.contains("name") || t.contains("نام") ->
            Reply(
                "Mera naam Miral hai. Main aap ki Urdu AI dost hoon.",
                "میرا نام میرل ہے۔ میں آپ کی اردو اے آئی دوست ہوں۔"
            )

        t.contains("kaise") || t.contains("kaisi") || t.contains("how are") ||
            t.contains("کیسے") || t.contains("کیسی") || t.contains("حال") ->
            Reply(
                "Main bilkul theek hoon, shukriya! Aap sunayein.",
                "میں بالکل ٹھیک ہوں، شکریہ! آپ سنائیں۔"
            )

        else ->
            Reply(
                "Abhi main seekh rahi hoon. Jald hi mere paas asli AI dimagh hoga. Tab tak salam, waqt ya tareekh poochh kar dekhein.",
                "ابھی میں سیکھ رہی ہوں۔ جلد ہی میرے پاس اصل اے آئی دماغ ہوگا۔ تب تک سلام، وقت یا تاریخ پوچھ کر دیکھیں۔"
            )
    }
}

enum class Screen { CHAT, VOICE }

@Composable
fun MiralApp(voice: VoiceManager) {
    val ctx = LocalContext.current

    val messages = remember {
        mutableStateListOf(
            ChatMessage("Assalam o Alaikum! Main Miral hoon. Mujh se kuch bhi poochein.", true)
        )
    }
    var screen by remember { mutableStateOf(Screen.CHAT) }
    var lastHeard by remember { mutableStateOf("") }
    var lastReply by remember { mutableStateOf("") }

    fun handleText(text: String, speakReply: Boolean) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val reply = miralReply(clean)
        messages.add(ChatMessage(clean, false))
        messages.add(ChatMessage(reply.roman, true))
        lastReply = reply.roman
        if (speakReply) voice.speak(reply.roman, reply.urdu)
    }

    fun startVoice() {
        voice.startConversation { heard ->
            lastHeard = heard
            handleText(heard, true)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoice()
        } else {
            voice.statusText = "Mic ki ijazat nahi mili. Phone Settings > Apps > MIRAL AI > Permissions"
        }
    }

    fun onMicClick() {
        if (voice.conversationMode || voice.isListening || voice.isSpeaking) {
            voice.stopConversation()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startVoice()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (screen == Screen.VOICE) {
        BackHandler {
            voice.stopConversation()
            screen = Screen.CHAT
        }
        VoiceScreen(
            voice = voice,
            lastHeard = lastHeard,
            lastReply = lastReply,
            onMicClick = { onMicClick() },
            onBack = {
                voice.stopConversation()
                screen = Screen.CHAT
            }
        )
    } else {
        ChatScreen(
            messages = messages,
            onSend = { handleText(it, false) },
            onOpenVoice = { screen = Screen.VOICE }
        )
    }
}

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    onSend: (String) -> Unit,
    onOpenVoice: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B14))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "MIRAL AI",
                color = Color(0xFFB388FF),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onOpenVoice) {
                Text("Awaaz 🎙", color = Color(0xFFE040FB), fontSize = 16.sp)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Yahan likhein...") },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Button(onClick = {
                onSend(input)
                input = ""
            }) {
                Text("Bhejein")
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromMiral) Arrangement.Start else Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = if (message.fromMiral) Color(0xFF2A1F4D) else Color(0xFF3B3B52),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.text,
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun VoiceScreen(
    voice: VoiceManager,
    lastHeard: String,
    lastReply: String,
    onMicClick: () -> Unit,
    onBack: () -> Unit
) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val active = voice.isListening || voice.isSpeaking
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val scale = if (active) pulse else 1f
    val circleColor = when {
        voice.isListening -> Color(0xFFE040FB)
        voice.isSpeaking -> Color(0xFF7C4DFF)
        else -> Color(0xFF3B2A70)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B14))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← Chat par wapas", color = Color(0xFFB388FF), fontSize = 16.sp)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(circleColor)
                    .clickable { onMicClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("🎤", fontSize = 56.sp)
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = voice.statusText,
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (voice.conversationMode) {
                    "Baat-cheet ON ✓  Rokne ke liye mic dabayein"
                } else {
                    "Ek baar mic dabayein, phir baat-cheet khud chalti rahegi"
                },
                color = if (voice.conversationMode) Color(0xFF69F0AE) else Color(0xFF9E9E9E),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            if (lastHeard.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Aap ne kaha: $lastHeard",
                    color = Color(0xFFBDBDBD),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (lastReply.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Miral: $lastReply",
                    color = Color(0xFFB388FF),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (voice.voiceNote.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = voice.voiceNote,
                    color = Color(0xFFFFB74D),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
