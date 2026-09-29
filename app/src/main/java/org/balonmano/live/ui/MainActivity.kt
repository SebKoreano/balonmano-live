package org.balonmano.live.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.*
import android.widget.*
import org.balonmano.live.data.CredentialsStore
import org.balonmano.live.data.StreamCredentials
import org.balonmano.live.domain.*
import org.balonmano.live.stream.*
import java.util.Locale

class MainActivity : Activity() {
    private var service: MatchService? = null
    private var binding = false
    private var visible = false
    private var surfaceReady = false
    private lateinit var camera: SurfaceView
    private lateinit var previewFrame: FrameLayout
    private lateinit var cameraMessage: TextView
    private lateinit var fallback: ImageView
    private lateinit var statusText: TextView
    private lateinit var streamButton: Button
    private lateinit var clock: TextView
    private lateinit var period: TextView
    private lateinit var clockButton: Button
    private lateinit var nextButton: Button
    private lateinit var exclusionsButton: Button
    private lateinit var youtubeButton: Button
    private val names = mutableListOf<TextView>()
    private val goals = mutableListOf<TextView>()
    private val timeoutButtons = mutableListOf<Button>()
    private val teamBars = mutableListOf<View>()
    private var lastFallback = ""
    private var lastUi = ""
    private var lastFallbackBitmap: android.graphics.Bitmap? = null
    private val renderer = ScoreboardRenderer()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as MatchService.LocalBinder).service
            service?.listener = { render() }
            render(); attachCamera()
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; render() }
    }
    private val s get() = service?.match ?: MatchState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildScreen()
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
            window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        if (Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { handleBack() }
    }
    override fun onStart() {
        super.onStart(); visible = true
        binding = bindService(Intent(this, MatchService::class.java), connection, Context.BIND_AUTO_CREATE)
    }
    override fun onResume() { super.onResume(); attachCamera() }
    override fun onStop() {
        visible = false
        service?.listener = null
        service?.stream?.detach()
        if (binding) { unbindService(connection); binding = false }
        service = null
        super.onStop()
    }
    override fun onDestroy() {
        fallback.setImageDrawable(null)
        lastFallbackBitmap?.recycle()
        super.onDestroy()
    }
    // Android 13+ uses the callback registered above; retain this path for Android 8–12.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Compatibility for Android 8–12")
    override fun onBackPressed() {
        handleBack()
    }
    private fun handleBack() {
        if (service?.stream?.desired == true) confirm("Salir de la app", "La transmisión seguirá activa. Puedes volver desde la notificación.") { moveTaskToBack(true) }
        else finish()
    }

    private fun buildScreen() {
        val root = column().apply { setBackgroundColor(BG); setPadding(dp(14), dp(8), dp(14), dp(8)) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(dp(14) + bars.left, dp(8) + bars.top, dp(14) + bars.right, dp(8) + bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(dp(14) + insets.systemWindowInsetLeft, dp(8) + insets.systemWindowInsetTop,
                    dp(14) + insets.systemWindowInsetRight, dp(8) + insets.systemWindowInsetBottom)
            }
            insets
        }
        val header = row()
        val brand = column().apply {
            addView(label("BALONMANO LIVE", 17f, MINT))
            addView(label("TU CANCHA, EN DIRECTO", 9f, MUTED))
        }
        header.addView(brand, LinearLayout.LayoutParams(0, dp(40), 1f))
        header.addView(button("Partido", PANEL) { configureMatch() }, LinearLayout.LayoutParams(dp(84), dp(40)))
        header.addView(button("Guía", PANEL) { help() }, LinearLayout.LayoutParams(dp(65), dp(40)))
        root.addView(header)

        val body = row().apply { gravity = Gravity.TOP; setPadding(0, dp(7), 0, 0) }
        val left = column().apply { setPadding(0, 0, dp(10), 0) }
        val stage = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewFrame = FrameLayout(this)
        stage.addView(previewFrame, FrameLayout.LayoutParams(1, 1, Gravity.CENTER))
        stage.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val width = r - l; val height = b - t
            if (width > 0 && height > 0) {
                val fitWidth = minOf(width, height * 16 / 9)
                val fitHeight = fitWidth * 9 / 16
                val old = previewFrame.layoutParams
                if (old.width != fitWidth || old.height != fitHeight) previewFrame.layoutParams = FrameLayout.LayoutParams(fitWidth, fitHeight, Gravity.CENTER)
            }
        }
        camera = SurfaceView(this)
        previewFrame.addView(camera, FrameLayout.LayoutParams(-1, -1))
        fallback = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_START }
        previewFrame.addView(fallback, FrameLayout.LayoutParams(1, 1))
        previewFrame.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val w = r - l; val h = b - t
            fallback.layoutParams = FrameLayout.LayoutParams((w * 560 / 1280f).toInt().coerceAtLeast(1), (h * 272 / 720f).toInt().coerceAtLeast(1)).apply {
                leftMargin = (w * .02f).toInt(); topMargin = (h * .03f).toInt()
            }
        }
        cameraMessage = label("Activa la cámara para preparar tu directo", 14f, Color.WHITE).apply {
            gravity = Gravity.CENTER; setPadding(dp(22), dp(16), dp(22), dp(16)); setOnClickListener { enableCamera() }
        }
        previewFrame.addView(cameraMessage, FrameLayout.LayoutParams(-1, -1))
        val scale = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean { service?.stream?.zoom(detector.scaleFactor); return true }
        })
        camera.setOnTouchListener { view, event -> scale.onTouchEvent(event); if (event.action == MotionEvent.ACTION_UP) view.performClick(); true }
        camera.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { surfaceReady = true; attachCamera() }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { service?.stream?.resize(width, height) }
            override fun surfaceDestroyed(holder: SurfaceHolder) { surfaceReady = false; service?.stream?.detach() }
        })
        left.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))
        statusText = label("Vista previa · sin transmitir", 11f, MUTED).apply { setPadding(dp(2), dp(5), 0, dp(5)); maxLines = 2 }
        left.addView(statusText, LinearLayout.LayoutParams(-1, dp(34)))
        val actions = row()
        youtubeButton = button("YouTube", PANEL) { configureYouTube() }
        streamButton = button("Transmitir", MINT, BG) { toggleBroadcast() }
        actions.addView(youtubeButton, LinearLayout.LayoutParams(0, dp(44), .8f))
        actions.addView(streamButton, LinearLayout.LayoutParams(0, dp(44), 1.2f))
        left.addView(actions)
        body.addView(left, LinearLayout.LayoutParams(0, -1, 1f))

        val controls = column().apply { setPadding(dp(10), dp(6), dp(10), dp(6)); background = shape(PANEL, 14f) }
        val clockRow = row()
        period = label("1T · PAUSA", 11f, MINT)
        clock = label("00:00", 35f).apply { typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD); gravity = Gravity.END; setOnClickListener { correctClock() } }
        clockRow.addView(period, LinearLayout.LayoutParams(0, dp(44), .9f))
        clockRow.addView(clock, LinearLayout.LayoutParams(0, dp(44), 1.1f))
        controls.addView(clockRow)
        val timeActions = row()
        clockButton = button("Iniciar reloj", MINT, BG) { handleClock() }
        nextButton = button("Siguiente", SUBTLE) { nextPeriod() }
        timeActions.addView(clockButton, LinearLayout.LayoutParams(0, dp(42), 1.3f))
        timeActions.addView(nextButton, LinearLayout.LayoutParams(0, dp(42), 1f))
        controls.addView(timeActions)
        repeat(2) { team ->
            val group = column().apply { setPadding(0, dp(5), 0, dp(2)) }
            val nameRow = row()
            val bar = View(this).apply { setBackgroundColor(s.teams[team].color) }
            teamBars.add(bar); nameRow.addView(bar, LinearLayout.LayoutParams(dp(4), dp(15)))
            val title = label(s.teams[team].name, 12f).apply { setPadding(dp(7), 0, 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
            names.add(title); nameRow.addView(title, LinearLayout.LayoutParams(0, dp(24), 1f))
            group.addView(nameRow)
            val scoreRow = row()
            val minus = button("−", SUBTLE) { action(MatchAction.Goal(team, -1)) }.apply { contentDescription = "Restar gol al equipo ${team + 1}"; textSize = 22f }
            val value = label("0", 32f).apply { gravity = Gravity.CENTER }
            goals.add(value)
            val plus = button("+ GOL", MINT, BG) { action(MatchAction.Goal(team, 1)) }.apply { contentDescription = "Sumar gol al equipo ${team + 1}" }
            scoreRow.addView(minus, LinearLayout.LayoutParams(dp(45), dp(47)))
            scoreRow.addView(value, LinearLayout.LayoutParams(0, dp(47), 1f))
            scoreRow.addView(plus, LinearLayout.LayoutParams(dp(101), dp(47)))
            group.addView(scoreRow)
            val extras = row()
            val exclusion = button("+ 2 min", SUBTLE) { addExclusion(team) }.apply { textSize = 12f }
            val timeout = button("T. muerto · 0", SUBTLE) { startTimeout(team) }.apply {
                textSize = 12f; setOnLongClickListener { correctTimeouts(team); true }
            }
            timeoutButtons.add(timeout)
            extras.addView(exclusion, LinearLayout.LayoutParams(0, dp(38), 1f)); extras.addView(timeout, LinearLayout.LayoutParams(0, dp(38), 1.3f))
            group.addView(extras); controls.addView(group)
        }
        exclusionsButton = button("Gestionar exclusiones · 0", SUBTLE) { manageExclusions() }.apply { textSize = 12f }
        controls.addView(exclusionsButton, LinearLayout.LayoutParams(-1, dp(39)))
        val scroll = ScrollView(this).apply { isFillViewport = false; addView(controls) }
        val panelWidth = minOf(294, (resources.configuration.screenWidthDp * .4f).toInt().coerceAtLeast(242))
        body.addView(scroll, LinearLayout.LayoutParams(dp(panelWidth), -1))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        render()
    }

    @SuppressLint("SetTextI18n") // Spanish-only scoreboard: stable ASCII match times and counts.
    private fun render() {
        val state = s
        val streamState = service?.stream?.status ?: StreamStatus()
        val signature = "${state.teams}|${state.period}|${clockText(state.totalElapsedMs)}|${state.running}|${state.periodEnded}|" +
            "${state.exclusions.map { "${it.id}:${it.shirt}:${clockText(it.remainingMs, true)}" }}|${state.timeout?.team}:${state.timeout?.running}:${state.timeout?.let { clockText(it.remainingMs, true) }}|" +
            "${streamState.phase}:${streamState.message}:${streamState.bitrate / 100_000}|${service?.storageWarning}|${service != null}|${permissions()}"
        if (signature == lastUi) return
        lastUi = signature
        clock.text = clockText(state.totalElapsedMs)
        val timeout = state.timeout
        period.text = state.periodLabel + " · " + when { timeout != null -> "TM ${clockText(timeout.remainingMs, true)}"; state.periodEnded -> "FINAL"; state.running -> "EN JUEGO"; else -> "PAUSA" }
        clockButton.text = when { timeout != null && !timeout.running -> "Continuar TM"; timeout != null -> "Terminar TM"; state.running -> "Pausar reloj"; else -> "Iniciar reloj" }
        clockButton.isEnabled = service != null && (!state.periodEnded || timeout != null)
        nextButton.text = if (state.period + 1 >= state.periods.size) "+ Prórroga" else "2.º tiempo"
        nextButton.isEnabled = service != null && state.periodEnded && timeout == null
        nextButton.alpha = if (nextButton.isEnabled) 1f else .42f
        clockButton.alpha = if (clockButton.isEnabled) 1f else .42f
        state.teams.forEachIndexed { i, t -> if (names.size > i) {
            names[i].text = t.name; goals[i].text = t.goals.toString(); teamBars[i].setBackgroundColor(t.color)
            timeoutButtons[i].text = "T. muerto · ${t.timeouts}"
        } }
        exclusionsButton.text = "Gestionar exclusiones · ${state.exclusions.size}"
        val status = service?.stream?.status ?: StreamStatus()
        val active = service?.stream?.desired == true
        streamButton.text = if (active) "Detener envío" else "Transmitir"
        streamButton.background = shape(if (active) ORANGE else MINT, 9f)
        youtubeButton.isEnabled = !active
        val rate = if (status.bitrate > 0) " · ${String.format(Locale.ROOT, "%.1f", status.bitrate / 1_000_000f)} Mb/s" else ""
        statusText.text = service?.storageWarning ?: (status.message + rate)
        statusText.setTextColor(if (status.phase in listOf(StreamPhase.ERROR, StreamPhase.RETRYING) || service?.storageWarning != null) ORANGE else MUTED)
        val hasPreview = permissions() && status.phase !in listOf(StreamPhase.IDLE, StreamPhase.ERROR)
        cameraMessage.visibility = if (hasPreview) View.GONE else View.VISIBLE
        cameraMessage.text = if (status.phase == StreamPhase.ERROR) "Cámara no disponible\nToca para reintentar" else "Activar cámara\nToca aquí para preparar tu directo"
        fallback.visibility = if (hasPreview) View.GONE else View.VISIBLE
        if (!hasPreview) {
            val signature = state.toString()
            if (signature != lastFallback) {
                val image = renderer.render(state); fallback.setImageBitmap(image)
                lastFallbackBitmap?.recycle(); lastFallbackBitmap = image; lastFallback = signature
            }
        }
    }

    private fun permissions() = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    private fun enableCamera() {
        if (permissions()) attachCamera()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 10)
    }
    private fun attachCamera() { if (visible && surfaceReady && permissions()) service?.stream?.attach(camera) }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10) {
            if (permissions()) attachCamera()
            else AlertDialog.Builder(this).setTitle("Permisos de cámara y micrófono")
                .setMessage("Puedes usar el marcador. Para transmitir, activa cámara y micrófono en los permisos de la app.")
                .setPositiveButton("Abrir ajustes") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                .setNegativeButton("Ahora no", null).show()
        }
    }
    private fun toggleBroadcast() {
        if (service?.stream?.desired == true) {
            confirm("Detener la transmisión", "Se dejará de enviar video a YouTube. El marcador se conservará.") { service?.stopBroadcast() }
            return
        }
        if (!permissions()) { enableCamera(); return }
        val credentials = runCatching { CredentialsStore(this).load() }.getOrNull()
        if (credentials == null) { configureYouTube(); return }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        confirm("Transmitir a YouTube", "Se enviarán cámara, sonido y marcador al directo configurado. Revisa su privacidad en YouTube Studio.") {
            startForegroundService(Intent(this, MatchService::class.java).setAction(MatchService.START))
        }
    }
    private fun handleClock() {
        val timeout = s.timeout
        when {
            timeout != null && !timeout.running -> action(MatchAction.ResumeTimeout)
            timeout != null -> action(MatchAction.EndTimeout)
            else -> action(MatchAction.ToggleClock)
        }
    }
    private fun nextPeriod() {
        val title = if (s.period + 1 >= s.periods.size) "Añadir prórroga" else "Pasar al segundo tiempo"
        confirm(title, "Se conservarán goles y exclusiones. El reloj comenzará en pausa.") { action(MatchAction.NextPeriod) }
    }
    private fun action(value: MatchAction) {
        val bound = service ?: return toast("Espera a que termine de abrir la app.")
        try { bound.dispatch(value) } catch (e: IllegalArgumentException) { toast(e.message ?: "Revisa los datos.") }
    }
    private fun correctClock() {
        val state = s
        val form = form()
        form.addView(label("Reloj acumulado · entre ${clockText(state.periodStartMs)} y ${clockText(state.periodStartMs + state.periods[state.period])}", 13f, MUTED))
        val input = field(form, "Minutos:segundos", clockText(state.totalElapsedMs), InputType.TYPE_CLASS_DATETIME)
        form.addView(label("Al guardar se pausa el reloj. Las exclusiones conservan su tiempo pendiente.", 12f, MUTED))
        editDialog("Corregir reloj", form) { service?.dispatch(MatchAction.CorrectClock(parseTime(input.text.toString()))) }
    }
    private fun addExclusion(team: Int) {
        val form = form()
        val input = field(form, "Dorsal (opcional)", "", InputType.TYPE_CLASS_NUMBER, 3)
        form.addView(label("Se añaden 2 minutos de tiempo de juego a ${s.teams[team].name}.", 13f, MUTED))
        editDialog("Añadir exclusión", form, "Añadir") { service?.dispatch(MatchAction.AddExclusion(team, input.text.toString().trim())) }
    }
    private fun manageExclusions() {
        val current = s.exclusions
        if (current.isEmpty()) { toast("No hay exclusiones activas."); return }
        val rows = current.map { "${s.teams[it.team].name} · ${if (it.shirt.isBlank()) "sin dorsal" else "#${it.shirt}"} · ${clockText(it.remainingMs, true)}" }
        AlertDialog.Builder(this).setTitle("Exclusiones activas").setItems(rows.toTypedArray()) { _, index -> editExclusion(current[index].id) }
            .setNegativeButton("Cerrar", null).show()
    }
    private fun editExclusion(id: Long) {
        val exclusion = s.exclusions.firstOrNull { it.id == id } ?: return
        val form = form()
        val shirt = field(form, "Dorsal (opcional)", exclusion.shirt, InputType.TYPE_CLASS_NUMBER, 3)
        val time = field(form, "Tiempo pendiente (mm:ss)", clockText(exclusion.remainingMs, true), InputType.TYPE_CLASS_DATETIME)
        form.addView(label("El tiempo sigue corriendo mientras editas. Guardar sustituye el tiempo pendiente.", 12f, MUTED))
        editDialog("Corregir exclusión", form, neutral = "Eliminar", onNeutral = { action(MatchAction.RemoveExclusion(id)) }) {
            require(s.exclusions.any { it.id == id }) { "La exclusión ya terminó." }
            service?.dispatch(MatchAction.EditExclusion(id, parseTime(time.text.toString()), shirt.text.toString().trim()))
        }
    }
    private fun startTimeout(team: Int) {
        if (s.timeout != null) { toast("Ya hay un tiempo muerto activo."); return }
        confirm("Tiempo muerto · ${s.teams[team].name}", "Pausar el partido y empezar ${s.timeoutDurationMs / 1000} segundos. La reanudación será manual.") { action(MatchAction.StartTimeout(team)) }
    }
    private fun correctTimeouts(team: Int) {
        AlertDialog.Builder(this).setTitle("Tiempos muertos · ${s.teams[team].name}")
            .setItems(arrayOf("Sumar uno al contador", "Restar uno al contador")) { _, i -> action(MatchAction.CorrectTimeoutCount(team, if (i == 0) 1 else -1)) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun configureMatch() {
        val state = s
        val form = form()
        val home = field(form, "Equipo local", state.teams[0].name, maxLength = 24)
        val homeColor = field(form, "Color local (#RRGGBB)", hex(state.teams[0].color), maxLength = 7)
        val away = field(form, "Equipo visitante", state.teams[1].name, maxLength = 24)
        val awayColor = field(form, "Color visitante (#RRGGBB)", hex(state.teams[1].color), maxLength = 7)
        val duration = field(form, "Minutos por tiempo (antes de empezar)", (state.periods[0] / 60_000).toString(), InputType.TYPE_CLASS_NUMBER)
        duration.isEnabled = state.period == 0 && state.elapsedMs == 0L && !state.running && state.timeout == null
        val timeout = field(form, "Segundos de tiempo muerto", (state.timeoutDurationMs / 1000).toString(), InputType.TYPE_CLASS_NUMBER)
        val extra = field(form, "Minutos por período de prórroga", (state.extraDurationMs / 60_000).toString(), InputType.TYPE_CLASS_NUMBER)
        form.addView(button("Corregir tiempos muertos", SUBTLE) {
            AlertDialog.Builder(this).setItems(s.teams.map { it.name }.toTypedArray()) { _, index -> correctTimeouts(index) }.show()
        }, LinearLayout.LayoutParams(-1, dp(45)))
        editDialog("Preparar partido", form, neutral = "Nuevo partido", onNeutral = {
            confirm("Borrar el partido actual", "Se pondrán a cero goles, reloj y tiempos muertos, y se eliminarán las exclusiones. Se conservarán los equipos y YouTube.") { action(MatchAction.Reset) }
        }) {
            service?.dispatch(MatchAction.Configure(listOf(home.text.toString().trim(), away.text.toString().trim()),
                listOf(parseColor(homeColor.text.toString()), parseColor(awayColor.text.toString())),
                number(duration, 1..120) * 60_000L, number(timeout, 1..600) * 1000L, number(extra, 1..120) * 60_000L))
        }
    }
    private fun configureYouTube() {
        if (service?.stream?.desired == true) { toast("Detén el envío antes de cambiar YouTube."); return }
        val store = CredentialsStore(this)
        val credentials = runCatching { store.load() }.getOrNull()
        val form = form()
        form.addView(label("Copia estos datos desde YouTube Studio → Emitir en directo. La app los guarda cifrados en este teléfono.", 13f, MUTED))
        val server = field(form, "Servidor de emisión", credentials?.server ?: YouTubeEndpoint.DEFAULT_SERVER, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val key = field(form, "Clave de transmisión", credentials?.key ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, 200)
        key.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        form.addView(button("Abrir YouTube Studio", SUBTLE) { openStudio() })
        editDialog("Conectar YouTube", form, secure = true, neutral = "Borrar conexión", onNeutral = { store.clear(); toast("Conexión eliminada.") }) {
            val normalized = YouTubeEndpoint.server(server.text.toString())
            YouTubeEndpoint.endpoint(normalized, key.text.toString())
            store.save(StreamCredentials(normalized, key.text.toString().trim()))
            toast("Conexión guardada en este teléfono.")
        }
    }
    private fun help() {
        AlertDialog.Builder(this).setTitle("Tu primer directo")
            .setMessage("1. Prepara una emisión en YouTube Studio. Elige título y privacidad; activa inicio automático si está disponible.\n\n2. Copia servidor y clave en el botón YouTube de esta app.\n\n3. Configura equipos y duraciones en Partido. Activa la cámara y toca Transmitir.\n\n4. Inicia el reloj cuando empiece el juego. Toca el reloj para corregirlo; pellizca la imagen para ajustar zoom. Mantén pulsado T. muerto para corregir su contador.\n\n5. Al terminar, detén el envío y comprueba que el directo terminó en YouTube Studio.\n\n“Enviando a YouTube” confirma la conexión del video; revisa la publicación del directo en YouTube. Haz una prueba privada antes del partido.\n\nBalonmano Live 1.0.0 · Sin suscripción. Requiere internet. Cámara, sonido y marcador se envían directamente a YouTube.")
            .setPositiveButton("Entendido", null).setNeutralButton("YouTube Studio") { _, _ -> openStudio() }.show()
    }
    private fun openStudio() { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://studio.youtube.com"))) }.onFailure { toast("Abre studio.youtube.com en el navegador.") } }

    private fun editDialog(title: String, content: View, positive: String = "Guardar", secure: Boolean = false,
        neutral: String? = null, onNeutral: (() -> Unit)? = null, save: () -> Unit): AlertDialog {
        val scroll = ScrollView(this).apply { addView(content) }
        val builder = AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton(positive, null).setNegativeButton("Cancelar", null)
        if (neutral != null) builder.setNeutralButton(neutral) { _, _ -> onNeutral?.invoke() }
        val dialog = builder.create()
        if (secure) dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try { save(); dialog.dismiss() } catch (e: IllegalArgumentException) { toast(e.message ?: "Revisa los datos.") }
                catch (_: Exception) { toast("No se pudo guardar. Revisa el espacio y vuelve a intentarlo.") }
            }
        }
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }
    private fun confirm(title: String, message: String, run: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Confirmar") { _, _ -> run() }.setNegativeButton("Cancelar", null).show()
    }
    private fun form() = column().apply { setPadding(dp(22), dp(8), dp(22), dp(14)) }
    private fun field(parent: LinearLayout, title: String, value: String, input: Int = InputType.TYPE_CLASS_TEXT, maxLength: Int = 100): EditText {
        parent.addView(label(title, 12f, MINT).apply { setPadding(0, dp(10), 0, 0) })
        return EditText(this).apply {
            inputType = input; setSingleLine(); setText(value); setTextColor(Color.WHITE); textSize = 16f
            filters = arrayOf(InputFilter.LengthFilter(maxLength)); setSelectAllOnFocus(true)
            parent.addView(this, LinearLayout.LayoutParams(-1, dp(48)))
        }
    }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun label(text: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private fun button(text: String, color: Int, foreground: Int = Color.WHITE, run: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 13f; isAllCaps = false; setTextColor(foreground); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = shape(color, 9f); minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        setPadding(dp(6), 0, dp(6), 0); stateListAnimator = null
        setOnClickListener { run() }
    }
    private fun shape(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), BG) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
    private fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    private fun hex(color: Int) = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)
    private fun parseColor(text: String): Int {
        require(text.trim().matches(Regex("#[0-9A-Fa-f]{6}"))) { "Usa un color como #369EE8." }
        return Color.parseColor(text.trim())
    }
    private fun number(input: EditText, range: IntRange): Int {
        val value = input.text.toString().toIntOrNull()
        require(value != null && value in range) { "Introduce un valor entre ${range.first} y ${range.last}." }
        return value
    }
    private fun parseTime(input: String): Long {
        val parts = input.trim().split(':')
        require(parts.size == 2) { "Usa el formato minutos:segundos, por ejemplo 12:34." }
        val minutes = parts[0].toLongOrNull(); val seconds = parts[1].toLongOrNull()
        require(minutes != null && minutes in 0..120_000 && seconds != null && seconds in 0..59) { "Revisa minutos y segundos." }
        return minutes * 60_000 + seconds * 1000
    }
    companion object {
        private val BG = 0xFF101817.toInt(); private val PANEL = 0xFF1A2824.toInt(); private val SUBTLE = 0xFF2A3D35.toInt()
        private val MINT = 0xFF83F3C4.toInt(); private val ORANGE = 0xFFFFB86B.toInt(); private val MUTED = 0xFFBACAC1.toInt()
    }
}
