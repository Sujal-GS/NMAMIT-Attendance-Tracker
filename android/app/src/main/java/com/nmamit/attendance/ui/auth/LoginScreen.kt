package com.nmamit.attendance.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmamit.attendance.ui.components.*
import com.nmamit.attendance.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    val hazeState = remember { HazeState() }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            onLoginSuccess()
        }
    }

    BackHandler(enabled = showCreditsDialog) {
        showCreditsDialog = false
    }

    val cardScale = remember { Animatable(0.94f) }
    val cardAlpha = remember { Animatable(0f) }
    val cardTranslateY = remember { Animatable(28f) }

    LaunchedEffect(Unit) {
        cardScale.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(Unit) {
        cardAlpha.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        cardTranslateY.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
    }

    TechGridBackground(hazeState = hazeState, enableHazeSource = showCreditsDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .graphicsLayer {
                        scaleX = cardScale.value
                        scaleY = cardScale.value
                        alpha = cardAlpha.value
                        translationY = cardTranslateY.value
                    }
                    .verticalScroll(rememberScrollState())
                    .then(if (showCreditsDialog) Modifier.hazeSource(hazeState) else Modifier),
                cornerRadius = 24.dp,
                tintColor = AuthCardTint,
                glassAlpha = 0.56f,
                blurRadius = 20.dp,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 26.dp)
            ) {
                // Header: Brand Title & Subtitle
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fluidEnter(delayMs = 40, offsetYDp = 12f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BrandShimmerTitle()
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Calibrated academic tracking, daily timetable & 85% buffer audit",
                        fontSize = 12.5.sp,
                        color = ForegroundMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Error Banner
                AnimatedVisibility(
                    visible = !uiState.errorMessage.isNullOrBlank(),
                    enter = expandVertically(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(160)) + fadeOut(tween(140))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(RoseBg)
                            .border(1.dp, RoseBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Rose,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            color = Rose,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 1. University / Institute (Locked to NITTE)
                Column(modifier = Modifier.fluidEnter(delayMs = 70, offsetYDp = 12f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FormLabelText("UNIVERSITY / INSTITUTE")
                        GlassPillBadge(text = "NITTE University")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    GlassInputField(
                        value = "049 - NITTE (DEEMED TO BE UNIVERSITY)",
                        onValueChange = {},
                        readOnly = true,
                        textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        leadingIcon = {
                            Icon(
                                Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = ForegroundMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))

                // 2. Registered Mobile Number / USN
                Column(modifier = Modifier.fluidEnter(delayMs = 100, offsetYDp = 12f)) {
                    FormLabelText("REGISTERED MOBILE NUMBER")
                    Spacer(modifier = Modifier.height(6.dp))
                    GlassInputField(
                        value = uiState.regno,
                        onValueChange = { viewModel.onRegnoChanged(it) },
                        placeholder = "e.g. 9876543210",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next,
                            autoCorrectEnabled = false
                        ),
                        leadingIcon = {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                tint = ForegroundMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))

                // 3. Portal Password
                Column(modifier = Modifier.fluidEnter(delayMs = 130, offsetYDp = 12f)) {
                    FormLabelText("PORTAL PASSWORD")
                    Spacer(modifier = Modifier.height(6.dp))
                    GlassInputField(
                        value = uiState.passwd,
                        onValueChange = { viewModel.onPasswordChanged(it) },
                        placeholder = "••••••••",
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                            autoCorrectEnabled = false
                        ),
                        leadingIcon = {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = ForegroundMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password",
                                    tint = ForegroundMuted,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))

                // 4. Captcha Verification
                Column(modifier = Modifier.fluidEnter(delayMs = 160, offsetYDp = 12f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FormLabelText("CAPTCHA VERIFICATION")
                        GlassPillBadge(text = "Live Sync")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Captcha Display Box
                        Row(
                            modifier = Modifier
                                .width(145.dp)
                                .height(44.dp)
                                .bouncyClickable { viewModel.fetchCaptcha() }
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.07f))
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(listOf(GlassInputTopRim, Color.White.copy(alpha = 0.17f))),
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(start = 12.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (uiState.isCaptchaLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Foreground
                                )
                            } else {
                                Text(
                                    text = uiState.captchaText.ifBlank { "------" },
                                    fontFamily = AppMonoFont,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.8.sp,
                                    color = Foreground
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .bouncyClickable { viewModel.fetchCaptcha() }
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color.White.copy(alpha = 0.09f))
                                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(7.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                CaptchaRefreshIcon(isLoading = uiState.isCaptchaLoading)
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Right: Captcha Input
                        GlassInputField(
                            value = uiState.captchaInput,
                            onValueChange = { viewModel.onCaptchaChanged(it) },
                            placeholder = "Captcha",
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(
                                fontFamily = AppMonoFont,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.5.sp
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                                autoCorrectEnabled = false
                            ),
                            keyboardActions = KeyboardActions(onDone = { viewModel.login() })
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                val signInInteraction = remember { MutableInteractionSource() }
                val signInPressed by signInInteraction.collectIsPressedAsState()
                val signInScale by animateFloatAsState(
                    targetValue = if (signInPressed && !uiState.isLoading) 0.96f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
                    label = "signInScale"
                )

                // Primary White Action Button
                Button(
                    onClick = { viewModel.login() },
                    enabled = !uiState.isLoading,
                    interactionSource = signInInteraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .fluidEnter(delayMs = 190, offsetYDp = 12f)
                        .graphicsLayer {
                            scaleX = signInScale
                            scaleY = signInScale
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Foreground,
                        contentColor = BgCanvas,
                        disabledContainerColor = Foreground.copy(alpha = 0.5f),
                        disabledContentColor = BgCanvas
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = BgCanvas,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Sign In & Fetch Attendance",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BgCanvas
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val demoInteraction = remember { MutableInteractionSource() }
                val demoPressed by demoInteraction.collectIsPressedAsState()
                val demoScale by animateFloatAsState(
                    targetValue = if (demoPressed && !uiState.isLoading) 0.96f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
                    label = "demoScale"
                )

                // Secondary Frosted Glass Demo Button
                OutlinedButton(
                    onClick = { viewModel.loginAsDemo() },
                    enabled = !uiState.isLoading,
                    interactionSource = demoInteraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .fluidEnter(delayMs = 215, offsetYDp = 10f)
                        .graphicsLayer {
                            scaleX = demoScale
                            scaleY = demoScale
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White.copy(alpha = 0.07f),
                        contentColor = Foreground
                    ),
                    border = BorderStroke(
                        1.dp,
                        Brush.verticalGradient(listOf(GlassInputTopRim, Color.White.copy(alpha = 0.16f)))
                    )
                ) {
                    Text(
                        text = "Explore Demo Mode",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Foreground
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.09f))
                Spacer(modifier = Modifier.height(16.dp))

                // Footer: Crafted By Pill
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fluidEnter(delayMs = 240, offsetYDp = 8f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .bouncyClickable { showCreditsDialog = true }
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, GlassBorder, RoundedCornerShape(50))
                            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CRAFTED BY",
                            fontSize = 9.5.sp,
                            fontFamily = AppMonoFont,
                            fontWeight = FontWeight.Bold,
                            color = Foreground,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sujal G S & Lakshminarayanan P S",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Foreground.copy(alpha = 0.9f)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.CallMade,
                            contentDescription = null,
                            tint = ForegroundMuted,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        // Frosted Engineering Credits Overlay (blurs Login Card behind it!)
        AnimatedVisibility(
            visible = showCreditsDialog,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150))
        ) {
            DeveloperCreditsOverlay(
                cardModifier = Modifier.animateEnterExit(
                    enter = scaleIn(
                        initialScale = 0.92f,
                        animationSpec = spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow)
                    ),
                    exit = scaleOut(targetScale = 0.94f, animationSpec = tween(150))
                ),
                onDismiss = { showCreditsDialog = false }
            )
        }
    }
}

@Composable
private fun FormLabelText(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.55.sp,
        color = ForegroundMuted
    )
}

@Composable
private fun DeveloperCreditsOverlay(
    cardModifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassCard(
            modifier = cardModifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            cornerRadius = 26.dp,
            tintColor = GlassCardTint,
            glassAlpha = 0.78f,
            blurRadius = 28.dp,
            enableBlur = true,
            contentPadding = PaddingValues(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    SectionTag("// DEVELOPERS")
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Engineering Credits",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Foreground
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .bouncyClickable(onClick = onDismiss)
                        .clip(CircleShape)
                        .background(GlassSubCardBg)
                        .border(1.dp, GlassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = ForegroundMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = buildAnnotatedString {
                    append("The attendance portal was designed & developed by ")
                    withStyle(SpanStyle(color = Foreground, fontWeight = FontWeight.Bold)) {
                        append("Sujal G S")
                    }
                    append(" and ")
                    withStyle(SpanStyle(color = Foreground, fontWeight = FontWeight.Bold)) {
                        append("Lakshminarayanan P S")
                    }
                    append(". Connect with us on LinkedIn:")
                },
                fontSize = 12.5.sp,
                color = ForegroundMuted,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            CreatorCard(
                initials = "SG",
                name = "Sujal G S",
                handle = "linkedin.com/in/sujal-g-s",
                onClick = { runCatching { uriHandler.openUri("https://www.linkedin.com/in/sujal-g-s/") } }
            )
            Spacer(modifier = Modifier.height(10.dp))
            CreatorCard(
                initials = "LP",
                name = "Lakshminarayanan P S",
                handle = "linkedin.com/in/lakshminarayanan-p-s",
                onClick = { runCatching { uriHandler.openUri("https://www.linkedin.com/in/lakshminarayanan-p-s/") } }
            )
        }
    }
}

@Composable
private fun CreatorCard(
    initials: String,
    name: String,
    handle: String,
    onClick: () -> Unit
) {
    GlassSubCard(
        modifier = Modifier.bouncyClickable(onClick = onClick),
        cornerRadius = 18.dp,
        borderColor = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), GlassBorder))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.5.dp, GlassBorderSpecular, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontSize = 13.5.sp,
                    fontFamily = AppMonoFont,
                    fontWeight = FontWeight.ExtraBold,
                    color = Foreground
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Foreground
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LinkedInBlue)
                            .padding(horizontal = 8.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LinkedIn",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.CallMade,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = handle,
                    fontSize = 11.sp,
                    fontFamily = AppMonoFont,
                    color = ForegroundMuted
                )
            }
        }
    }
}

@Composable
private fun BrandShimmerTitle() {
    val infiniteTransition = rememberInfiniteTransition(label = "titleShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerOffset"
    )
    val titleBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFFFFF),
            Color(0xFF8E8E93),
            Color(0xFF52525B),
            Color(0xFFE4E4E7),
            Color(0xFFFFFFFF)
        ),
        start = Offset(shimmerOffset - 300f, 0f),
        end = Offset(shimmerOffset + 300f, 0f)
    )
    Text(
        text = "NMAMIT Attendance Tracker",
        style = TextStyle(
            brush = titleBrush,
            fontFamily = AppBodyFont,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp
        ),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun CaptchaRefreshIcon(isLoading: Boolean) {
    if (isLoading) {
        val spinTransition = rememberInfiniteTransition(label = "captchaSpin")
        val angle = spinTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "captchaAngle"
        )
        Icon(
            Icons.Default.Refresh,
            contentDescription = "Refresh Captcha",
            tint = ForegroundMuted,
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer { rotationZ = angle.value }
        )
    } else {
        Icon(
            Icons.Default.Refresh,
            contentDescription = "Refresh Captcha",
            tint = ForegroundMuted,
            modifier = Modifier.size(14.dp)
        )
    }
}
