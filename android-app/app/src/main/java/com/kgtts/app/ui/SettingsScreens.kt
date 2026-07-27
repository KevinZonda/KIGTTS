@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.lhtstudio.kigtts.app.ui

import android.annotation.SuppressLint
import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.camera.core.CameraSelector
import androidx.camera.core.Camera
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material.*
import androidx.compose.material.DropdownMenuItem as M2DropdownMenuItem
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.lhtstudio.kigtts.app.R
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.lhtstudio.kigtts.app.audio.AudioRoutePreference
import com.lhtstudio.kigtts.app.audio.AudioDenoiserMode
import com.lhtstudio.kigtts.app.audio.AudioLoopbackTester
import com.lhtstudio.kigtts.app.audio.AudioTestConfig
import com.lhtstudio.kigtts.app.audio.SoundboardManager
import com.lhtstudio.kigtts.app.audio.SoundboardPlaybackState
import com.lhtstudio.kigtts.app.audio.SpeechEnhancementMode
import com.lhtstudio.kigtts.app.audio.SpeakerEnrollResult
import com.lhtstudio.kigtts.app.audio.VadMode
import com.lhtstudio.kigtts.app.data.ModelRepository
import com.lhtstudio.kigtts.app.data.RecognitionResourceProgress
import com.lhtstudio.kigtts.app.data.RecognitionResourceStatus
import com.lhtstudio.kigtts.app.data.KokoroVoiceStatus
import com.lhtstudio.kigtts.app.data.SoundboardGroup
import com.lhtstudio.kigtts.app.data.SoundboardItem
import com.lhtstudio.kigtts.app.data.SoundboardLayoutMode
import com.lhtstudio.kigtts.app.data.SoundboardConfig
import com.lhtstudio.kigtts.app.data.SoundboardPresetIo
import com.lhtstudio.kigtts.app.data.KOKORO_VOICE_NAME
import com.lhtstudio.kigtts.app.data.SYSTEM_TTS_VOICE_NAME
import com.lhtstudio.kigtts.app.data.VoicePackInfo
import com.lhtstudio.kigtts.app.data.UserPrefs
import com.lhtstudio.kigtts.app.theme.ThemeColorResolver
import com.lhtstudio.kigtts.app.data.VoicePackMeta
import com.lhtstudio.kigtts.app.data.defaultSoundboardGroups
import com.lhtstudio.kigtts.app.data.isKokoroVoiceDir
import com.lhtstudio.kigtts.app.data.isSystemTtsVoiceDir
import com.lhtstudio.kigtts.app.data.parseSoundboardConfig
import com.lhtstudio.kigtts.app.data.serializeSoundboardConfig
import com.lhtstudio.kigtts.app.data.uniqueImportedGroupTitle
import com.lhtstudio.kigtts.app.overlay.FloatingOverlayService
import com.lhtstudio.kigtts.app.overlay.OverlayBridge
import com.lhtstudio.kigtts.app.overlay.RealtimeOwnerGate
import com.lhtstudio.kigtts.app.overlay.RealtimeRuntimeBridge
import com.lhtstudio.kigtts.app.service.KeepAliveService
import com.lhtstudio.kigtts.app.service.RealtimeHostService
import com.lhtstudio.kigtts.app.service.VolumeHotkeyAccessibilityGuideService
import com.lhtstudio.kigtts.app.service.VolumeHotkeyAccessibilityService
import com.lhtstudio.kigtts.app.service.VolumeHotkeyService
import com.lhtstudio.kigtts.app.util.AlipayScannerSupport
import com.lhtstudio.kigtts.app.util.AppLogger
import com.lhtstudio.kigtts.app.util.BluetoothMediaTitleBridge
import com.lhtstudio.kigtts.app.util.ExternalShortcutCatalog
import com.lhtstudio.kigtts.app.util.ExternalShortcutChoice
import com.lhtstudio.kigtts.app.util.LauncherMenuShortcuts
import com.lhtstudio.kigtts.app.util.LiveSubtitleNotificationBridge
import com.lhtstudio.kigtts.app.util.QqScannerSupport
import com.lhtstudio.kigtts.app.util.QuickCardRenderCache
import com.lhtstudio.kigtts.app.util.VolumeHotkeyActionSpec
import com.lhtstudio.kigtts.app.util.VolumeHotkeyActions
import com.lhtstudio.kigtts.app.util.VolumeHotkeySequence
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.InvertedLuminanceSource
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.roundToInt

private const val KIGTTS_OFFICIAL_WEBSITE = "https://kigtts.lhtstudio.com"


@Composable
internal fun SettingsNavHost(
    navController: NavHostController,
    viewModel: MainViewModel,
    state: UiState,
    onTopBarActionsChange: (LogTopBarActions?) -> Unit,
    onFontTopBarActionsChange: (FontTopBarActions?) -> Unit,
    onOpenQuickSubtitleGuide: () -> Unit,
    onOpenRecognitionResourceSources: () -> Unit,
    onPickRecognitionResourcePackage: () -> Unit,
    onDownloadRecognitionResources: () -> Unit,
    onOpenKokoroSources: () -> Unit,
    onPickKokoroVoicePackage: () -> Unit,
    onDownloadKokoroVoice: () -> Unit,
    onOpenKokoroVoiceSettings: () -> Unit
) {
    fun isSettingsSubPage(route: String?): Boolean = route != null && route != SettingsRoutes.Main
    NavHost(
        navController = navController,
        startDestination = SettingsRoutes.Main,
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            if (initialState.destination.route == SettingsRoutes.Main &&
                isSettingsSubPage(targetState.destination.route)
            ) {
                fadeIn(animationSpec = tween(170)) +
                        androidx.compose.animation.slideInHorizontally(
                            initialOffsetX = { full -> full / 12 },
                            animationSpec = tween(170, easing = FastOutSlowInEasing)
                        )
            } else {
                fadeIn(animationSpec = tween(120))
            }
        },
        exitTransition = {
            if (initialState.destination.route == SettingsRoutes.Main &&
                isSettingsSubPage(targetState.destination.route)
            ) {
                fadeOut(animationSpec = tween(120)) +
                        androidx.compose.animation.slideOutHorizontally(
                            targetOffsetX = { full -> -full / 14 },
                            animationSpec = tween(120, easing = FastOutSlowInEasing)
                        )
            } else {
                fadeOut(animationSpec = tween(90))
            }
        },
        popEnterTransition = {
            if (isSettingsSubPage(initialState.destination.route) &&
                targetState.destination.route == SettingsRoutes.Main
            ) {
                fadeIn(animationSpec = tween(150)) +
                        androidx.compose.animation.slideInHorizontally(
                            initialOffsetX = { full -> -full / 12 },
                            animationSpec = tween(150, easing = FastOutSlowInEasing)
                        )
            } else {
                fadeIn(animationSpec = tween(120))
            }
        },
        popExitTransition = {
            if (isSettingsSubPage(initialState.destination.route) &&
                targetState.destination.route == SettingsRoutes.Main
            ) {
                fadeOut(animationSpec = tween(120)) +
                        androidx.compose.animation.slideOutHorizontally(
                            targetOffsetX = { full -> full / 16 },
                            animationSpec = tween(120, easing = FastOutSlowInEasing)
                        )
            } else {
                fadeOut(animationSpec = tween(90))
            }
        }
    ) {
        composable(SettingsRoutes.Main) {
            SettingsScreen(
                viewModel = viewModel,
                state = state,
                onOpenFonts = {
                    navController.navigate(SettingsRoutes.Fonts) { launchSingleTop = true }
                },
                onOpenLicenses = {
                    navController.navigate(SettingsRoutes.Licenses) { launchSingleTop = true }
                },
                onOpenPrivacy = {
                    navController.navigate(SettingsRoutes.Privacy) { launchSingleTop = true }
                },
                onOpenAgreement = {
                    navController.navigate(SettingsRoutes.Agreement) { launchSingleTop = true }
                },
                onOpenQuickSubtitleGuide = onOpenQuickSubtitleGuide,
                onOpenRecognitionResourceSources = onOpenRecognitionResourceSources,
                onPickRecognitionResourcePackage = onPickRecognitionResourcePackage,
                onDownloadRecognitionResources = onDownloadRecognitionResources,
                onOpenKokoroSources = onOpenKokoroSources,
                onPickKokoroVoicePackage = onPickKokoroVoicePackage,
                onDownloadKokoroVoice = onDownloadKokoroVoice,
                onOpenKokoroVoiceSettings = onOpenKokoroVoiceSettings
            )
        }
        composable(SettingsRoutes.Log) {
            LogScreen(onTopBarActionsChange = onTopBarActionsChange)
        }
        composable(SettingsRoutes.Fonts) {
            FontSettingsScreen(
                onTopBarActionsChange = onFontTopBarActionsChange,
                useBuiltinFileManager = state.useBuiltinFileManager
            )
        }
        composable(SettingsRoutes.Licenses) {
            LegalDocumentScreen(
                assetPath = "legal/open_source_licenses.md"
            )
        }
        composable(SettingsRoutes.Privacy) {
            LegalDocumentScreen(
                assetPath = "legal/privacy_policy.md"
            )
        }
        composable(SettingsRoutes.Agreement) {
            LegalDocumentScreen(
                assetPath = "legal/user_agreement.md"
            )
        }
    }
}


