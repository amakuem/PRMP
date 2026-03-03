package com.example.mobilki

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import android.widget.Button
import android.widget.TextView
import android.view.View

class MainActivity : AppCompatActivity() {

    private var firstValue: Double = 0.0
    private var currentOperation: String? = null
    private var isNewOperation: Boolean = true
    private lateinit var tvDisplay: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvDisplay = findViewById(R.id.tvDisplay)

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
        tvDisplay.text = if (result % 1 == 0.0) result.toInt().toString() else result.toString()
        isNewOperation = true
    }


}