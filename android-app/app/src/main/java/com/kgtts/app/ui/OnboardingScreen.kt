package com.lhtstudio.kigtts.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lhtstudio.kigtts.app.R

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun KigttsOnboardingScreen(
    onComplete: (List<QuickSubtitleGroup>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnComplete by rememberUpdatedState(onComplete)
    var page by rememberSaveable { mutableIntStateOf(0) }
    var privacyAccepted by rememberSaveable { mutableStateOf(false) }
    var privacyOpen by rememberSaveable { mutableStateOf(false) }
    var agreementOpen by rememberSaveable { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    val presetGroups = remember { defaultQuickSubtitlePresetGroups() }
    var selectedPresetGroupIds by rememberSaveable {
        mutableStateOf(defaultSelectedQuickSubtitlePresetGroupIds())
    }
    var expandedPresetGroupIds by rememberSaveable { mutableStateOf(listOf<Long>()) }
    val pageCount = 4
    var pendingPermissionPurpose by remember { mutableStateOf<OnboardingPermissionRequest?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshToken++
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshToken++
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshToken++
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshToken++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (currentAppDarkTheme()) 0.24f else 0.18f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (
                    slideInHorizontally { it * direction } + fadeIn()
                    ).togetherWith(
                    slideOutHorizontally { -it * direction } + fadeOut()
                )
            },
            label = "kigtts_onboarding_page",
            modifier = Modifier.fillMaxSize()
        ) { targetPage ->
            OnboardingPageFrame(
                page = targetPage,
                pageCount = pageCount,
                canGoBack = targetPage > 0,
                canGoNext = when (targetPage) {
                    0 -> privacyAccepted
                    2 -> selectedPresetGroupIds.isNotEmpty()
                    else -> true
                },
                nextLabel = if (targetPage == pageCount - 1) "开始使用" else "下一步",
                onBack = { page = (page - 1).coerceAtLeast(0) },
                onNext = {
                    if (page == pageCount - 1) {
                        val selectedGroups = presetGroups.filter { it.id in selectedPresetGroupIds.toSet() }
                        currentOnComplete(selectedGroups)
                    } else {
                        page = (page + 1).coerceAtMost(pageCount - 1)
                    }
                }
            ) {
                when (targetPage) {
                    0 -> OnboardingWelcomePage(
                        privacyAccepted = privacyAccepted,
                        onPrivacyAcceptedChange = { privacyAccepted = it },
                        onOpenPrivacy = { privacyOpen = true },
                        onOpenAgreement = { agreementOpen = true }
                    )
                    1 -> OnboardingPermissionPage(
                        refreshToken = refreshToken,
                        onRequestMicrophone = {
                            pendingPermissionPurpose = OnboardingPermissionRequest.Microphone
                        },
                        onRequestCamera = {
                            pendingPermissionPurpose = OnboardingPermissionRequest.Camera
                        },
                        onRequestNotification = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                pendingPermissionPurpose = OnboardingPermissionRequest.Notification
                            }
                        },
                        onOpenOverlaySettings = {
                            pendingPermissionPurpose = OnboardingPermissionRequest.Overlay
                        }
                    )
                    2 -> OnboardingQuickTextPresetPage(
                        groups = presetGroups,
                        selectedGroupIds = selectedPresetGroupIds,
                        expandedGroupIds = expandedPresetGroupIds,
                        onToggleSelected = { groupId ->
                            selectedPresetGroupIds = if (groupId in selectedPresetGroupIds) {
                                selectedPresetGroupIds - groupId
                            } else {
                                selectedPresetGroupIds + groupId
                            }
                        },
                        onToggleExpanded = { groupId ->
                            expandedPresetGroupIds = if (groupId in expandedPresetGroupIds) {
                                expandedPresetGroupIds - groupId
                            } else {
                                expandedPresetGroupIds + groupId
                            }
                        }
                    )
                    else -> OnboardingDonePage()
                }
            }
        }

        pendingPermissionPurpose?.let { request ->
            PermissionPurposeDialog(
                info = when (request) {
                    OnboardingPermissionRequest.Microphone -> recordAudioPermissionPurpose(
                        serviceFeature = "实时语音识别、音频测试和说话人验证",
                        purpose = "在你主动使用语音相关功能时采集麦克风声音，用于生成字幕、测试输入或完成本机验证。"
                    )
                    OnboardingPermissionRequest.Camera -> cameraScannerPermissionPurpose()
                    OnboardingPermissionRequest.Notification -> notificationPermissionPurpose()
                    OnboardingPermissionRequest.Overlay -> floatingOverlayPermissionPurpose()
                },
                onConfirm = {
                    pendingPermissionPurpose = null
                    when (request) {
                        OnboardingPermissionRequest.Microphone ->
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        OnboardingPermissionRequest.Camera ->
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        OnboardingPermissionRequest.Notification ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        OnboardingPermissionRequest.Overlay ->
                            openOverlayPermissionSettings(context)
                    }
                },
                onDismiss = { pendingPermissionPurpose = null }
            )
        }

        if (privacyOpen) {
            OnboardingLegalDialog(
                assetPath = "legal/privacy_policy.md",
                closeDescription = "关闭隐私政策",
                onDismiss = { privacyOpen = false }
            )
        }

        if (agreementOpen) {
            OnboardingLegalDialog(
                assetPath = "legal/user_agreement.md",
                closeDescription = "关闭用户协议",
                onDismiss = { agreementOpen = false }
            )
        }
    }
}

