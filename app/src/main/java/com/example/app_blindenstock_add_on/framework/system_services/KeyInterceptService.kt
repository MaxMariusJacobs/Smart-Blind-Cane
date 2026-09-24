package com.example.app_blindenstock_add_on.framework.system_services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class KeyInterceptService : AccessibilityService() {

    private var lastVolDirection = 0
    private var lastVolTime = 0L

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Wir werten nur das Herunterdrücken der Tasten aus
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {

                // Verhindert, dass Dauerfeuer entsteht, wenn man die Taste gedrückt hält
                if (event.repeatCount == 0) {
                    val direction = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1
                    val now = System.currentTimeMillis()

                    // Zick-Zack-Prüfung: Gegensätzliche Taste innerhalb von 800ms?
                    if (now - lastVolTime < 800L && direction != lastVolDirection) {
                        // Treffer! Zick-Zack erkannt.
                        lastVolTime = 0L // Reset

                        // Expliziter Intent an unseren Foreground Service (wichtig für Android 14 Sicherheit)
                        val intent = Intent("com.example.app_blindenstock.TRIGGER_GEMINI")
                        intent.setPackage(packageName)
                        sendBroadcast(intent)

                        // Wir schlucken diesen zweiten Tastendruck, damit das Handy nicht piept
                        return true
                    } else {
                        // Das war der erste Tastendruck der Sequenz.
                        lastVolDirection = direction
                        lastVolTime = now
                    }
                }
            }
        }

        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}