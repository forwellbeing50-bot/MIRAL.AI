package com.miral.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val voice = MiralHub.voice(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MiralApp(voice)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!MiralHub.backgroundRunning) {
            MiralHub.peekVoice()?.stopConversation()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!MiralHub.backgroundRunning) {
            MiralHub.releaseVoice()
        }
    }
}

data class ChatMessage(val text: String, val fromMiral: Boolean)

data class Reply(val roman: String, val urdu: String)

/** Sirf tab kaam aata hai jab brain se rabta na ho (offline jawab). */
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

enum class Screen { CHAT, VOICE, SETTINGS }

@Composable
fun MiralApp(voice: VoiceManager) {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf(Screen.CHAT) }

    fun micGranted(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun startVoice() {
        voice.startConversation { heard ->
            MiralHub.lastHeard = heard
            MiralHub.handleText(heard, true)
        }
    }

    fun startBackground() {
        voice.stopConversation()
        MiralService.start(ctx)
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoice()
        } else {
            voice.statusText = "Mic ki ijazat nahi mili. Phone Settings > Apps > MIRAL AI > Permissions"
        }
    }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (micGranted()) {
            startBackground()
        } else {
            voice.statusText = "Mic ki ijazat nahi mili. Phone Settings > Apps > MIRAL AI > Permissions"
        }
    }

    fun onMicClick() {
        if (MiralHub.backgroundRunning) {
            MiralService.stop(ctx)
            return
        }
        if (voice.conversationMode || voice.isListening || voice.isSpeaking) {
            voice.stopConversation()
            return
        }
        if (micGranted()) {
            startVoice()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun onToggleBackground() {
        if (MiralHub.backgroundRunning) {
            MiralService.stop(ctx)
            return
        }
        val needed = mutableListOf<String>()
        if (!micGranted()) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) {
            startBackground()
        } else {
            backgroundLauncher.launch(needed.toTypedArray())
        }
    }

    when (screen) {
        Screen.VOICE -> {
            BackHandler {
                if (!MiralHub.backgroundRunning) voice.stopConversation()
                screen = Screen.CHAT
            }
            VoiceScreen(
                voice = voice,
                lastHeard = MiralHub.lastHeard,
                lastReply = MiralHub.lastReply,
                backgroundRunning = MiralHub.backgroundRunning,
                onMicClick = { onMicClick() },
                onToggleBackground = { onToggleBackground() },
                onBack = {
                    if (!MiralHub.backgroundRunning) voice.stopConversation()
                    screen = Screen.CHAT
                }
            )
        }

        Screen.SETTINGS -> {
            BackHandler { screen = Screen.CHAT }
            SettingsScreen(onBack = { screen = Screen.CHAT })
        }

        Screen.CHAT -> {
            ChatScreen(
                messages = MiralHub.messages,
                thinking = MiralHub.thinking,
                onSend = { MiralHub.handleText(it, false) },
                onOpenVoice = { screen = Screen.VOICE },
                onOpenSettings = { screen = Screen.SETTINGS }
            )
        }
    }
}

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    thinking: Boolean,
    onSend: (String) -> Unit,
    onOpenVoice: () -> Unit,
    onOpenSettings: () -> Unit
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
            Row {
                TextButton(onClick = onOpenSettings) {
                    Text("⚙", color = Color(0xFFB388FF), fontSize = 20.sp)
                }
                TextButton(onClick = onOpenVoice) {
                    Text("Awaaz 🎙", color = Color(0xFFE040FB), fontSize = 16.sp)
                }
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

        if (thinking) {
            Text(
                text = "Miral soch rahi hai...",
                color = Color(0xFF9E9E9E),
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
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
fun SettingsLabel(text: String) {
    Text(
        text = text,
        color = Color(0xFFBDBDBD),
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
    )
}

@Composable
fun FlirtChoice(label: String, value: String, modifier: Modifier) {
    val selected = MiralSettings.flirtLevel == value
    Button(
        onClick = { MiralSettings.updateFlirtLevel(value) },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF7C4DFF) else Color(0xFF2B2B3D)
        )
    ) {
        Text(label, fontSize = 12.sp, color = Color.White)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var testResult by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B14))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← Wapas", color = Color(0xFFB388FF), fontSize = 16.sp)
        }
        Text(
            text = "Settings",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        SettingsLabel("Worker link (Cloudflare se)")
        TextField(
            value = MiralSettings.workerUrl,
            onValueChange = { MiralSettings.updateWorkerUrl(it) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://miral.xxxx.workers.dev") }
        )

        SettingsLabel("Secret (wohi jo Worker mein APP_SECRET rakha)")
        TextField(
            value = MiralSettings.appSecret,
            onValueChange = { MiralSettings.updateAppSecret(it) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("APP_SECRET") }
        )

        SettingsLabel("Miral ka andaaz")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlirtChoice("Seedha", "off", Modifier.weight(1f))
            FlirtChoice("Halka flirt", "flirty", Modifier.weight(1f))
            FlirtChoice("Romantic", "romantic", Modifier.weight(1f))
        }

        SettingsLabel("Awaaz")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Switch(
                checked = MiralSettings.cloudVoice,
                onCheckedChange = { MiralSettings.updateCloudVoice(it) }
            )
            Text(
                text = "Realistic cloud awaaz (Azure). Band ho to phone ki awaaz.",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            enabled = !testing,
            onClick = {
                testing = true
                testResult = "Test ho raha hai..."
                MiralBrain.test { result ->
                    testing = false
                    testResult = result
                }
            }
        ) {
            Text("Brain test karein")
        }

        if (testResult.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = testResult, color = Color.White, fontSize = 15.sp)
        }
    }
}

@Composable
fun VoiceScreen(
    voice: VoiceManager,
    lastHeard: String,
    lastReply: String,
    backgroundRunning: Boolean,
    onMicClick: () -> Unit,
    onToggleBackground: () -> Unit,
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
                text = when {
                    backgroundRunning ->
                        "Background mode ON ✓  App band ya phone lock ho, 'Miral' bol kar baat karein. Jaise: Miral, waqt kya hai"
                    voice.conversationMode ->
                        "Baat-cheet ON ✓  Rokne ke liye mic dabayein"
                    else ->
                        "Ek baar mic dabayein, phir baat-cheet khud chalti rahegi"
                },
                color = if (backgroundRunning || voice.conversationMode) {
                    Color(0xFF69F0AE)
                } else {
                    Color(0xFF9E9E9E)
                },
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

                        Button(onClick = onToggleBackground) {
                Text(
                    if (backgroundRunning) {
                        "Background mode band karein"
                    } else {
                        "Background mode shuru karein"
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B3B52)
                )
            ) {
                Text("Chat par wapas")
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (lastHeard.isNotBlank()) {
                Text(
                    text = "Aap ne kaha: $lastHeard",
                    color = Color(0xFFB388FF),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (lastReply.isNotBlank()) {
                Text(
                    text = "Miral: $lastReply",
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