private enum class OnboardingPermissionRequest {
    Microphone,
    Camera,
    Notification,
    Overlay
}

@Composable
private fun OnboardingPageFrame(
    page: Int,
    pageCount: Int,
    canGoBack: Boolean,
    canGoNext: Boolean,
    nextLabel: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pageCount) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (index == page) 28.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == page) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                            }
                        )
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            val maxCardWidth = 520.dp
            val extraPadding = (maxWidth - maxCardWidth).coerceAtLeast(0.dp) / 2
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = extraPadding)
            ) {
                content()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canGoBack) {
                Md2TextButton(onClick = onBack) {
                    Text("上一步")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            Spacer(Modifier.weight(1f))
            Md2Button(
                onClick = onNext,
                enabled = canGoNext,
                content = { Text(nextLabel) }
            )
        }
    }
}

@Composable
private fun OnboardingWelcomePage(
    privacyAccepted: Boolean,
    onPrivacyAcceptedChange: (Boolean) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAgreement: () -> Unit
) {
    OnboardingCard {
        OnboardingLogo()
        Text(
            text = "欢迎使用",
            style = MaterialTheme.typography.h4,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "KIGTTS 会把字幕、朗读、名片、音效、画板和悬浮入口整合在一起，帮助你在不方便说话、现场嘈杂或需要快速互动时更顺手地表达。",
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        Text(
            text = "使用前请阅读并确认《KIGTTS隐私政策》和《KIGTTS用户协议》。涉及麦克风、相机、悬浮窗、通知、无障碍等能力时，KIGTTS 会在对应功能入口再次说明用途并由你主动授权。",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Md2OutlinedButton(
                onClick = onOpenPrivacy,
                modifier = Modifier.weight(1f)
            ) {
                MsIcon("policy", contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("隐私政策")
            }
            Md2OutlinedButton(
                onClick = onOpenAgreement,
                modifier = Modifier.weight(1f)
            ) {
                MsIcon("contract", contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("用户协议")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = privacyAccepted,
                onCheckedChange = onPrivacyAcceptedChange
            )
            OnboardingAgreementText(
                modifier = Modifier.weight(1f),
                onOpenPrivacy = onOpenPrivacy,
                onOpenAgreement = onOpenAgreement
            )
        }
    }
}

@Composable
private fun OnboardingPermissionPage(
    refreshToken: Int,
    onRequestMicrophone: () -> Unit,
    onRequestCamera: () -> Unit,
    onRequestNotification: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE")
    val ignoredRefresh = refreshToken
    OnboardingCard {
        OnboardingHeroIcon(
            name = "admin_panel_settings",
            contentDescription = null
        )
        Text(
            text = "权限开启引导",
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "下列权限可帮助你使用语音识别、扫码、悬浮提示等能力。这些权限都不是必须一次性开启，你可以先跳过，后续使用对应功能时再授权。",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        PermissionGuideRow(
            icon = "mic",
            title = "麦克风",
            description = "用于实时语音识别、音频测试和说话人验证。",
            granted = isPermissionGranted(context, Manifest.permission.RECORD_AUDIO),
            actionLabel = "授权",
            onClick = onRequestMicrophone
        )
        PermissionGuideRow(
            icon = "photo_camera",
            title = "相机",
            description = "用于扫一扫识别二维码，画面仅在本机用于识别。",
            granted = isPermissionGranted(context, Manifest.permission.CAMERA),
            actionLabel = "授权",
            onClick = onRequestCamera
        )
        PermissionGuideRow(
            icon = "notifications",
            title = "通知",
            description = "用于前台服务、实时通知和部分后台运行状态提示。",
            granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS),
            actionLabel = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) "无需授权" else "授权",
            onClick = onRequestNotification,
            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        )
        PermissionGuideRow(
            icon = "picture_in_picture",
            title = "悬浮窗",
            description = "用于在其它应用上方显示快捷入口、迷你字幕和迷你名片。",
            granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context),
            actionLabel = "去设置",
            onClick = onOpenOverlaySettings
        )
    }
}

@Composable
private fun OnboardingQuickTextPresetPage(
    groups: List<QuickSubtitleGroup>,
    selectedGroupIds: List<Long>,
    expandedGroupIds: List<Long>,
    onToggleSelected: (Long) -> Unit,
    onToggleExpanded: (Long) -> Unit
) {
    OnboardingCard {
        OnboardingHeroIcon(
            name = "text_snippet",
            contentDescription = null
        )
        Text(
            text = "添加快捷文本预设",
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "选择常用分组后，进入主界面会按导入预设的方式添加。点按分组可以展开或折叠预览；之后也可以在设置里继续添加这些预设。",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        QuickSubtitlePresetGroupSelectionList(
            groups = groups,
            selectedGroupIds = selectedGroupIds,
            expandedGroupIds = expandedGroupIds,
            onToggleSelected = onToggleSelected,
            onToggleExpanded = onToggleExpanded
        )
    }
}

@Composable
internal fun QuickSubtitlePresetGroupSelectionList(
    groups: List<QuickSubtitleGroup>,
    selectedGroupIds: Collection<Long>,
    expandedGroupIds: Collection<Long>,
    onToggleSelected: (Long) -> Unit,
    onToggleExpanded: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groups.forEach { group ->
            val selected = group.id in selectedGroupIds
            val expanded = group.id in expandedGroupIds
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(UiTokens.Radius))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        }
                    )
                    .clickable { onToggleExpanded(group.id) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelected(group.id) }
                    )
                    MsIcon(
                        name = group.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.accentText
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.body1,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${group.items.size} 条快捷文本",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MsIcon(
                        name = if (expanded) "expand_less" else "expand_more",
                        contentDescription = if (expanded) "折叠预览" else "展开预览",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 44.dp, end = 4.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        group.items.forEach { item ->
                            Text(
                                text = "• $item",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingDonePage() {
    OnboardingCard {
        OnboardingLogo()
        OnboardingHeroIcon(
            name = "check_circle",
            contentDescription = null
        )
        Text(
            text = "大功告成",
            style = MaterialTheme.typography.h4,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "你已经完成基础准备。接下来可以先从便捷字幕开始，也可以继续配置语音识别、语音朗读、悬浮窗、音效板和快捷名片。",
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = "点击“开始使用”后即可进入主界面。之后你仍然可以在设置中查看协议、调整权限和修改功能配置。",
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OnboardingAgreementText(
    modifier: Modifier,
    onOpenPrivacy: () -> Unit,
    onOpenAgreement: () -> Unit
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val text = buildAnnotatedString {
        append("我已阅读并同意 ")
        pushStringAnnotation(tag = "privacy", annotation = "privacy")
        withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)) {
            append("《KIGTTS隐私政策》")
        }
        pop()
        append(" 和 ")
        pushStringAnnotation(tag = "agreement", annotation = "agreement")
        withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)) {
            append("《KIGTTS用户协议》")
        }
        pop()
    }
    ClickableText(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.body2.copy(color = textColor),
        onClick = { offset ->
            when {
                text.getStringAnnotations("privacy", offset, offset).isNotEmpty() -> onOpenPrivacy()
                text.getStringAnnotations("agreement", offset, offset).isNotEmpty() -> onOpenAgreement()
            }
        }
    )
}

@Composable
private fun OnboardingLegalDialog(
    assetPath: String,
    closeDescription: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(Modifier.fillMaxSize()) {
                LegalDocumentScreen(assetPath = assetPath)
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .clip(CircleShape)
                        .background(md2CardContainerColor())
                ) {
                    MsIcon("close", contentDescription = closeDescription)
                }
            }
        }
    }
}

@Composable
private fun OnboardingCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiTokens.Radius),
        backgroundColor = md2CardContainerColor(),
        elevation = UiTokens.CardElevation
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
private fun OnboardingLogo() {
    val logoHeight = dimensionResource(R.dimen.kigtts_startup_logo_height)
    Image(
        painter = painterResource(
            id = if (currentAppDarkTheme()) R.drawable.logo_white else R.drawable.logo_black
        ),
        contentDescription = "KIGTTS Logo",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .height(logoHeight)
    )
}

@Composable
private fun OnboardingHeroIcon(
    name: String,
    contentDescription: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp),
        contentAlignment = Alignment.Center
    ) {
        MsIcon(
            name = name,
            contentDescription = contentDescription,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.accentText,
            iconSize = 88.dp
        )
    }
}

@Composable
private fun PermissionGuideRow(
    icon: String,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiTokens.Radius))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MsIcon(
            name = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.accentText
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (granted) "已开启" else "未开启",
                    color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.caption
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Md2OutlinedButton(
            onClick = onClick,
            enabled = enabled && !granted
        ) {
            Text(if (granted) "已完成" else actionLabel)
        }
    }
}

private fun isPermissionGranted(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun openOverlayPermissionSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
