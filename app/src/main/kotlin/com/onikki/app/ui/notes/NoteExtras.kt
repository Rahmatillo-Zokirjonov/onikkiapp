package com.onikki.app.ui.notes

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.CancellationSignal
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.onikki.app.data.db.entity.NoteAttachment
import com.onikki.app.data.local.NoteMedia
import com.onikki.app.domain.notes.NoteFormat
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiColorTokens
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

// ---------------------------------------------------------------- lock

fun isDeviceSecure(context: Context): Boolean =
    context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

/**
 * Asks for the phone's own fingerprint / face / PIN before a locked note opens. Uses the system
 * prompt on Android 10+, the "confirm your PIN" screen on older phones.
 */
@Composable
fun rememberNoteUnlocker(): (reason: String, onSuccess: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) pending?.invoke()
        pending = null
    }
    return remember(context) {
        { reason: String, onSuccess: () -> Unit ->
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            if (keyguard == null || !keyguard.isDeviceSecure) {
                Toast.makeText(context, "Telefonda ekran qulfi yo'q — maxfiy qaydlar himoyasiz", Toast.LENGTH_LONG).show()
                onSuccess()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val builder = BiometricPrompt.Builder(context).setTitle("Maxfiy qayd").setSubtitle(reason)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                } else {
                    @Suppress("DEPRECATION")
                    builder.setDeviceCredentialAllowed(true)
                }
                builder.build().authenticate(
                    CancellationSignal(),
                    context.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                val intent = keyguard.createConfirmDeviceCredentialIntent("Maxfiy qayd", reason)
                if (intent == null) onSuccess() else {
                    pending = onSuccess
                    legacy.launch(intent)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- formatting

/** Styles **qalin**, *kursiv*, ~~o'chirilgan~~, # sarlavha… live, keeping the markers (dimmed) so offsets never shift. */
class MarkdownTransformation(private val colors: OnIkkiColorTokens) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        NoteFormat.spans(text.text).forEach { span ->
            val style = when (span.style) {
                NoteFormat.Style.H1 -> SpanStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                NoteFormat.Style.H2 -> SpanStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.accent)
                NoteFormat.Style.BULLET -> SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold)
                NoteFormat.Style.QUOTE -> SpanStyle(fontStyle = FontStyle.Italic, color = colors.text.muted(0.75f))
                NoteFormat.Style.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                NoteFormat.Style.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                NoteFormat.Style.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough, color = colors.text.muted(0.55f))
                NoteFormat.Style.MARKER -> SpanStyle(color = colors.text.muted(0.3f))
            }
            builder.addStyle(style, span.start, span.end)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

// ---------------------------------------------------------------- images

private val thumbCache = LruCache<String, ImageBitmap>(40)

@Composable
fun NoteImage(name: String, modifier: Modifier, maxPx: Int = 480, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val colors = LocalOnIkkiColors.current
    val key = "$name@$maxPx"
    val bitmap by produceState(initialValue = thumbCache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { NoteMedia.loadBitmap(context, name, maxPx)?.asImageBitmap() }?.also { thumbCache.put(key, it) }
        }
    }
    val b = bitmap
    if (b != null) {
        Image(bitmap = b, contentDescription = null, contentScale = contentScale, modifier = modifier)
    } else {
        Box(modifier = modifier.background(colors.neutral800))
    }
}

@Composable
fun ImageViewer(name: String, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            NoteImage(name, Modifier.fillMaxSize(), maxPx = 2000, contentScale = ContentScale.Fit)
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OnIkkiButton(text = "Yopish", onClick = onDismiss, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                Text(
                    text = "O'chirish",
                    color = colors.warmAccent,
                    fontSize = 14.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.clickable(onClick = onDelete).padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- audio

private fun mmss(ms: Long): String = "%d:%02d".format(ms / 60_000, (ms / 1000) % 60)

@Composable
fun AudioAttachmentRow(attachment: NoteAttachment, onDelete: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalOnIkkiColors.current
    val file = remember(attachment.fileName) { NoteMedia.file(context, attachment.fileName) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    val duration = attachment.durationMs ?: 0L
    DisposableEffect(attachment.fileName) {
        onDispose { player?.release(); player = null }
    }
    LaunchedEffect(playing) {
        while (playing) {
            position = player?.currentPosition?.toLong() ?: 0L
            delay(200)
        }
    }
    fun toggle() {
        val p = player ?: runCatching {
            MediaPlayer().apply {
                setDataSource(file.path)
                prepare()
                setOnCompletionListener { playing = false; position = 0L }
            }
        }.getOrNull()?.also { player = it }
        if (p == null) {
            Toast.makeText(context, "Yozuvni ochib bo'lmadi", Toast.LENGTH_SHORT).show()
            return
        }
        if (p.isPlaying) { p.pause(); playing = false } else { p.start(); playing = true }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(colors.accent).clickable(onClick = ::toggle),
            contentAlignment = Alignment.Center
        ) { Text(text = if (playing) "❚❚" else "▶", color = colors.onAccent, fontSize = 13.sp) }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            LinearProgressIndicator(
                progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                color = colors.accent,
                trackColor = colors.neutral800,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "🎙 ${mmss(if (playing || position > 0) position else duration)}${if (playing || position > 0) " / ${mmss(duration)}" else ""}",
                color = colors.text.muted(0.6f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Text(text = "×", color = colors.text.muted(0.4f), fontSize = 18.sp, modifier = Modifier.clickable(onClick = onDelete).padding(6.dp))
    }
}

/** Records a voice note; [onDone] gets the stored file name and its length. */
@Composable
fun AudioRecorderSheet(onDone: (fileName: String, durationMs: Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalOnIkkiColors.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    fun start() {
        val target = NoteMedia.newAudioFile(context)
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        val ok = runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(target.path)
            r.prepare()
            r.start()
        }.isSuccess
        if (!ok) {
            r.release()
            Toast.makeText(context, "Mikrofonni ishga tushirib bo'lmadi", Toast.LENGTH_SHORT).show()
            return
        }
        recorder = r
        file = target
        startedAt = System.currentTimeMillis()
    }

    fun stop(save: Boolean) {
        val r = recorder
        val f = file
        val length = System.currentTimeMillis() - startedAt
        val stopped = r != null && runCatching { r.stop() }.isSuccess
        r?.release()
        recorder = null
        if (save && stopped && f != null && length >= 800) onDone(f.name, length)
        else {
            f?.delete()
            if (save) Toast.makeText(context, "Yozuv juda qisqa", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
        file = null
    }

    LaunchedEffect(recorder) {
        while (recorder != null) {
            elapsed = System.currentTimeMillis() - startedAt
            delay(200)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            recorder?.let { runCatching { it.stop() }; it.release() }
            if (recorder != null) file?.delete()
        }
    }

    OnIkkiSheet(title = "🎙 Ovozli yozuv", onDismiss = { stop(save = false) }) {
        when {
            !granted -> {
                Text(text = "Ovoz yozish uchun mikrofonga ruxsat kerak.", color = colors.text.muted(0.7f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                OnIkkiButton(text = "Ruxsat berish", onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.fillMaxWidth())
            }
            recorder == null -> {
                Text(text = "Yozuv faqat telefoningizda saqlanadi.", color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                OnIkkiButton(text = "● Yozishni boshlash", onClick = ::start, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                Text(
                    text = "● ${mmss(elapsed)}",
                    color = colors.warmAccent,
                    fontSize = 30.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OnIkkiButton(text = "Bekor qilish", onClick = { stop(save = false) }, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                    OnIkkiButton(text = "■ To'xtatib saqlash", onClick = { stop(save = true) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ImageThumbRow(images: List<NoteAttachment>, size: Dp, onOpen: (NoteAttachment) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        images.forEach { img ->
            NoteImage(img.fileName, Modifier.size(size).clip(RoundedCornerShape(10.dp)).clickable { onOpen(img) })
        }
    }
}
