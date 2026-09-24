package com.example.yalarma

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val youtubeUrl = intent.getStringExtra("YOUTUBE_URL") ?: ""
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("YOUTUBE_URL", youtubeUrl)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}

class AlarmService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var webView: WebView? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val youtubeUrl = intent?.getStringExtra("YOUTUBE_URL") ?: "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val videoId = extractYouTubeId(youtubeUrl)

        // Probuzení CPU ze spánku
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Yalarma::AlarmServiceWakeLock"
        ).apply { acquire(15 * 60 * 1000L) } // Udrží běh až 15 min

        createNotificationChannel()

        // Odpovídající Intent pro otevření MainActivity při kliknutí na notifikaci
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "ALARM_CHANNEL_ID")
            .setContentTitle("Budík Yalarma hraje!")
            .setContentText("Klepnutím otevřete aplikaci nebo zastavte budík.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(101, notification)

        // Spuštění WebView a načtení YouTube
        playYouTubeAudio(videoId)

        return START_NOT_STICKY
    }

    private fun playYouTubeAudio(videoId: String) {
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false // Obchází nutnost kliknutí uživatele
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    evaluateJavascript("javascript:playVideo()", null)
                }
            }

            val htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <script src="https://www.youtube.com/iframe_api"></script>
                    <script>
                        var player;
                        function onYouTubeIframeAPIReady() {
                            player = new YT.Player('player', {
                                height: '1',
                                width: '1',
                                videoId: '$videoId',
                                playerVars: {
                                    'autoplay': 1,
                                    'controls': 0,
                                    'playsinline': 1
                                },
                                events: {
                                    'onReady': function(e) { e.target.playVideo(); }
                                }
                            });
                        }
                        function playVideo() {
                            if (player && player.playVideo) { player.playVideo(); }
                        }
                    </script>
                </head>
                <body style="background:black;">
                    <div id="player"></div>
                </body>
                </html>
            """.trimIndent()

            loadDataWithBaseURL("https://www.youtube.com", htmlContent, "text/html", "UTF-8", null)
        }
    }

    private fun extractYouTubeId(url: String): String {
        val pattern = "(?:youtube\\.com/(?:[^/]+/.+/|(?:v|e(?:mbed)?)/|.*[?&]v=)|youtu\\.be/)([^\"&?/s]{11})"
        val regex = Regex(pattern)
        val match = regex.find(url)
        return match?.groupValues?.get(1) ?: "dQw4w9WgXcQ"
    }

    override fun onDestroy() {
        super.onDestroy()
        webView?.destroy()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}