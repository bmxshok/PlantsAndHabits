package com.example.plantsandhabits

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "reminders_channel"
        private const val CHANNEL_NAME = "Напоминания о растениях"
        private const val CHANNEL_DESCRIPTION = "Уведомления о необходимости ухода за растениями"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESCRIPTION
                    enableVibration(true)
                    enableLights(true)
                }

                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    fun showReminderNotification(
        reminderId: Int,
        plantName: String,
        workType: String
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Генерируем интересные тексты в зависимости от типа работы
        val (title, text) = when (workType.lowercase()) {
            "полив" -> Pair(
                "💧 Время полива!",
                "$plantName ждёт вашего внимания. Пора полить растение!"
            )
            "пересадка" -> Pair(
                "🌱 Пора пересадить",
                "$plantName выросло и готово к новому дому. Время пересадки!"
            )
            "удобрение" -> Pair(
                "🌿 Время подкормки",
                "$plantName нуждается в питательных веществах. Добавьте удобрения!"
            )
            else -> Pair(
                "🌳 Уход за растением",
                "Пора $workType для $plantName. Ваше растение будет благодарно!"
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = NotificationManagerCompat.from(context)
        
        // Проверяем разрешение на показ уведомлений (требуется для Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(reminderId, notification)
            } else {
                android.util.Log.w("NotificationHelper", "Permission POST_NOTIFICATIONS not granted")
            }
        } else {
            // Для версий ниже Android 13 разрешение не требуется
            notificationManager.notify(reminderId, notification)
        }
    }
}