@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    state: UiState,
    onOpenFonts: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAgreement: () -> Unit,
    onOpenQuickSubtitleGuide: () -> Unit,
    onOpenRecognitionResourceSources: () -> Unit,
    onPickRecognitionResourcePackage: () -> Unit,
    onDownloadRecognitionResources: () -> Unit,
    onOpenKokoroSources: () -> Unit,
    onPickKokoroVoicePackage: () -> Unit,
    onDownloadKokoroVoice: () -> Unit,
    onOpenKokoroVoiceSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var speakerEnrollPermissionPurposeOpen by remember { mutableStateOf(false) }
    var pendingSpeakerEnrollPermissionStep by remember { mutableIntStateOf(0) }
    val drawerModeOptions = listOf(
        UserPrefs.DRAWER_MODE_HIDDEN to "隐藏式抽屉",
        UserPrefs.DRAWER_MODE_PERMANENT to "常驻可折叠"
    )
    val inputTypeOptions = listOf(
        AudioRoutePreference.INPUT_AUTO to "自动",
        AudioRoutePreference.INPUT_BUILTIN_MIC to "内置麦克风/话筒",
        AudioRoutePreference.INPUT_USB to "USB 麦克风",
        AudioRoutePreference.INPUT_BLUETOOTH to "蓝牙麦克风",
        AudioRoutePreference.INPUT_WIRED to "有线麦克风"
    )
    val outputTypeOptions = listOf(
        AudioRoutePreference.OUTPUT_AUTO to "自动",
        AudioRoutePreference.OUTPUT_SPEAKER to "扬声器",
        AudioRoutePreference.OUTPUT_EARPIECE to "听筒",
        AudioRoutePreference.OUTPUT_BLUETOOTH to "蓝牙音频",
        AudioRoutePreference.OUTPUT_USB to "USB 音频",
        AudioRoutePreference.OUTPUT_WIRED to "有线耳机/线路"
    )
    val denoiserModeOptions = listOf(
        AudioDenoiserMode.OFF to "关闭",
        AudioDenoiserMode.RNNOISE to "智能语音降噪（RNNoise）",
        AudioDenoiserMode.SPEEX to "兼容降噪（Speex）"
    )
    val themeModeOptions = listOf(
        UserPrefs.THEME_MODE_FOLLOW_SYSTEM to "跟随系统",
        UserPrefs.THEME_MODE_LIGHT to "亮色",
        UserPrefs.THEME_MODE_DARK to "暗色"
    )
    val fontScaleBlockModeOptions = listOf(
        UserPrefs.FONT_SCALE_BLOCK_NONE to "字体和图标跟随系统",
        UserPrefs.FONT_SCALE_BLOCK_ICONS_ONLY to "固定图标大小",
        UserPrefs.FONT_SCALE_BLOCK_ALL to "固定字体和图标大小"
    )
    val audioFocusAvoidanceOptions = listOf(
        UserPrefs.AUDIO_FOCUS_AVOID_DUCK to "压低音量",
        UserPrefs.AUDIO_FOCUS_AVOID_MUTE to "静音音乐播放器",
        UserPrefs.AUDIO_FOCUS_AVOID_PAUSE to "暂停播放",
        UserPrefs.AUDIO_FOCUS_AVOID_NONE to "无"
    )
    var drawerModeExpanded by remember { mutableStateOf(false) }
    var themeModeExpanded by remember { mutableStateOf(false) }
    var overlayThemeModeExpanded by remember { mutableStateOf(false) }
    var fontScaleBlockModeExpanded by remember { mutableStateOf(false) }
    var themeColorPickerVisible by remember { mutableStateOf(false) }
    var themeToneCorrectionSuggestionVisible by remember { mutableStateOf(false) }
    var restoreQuickTextPresetDialogVisible by remember { mutableStateOf(false) }
    var restoreQuickTextSelectedGroupIds by rememberSaveable {
        mutableStateOf(defaultSelectedQuickSubtitlePresetGroupIds())
    }
    var restoreQuickTextExpandedGroupIds by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var inputTypeExpanded by remember { mutableStateOf(false) }
    var outputTypeExpanded by remember { mutableStateOf(false) }
    var denoiserModeExpanded by remember { mutableStateOf(false) }
    var audioFocusAvoidanceExpanded by remember { mutableStateOf(false) }
    var speechEnhancementExpanded by remember { mutableStateOf(false) }
    var vadModeExpanded by remember { mutableStateOf(false) }
    var showSpeakerEnrollDialog by remember { mutableStateOf(false) }
    var speakerEnrollStep by remember { mutableIntStateOf(0) } // 0准备 1句1 2句2 3句3 4结果
    var speakerEnrollCountingDown by remember { mutableStateOf(false) }
    var speakerEnrollCountdown by remember { mutableIntStateOf(3) }
    var speakerEnrollReading by remember { mutableStateOf(false) }
    var speakerEnrollRemainingSec by remember { mutableFloatStateOf(4f) }
    var speakerEnrollProgress by remember { mutableFloatStateOf(0f) }
    var speakerEnrollLevel by remember { mutableFloatStateOf(0f) }
    var speakerEnrollSuccess by remember { mutableStateOf(false) }
    var speakerEnrollMessage by remember { mutableStateOf("") }
    var speakerEnrollRetryDialog by remember { mutableStateOf(false) }
    var speakerEnrollOpenedByToggle by remember { mutableStateOf(false) }
    val speakerEnrollTexts = remember {
        listOf(
            "清晨的风吹过脸颊，我大步沿着河边走。",
            "远处钟声敲响，心跳也慢慢静下来。",
            "水面浮着天光，闭上眼，听见自己的呼吸。"
        )
    }
    val speakerEnrollSamples = remember { mutableStateListOf<FloatArray?>() }
    val drawingDirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            viewModel.setDrawingSavePathFromTreeUri(uri)
        } else {
            toast(context, "未选择目录")
        }
    }
    fun openExternalPage(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure {
            toast(context, "打开主页失败")
        }
    }
    fun startSpeakerEnrollStepCapture(step: Int) {
        if (speakerEnrollReading || speakerEnrollCountingDown) return
        if (step !in 1..3) return
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!micGranted) {
            pendingSpeakerEnrollPermissionStep = step
            speakerEnrollPermissionPurposeOpen = true
            return
        }
        scope.launch {
            speakerEnrollCountingDown = true
            speakerEnrollCountdown = 3
            speakerEnrollMessage = "请准备，第 $step 句即将开始"
            for (i in 3 downTo 1) {
                speakerEnrollCountdown = i
                delay(800)
                if (!showSpeakerEnrollDialog) {
                    speakerEnrollCountingDown = false
                    return@launch
                }
            }
            speakerEnrollCountingDown = false
            speakerEnrollReading = true
            speakerEnrollProgress = 0f
            speakerEnrollLevel = 0f
            speakerEnrollRemainingSec = 4f
            speakerEnrollMessage = "请朗读第 $step 句"
            val result = viewModel.enrollSpeakerProfileNow(
                durationSec = 4f,
                onCapture = { progress, level ->
                    speakerEnrollProgress = progress.coerceIn(0f, 1f)
                    speakerEnrollLevel = level.coerceIn(0f, 1f)
                    speakerEnrollRemainingSec = (4f * (1f - speakerEnrollProgress)).coerceAtLeast(0f)
                },
                persist = false
            )
            speakerEnrollReading = false
            speakerEnrollLevel = 0f
            if (result.success && result.profile != null) {
                val index = step - 1
                while (speakerEnrollSamples.size <= index) {
                    speakerEnrollSamples.add(null)
                }
                speakerEnrollSamples[index] = result.profile
                if (step < 3) {
                    speakerEnrollStep = step + 1
                    speakerEnrollProgress = 0f
                    speakerEnrollRemainingSec = 4f
                    speakerEnrollMessage = "第 $step 句录制成功"
                } else {
                    val collectedSamples = speakerEnrollSamples.filterNotNull()
                    if (viewModel.applySpeakerProfiles(collectedSamples)) {
                        if (speakerEnrollOpenedByToggle) {
                            viewModel.setSpeakerVerifyEnabled(true)
                        }
                        speakerEnrollSuccess = true
                        speakerEnrollStep = 4
                        speakerEnrollProgress = 1f
                        speakerEnrollMessage = "本人语音样本采集完成"
                    } else {
                        speakerEnrollSuccess = false
                        speakerEnrollStep = 4
                        speakerEnrollProgress = 0f
                        speakerEnrollMessage = "样本保存失败，请稍后重试"
                    }
                }
            } else {
                speakerEnrollSuccess = false
                speakerEnrollRetryDialog = true
                speakerEnrollMessage = result.message
            }
        }
    }
    val speakerEnrollPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val step = pendingSpeakerEnrollPermissionStep
            pendingSpeakerEnrollPermissionStep = 0
            if (granted && step in 1..3) {
                startSpeakerEnrollStepCapture(step)
            } else if (!granted) {
                toast(context, "未授予麦克风权限，无法采集说话人样本")
            }
        }
    fun closeSpeakerEnrollDialog() {
        showSpeakerEnrollDialog = false
        speakerEnrollCountingDown = false
        speakerEnrollReading = false
        val hasRegistered = state.speakerProfileReady || speakerEnrollSuccess
        if (speakerEnrollOpenedByToggle && !hasRegistered) {
            viewModel.setSpeakerVerifyEnabled(false)
        }
        speakerEnrollOpenedByToggle = false
    }
    val selectedCategoryName = viewModel.settingsSelectedCategoryName
    val selectedCategory = remember(selectedCategoryName) { SettingsCategory.valueOf(selectedCategoryName) }
    val numberReplaceOptions = remember { listOf("不替换", "数字替换为中文字符", "数字替换为中文表达") }
    var numberReplaceExpanded by remember { mutableStateOf(false) }
    val isSystemTtsSelected = isSystemTtsVoiceDir(state.voiceDir)
    val isKokoroTtsSelected = isKokoroVoiceDir(state.voiceDir)

    LaunchedEffect(selectedCategory) {
        scroll.animateScrollTo(0)
    }

    if (themeColorPickerVisible) {
        ThemeColorPickerDialog(
            title = "应用主题色",
            initialColor = Color(state.themeColorArgb),
            onDismissRequest = { themeColorPickerVisible = false },
            onColorSelected = { color ->
                val selectedArgb = color.toArgb()
                viewModel.setThemeColorArgb(selectedArgb)
                themeColorPickerVisible = false
                themeToneCorrectionSuggestionVisible =
                    !state.themeToneCorrectionEnabled &&
                    ThemeColorResolver.shouldSuggestToneCorrection(selectedArgb)
            }
        )
    }

    if (themeToneCorrectionSuggestionVisible) {
        val dialogActionColor = MaterialTheme.colorScheme.onSurface
        KigttsAlertDialog(
            onDismissRequest = { themeToneCorrectionSuggestionVisible = false },
            title = { Text("主题色可能影响可读性") },
            text = {
                Text(
                    "当前颜色在亮色或暗色界面中可能过亮或过暗。是否开启主题色色调修正，自动生成适合当前亮暗模式的控件色和强调文字色？"
                )
            },
            confirmButton = {
                Md2TextButton(
                    onClick = {
                        viewModel.setThemeToneCorrectionEnabled(true)
                        themeToneCorrectionSuggestionVisible = false
                    },
                    contentColor = dialogActionColor
                ) {
                    Text("开启修正")
                }
            },
            dismissButton = {
                Md2TextButton(
                    onClick = { themeToneCorrectionSuggestionVisible = false },
                    contentColor = dialogActionColor
                ) {
                    Text("保持原色")
                }
            }
        )
    }

    if (restoreQuickTextPresetDialogVisible) {
        val presetGroups = remember { defaultQuickSubtitlePresetGroups() }
        Dialog(
            onDismissRequest = { restoreQuickTextPresetDialogVisible = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 40.dp)
                        .widthIn(max = 560.dp)
                        .heightIn(max = 640.dp),
                    shape = RoundedCornerShape(UiTokens.Radius),
                    color = md2CardContainerColor(),
                    elevation = UiTokens.CardElevation
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            "恢复文本预设分组",
                            style = MaterialTheme.typography.h6,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "选择要添加的分组。同名分组会作为新的导入分组添加，不会合并到现有分组。",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QuickSubtitlePresetGroupSelectionList(
                                groups = presetGroups,
                                selectedGroupIds = restoreQuickTextSelectedGroupIds,
                                expandedGroupIds = restoreQuickTextExpandedGroupIds,
                                onToggleSelected = { groupId ->
                                    restoreQuickTextSelectedGroupIds =
                                        if (groupId in restoreQuickTextSelectedGroupIds) {
                                            restoreQuickTextSelectedGroupIds - groupId
                                        } else {
                                            restoreQuickTextSelectedGroupIds + groupId
                                        }
                                },
                                onToggleExpanded = { groupId ->
                                    restoreQuickTextExpandedGroupIds =
                                        if (groupId in restoreQuickTextExpandedGroupIds) {
                                            restoreQuickTextExpandedGroupIds - groupId
                                        } else {
                                            restoreQuickTextExpandedGroupIds + groupId
                                        }
                                }
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Md2TextButton(onClick = { restoreQuickTextPresetDialogVisible = false }) {
                                Text("取消")
                            }
                            Spacer(Modifier.width(8.dp))
                            Md2TextButton(
                                onClick = {
                                    viewModel.installDefaultQuickSubtitlePresetGroups(restoreQuickTextSelectedGroupIds)
                                    restoreQuickTextPresetDialogVisible = false
                                },
                                enabled = restoreQuickTextSelectedGroupIds.isNotEmpty()
                            ) {
                                Text("添加")
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun AboutContributorItem(
        avatarRes: Int,
        name: String,
        homepage: String,
        modifier: Modifier = Modifier,
        avatarSize: Dp = 54.dp
    ) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(id = avatarRes),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        shape = CircleShape
                    )
            )
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                Md2IconButton(
                    icon = "open_in_new",
                    contentDescription = "打开${name}主页",
                    onClick = { openExternalPage(homepage) }
                )
            }
        }
    }

    @Composable
    fun AboutDocumentRow(
        title: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        showDivider: Boolean = true
    ) {
        val hapticOnClick = rememberKigttsHapticClick(onClick)
        Column(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(bounded = true),
                        onClick = hapticOnClick
                    )
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                MsIcon("chevron_right", contentDescription = null)
            }
            if (showDivider) {
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            }
        }
    }

    @Composable
    fun AboutSettingsContent() {
        val configuration = LocalConfiguration.current
        val isPortrait = configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val packageInfo = remember(context) {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()
        }
        val versionLabel = remember(packageInfo) {
            val versionName = packageInfo?.versionName ?: "未知版本"
            "版本 $versionName"
        }
        val openOfficialWebsite = rememberKigttsHapticClick {
            if (
                !openChromeCustomTab(context, KIGTTS_OFFICIAL_WEBSITE) &&
                !openExternalBrowser(context, KIGTTS_OFFICIAL_WEBSITE)
            ) {
                toast(context, "无法打开官网")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Md2StaggeredFloatIn(index = 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(UiTokens.Radius),
                    backgroundColor = md2CardContainerColor(),
                    elevation = UiTokens.CardElevation
                ) {
                    val logoRes = if (currentAppDarkTheme()) R.drawable.logo_white else R.drawable.logo_black
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(id = logoRes),
                            contentDescription = "KIGTTS Logo",
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(bounded = true),
                                    onClick = openOfficialWebsite
                                ),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            text = versionLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Md2StaggeredFloatIn(index = 1) {
                Md2SettingsCard(title = "软件制作") {
                    if (isPortrait) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AboutContributorItem(
                                avatarRes = R.drawable.avatar_lht,
                                name = "LHT",
                                homepage = "https://space.bilibili.com/87244951",
                                modifier = Modifier.fillMaxWidth(),
                                avatarSize = 60.dp
                            )
                            AboutContributorItem(
                                avatarRes = R.drawable.avatar_yuilu,
                                name = "Yui Lu",
                                homepage = "https://space.bilibili.com/23208863",
                                modifier = Modifier.fillMaxWidth(),
                                avatarSize = 60.dp
                            )
                            AboutContributorItem(
                                avatarRes = R.drawable.avatar_huajiang,
                                name = "花酱",
                                homepage = "https://space.bilibili.com/573842321",
                                modifier = Modifier.fillMaxWidth(),
                                avatarSize = 60.dp
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AboutContributorItem(
                                avatarRes = R.drawable.avatar_lht,
                                name = "LHT",
                                homepage = "https://space.bilibili.com/87244951",
                                modifier = Modifier.weight(1f),
                                avatarSize = 48.dp
                            )
                            AboutContributorItem(
                                avatarRes = R.drawable.avatar_yuilu,
                                name = "Yui Lu",
                                homepage = "https://space.bilibili.com/23208863",
                                modifier = Modifier.weight(1f),
                                avatarSize = 48.dp
                            )
                            AboutContributorItem(
                                avatarRes = R.drawable.avatar_huajiang,
                                name = "花酱",
                                homepage = "https://space.bilibili.com/573842321",
                                modifier = Modifier.weight(1f),
                                avatarSize = 48.dp
                            )
                        }
                    }
                }
            }

            Md2StaggeredFloatIn(index = 2) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(UiTokens.Radius),
                    backgroundColor = md2CardContainerColor(),
                    elevation = UiTokens.CardElevation
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        AboutDocumentRow(
                            title = "开源许可证",
                            onClick = onOpenLicenses,
                            showDivider = false
                        )
                        AboutDocumentRow(
                            title = "隐私政策",
                            onClick = onOpenPrivacy,
                            showDivider = false
                        )
                        AboutDocumentRow(
                            title = "用户协议",
                            onClick = onOpenAgreement,
                            showDivider = false
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun RecognitionSettingsContent() {
        var showMicrophoneResetConfirmation by rememberSaveable { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Md2StaggeredFloatIn(index = 0) {
                Md2SettingsCard(title = "语音识别资源包") {
                    Text(
                        text = state.recognitionResourceStatus,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (state.recognitionResourceInstalled) {
                        Text(
                            text = buildString {
                                append("当前资源：")
                                append(state.recognitionResourceName)
                                if (state.recognitionResourceVersion.isNotBlank()) {
                                    append(" / ")
                                    append(state.recognitionResourceVersion)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "安装后可使用语音识别、智能断句和语音降噪增强。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.recognitionResourceBusy) {
                        val progress = state.recognitionResourceProgress
                        if (progress in 0f..1f) {
                            LinearProgressIndicator(
                                progress = progress.coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            text = state.recognitionResourceProgressStage.ifBlank { "处理中" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Md2Button(
                            onClick = onDownloadRecognitionResources,
                            enabled = !state.recognitionResourceBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("下载资源包")
                        }
                        Md2OutlinedButton(
                            onClick = onPickRecognitionResourcePackage,
                            enabled = !state.recognitionResourceBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("本地安装")
                        }
                    }
                    Md2TextButton(
                        onClick = onOpenRecognitionResourceSources,
                        enabled = !state.recognitionResourceBusy
                    ) {
                        Text("管理下载源")
                    }
                }
            }

            Md2StaggeredFloatIn(index = 1) {
                Md2SettingsCard(title = "识别行为") {
                    Md2SettingSwitchRow(
                        title = "自动显示识别结果",
                        checked = state.asrSendToQuickSubtitle,
                        onCheckedChange = { viewModel.setAsrSendToQuickSubtitle(it) },
                        supportingText = "识别完成后，自动更新便捷字幕的大字幕内容。"
                    )
                    Md2SettingSwitchRow(
                        title = "按住说话",
                        checked = state.pushToTalkMode,
                        onCheckedChange = { viewModel.setPushToTalkMode(it) },
                        supportingText = "按住麦克风按钮开始收音，松开后完成识别。"
                    )
                    Md2SettingSwitchRow(
                        title = "快捷文本联动音效板",
                        checked = state.allowQuickTextTriggerSoundboard,
                        onCheckedChange = { viewModel.setAllowQuickTextTriggerSoundboard(it) },
                        supportingText = "快捷文本中包含音效触发词时，同时播放对应音效。"
                    )
                    Md2SettingSwitchRow(
                        title = "新快捷文本优先朗读",
                        checked = state.quickSubtitleInterruptQueue,
                        onCheckedChange = { viewModel.setQuickSubtitleInterruptQueue(it) },
                        supportingText = "点击新条目时停止当前朗读并立即播放。"
                    )
                    Md2SettingSwitchRow(
                        title = "按住说话确认模式",
                        checked = state.pushToTalkConfirmInputMode,
                        enabled = state.pushToTalkMode,
                        onCheckedChange = { viewModel.setPushToTalkConfirmInputMode(it) },
                        supportingText = "松手前预览识别文字，也可转入输入框或取消。"
                    )
                    Md2SettingSwitchRow(
                        title = "播放时暂停识别",
                        checked = state.muteWhilePlaying,
                        onCheckedChange = { viewModel.setMuteWhilePlaying(it) },
                        supportingText = "朗读或播放音效期间暂时停止语音识别。"
                    )
                    Text(
                        "恢复识别延迟：${String.format("%.1f", state.muteWhilePlayingDelaySec)} 秒",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = state.muteWhilePlayingDelaySec,
                        onValueChange = { viewModel.setMuteWhilePlayingDelay(it) },
                        valueRange = 0f..5f
                    )
                    Text(
                        "最低识别音量：${state.minVolumePercent}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = state.minVolumePercent.toFloat(),
                        onValueChange = { viewModel.setMinVolumePercent(it.toInt()) },
                        valueRange = 0f..100f
                    )
                    Md2SettingDropdownRow(
                        title = "语音降噪增强",
                        value = SpeechEnhancementMode.labelOf(state.speechEnhancementMode),
                        expanded = speechEnhancementExpanded,
                        onExpandedChange = { speechEnhancementExpanded = it },
                        supportingText = when (state.speechEnhancementMode) {
                            SpeechEnhancementMode.GTCRN_OFFLINE -> "一句话结束后再处理，效果稳定，但会增加少量延迟。"
                            SpeechEnhancementMode.GTCRN_STREAMING -> "边收音边降噪，适合实时字幕，资源占用较低。"
                            SpeechEnhancementMode.DPDFNET2_STREAMING -> "实时增强降噪，效果更强，资源占用中等。"
                            SpeechEnhancementMode.DPDFNET4_STREAMING -> "实时强力降噪，效果更好，但更耗性能和电量。"
                            else -> "不使用额外的语音降噪增强。"
                        }
                    ) {
                        SpeechEnhancementMode.options.forEach { (value, label) ->
                            M2DropdownMenuItem(
                                onClick = {
                                    speechEnhancementExpanded = false
                                    viewModel.setSpeechEnhancementMode(value)
                                }
                            ) { Text(label) }
                        }
                    }
                    val currentVadMode = VadMode.fromFlags(state.classicVadEnabled, state.sileroVadEnabled)
                    Md2SettingDropdownRow(
                        title = "自动判断说话起止",
                        value = VadMode.labelOf(currentVadMode),
                        expanded = vadModeExpanded,
                        onExpandedChange = { vadModeExpanded = it },
                        supportingText = when (currentVadMode) {
                            VadMode.SILERO -> "使用智能模型判断说话起止，对轻声和持续噪声更稳定。"
                            VadMode.HYBRID -> "结合音量和智能模型判断，兼顾响应速度与断句准确性。"
                            else -> "根据音量和静音时长判断说话起止。"
                        }
                    ) {
                        VadMode.options.forEach { (value, label) ->
                            M2DropdownMenuItem(
                                onClick = {
                                    vadModeExpanded = false
                                    viewModel.setVadMode(value)
                                }
                            ) { Text(label) }
                        }
                    }
                    val sileroVadControlsEnabled = state.sileroVadEnabled
                    Text(
                        "说话触发门槛：${String.format("%.2f", state.sileroVadThreshold)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(
                            alpha = if (sileroVadControlsEnabled) 1f else 0.38f
                        )
                    )
                    Slider(
                        value = state.sileroVadThreshold,
                        onValueChange = { viewModel.setSileroVadThreshold(it) },
                        valueRange = UserPrefs.SILERO_VAD_MIN_THRESHOLD..UserPrefs.SILERO_VAD_MAX_THRESHOLD,
                        steps = 17,
                        enabled = sileroVadControlsEnabled,
                        colors = SliderDefaults.colors(
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )
                    Text(
                        "越低越容易触发；轻声吞首字可先试 0.35-0.45。",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(
                            alpha = if (sileroVadControlsEnabled) 0.74f else 0.38f
                        )
                    )
                    Text(
                        "保留触发前声音：${state.sileroVadPreRollMs} 毫秒",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(
                            alpha = if (sileroVadControlsEnabled) 1f else 0.38f
                        )
                    )
                    Slider(
                        value = state.sileroVadPreRollMs.toFloat(),
                        onValueChange = { viewModel.setSileroVadPreRollMs(it.roundToInt()) },
                        valueRange = UserPrefs.SILERO_VAD_MIN_PRE_ROLL_MS.toFloat()..
                            UserPrefs.SILERO_VAD_MAX_PRE_ROLL_MS.toFloat(),
                        steps = 15,
                        enabled = sileroVadControlsEnabled,
                        colors = SliderDefaults.colors(
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )
                    Text(
                        "保留开始说话前的一小段声音，减少首字缺失；时间过长可能带入更多环境音。",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(
                            alpha = if (sileroVadControlsEnabled) 0.74f else 0.38f
                        )
                    )
                    Md2SettingDropdownRow(
                        title = "数字读法",
                        value = numberReplaceOptions.getOrElse(state.numberReplaceMode) { numberReplaceOptions[0] },
                        expanded = numberReplaceExpanded,
                        onExpandedChange = { numberReplaceExpanded = it },
                        supportingText = "选择数字的朗读方式，例如“二零零零”或“两千”。"
                    ) {
                        numberReplaceOptions.forEachIndexed { idx, label ->
                            M2DropdownMenuItem(
                                onClick = {
                                    numberReplaceExpanded = false
                                    viewModel.setNumberReplaceMode(idx)
                                }
                            ) { Text(label) }
                        }
                    }
                }
            }

            Md2StaggeredFloatIn(index = 2) {
                Md2SettingsCard(title = "指定说话人") {
                    Md2SettingSwitchRow(
                        title = "仅响应指定说话人",
                        checked = state.speakerVerifyEnabled,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                viewModel.setSpeakerVerifyEnabled(false)
                            } else if (state.speakerProfileReady) {
                                viewModel.setSpeakerVerifyEnabled(true)
                            } else {
                                speakerEnrollSamples.clear()
                                speakerEnrollStep = 0
                                speakerEnrollCountingDown = false
                                speakerEnrollCountdown = 3
                                speakerEnrollReading = false
                                speakerEnrollProgress = 0f
                                speakerEnrollLevel = 0f
                                speakerEnrollRemainingSec = 4f
                                speakerEnrollSuccess = false
                                speakerEnrollMessage = "请按页面引导完成本人样本采集。"
                                speakerEnrollRetryDialog = false
                                speakerEnrollOpenedByToggle = true
                                showSpeakerEnrollDialog = true
                            }
                        },
                        supportingText = "已采集样本：${state.speakerProfiles.size}/3"
                    )
                    state.speakerProfiles.forEachIndexed { idx, profile ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            backgroundColor = md2CardContainerColor(),
                            elevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = profile.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Md2IconButton(
                                    icon = "delete",
                                    contentDescription = "删除样本",
                                    onClick = { viewModel.removeSpeakerProfileAt(idx) }
                                )
                            }
                        }
                    }
                    Text(
                        "识别严格程度：${String.format("%.2f", state.speakerVerifyThreshold)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = state.speakerVerifyThreshold,
                        onValueChange = { viewModel.setSpeakerVerifyThreshold(it) },
                        valueRange = 0.05f..0.95f
                    )
                    if (state.speakerLastSimilarity >= 0f) {
                        Text(
                            "最近一次匹配程度：${String.format("%.2f", state.speakerLastSimilarity)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Md2OutlinedButton(onClick = {
                            speakerEnrollSamples.clear()
                            speakerEnrollStep = 0
                            speakerEnrollCountingDown = false
                            speakerEnrollCountdown = 3
                            speakerEnrollReading = false
                            speakerEnrollProgress = 0f
                            speakerEnrollLevel = 0f
                            speakerEnrollRemainingSec = 4f
                            speakerEnrollSuccess = false
                            speakerEnrollMessage = "请按页面引导完成本人样本采集。"
                            speakerEnrollRetryDialog = false
                            speakerEnrollOpenedByToggle = false
                            showSpeakerEnrollDialog = true
                        }) {
                            Text(if (state.speakerProfiles.isEmpty()) "采集本人样本" else "重新采集样本")
                        }
                        Md2TextButton(onClick = { viewModel.clearSpeakerProfile() }) {
                            Text("清空样本")
                        }
                    }
                }
            }
            Md2StaggeredFloatIn(index = 3) {
                Md2SettingsCard(title = "音频设备状态") {
                    val realtimeInputLevel = viewModel.realtimeInputLevel
                    Text("输入音量", fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = realtimeInputLevel.coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 8.dp)
                    )
                    Text("当前麦克风：${state.inputDeviceLabel}", style = MaterialTheme.typography.bodySmall)
                    Text("当前播放设备：${state.outputDeviceLabel}", style = MaterialTheme.typography.bodySmall)
                }
            }

            Md2StaggeredFloatIn(index = 4) {
                Md2SettingsCard(title = "回声与降噪") {
                    Md2SettingSwitchRow(
                        title = "系统回声抑制",
                        checked = state.echoSuppression,
                        onCheckedChange = { viewModel.setEchoSuppression(it) },
                        supportingText = "使用设备提供的回声和噪声抑制能力，效果因设备而异。"
                    )
                    Md2SettingSwitchRow(
                        title = "增强通话降噪",
                        checked = state.communicationMode,
                        onCheckedChange = { viewModel.setCommunicationMode(it) },
                        supportingText = "使用系统通话模式改善麦克风收音。"
                    )
                    Md2SettingSwitchRow(
                        title = "增强回声消除（AEC3）",
                        checked = state.aec3Enabled,
                        onCheckedChange = { viewModel.setAec3Enabled(it) },
                        supportingText = "减少外放声音被麦克风再次识别；出现声音异常时可关闭此项。"
                    )
                    Md2SettingDropdownRow(
                        title = "降噪方式",
                        value = denoiserModeOptions.firstOrNull { it.first == state.denoiserMode }?.second
                            ?: denoiserModeOptions.first().second,
                        expanded = denoiserModeExpanded,
                        onExpandedChange = { denoiserModeExpanded = it },
                        supportingText = "智能语音降噪效果更好；兼容降噪适合性能较弱的设备。"
                    ) {
                        denoiserModeOptions.forEach { (value, label) ->
                            M2DropdownMenuItem(
                                onClick = {
                                    denoiserModeExpanded = false
                                    viewModel.setDenoiserMode(value)
                                }
                            ) { Text(label) }
                        }
                    }
                    Text("回声消除状态：${state.aec3Status}", style = MaterialTheme.typography.bodySmall)
                    Md2TextButton(
                        onClick = { showMicrophoneResetConfirmation = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MsIcon("settings_backup_restore", contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("恢复默认值")
                    }
                }
            }

            Md2StaggeredFloatIn(index = 5) {
                Md2SettingsCard(title = "音频设备选择") {
                    Md2SettingDropdownRow(
                        title = "首选麦克风",
                        value = inputTypeOptions.firstOrNull { it.first == state.preferredInputType }?.second
                            ?: inputTypeOptions.first().second,
                        expanded = inputTypeExpanded,
                        onExpandedChange = { inputTypeExpanded = it },
                        supportingText = "连接多个录音设备时，优先使用此类型。"
                    ) {
                        inputTypeOptions.forEach { (value, label) ->
                            M2DropdownMenuItem(
                                onClick = {
                                    inputTypeExpanded = false
                                    viewModel.setPreferredInputType(value)
                                }
                            ) { Text(label) }
                        }
                    }
                    Md2SettingDropdownRow(
                        title = "首选播放设备",
                        value = outputTypeOptions.firstOrNull { it.first == state.preferredOutputType }?.second
                            ?: outputTypeOptions.first().second,
                        expanded = outputTypeExpanded,
                        onExpandedChange = { outputTypeExpanded = it },
                        supportingText = "连接多个播放设备时，优先使用此类型。"
                    ) {
                        outputTypeOptions.forEach { (value, label) ->
                            M2DropdownMenuItem(
                                onClick = {
                                    outputTypeExpanded = false
                                    viewModel.setPreferredOutputType(value)
                                }
                            ) { Text(label) }
                        }
                    }
                }
            }

            Md2StaggeredFloatIn(index = 6) {
                Md2SettingsCard(title = "音频测试") {
                    Text("当前状态：${state.audioTestStatus}", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = state.audioTestLevel.coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 10.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.audioTestRecording) {
                            Md2Button(
                                onClick = { viewModel.stopAudioTestRecording() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("停止录音")
                            }
                        } else {
                            Md2Button(
                                onClick = { viewModel.startAudioTestRecording() },
                                modifier = Modifier.weight(1f),
                                enabled = !state.audioTestPlaying
                            ) {
                                Text("开始录音")
                            }
                        }
                        if (state.audioTestPlaying) {
                            Md2OutlinedButton(
                                onClick = { viewModel.stopAudioTestPlayback() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("停止回放")
                            }
                        } else {
                            Md2OutlinedButton(
                                onClick = { viewModel.startAudioTestPlayback() },
                                modifier = Modifier.weight(1f),
                                enabled = state.audioTestHasClip && !state.audioTestRecording
                            ) {
                                Text("回放测试")
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Md2TextButton(
                            onClick = { viewModel.clearAudioTest() },
                            enabled = state.audioTestHasClip && !state.audioTestRecording && !state.audioTestPlaying
                        ) {
                            Text("清空录音")
                        }
                    }
                    Text(
                        "用于测试当前麦克风收音和本地回放。回放会应用当前语音降噪设置，但不会触发识别或朗读。测试前请先停止正在进行的语音识别。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        if (showMicrophoneResetConfirmation) {
            KigttsAlertDialog(
                onDismissRequest = { showMicrophoneResetConfirmation = false },
                title = { Text("恢复麦克风设置") },
                text = {
                    Text("将首选麦克风、回声处理和降噪方式恢复为默认值？")
                },
                confirmButton = {
                    Md2TextButton(
                        onClick = {
                            showMicrophoneResetConfirmation = false
                            viewModel.resetMicrophoneSettingsToDefaults()
                        }
                    ) { Text("恢复") }
                },
                dismissButton = {
                    Md2TextButton(
                        onClick = { showMicrophoneResetConfirmation = false }
                    ) { Text("取消") }
                }
            )
        }
    }

    @Composable
    fun AudioSettingsContent() {
        var showTtsResetConfirmation by rememberSaveable { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Md2StaggeredFloatIn(index = 0) {
                Md2SettingsCard(title = "语音合成与播放") {
                    Text(
                        "当前语音合成引擎：${when {
                            isSystemTtsSelected -> SYSTEM_TTS_DEFAULT_LABEL
                            isKokoroTtsSelected -> "Kokoro"
                            else -> "语音包"
                        }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Md2SettingSwitchRow(
                        title = "关闭语音朗读",
                        checked = state.ttsDisabled,
                        onCheckedChange = { viewModel.setTtsDisabled(it) },
                        supportingText = "开启后只显示字幕，不再朗读；音效板仍可使用。"
                    )
                    Text("朗读与音效音量：${state.playbackGainPercent}%", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = state.playbackGainPercent.toFloat(),
                        onValueChange = { viewModel.setPlaybackGainPercent(it.toInt()) },
                        valueRange = 0f..1000f
                    )
                    Text("100% 为原始音量，拖动接近 100% 时会自动吸附。", style = MaterialTheme.typography.bodySmall)
                    Md2SettingDropdownRow(
                        title = "播放时如何处理其他应用的声音",
                        value = audioFocusAvoidanceOptions
                            .firstOrNull { it.first == state.audioFocusAvoidanceMode }
                            ?.second
                            ?: "无",
                        expanded = audioFocusAvoidanceExpanded,
                        onExpandedChange = { audioFocusAvoidanceExpanded = it },
                        supportingText = "朗读或播放音效时，可降低、静音或暂停其他应用的声音。"
                    ) {
                        audioFocusAvoidanceOptions.forEach { (value, label) ->
                            M2DropdownMenuItem(
                                onClick = {
                                    audioFocusAvoidanceExpanded = false
                                    viewModel.setAudioFocusAvoidanceMode(value)
                                }
                            ) { Text(label) }
                        }
                    }
                    if (isSystemTtsSelected) {
                        Text(
                            "系统语音合成使用设备已安装的引擎和音色；部分语音包专属参数不会生效。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Md2Button(
                            onClick = {
                                viewModel.openSystemTtsSetup(context)
                            }
                        ) {
                            Text("打开系统语音合成设置")
                        }
                    } else if (isKokoroTtsSelected) {
                        Text(
                            "Kokoro 使用独立的声音编号；部分语音包专属参数不会生效。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "当前声音编号：${state.kokoroSpeakerId.coerceIn(UserPrefs.KOKORO_MIN_SPEAKER_ID, UserPrefs.KOKORO_MAX_SPEAKER_ID)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Md2Button(
                            onClick = onOpenKokoroVoiceSettings,
                            enabled = state.kokoroInstalled && !state.kokoroBusy
                        ) {
                            Text("选择 Kokoro 音色")
                        }
                    } else {
                        Text("语调起伏：${String.format("%.3f", state.piperNoiseScale)}", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = state.piperNoiseScale,
                            onValueChange = { viewModel.setPiperNoiseScale(it) },
                            valueRange = 0f..2f
                        )
                        Text(
                            "语调力度：${String.format("%.3f", state.piperNoiseW)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = state.piperNoiseW,
                            onValueChange = { viewModel.setPiperNoiseW(it) },
                            valueRange = 0.3f..1.5f
                        )
                    }
                    Text(
                        if (isSystemTtsSelected) {
                            "语速（数值越大越慢）：${String.format("%.3f", state.piperLengthScale)}"
                        } else {
                            "语速（数值越大越慢）：${String.format("%.3f", state.piperLengthScale)}"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = state.piperLengthScale,
                        onValueChange = { viewModel.setPiperLengthScale(it) },
                        valueRange = 0.1f..5f
                    )
                    Text(
                        if (isSystemTtsSelected) {
                            "句末停顿：${String.format("%.2f", state.piperSentenceSilence)} 秒"
                        } else {
                            "句末停顿：${String.format("%.2f", state.piperSentenceSilence)} 秒"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = state.piperSentenceSilence,
                        onValueChange = { viewModel.setPiperSentenceSilence(it) },
                        valueRange = 0f..2f
                    )
                    Md2TextButton(
                        onClick = { showTtsResetConfirmation = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MsIcon("settings_backup_restore", contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("恢复默认值")
                    }
                }
            }

            Md2StaggeredFloatIn(index = 1) {
                Md2SettingsCard(title = "Kokoro 离线朗读") {
                    Text(
                        text = state.kokoroStatus,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Kokoro 是一套可选的离线朗读声音。安装后，你可以在“语音包”页面选择 Kokoro，并在设置或语音包页面切换不同声音用于朗读。\n\n由于资源体积较大，Kokoro 需要单独下载或从本地安装，不会直接内置在安装包中。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.kokoroBusy) {
                        val progress = state.kokoroProgress
                        if (progress in 0f..1f) {
                            LinearProgressIndicator(
                                progress = progress.coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Text(
                            text = state.kokoroProgressStage.ifBlank { "处理中" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Md2Button(
                            onClick = onDownloadKokoroVoice,
                            enabled = !state.kokoroBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (state.kokoroInstalled) "重新下载" else "下载 Kokoro")
                        }
                        Md2OutlinedButton(
                            onClick = onPickKokoroVoicePackage,
                            enabled = !state.kokoroBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("本地安装")
                        }
                    }
                    Md2TextButton(
                        onClick = onOpenKokoroSources,
                        enabled = !state.kokoroBusy
                    ) {
                        Text("下载源设置")
                    }
                }
            }


            Md2StaggeredFloatIn(index = 2) {
                Md2SettingsCard(title = "音频设备选择") {
                    Md2SettingDropdownRow(
                        title = "首选麦克风",
                        value = inputTypeOptions.firstOrNull { it.first == state.preferredInputType }?.second
                            ?: inputTypeOptions.first().second,
                        expanded = inputTypeExpanded,
                        onExpandedChange = { inputTypeExpanded = it },
                        supportingText = "连接多个录音设备时，优先使用此类型。"
                    ) {
                        inputTypeOptions.forEach { (value, label) ->
                            M2DropdownMenuItem(
                                onClick = {
                                    inputTypeExpanded = false
                                    viewModel.setPreferredInputType(value)
                                }
                            ) { Text(label) }
                        }
                    }
                    Md2SettingDropdownRow(
                        title = "首选播放设备",
                        value = outputTypeOptions.firstOrNull { it.first == state.preferredOutputType }?.second
                            ?: outputTypeOptions.first().second,
                        expanded = outputTypeExpanded,
                        onExpandedChange = { outputTypeExpanded = it },
                        supportingText = "连接多个播放设备时，优先使用此类型。"
                    ) {
                        outputTypeOptions.forEach { (value, label) ->
                            M2DropdownMenuItem(
                                onClick = {
                                    outputTypeExpanded = false
                                    viewModel.setPreferredOutputType(value)
                                }
                            ) { Text(label) }
                        }
                    }
                }
            }

        }
        if (showTtsResetConfirmation) {
            KigttsAlertDialog(
                onDismissRequest = { showTtsResetConfirmation = false },
                title = { Text("恢复语音合成设置") },
                text = { Text("将朗读、播放和语音合成参数恢复为默认值？") },
                confirmButton = {
                    Md2TextButton(
                        onClick = {
                            showTtsResetConfirmation = false
                            viewModel.resetTtsSettingsToDefaults()
                        }
                    ) { Text("恢复") }
                },
                dismissButton = {
                    Md2TextButton(
                        onClick = { showTtsResetConfirmation = false }
                    ) { Text("取消") }
                }
            )
        }
    }

    @Composable
    fun SystemSettingsContent() {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Md2StaggeredFloatIn(index = 0) {
                Md2SettingsCard(title = "显示与主题") {
                    Md2SettingDropdownRow(
                        title = "主题模式",
                        value = themeModeOptions.firstOrNull { it.first == state.themeMode }?.second
                            ?: themeModeOptions.first().second,
                        expanded = themeModeExpanded,
                        onExpandedChange = { themeModeExpanded = it },
                        supportingText = "默认跟随系统，仅影响主软件界面。"
                    ) {
                        themeModeOptions.forEach { (value, label) ->
                            M2DropdownMenuItem(
                                onClick = {
                                    themeModeExpanded = false
                                    viewModel.setThemeMode(value)
                                }
                            ) { Text(label) }
                        }
                    }
                    ThemeColorSettingRow(
                        colorArgb = state.themeColorArgb,
                        onClick = { themeColorPickerVisible = true }
                    )
                    FontSettingEntryRow(
                        usingSystemFont = state.appFontId == "system",
                        onClick = onOpenFonts
                    )
                    Md2SettingSwitchRow(
                        title = "使用系统文本编辑菜单",
                        checked = state.useSystemTextToolbar,
                        onCheckedChange = { viewModel.setUseSystemTextToolbar(it) },
                        supportingText = "复制、粘贴等操作使用 Android 默认样式；弹窗始终使用系统菜单。"
                    )
                    Md2SettingSwitchRow(
                        title = "悬浮窗使用默认字体",
                        checked = state.floatingOverlayUseSystemFont,
                        onCheckedChange = { viewModel.setFloatingOverlayUseSystemFont(it) },
                        supportingText = "开启后，悬浮窗不再使用应用内选择的自定义字体。"
                    )
                    Md2SettingSwitchRow(
                        title = "自动优化主题色",
                        checked = state.themeToneCorrectionEnabled,
                        onCheckedChange = { viewModel.setThemeToneCorrectionEnabled(it) },
                        supportingText = "自动调整过亮或过暗的颜色，让文字和按钮保持清晰。"
                    )
                    Md2SettingDropdownRow(
                        title = "悬浮窗外观",
                        value = themeModeOptions.firstOrNull { it.first == state.overlayThemeMode }?.second
                            ?: themeModeOptions.first().second,
                        expanded = overlayThemeModeExpanded,
                        onExpandedChange = { overlayThemeModeExpanded = it },
                        supportingText = "单独选择悬浮窗使用亮色、暗色或跟随系统。"
                    ) {
                        themeModeOptions.forEach { (value, label) ->
                            M2DropdownMenuItem(
                                onClick = {
                                    overlayThemeModeExpanded = false
                                    viewModel.setOverlayThemeMode(value)
                                }
                            ) { Text(label) }
                        }
                    }
                    Md2SettingDropdownRow(
                        title = "字体与图标缩放",
                        value = fontScaleBlockModeOptions
                            .firstOrNull { it.first == state.fontScaleBlockMode }?.second
                            ?: fontScaleBlockModeOptions[1].second,
                        expanded = fontScaleBlockModeExpanded,
                        onExpandedChange = { fontScaleBlockModeExpanded = it },
                        supportingText = "控制应用字体和图标是否跟随系统显示大小。"
                    ) {
                        fontScaleBlockModeOptions.forEach { (value, label) ->
                            M2DropdownMenuItem(
                                onClick = {
                                    fontScaleBlockModeExpanded = false
                                    viewModel.setFontScaleBlockMode(value)
                                }
                            ) { Text(label) }
                        }
                    }
                    Md2SettingSwitchRow(
                        title = "使用主题色顶栏",
                        checked = state.solidTopBar,
                        onCheckedChange = { viewModel.setSolidTopBar(it) },
                        supportingText = "让顶栏和状态栏使用当前主题配色。"
                    )
                }
            }
            Md2StaggeredFloatIn(index = 1) {
                Md2SettingsCard(title = "布局与交互") {
                    Md2SettingSwitchRow(
                        title = "触感反馈",
                        checked = state.hapticFeedbackEnabled,
                        onCheckedChange = { viewModel.setHapticFeedbackEnabled(it) },
                        supportingText = "操作按钮或切换快捷字幕分组时提供轻微振动。"
                    )
                    Md2SettingDropdownRow(
                        title = "横屏侧栏样式",
                        value = drawerModeOptions.firstOrNull { it.first == state.landscapeDrawerMode }?.second
                            ?: drawerModeOptions.first().second,
                        expanded = drawerModeExpanded,
                        onExpandedChange = { drawerModeExpanded = it },
                        supportingText = "选择横屏时侧栏保持隐藏，或常驻并支持折叠。"
                    ) {
                        drawerModeOptions.forEach { (value, label) ->
                            M2DropdownMenuItem(
                                onClick = {
                                    drawerModeExpanded = false
                                    viewModel.setLandscapeDrawerMode(value)
                                }
                            ) { Text(label) }
                        }
                    }
                    Md2SettingSwitchRow(
                        title = "横屏显示完整分组名称",
                        checked = state.forceFullWidthTabsOnPhone,
                        onCheckedChange = { viewModel.setForceFullWidthTabsOnPhone(it) },
                        supportingText = "在手机横屏时，便捷字幕和音效板的分组同时显示图标与名称。"
                    )
                    Md2SettingSwitchRow(
                        title = "音效板网格铺满屏幕",
                        checked = state.soundboardGridFullWidth,
                        onCheckedChange = { viewModel.setSoundboardGridFullWidth(it) },
                        supportingText = "网格模式使用全部可用宽度；列表模式不受影响。"
                    )
                }
            }
            Md2StaggeredFloatIn(index = 3) {
                Md2SettingsCard(title = "便捷字幕显示") {
                    QuickSubtitleGuideSettingsEntry(onClick = onOpenQuickSubtitleGuide)
                    val openRestorePresets = rememberKigttsHapticClick {
                        restoreQuickTextSelectedGroupIds = defaultSelectedQuickSubtitlePresetGroupIds()
                        restoreQuickTextExpandedGroupIds = emptyList()
                        restoreQuickTextPresetDialogVisible = true
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = openRestorePresets)
                            .padding(horizontal = 2.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MsIcon(
                            name = "restore",
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.accentText
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "恢复文本预设分组",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "从内置文本预设中选择分组添加；同名分组不会合并。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MsIcon(
                            name = "chevron_right",
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Md2SettingSwitchRow(
                        title = "文字过多时自动缩小",
                        checked = state.quickSubtitleAutoFit,
                        onCheckedChange = { viewModel.setQuickSubtitleAutoFit(it) },
                        supportingText = "内容较多时自动缩小大字幕和预览文字，尽量完整显示。"
                    )
                    Md2SettingSwitchRow(
                        title = "开放超大字幕字号",
                        checked = state.quickSubtitleAllowLargeFont,
                        onCheckedChange = { viewModel.setQuickSubtitleAllowLargeFont(it) },
                        supportingText = "允许将便捷字幕调到更大的尺寸；关闭后使用常规字号范围。"
                    )
                    Md2SettingSwitchRow(
                        title = "紧凑快捷文本布局",
                        checked = state.quickSubtitleCompactControls,
                        onCheckedChange = { viewModel.setQuickSubtitleCompactControls(it) },
                        supportingText = "缩小快捷文本区域，并将编辑入口移到顶栏。"
                    )
                    Md2SettingSwitchRow(
                        title = "收起键盘后保留输入预览",
                        checked = state.quickSubtitleKeepInputPreview,
                        onCheckedChange = { viewModel.setQuickSubtitleKeepInputPreview(it) },
                        supportingText = "键盘收起后，大字幕继续显示尚未发送的输入内容。"
                    )
                }
            }
            Md2StaggeredFloatIn(index = 4) {
                Md2SettingsCard(title = "文件与保存") {
                    Text("画板保存路径（相册）", fontWeight = FontWeight.Bold)
                    Text(state.drawingSaveRelativePath, style = MaterialTheme.typography.bodySmall)
                    Md2SettingSwitchRow(
                        title = "旋转设备时保持画布朝向",
                        checked = state.drawingKeepCanvasOrientationToDevice,
                        onCheckedChange = { viewModel.setDrawingKeepCanvasOrientationToDevice(it) },
                        supportingText = "设备旋转时自动调整画布，保持原有观看方向；手动旋转仍会保留。"
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Md2OutlinedButton(onClick = { drawingDirPicker.launch(null) }) {
                            Text("选择目录")
                        }
                        Md2TextButton(onClick = {
                            val def = UserPrefs.DEFAULT_DRAWING_SAVE_RELATIVE_PATH
                            viewModel.setDrawingSaveRelativePath(def)
                        }) {
                            Text("恢复默认")
                        }
                    }
                    Text("通过系统文件管理器选择目录（建议内部存储）", style = MaterialTheme.typography.bodySmall)
                }
            }
            Md2StaggeredFloatIn(index = 6) {
                Md2SettingsCard(title = "名片编辑") {
                    Md2SettingSwitchRow(
                        title = "退出名片编辑时自动保存",
                        checked = state.quickCardAutoSaveOnExit,
                        onCheckedChange = { viewModel.setQuickCardAutoSaveOnExit(it) },
                        supportingText = "关闭时将弹窗询问“是否保存名片”"
                    )
                }
            }
            Md2StaggeredFloatIn(index = 7) {
                Md2SettingsCard(title = "文件选择") {
                    Md2SettingSwitchRow(
                        title = "使用应用内文件管理器",
                        checked = state.useBuiltinFileManager,
                        onCheckedChange = { viewModel.setUseBuiltinFileManager(it) },
                        supportingText = "关闭后改用系统文件选择器。"
                    )
                    Md2SettingSwitchRow(
                        title = "使用应用内图库",
                        checked = state.useBuiltinGallery,
                        onCheckedChange = { viewModel.setUseBuiltinGallery(it) },
                        supportingText = "关闭后改用系统图库选择器。"
                    )
                }
            }
            Md2StaggeredFloatIn(index = 8) {
                Md2SettingsCard(title = "常用应用快捷操作") {
                    Md2SettingSwitchRow(
                        title = "补全常用应用快捷操作",
                        checked = state.floatingOverlayHardcodedShortcutSupplement,
                        onCheckedChange = { viewModel.setFloatingOverlayHardcodedShortcutSupplement(it) },
                        supportingText = "为悬浮窗启动器补充扫一扫等常用操作。"
                    )
                    Text(
                        "仅补充系统未提供的常用操作，不会影响已经能够获取的快捷方式。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Md2StaggeredFloatIn(index = 9) {
                AppConfigBackupSettingsCard(
                    viewModel = viewModel,
                    state = state
                )
            }
        }
    }

    @Composable
    fun SettingsCategoryContent(category: SettingsCategory) {
        when (category) {
            SettingsCategory.About -> AboutSettingsContent()
            SettingsCategory.Recognition -> RecognitionSettingsContent()
            SettingsCategory.Audio -> AudioSettingsContent()
            SettingsCategory.System -> SystemSettingsContent()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compactTabs = maxWidth < 600.dp
        val wideLayout = maxWidth >= 1100.dp
        val horizontalInset = if (wideLayout) 24.dp else 16.dp

        if (compactTabs) {
            CenteredPageColumn(
                maxWidth = UiTokens.WideContentMaxWidth,
                scroll = scroll,
                horizontalPadding = horizontalInset
            ) {
                Spacer(Modifier.height(UiTokens.PageTopBlank))
                SettingsTabsCard(
                    selectedCategory = selectedCategory,
                    compact = true,
                    onSelect = { viewModel.updateSettingsSelectedCategory(it) }
                )
                AnimatedContent(
                    targetState = selectedCategory,
                    modifier = Modifier.padding(vertical = 4.dp),
                    transitionSpec = {
                        val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        ContentTransform(
                            targetContentEnter = fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                                slideInHorizontally(
                                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                                    initialOffsetX = { direction * (it / 6) }
                                ),
                            initialContentExit = fadeOut(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                                slideOutHorizontally(
                                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                                    targetOffsetX = { -direction * (it / 7) }
                                ),
                            sizeTransform = androidx.compose.animation.SizeTransform(clip = false)
                        )
                    },
                    label = "settings_tabs_content_compact"
                ) { category ->
                    CompositionLocalProvider(LocalSuppressStaggeredFloatIn provides true) {
                        SettingsCategoryContent(category)
                    }
                }
                Spacer(Modifier.height(pageBottomBlankPadding()))
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalInset),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .width(if (wideLayout) 156.dp else 144.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(Modifier.height(UiTokens.PageTopBlank))
                    SettingsTabsCard(
                        selectedCategory = selectedCategory,
                        compact = false,
                        onSelect = { viewModel.updateSettingsSelectedCategory(it) }
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    CenteredPageColumn(
                        maxWidth = UiTokens.WideContentMaxWidth,
                        modifier = Modifier.fillMaxSize(),
                        scroll = scroll,
                        horizontalPadding = 0.dp
                    ) {
                        Spacer(Modifier.height(UiTokens.PageTopBlank))
                        AnimatedContent(
                            targetState = selectedCategory,
                            modifier = Modifier.padding(vertical = 4.dp),
                            transitionSpec = {
                                val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                ContentTransform(
                                    targetContentEnter = fadeIn(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                                        slideInVertically(
                                            animationSpec = tween(240, easing = FastOutSlowInEasing),
                                            initialOffsetY = { direction * (it / 6) }
                                        ),
                                    initialContentExit = fadeOut(animationSpec = tween(240, easing = FastOutSlowInEasing)) +
                                        slideOutVertically(
                                            animationSpec = tween(240, easing = FastOutSlowInEasing),
                                            targetOffsetY = { -direction * (it / 7) }
                                        ),
                                    sizeTransform = androidx.compose.animation.SizeTransform(clip = false)
                                )
                            },
                            label = "settings_tabs_content_rail"
                        ) { category ->
                            CompositionLocalProvider(LocalSuppressStaggeredFloatIn provides true) {
                                SettingsCategoryContent(category)
                            }
                        }
                        Spacer(Modifier.height(pageBottomBlankPadding()))
                    }
                }
            }
        }
    }

    if (showSpeakerEnrollDialog) {
        val canDismiss = !(speakerEnrollReading || speakerEnrollCountingDown)
        KigttsAlertDialog(
            onDismissRequest = {
                if (canDismiss) {
                    closeSpeakerEnrollDialog()
                }
            },
            title = {
                val title = when (speakerEnrollStep) {
                    0 -> "说话人注册（准备）"
                    1, 2, 3 -> "说话人注册（第 ${speakerEnrollStep} 句）"
                    else -> "说话人注册（结果）"
                }
                Text(title)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (speakerEnrollStep) {
                        0 -> {
                            Text("请按顺序朗读三句，每句约 4 秒。")
                            Text("环境尽量安静，手机靠近说话人。", style = MaterialTheme.typography.bodySmall)
                            Text("第一页仅说明，点击“下一步”开始。", style = MaterialTheme.typography.bodySmall)
                        }
                        1, 2, 3 -> {
                            val phrase = speakerEnrollTexts[speakerEnrollStep - 1]
                            Text("请朗读以下文本：")
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(4.dp),
                                backgroundColor = md2CardContainerColor()
                            ) {
                                Text(
                                    text = phrase,
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            if (speakerEnrollCountingDown) {
                                Text(
                                    "倒计时：${speakerEnrollCountdown}",
                                    style = MaterialTheme.typography.h4,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "倒计时结束后将自动开始录音。",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else if (speakerEnrollReading) {
                                Text(
                                    "录制中，剩余 ${String.format(Locale.US, "%.1f", speakerEnrollRemainingSec)}s",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                LinearProgressIndicator(
                                    progress = speakerEnrollProgress.coerceIn(0f, 1f),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("实时音量", style = MaterialTheme.typography.bodySmall)
                                LinearProgressIndicator(
                                    progress = speakerEnrollLevel.coerceIn(0f, 1f),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    "点击“开始朗读”后会先倒计时，再开始计时录音。",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        else -> {
                            Text(if (speakerEnrollSuccess) "注册完成" else "注册失败")
                            Text(speakerEnrollMessage, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "你可以直接完成，或重新打开注册流程重录。",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                when (speakerEnrollStep) {
                    0 -> {
                        Md2TextButton(onClick = { speakerEnrollStep = 1 }) {
                            Text("下一步")
                        }
                    }
                    1, 2, 3 -> {
                        Md2TextButton(
                            onClick = { startSpeakerEnrollStepCapture(speakerEnrollStep) },
                            enabled = !(speakerEnrollReading || speakerEnrollCountingDown)
                        ) {
                            Text(
                                when {
                                    speakerEnrollCountingDown -> "倒计时中..."
                                    speakerEnrollReading -> "录制中..."
                                    else -> "开始朗读"
                                }
                            )
                        }
                    }
                    else -> {
                        Md2TextButton(onClick = { closeSpeakerEnrollDialog() }) {
                            Text("完成")
                        }
                    }
                }
            },
            dismissButton = {
                when (speakerEnrollStep) {
                    0, 1, 2, 3 -> {
                        Md2TextButton(
                            onClick = { closeSpeakerEnrollDialog() },
                            enabled = !(speakerEnrollReading || speakerEnrollCountingDown)
                        ) {
                            Text("取消")
                        }
                    }
                    else -> {
                        Md2TextButton(onClick = {
                            speakerEnrollSamples.clear()
                            speakerEnrollStep = 0
                            speakerEnrollCountingDown = false
                            speakerEnrollCountdown = 3
                            speakerEnrollReading = false
                            speakerEnrollProgress = 0f
                            speakerEnrollLevel = 0f
                            speakerEnrollRemainingSec = 4f
                            speakerEnrollSuccess = false
                            speakerEnrollMessage = "请按页面引导完成本人样本采集。"
                            speakerEnrollRetryDialog = false
                        }) {
                            Text("重新采集")
                        }
                    }
                }
            }
        )
    }

    if (speakerEnrollRetryDialog) {
        KigttsAlertDialog(
            onDismissRequest = { speakerEnrollRetryDialog = false },
            title = { Text("录制失败") },
            text = { Text("${speakerEnrollMessage}\n请重录当前句子。") },
            confirmButton = {
                Md2TextButton(onClick = { speakerEnrollRetryDialog = false }) {
                    Text("重录")
                }
            }
        )
    }

    if (speakerEnrollPermissionPurposeOpen) {
        PermissionPurposeDialog(
            info = recordAudioPermissionPurpose(
                serviceFeature = "说话人验证样本采集",
                purpose = "采集你按页面提示主动朗读的三段语音，用于在本机生成说话人验证样本。"
            ),
            onConfirm = {
                speakerEnrollPermissionPurposeOpen = false
                speakerEnrollPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onDismiss = {
                speakerEnrollPermissionPurposeOpen = false
                pendingSpeakerEnrollPermissionStep = 0
            }
        )
    }
}

@Composable
internal fun Md2SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiTokens.Radius),
        backgroundColor = md2CardContainerColor(),
        elevation = UiTokens.CardElevation
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = {
                Md2CardTitleText(title)
                content()
            }
        )
    }
}

@Composable
fun LogScreen(
    onTopBarActionsChange: (LogTopBarActions?) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<File>>(emptyList()) }
    var selected by remember { mutableStateOf<File?>(null) }
    val logLines = remember { mutableStateListOf<String>() }
    var isLoading by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val logBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
    val onTopBarActionsChangeState = rememberUpdatedState(onTopBarActionsChange)

    fun refreshLogs() {
        logs = AppLogger.listLogFiles(context)
        if (selected == null || selected !in logs) {
            selected = logs.firstOrNull()
        }
        refreshToken++
    }

    LaunchedEffect(Unit) {
        refreshLogs()
    }

    LaunchedEffect(selected?.absolutePath, refreshToken) {
        val file = selected
        listState.scrollToItem(0)
        logLines.clear()
        if (file == null) {
            logLines += "暂无日志"
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        val rawContent = withContext(Dispatchers.IO) {
            AppLogger.readLog(file)
        }.removePrefix("\uFEFF")
        withContext(Dispatchers.Default) {
            val normalizedLines = rawContent
                .ifEmpty { "日志为空" }
                .lineSequence()
                .map { it.ifEmpty { " " } }
                .toList()
            if (normalizedLines.isEmpty()) {
                withContext(Dispatchers.Main) {
                    logLines += "日志为空"
                }
            } else {
                normalizedLines.chunked(200).forEach { chunk ->
                    withContext(Dispatchers.Main) {
                        logLines.addAll(chunk)
                    }
                    yield()
                }
            }
        }
        isLoading = false
    }

    SideEffect {
        onTopBarActionsChangeState.value(
            LogTopBarActions(
                onRefresh = { refreshLogs() },
                onCopy = {
                    scope.launch {
                        val text = selected?.let { file ->
                            withContext(Dispatchers.IO) {
                                AppLogger.readLog(file).ifEmpty { "日志为空" }
                            }
                        } ?: "暂无日志"
                        clipboard.setText(AnnotatedString(text))
                        toast(context, "已复制")
                    }
                },
                onShare = {
                    val file = selected
                    if (file != null) {
                        shareLogFile(context, file)
                    } else {
                        toast(context, "暂无可分享日志")
                    }
                },
                canCopy = selected != null,
                canShare = selected != null
            )
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            onTopBarActionsChangeState.value(null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(UiTokens.PageTopBlank))
        Md2StaggeredFloatIn(index = 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    Md2DropdownButton(
                        label = selected?.name ?: "选择日志",
                        onClick = { expanded = true },
                        expanded = expanded
                    )
                    Md2AnimatedOptionMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        logs.forEach { file ->
                            M2DropdownMenuItem(
                                onClick = {
                                    selected = file
                                    expanded = false
                                }
                            ) { Text(file.name) }
                        }
                    }
                }
            }
        }
        Md2StaggeredFloatIn(index = 1) {
            if (selected != null) {
                Text("路径：${selected!!.absolutePath}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Divider()
        Md2StaggeredFloatIn(
            index = 2,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(UiTokens.Radius),
                backgroundColor = md2CardContainerColor(),
                elevation = UiTokens.CardElevation
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (logLines.isEmpty() && isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.accentText)
                            }
                        }
                    }
                    itemsIndexed(logLines) { _, line ->
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (isLoading && logLines.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.accentText,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(logBottomPadding))
    }
}

@Composable
internal fun LegalDocumentScreen(assetPath: String) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val legalBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
    val markdownBlocks = remember(assetPath) { mutableStateListOf<MarkdownBlock>() }
    var isParsing by remember(assetPath) { mutableStateOf(true) }

    LaunchedEffect(assetPath) {
        isParsing = true
        markdownBlocks.clear()
        val documentText = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    .removePrefix("\uFEFF")
            }.getOrElse {
                AppLogger.e("load legal document failed: $assetPath", it)
                "无法加载文档，请稍后重试"
            }
        }
        var emittedAny = false
        withContext(Dispatchers.Default) {
            parseMarkdownBlocksStreaming(documentText, chunkSize = 24) { chunk ->
                withContext(Dispatchers.Main) {
                    markdownBlocks.addAll(chunk)
                    if (!emittedAny) {
                        emittedAny = true
                        isParsing = false
                    }
                }
            }
        }
        if (!emittedAny) {
            markdownBlocks += MarkdownBlock.Paragraph("暂无内容")
        }
        isParsing = false
    }
    fun openExternalUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure {
            toast(context, "打开链接失败")
        }
    }
    CenteredPageBox(
        maxWidth = UiTokens.WideContentMaxWidth,
        modifier = Modifier.fillMaxSize()
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = UiTokens.PageTopBlank, bottom = legalBottomPadding),
            shape = RoundedCornerShape(UiTokens.Radius),
            backgroundColor = md2CardContainerColor(),
            elevation = UiTokens.CardElevation
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(14.dp)
            ) {
                if (markdownBlocks.isEmpty() && isParsing) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.accentText)
                        }
                    }
                }
                itemsIndexed(markdownBlocks) { _, block ->
                    MarkdownBlockView(
                        block = block,
                        onOpenUrl = ::openExternalUrl
                    )
                }
                if (isParsing && markdownBlocks.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.accentText,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class ListBlock(
        val items: List<String>,
        val ordered: Boolean,
        val startIndex: Int = 1
    ) : MarkdownBlock
    data class CodeFence(val code: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

internal fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.replace("\r\n", "\n").split('\n')
    val paragraphLines = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphLines.isEmpty()) return
        val text = paragraphLines.joinToString(" ") { it.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(text)
        }
        paragraphLines.clear()
    }

    var index = 0
    while (index < lines.size) {
        val rawLine = lines[index]
        val trimmed = rawLine.trim()

        if (trimmed.startsWith("```")) {
            flushParagraph()
            val codeLines = mutableListOf<String>()
            index++
            while (index < lines.size && !lines[index].trim().startsWith("```")) {
                codeLines += lines[index]
                index++
            }
            blocks += MarkdownBlock.CodeFence(codeLines.joinToString("\n").trimEnd())
            index++
            continue
        }

        if (trimmed.isBlank()) {
            flushParagraph()
            index++
            continue
        }

        val headingMatch = Regex("^(#{1,6})\\s+(.+)$").matchEntire(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            blocks += MarkdownBlock.Heading(
                level = headingMatch.groupValues[1].length,
                text = headingMatch.groupValues[2].trim()
            )
            index++
            continue
        }

        if (trimmed.matches(Regex("^([-*_])\\1{2,}$"))) {
            flushParagraph()
            blocks += MarkdownBlock.Divider
            index++
            continue
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            flushParagraph()
            val items = mutableListOf<String>()
            while (index < lines.size) {
                val listLine = lines[index].trim()
                if (listLine.startsWith("- ") || listLine.startsWith("* ")) {
                    items += listLine.substring(2).trim()
                    index++
                } else {
                    break
                }
            }
            if (items.isNotEmpty()) {
                blocks += MarkdownBlock.ListBlock(items = items, ordered = false)
            }
            continue
        }

        val orderedMatch = Regex("^(\\d+)\\.\\s+(.+)$").matchEntire(trimmed)
        if (orderedMatch != null) {
            flushParagraph()
            val startIndex = orderedMatch.groupValues[1].toIntOrNull() ?: 1
            val items = mutableListOf<String>()
            while (index < lines.size) {
                val listLine = lines[index].trim()
                val match = Regex("^(\\d+)\\.\\s+(.+)$").matchEntire(listLine)
                if (match != null) {
                    items += match.groupValues[2].trim()
                    index++
                } else {
                    break
                }
            }
            if (items.isNotEmpty()) {
                blocks += MarkdownBlock.ListBlock(items = items, ordered = true, startIndex = startIndex)
            }
            continue
        }

        paragraphLines += rawLine
        index++
    }

    flushParagraph()
    return blocks
}

internal data class MarkdownColors(
    val text: Color,
    val heading: Color,
    val link: Color,
    val code: Color,
    val codeBackground: Color,
    val codeBlockBackground: Color,
    val divider: Color,
    val hint: Color
)

private suspend fun parseMarkdownBlocksStreaming(
    markdown: String,
    chunkSize: Int,
    onChunk: suspend (List<MarkdownBlock>) -> Unit
) {
    val lines = markdown.replace("\r\n", "\n").split('\n')
    val paragraphLines = mutableListOf<String>()
    val pendingBlocks = mutableListOf<MarkdownBlock>()

    suspend fun emitBlock(block: MarkdownBlock) {
        pendingBlocks += block
        if (pendingBlocks.size >= chunkSize) {
            onChunk(pendingBlocks.toList())
            pendingBlocks.clear()
            yield()
        }
    }

    suspend fun flushParagraph() {
        if (paragraphLines.isEmpty()) return
        val text = paragraphLines.joinToString(" ") { it.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.isNotEmpty()) {
            emitBlock(MarkdownBlock.Paragraph(text))
        }
        paragraphLines.clear()
    }

    var index = 0
    while (index < lines.size) {
        val rawLine = lines[index]
        val trimmed = rawLine.trim()

        if (trimmed.startsWith("```")) {
            flushParagraph()
            val codeLines = mutableListOf<String>()
            index++
            while (index < lines.size && !lines[index].trim().startsWith("```")) {
                codeLines += lines[index]
                index++
            }
            emitBlock(MarkdownBlock.CodeFence(codeLines.joinToString("\n").trimEnd()))
            index++
            continue
        }

        if (trimmed.isBlank()) {
            flushParagraph()
            index++
            continue
        }

        val headingMatch = Regex("^(#{1,6})\\s+(.+)$").matchEntire(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            emitBlock(
                MarkdownBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    text = headingMatch.groupValues[2].trim()
                )
            )
            index++
            continue
        }

        if (trimmed.matches(Regex("^([-*_])\\1{2,}$"))) {
            flushParagraph()
            emitBlock(MarkdownBlock.Divider)
            index++
            continue
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            flushParagraph()
            val items = mutableListOf<String>()
            while (index < lines.size) {
                val listLine = lines[index].trim()
                if (listLine.startsWith("- ") || listLine.startsWith("* ")) {
                    items += listLine.substring(2).trim()
                    index++
                } else {
                    break
                }
            }
            if (items.isNotEmpty()) {
                emitBlock(MarkdownBlock.ListBlock(items = items, ordered = false))
            }
            continue
        }

        val orderedMatch = Regex("^(\\d+)\\.\\s+(.+)$").matchEntire(trimmed)
        if (orderedMatch != null) {
            flushParagraph()
            val startIndex = orderedMatch.groupValues[1].toIntOrNull() ?: 1
            val items = mutableListOf<String>()
            while (index < lines.size) {
                val listLine = lines[index].trim()
                val match = Regex("^(\\d+)\\.\\s+(.+)$").matchEntire(listLine)
                if (match != null) {
                    items += match.groupValues[2].trim()
                    index++
                } else {
                    break
                }
            }
            if (items.isNotEmpty()) {
                emitBlock(
                    MarkdownBlock.ListBlock(
                        items = items,
                        ordered = true,
                        startIndex = startIndex
                    )
                )
            }
            continue
        }

        paragraphLines += rawLine
        index++
    }

    flushParagraph()
    if (pendingBlocks.isNotEmpty()) {
        onChunk(pendingBlocks.toList())
    }
}

@Composable
internal fun MarkdownBlockView(
    block: MarkdownBlock,
    onOpenUrl: (String) -> Unit
) {
    val dark = currentAppDarkTheme()
    val colors = remember(dark) {
        MarkdownColors(
            text = if (dark) Color(0xFFE5ECEF) else Color(0xFF1B1F22),
            heading = if (dark) Color(0xFFF4FAFC) else Color(0xFF101417),
            link = if (dark) Color(0xFF7FD7F1) else Color(0xFF007C91),
            code = if (dark) Color(0xFFF5F7F9) else Color(0xFF1D252B),
            codeBackground = if (dark) Color(0xFF24313A) else Color(0xFFE8EEF2),
            codeBlockBackground = if (dark) Color(0xFF1A232A) else Color(0xFFF3F6F8),
            divider = if (dark) Color(0xFF45525A) else Color(0xFFD6DEE3),
            hint = if (dark) Color(0xFF9FB0BA) else Color(0xFF687780)
        )
    }
    when (block) {
        is MarkdownBlock.Heading -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.h5.copy(color = colors.heading)
                2 -> MaterialTheme.typography.h6.copy(color = colors.heading)
                3 -> MaterialTheme.typography.subtitle1.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colors.heading
                )
                else -> MaterialTheme.typography.body1.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colors.heading
                )
            }
            MarkdownText(
                text = block.text,
                style = style,
                colors = colors,
                onOpenUrl = onOpenUrl
            )
        }

        is MarkdownBlock.Paragraph -> {
            MarkdownText(
                text = block.text,
                style = MaterialTheme.typography.body2.copy(
                    lineHeight = 20.sp,
                    color = colors.text
                ),
                colors = colors,
                onOpenUrl = onOpenUrl
            )
        }

        is MarkdownBlock.ListBlock -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.items.forEachIndexed { itemIndex, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (block.ordered) "${block.startIndex + itemIndex}." else "•",
                            style = MaterialTheme.typography.body2.copy(
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 20.sp,
                                color = colors.heading
                            ),
                            modifier = Modifier.padding(top = 1.dp)
                        )
                        MarkdownText(
                            text = item,
                            style = MaterialTheme.typography.body2.copy(
                                lineHeight = 20.sp,
                                color = colors.text
                            ),
                            modifier = Modifier.weight(1f),
                            colors = colors,
                            onOpenUrl = onOpenUrl
                        )
                    }
                }
            }
        }

        is MarkdownBlock.CodeFence -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                backgroundColor = colors.codeBlockBackground,
                elevation = 0.dp
            ) {
                SelectionContainer(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = block.code,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colors.code,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp
                        )
                    )
                }
            }
        }

        MarkdownBlock.Divider -> Divider(color = colors.divider)
    }
}

@Composable
internal fun MarkdownText(
    text: String,
    style: TextStyle,
    colors: MarkdownColors,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val annotated = remember(text, colors) {
        buildMarkdownAnnotatedString(
            text = text,
            linkColor = colors.link,
            codeColor = colors.code,
            codeBackground = colors.codeBackground
        )
    }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style,
        onClick = { offset ->
            annotated
                .getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()
                ?.let { onOpenUrl(it.item) }
        }
    )
}

internal fun buildMarkdownAnnotatedString(
    text: String,
    linkColor: Color,
    codeColor: Color,
    codeBackground: Color
): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        val linkMatch = Regex("""^\[([^\]]+)]\((https?://[^)]+)\)""").find(text.substring(index))
        if (linkMatch != null && linkMatch.range.first == 0) {
            val label = linkMatch.groupValues[1]
            val url = linkMatch.groupValues[2]
            pushStringAnnotation(tag = "URL", annotation = url)
            withStyle(
                SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(label)
            }
            pop()
            index += linkMatch.value.length
            continue
        }

        if (text.startsWith("**", index)) {
            val end = text.indexOf("**", startIndex = index + 2)
            if (end > index + 2) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text.substring(index + 2, end))
                }
                index = end + 2
                continue
            }
        }

        if (text[index] == '`') {
            val end = text.indexOf('`', startIndex = index + 1)
            if (end > index + 1) {
                withStyle(
                    SpanStyle(
                        color = codeColor,
                        background = codeBackground,
                        fontFamily = FontFamily.Monospace
                    )
                ) {
                    append(text.substring(index + 1, end))
                }
                index = end + 1
                continue
            }
        }

        append(text[index])
        index++
    }
}
