package org.balonmano.live.stream

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import org.balonmano.live.R
import org.balonmano.live.data.CredentialsStore
import org.balonmano.live.data.MatchStore
import org.balonmano.live.domain.MatchAction
import org.balonmano.live.domain.MatchEngine
import org.balonmano.live.domain.MatchState
import org.balonmano.live.ui.MainActivity

class MatchService : Service() {
    inner class LocalBinder : Binder() { val service get() = this@MatchService }
    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: MatchStore
    private lateinit var engine: MatchEngine
    lateinit var stream: StreamController
        private set
    var listener: (() -> Unit)? = null
    var storageWarning: String? = null
        private set
    private var saved: MatchState? = null
    private var lastSave = 0L
    private var foreground = false
    private var lastNotification = ""
    val match get() = engine.state

    override fun onCreate() {
        super.onCreate()
        store = MatchStore(this) { handler.post { storageWarning = "No se pudo guardar o recuperar el partido. Revisa el espacio del teléfono."; listener?.invoke() } }
        engine = MatchEngine(store.load(), SystemClock::elapsedRealtime)
        stream = StreamController(this, { notifyUi() }, { leaveForeground() })
        handler.post(ticker)
    }
    override fun onBind(intent: Intent) = binder
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == START) startBroadcast()
        return START_NOT_STICKY
    }
    private fun startBroadcast() {
        if (stream.desired) return
        try {
            // The activity starts this service while visible, after camera/microphone permissions.
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Transmisión de partidos", NotificationManager.IMPORTANCE_LOW))
            val notification = notification("Preparando transmisión…")
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIFICATION, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTIFICATION, notification)
            foreground = true
            require(checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            val credentials = CredentialsStore(this).load() ?: error("Falta configurar YouTube")
            stream.updateMatch(match)
            stream.start(credentials)
        } catch (_: Exception) {
            storageWarning = "No se pudo iniciar. Revisa permisos y vuelve a guardar la configuración de YouTube."
            leaveForeground(); notifyUi()
        }
    }
    fun stopBroadcast() { stream.stop(); leaveForeground() }
    private fun leaveForeground() {
        if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false }
        stopSelf()
    }
    fun dispatch(action: MatchAction) {
        try { engine.dispatch(action) } finally { save(); stream.updateMatch(match); listener?.invoke() }
    }
    private fun save() {
        if (match != saved) { store.save(match); saved = match; lastSave = SystemClock.elapsedRealtime() }
    }
    private val ticker = object : Runnable {
        override fun run() {
            engine.tick()
            stream.updateMatch(match)
            if (SystemClock.elapsedRealtime() - lastSave >= 1000) save()
            listener?.invoke()
            handler.postDelayed(this, 100)
        }
    }
    private fun notifyUi() {
        if (foreground && lastNotification != stream.status.message) {
            lastNotification = stream.status.message
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(lastNotification))
        }
        listener?.invoke()
    }
    private fun notification(text: String): Notification = Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_app).setContentTitle("Balonmano Live").setContentText(text)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).build()
    override fun onUnbind(intent: Intent?): Boolean { if (!stream.desired) stopSelf(); return super.onUnbind(intent) }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        engine.tick(); save(); store.close()
        stream.release(); listener = null
        super.onDestroy()
    }
    companion object { const val START = "org.balonmano.live.START"; private const val CHANNEL = "broadcast"; private const val NOTIFICATION = 100 }
}
