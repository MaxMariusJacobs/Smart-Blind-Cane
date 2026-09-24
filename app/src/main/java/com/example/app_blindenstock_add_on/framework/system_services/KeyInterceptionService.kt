package com.example.app_blindenstock_add_on.framework.system_services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class KeyInterceptService : AccessibilityService() {

    private var isVolUpPressed = false
    private var isVolDownPressed = false
    private var lastTriggerTime = 0L

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val action = event.action

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> isVolUpPressed = (action == KeyEvent.ACTION_DOWN)
            KeyEvent.KEYCODE_VOLUME_DOWN -> isVolDownPressed = (action == KeyEvent.ACTION_DOWN)
            else -> return super.onKeyEvent(event)
        }

        // Wenn BEIDE Tasten gehalten werden
        if (isVolUpPressed && isVolDownPressed) {
            val now = System.currentTimeMillis()
            // 2 Sekunden Cooldown, damit Gemini nicht mehrfach triggert
            if (now - lastTriggerTime > 2000L) {
                lastTriggerTime = now
                sendBroadcast(Intent("com.example.app_blindenstock.TRIGGER_GEMINI"))
            }
            return true // 'true' schluckt das Event: Das Handy piept nicht und die Lautstärke ändert sich nicht!
        }

        // Wenn nur eine Taste gedrückt wird, lassen wir Android die Lautstärke normal ändern
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}