package com.example.mobilki

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import android.widget.Button
import android.widget.TextView
import android.view.View
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import android.graphics.Color
import android.os.Build
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
class MainActivity : AppCompatActivity() {

    private var firstValue: Double = 0.0
    private var currentOperation: String? = null
    private var isNewOperation: Boolean = true
    private lateinit var tvDisplay: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mainLayout = findViewById<View>(R.id.mainLayout)
        mainLayout.visibility = View.GONE // Скрываем калькулятор до авторизации

        tvDisplay = findViewById(R.id.tvDisplay)

        FirebaseAnalytics.getInstance(this)

        // Добавляем обработку длинного нажатия для копирования
        tvDisplay.setOnLongClickListener {
            copyToClipboard()
            true // true означает, что событие обработано и обычный клик не сработает
        }

        // Список ID всех цифровых кнопок
        val numericButtons = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnDot
        )

        for (id in numericButtons) {
            findViewById<Button>(id).setOnClickListener { onNumberClick(it as Button) }
        }

        // Операции
        findViewById<Button>(R.id.btnSum).setOnClickListener { onOperationClick("+") }
        findViewById<Button>(R.id.btnSub).setOnClickListener { onOperationClick("-") }
        findViewById<Button>(R.id.btnMul).setOnClickListener { onOperationClick("*") }
        findViewById<Button>(R.id.btnDiv).setOnClickListener { onOperationClick("/") }
        findViewById<Button>(R.id.btnPlusMinus).setOnClickListener { onPlusMinusClick() }
        findViewById<Button>(R.id.btnPercent).setOnClickListener { onPercentClick() }
        // Равно и Очистка
        findViewById<Button>(R.id.btnEqual).setOnClickListener { calculateResult() }
        findViewById<Button>(R.id.btnAC).setOnClickListener {
            tvDisplay.text = "0"
            firstValue = 0.0
            currentOperation = null
            isNewOperation = true
        }
        findViewById<Button>(R.id.btnAC).setOnLongClickListener {
            showSetupPassKeyDialog { newPin ->
                savePassKey(newPin)
                Toast.makeText(this, "Код изменен", Toast.LENGTH_SHORT).show()
            }
            true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        fetchRemoteTheme()

        val passKey = getPassKey()
        if (passKey == null) {
            // Шаг 3: Инициализация (Первый запуск)
            showSetupPassKeyDialog { newPin ->
                savePassKey(newPin)
                mainLayout.visibility = View.VISIBLE
            }
        } else {
            // Шаг 4: Проверка существующего ключа
            showBiometricPrompt {
                mainLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun getSecurePrefs() = EncryptedSharedPreferences.create(
        "secure_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        this,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun savePassKey(pin: String) {
        getSecurePrefs().edit().putString("pass_key", pin).apply()
    }

    private fun getPassKey(): String? {
        return getSecurePrefs().getString("pass_key", null)
    }

    private fun showSetupPassKeyDialog(onSuccess: (String) -> Unit) {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Установка Pass Key")
            .setMessage("Придумайте цифровой код доступа")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Сохранить") { _, _ ->
                val pin = input.text.toString()
                if (pin.length >= 4) onSuccess(pin)
                else Toast.makeText(this, "Минимум 4 цифры", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    runOnUiThread { onSuccess() }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Если биометрия отклонена (например, нет пальца), просим PIN
                    showLoginPinDialog(onSuccess)
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Вход")
            .setNegativeButtonText("Использовать PIN")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
    private fun showLoginPinDialog(onSuccess: () -> Unit) {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Введите Pass Key")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Войти") { _, _ ->
                if (input.text.toString() == getPassKey()) onSuccess()
                else {
                    Toast.makeText(this, "Неверный код!", Toast.LENGTH_SHORT).show()
                    showLoginPinDialog(onSuccess) // Рекурсия при ошибке
                }
            }
            .show()
    }
    private fun fetchRemoteTheme() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 0
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val colorHex = remoteConfig.getString("status_bar_color")
                if (colorHex.isNotEmpty()) {
                    // Используем runOnUiThread, чтобы точно попасть в поток отрисовки
                    runOnUiThread {
                        try {
                            val color = Color.parseColor(colorHex)
                            window.apply {
                                // Чистим все мешающие флаги
                                clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                                addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                                // Устанавливаем цвет
                                statusBarColor = color
                            }

                            // Настройка иконок через современный контроллер
                            val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
                            controller.isAppearanceLightStatusBars = false // false для белых иконок на темном фоне

                            Toast.makeText(this, "Облако: $colorHex", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this, "Ошибка цвета: $colorHex", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun saveOperationToCloud(operation: String, result: String) {
        val db = FirebaseFirestore.getInstance()

        // Создаем объект данных
        val historyItem = hashMapOf(
            "expression" to operation,
            "result" to result,
            "timestamp" to com.google.firebase.Timestamp.now()
        )

        // Сохраняем в коллекцию "history"
        db.collection("history")
            .add(historyItem)
            .addOnSuccessListener {
                // Можно добавить лог, что успешно сохранено
            }
            .addOnFailureListener { e ->
                // Ошибка сохранения
            }
    }
    private fun copyToClipboard() {
        val textToCopy = tvDisplay.text.toString()

        // Проверяем, что на экране не "Error" и не пусто
        if (textToCopy == "Error" || textToCopy.isEmpty()) {
            Toast.makeText(this, "Нечего копировать", Toast.LENGTH_SHORT).show()
            return
        }

        // Получаем доступ к системному сервису буфера обмена
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Calculator Result", textToCopy)

        // Помещаем данные в буфер
        clipboard.setPrimaryClip(clip)

        // Уведомляем пользователя (User Experience!)
        Toast.makeText(this, "Результат скопирован: $textToCopy", Toast.LENGTH_SHORT).show()
    }
    private fun onPlusMinusClick() {
        val currentText = tvDisplay.text.toString()
        if (currentText == "0" || currentText == "Error") return

        if (currentText.startsWith("-")) {
            tvDisplay.text = currentText.substring(1)
        } else {
            tvDisplay.text = "-$currentText"
        }
    }

    private fun onPercentClick() {
        val currentText = tvDisplay.text.toString()
        val value = currentText.toDoubleOrNull() ?: return

        // Превращаем число в проценты (например, 5 -> 0.05)
        val result = value / 100

        // Форматируем вывод, чтобы не было лишних нулей
        tvDisplay.text = if (result % 1 == 0.0) result.toInt().toString() else result.toString()
        isNewOperation = true
    }

    private fun onNumberClick(button: Button) {
        val btnText = button.text.toString()
        if (isNewOperation) {
            tvDisplay.text = btnText
            isNewOperation = false
        } else {
            // Чтобы нельзя было поставить две точки
            if (btnText == "." && tvDisplay.text.contains(".")) return
            tvDisplay.append(btnText)
        }
    }

    private fun onOperationClick(op: String) {
        firstValue = tvDisplay.text.toString().toDoubleOrNull() ?: 0.0
        currentOperation = op
        isNewOperation = true
    }

    private fun calculateResult() {
        val secondValue = tvDisplay.text.toString().toDoubleOrNull() ?: 0.0
        var result = 0.0

        // Обработка ошибок (Error Handling - требование лабы!)
        if (currentOperation == "/" && secondValue == 0.0) {
            tvDisplay.text = "Error"
            isNewOperation = true
            return
        }

        when (currentOperation) {
            "+" -> result = firstValue + secondValue
            "-" -> result = firstValue - secondValue
            "*" -> result = firstValue * secondValue
            "/" -> result = firstValue / secondValue
        }

        // Убираем ".0" если число целое
        val finalResult = if (result % 1 == 0.0) result.toInt().toString() else result.toString()
        tvDisplay.text = finalResult
        isNewOperation = true

        val expression = "$firstValue $currentOperation $secondValue"
        saveOperationToCloud(expression, finalResult)
    }


}