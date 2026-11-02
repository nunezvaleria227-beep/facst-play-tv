package com.example.mitvplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mitvplayer.Config
import com.example.mitvplayer.R

@Composable
fun LoginScreen(vm: AppViewModel) {
    val focusManager = LocalFocusManager.current
    val needsServer = Config.PORTAL_URL.isBlank()
    val state = vm.loginState
    var showPassword by remember { mutableStateOf(true) }
    val cardShape = RoundedCornerShape(22.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = FocusRingColor,
        unfocusedBorderColor = LineColor,
        focusedLabelColor = TextPrimary,
        unfocusedLabelColor = TextSecondary,
        cursorColor = AccentColor,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
    )

    AppBackground(dim = 0f) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ------------------------------------------------ marca y lo que trae la app (mitad izquierda)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 56.dp, end = 24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.logo_app),
                    contentDescription = "Facst Play TV",
                    modifier = Modifier.width(300.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Toda tu TV, más rápida y en un solo lugar.",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic
                )
                Spacer(Modifier.height(16.dp))
                LoginFeature(Glyph.TV, LiveColor, "TV en vivo con guía de programación")
                LoginFeature(Glyph.CLAPPER, AccentText, "Películas y series con subtítulos")
                LoginFeature(Glyph.SWAP, LiveColor, "Sigue viendo justo donde te quedaste")
                LoginFeature(Glyph.CHECK, AccentText, "Control parental con PIN")
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Entrar",
                    color = TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Escribe el usuario y la contraseña que te dio tu proveedor.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                if (needsServer) {
                    OutlinedTextField(
                        value = vm.server,
                        onValueChange = { vm.server = it },
                        label = { Text("URL del servidor") },
                        placeholder = { Text("http://servidor.com:8080") },
                        singleLine = true,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = vm.username,
                    onValueChange = { vm.username = it },
                    label = { Text("Usuario") },
                    singleLine = true,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = vm.password,
                    onValueChange = { vm.password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    colors = fieldColors,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            text = if (showPassword) "OCULTAR" else "VER",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showPassword = !showPassword }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            vm.submitLogin()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                val error = state.error
                if (error != null) {
                    Text(
                        text = error,
                        color = ErrorColor,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                TvButton(
                    text = if (state.loading) "Entrando… un momento" else "Entrar",
                    onClick = { vm.submitLogin() },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!needsServer) {
                    Text(
                        text = "✓  El servidor ya viene configurado: solo necesitas tu usuario y contraseña.",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun LoginFeature(glyph: Glyph, tint: Color, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(RowSelectedColor)
                .border(1.dp, LineColor, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(glyph, Modifier.size(17.dp), tint)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color(0xFFD6D1E8), fontSize = 13.sp)
    }
}
