package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.LocationViewModel
import java.text.DecimalFormat

@Composable
fun CalculatorCamouflageScreen(
    viewModel: LocationViewModel,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current

    // Calculator display state
    var displayValue by remember { mutableStateOf("0") }
    var expressionHistory by remember { mutableStateOf("") }
    var pendingOperand by remember { mutableStateOf<Double?>(null) }
    var pendingOperator by remember { mutableStateOf<String?>(null) }
    var isNewOperand by remember { mutableStateOf(true) }

    // Secret PIN buffer to track typed numbers for disguise breach
    var secretPinBuffer by remember { mutableStateOf("") }

    val formatter = remember { DecimalFormat("#,###.########") }

    fun formatNumber(num: Double): String {
        return if (num % 1.0 == 0.0 && !num.isInfinite() && !num.isNaN() && Math.abs(num) < 1e12) {
            num.toLong().toString()
        } else {
            formatter.format(num)
        }
    }

    fun evaluateCalculation() {
        val current = displayValue.replace(",", "").toDoubleOrNull() ?: 0.0
        val op = pendingOperator
        val first = pendingOperand

        if (op != null && first != null) {
            val result = when (op) {
                "+" -> first + current
                "-" -> first - current
                "×" -> first * current
                "÷" -> if (current == 0.0) Double.NaN else first / current
                else -> current
            }

            expressionHistory = "${formatNumber(first)} $op ${formatNumber(current)} ="
            displayValue = if (result.isNaN()) "Error" else formatNumber(result)
            pendingOperand = null
            pendingOperator = null
            isNewOperand = true
        }
    }

    fun onKeyPressed(key: String) {
        when (key) {
            "C" -> {
                displayValue = "0"
                expressionHistory = ""
                pendingOperand = null
                pendingOperator = null
                isNewOperand = true
                secretPinBuffer = ""
            }
            "±" -> {
                val current = displayValue.replace(",", "").toDoubleOrNull() ?: return
                displayValue = formatNumber(-current)
            }
            "%" -> {
                val current = displayValue.replace(",", "").toDoubleOrNull() ?: return
                displayValue = formatNumber(current / 100.0)
            }
            "+", "-", "÷" -> {
                val current = displayValue.replace(",", "").toDoubleOrNull() ?: 0.0
                if (pendingOperator != null && !isNewOperand) {
                    evaluateCalculation()
                } else {
                    pendingOperand = current
                }
                pendingOperator = key
                expressionHistory = "${formatNumber(pendingOperand ?: current)} $key"
                isNewOperand = true
                secretPinBuffer = "" // operators reset the secret pin sequence
            }
            "=", "*", "×" -> {
                // Check if secret PIN was entered:
                val enteredCode = secretPinBuffer.ifEmpty { displayValue.trim() }
                val authenticatedRole = viewModel.authenticateWithCode(enteredCode)

                if (authenticatedRole != null) {
                    secretPinBuffer = ""
                    val roleLabel = if (authenticatedRole == "parent") "Owner Command Center" else "Worker Tracker"
                    Toast.makeText(context, "Identity verified. Launching $roleLabel.", Toast.LENGTH_SHORT).show()
                    onUnlocked()
                    return
                }

                if (key == "*" || key == "×") {
                    val current = displayValue.replace(",", "").toDoubleOrNull() ?: 0.0
                    if (pendingOperator != null && !isNewOperand) {
                        evaluateCalculation()
                    } else {
                        pendingOperand = current
                    }
                    pendingOperator = "×"
                    expressionHistory = "${formatNumber(pendingOperand ?: current)} ×"
                    isNewOperand = true
                    secretPinBuffer = ""
                } else {
                    secretPinBuffer = ""
                    evaluateCalculation()
                }
            }
            "." -> {
                if (isNewOperand) {
                    displayValue = "0."
                    isNewOperand = false
                    secretPinBuffer = ""
                } else if (!displayValue.contains(".")) {
                    displayValue += "."
                }
            }
            "del" -> {
                if (!isNewOperand && displayValue.isNotEmpty()) {
                    displayValue = if (displayValue.length == 1) "0" else displayValue.dropLast(1)
                    if (secretPinBuffer.isNotEmpty()) secretPinBuffer = secretPinBuffer.dropLast(1)
                }
            }
            else -> {
                // Digits 0-9
                secretPinBuffer += key
                if (isNewOperand) {
                    displayValue = key
                    isNewOperand = false
                } else {
                    if (displayValue == "0") {
                        displayValue = key
                    } else if (displayValue.length < 14) {
                        displayValue += key
                    }
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("calculator_screen"),
        color = Color(0xFF000000) // Deep iQOO Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp)
                .systemBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Calculation Display Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.2f)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                // History text
                Text(
                    text = expressionHistory,
                    color = Color(0xFF8E8E93),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Current Primary Digits Display
                Text(
                    text = displayValue,
                    color = Color.White,
                    fontSize = when {
                        displayValue.length > 9 -> 44.sp
                        displayValue.length > 6 -> 56.sp
                        else -> 72.sp
                    },
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calculator_display")
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF1C1C1E), thickness = 0.5.dp)
            }

            // Calculator Keypad Grid (iQOO Style)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val buttonRows = listOf(
                    listOf(
                        CalcKey("C", KeyType.FUNCTION),
                        CalcKey("±", KeyType.FUNCTION),
                        CalcKey("%", KeyType.FUNCTION),
                        CalcKey("÷", KeyType.OPERATOR)
                    ),
                    listOf(
                        CalcKey("7", KeyType.NUMERIC),
                        CalcKey("8", KeyType.NUMERIC),
                        CalcKey("9", KeyType.NUMERIC),
                        CalcKey("×", KeyType.OPERATOR)
                    ),
                    listOf(
                        CalcKey("4", KeyType.NUMERIC),
                        CalcKey("5", KeyType.NUMERIC),
                        CalcKey("6", KeyType.NUMERIC),
                        CalcKey("-", KeyType.OPERATOR)
                    ),
                    listOf(
                        CalcKey("1", KeyType.NUMERIC),
                        CalcKey("2", KeyType.NUMERIC),
                        CalcKey("3", KeyType.NUMERIC),
                        CalcKey("+", KeyType.OPERATOR)
                    ),
                    listOf(
                        CalcKey("0", KeyType.NUMERIC, span = 2),
                        CalcKey(".", KeyType.NUMERIC),
                        CalcKey("=", KeyType.EQUALS)
                    )
                )

                buttonRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        row.forEach { keyItem ->
                            val weight = if (keyItem.span == 2) 2.1f else 1f
                            IqooCalculatorButton(
                                symbol = keyItem.symbol,
                                keyType = keyItem.type,
                                modifier = Modifier
                                    .weight(weight)
                                    .aspectRatio(if (keyItem.span == 2) 2.1f else 1f)
                                    .testTag("calc_btn_${keyItem.symbol}"),
                                onClick = { onKeyPressed(keyItem.symbol) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class KeyType {
    NUMERIC, FUNCTION, OPERATOR, EQUALS
}

private data class CalcKey(
    val symbol: String,
    val type: KeyType,
    val span: Int = 1
)

@Composable
private fun IqooCalculatorButton(
    symbol: String,
    keyType: KeyType,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor = when (keyType) {
        KeyType.FUNCTION -> Color(0xFF2C2C2E)
        KeyType.OPERATOR -> Color(0xFF007AFF) // Funtouch Blue
        KeyType.EQUALS -> Color(0xFF007AFF) // Matches operators in iQOO
        KeyType.NUMERIC -> Color(0xFF1C1C1E)
    }

    val contentColor = when (keyType) {
        KeyType.FUNCTION -> Color.White
        KeyType.OPERATOR, KeyType.EQUALS -> Color.White
        KeyType.NUMERIC -> Color.White
    }

    val fontSize = when (keyType) {
        KeyType.OPERATOR, KeyType.EQUALS -> 32.sp
        else -> 28.sp
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (symbol == "×") "×" else if (symbol == "÷") "÷" else symbol,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            textAlign = TextAlign.Center
        )
    }
}
