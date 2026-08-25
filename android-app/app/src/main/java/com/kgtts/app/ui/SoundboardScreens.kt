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
import androidx.compose.ui.graphics.compositeOver
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
import com.lhtstudio.kigtts.app.data.adjustSoundboardClipRange
import com.lhtstudio.kigtts.app.data.initialSoundboardClipRange
import com.lhtstudio.kigtts.app.data.KOKORO_VOICE_NAME
import com.lhtstudio.kigtts.app.data.SYSTEM_TTS_VOICE_NAME
import com.lhtstudio.kigtts.app.data.VoicePackInfo
import com.lhtstudio.kigtts.app.data.UserPrefs
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


@Composable
internal fun SoundboardNavHost(
    navController: NavHostController,
    viewModel: MainViewModel,
    state: UiState,
    onEditorBatchTopBarActionsChange: (EditorBatchTopBarActions?) -> Unit,
    ultraSmallAdaptiveWindow: Boolean = false,
    forceLandscapeLayout: Boolean = false
) {
    NavHost(
        navController = navController,
        startDestination = SoundboardRoutes.Main,
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            if (initialState.destination.route == SoundboardRoutes.Main &&
                targetState.destination.route == SoundboardRoutes.Editor
            ) {
                fadeIn(animationSpec = tween(180)) +
                        slideInHorizontally(
                            initialOffsetX = { full -> full / 10 },
                            animationSpec = tween(180, easing = FastOutSlowInEasing)
                        )
            } else {
                fadeIn(animationSpec = tween(120))
            }
        },
        exitTransition = {
            if (initialState.destination.route == SoundboardRoutes.Main &&
                targetState.destination.route == SoundboardRoutes.Editor
            ) {
                fadeOut(animationSpec = tween(130)) +
                        slideOutHorizontally(
                            targetOffsetX = { full -> -full / 14 },
                            animationSpec = tween(130, easing = FastOutSlowInEasing)
                        )
            } else {
                fadeOut(animationSpec = tween(90))
            }
        },
        popEnterTransition = {
            if (initialState.destination.route == SoundboardRoutes.Editor &&
                targetState.destination.route == SoundboardRoutes.Main
            ) {
                fadeIn(animationSpec = tween(170)) +
                        slideInHorizontally(
                            initialOffsetX = { full -> -full / 12 },
                            animationSpec = tween(170, easing = FastOutSlowInEasing)
                        )
            } else {
                fadeIn(animationSpec = tween(120))
            }
        },
        popExitTransition = {
            if (initialState.destination.route == SoundboardRoutes.Editor &&
                targetState.destination.route == SoundboardRoutes.Main
            ) {
                fadeOut(animationSpec = tween(130)) +
                        slideOutHorizontally(
                            targetOffsetX = { full -> full / 16 },
                            animationSpec = tween(130, easing = FastOutSlowInEasing)
                        )
            } else {
                fadeOut(animationSpec = tween(90))
            }
        }
    ) {
        composable(SoundboardRoutes.Main) {
            SoundboardScreen(
                viewModel = viewModel,
                onOpenEditor = { navController.navigate(SoundboardRoutes.Editor) },
                ultraSmallAdaptiveWindow = ultraSmallAdaptiveWindow,
                forceLandscapeLayout = forceLandscapeLayout
            )
        }
        composable(SoundboardRoutes.Editor) {
            SoundboardEditorScreen(
                viewModel = viewModel,
                state = state,
                onBatchTopBarActionsChange = onEditorBatchTopBarActionsChange,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
internal fun PresetGroupExportDialog(
    title: String,
    groups: List<Pair<Long, String>>,
    prompt: String = "选择需要导出的分组",
    confirmLabel: String = "导出",
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit
) {
    var selectedIds by remember(groups) { mutableStateOf(groups.map { it.first }.toSet()) }
    Md2ScrollableDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            Md2TextButton(
                onClick = { onConfirm(selectedIds) },
                enabled = selectedIds.isNotEmpty()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            Md2TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        contentSpacing = 4.dp
    ) {
        Text(prompt)
        groups.forEach { (id, name) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(UiTokens.Radius))
                    .clickable {
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                    }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = id in selectedIds,
                    onCheckedChange = {
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                    }
                )
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun SoundboardScreen(
    viewModel: MainViewModel,
    onOpenEditor: () -> Unit,
    ultraSmallAdaptiveWindow: Boolean = false,
    forceLandscapeLayout: Boolean = false
) {
    val configuration = LocalConfiguration.current
    val isLandscape = forceLandscapeLayout || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenLongSideDp = maxOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val screenShortSideDp = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val phoneUa = screenShortSideDp < 600 || screenLongSideDp < 900
    val useFullWidthLandscapeTabs =
        isLandscape && !ultraSmallAdaptiveWindow && (!phoneUa || viewModel.uiState.forceFullWidthTabsOnPhone)
    val landscapeTabRailWidth = if (useFullWidthLandscapeTabs) 140.dp else 54.dp
    val navBarsPadding = WindowInsets.navigationBars.asPaddingValues()
    val navBarsBottomInset = navBarsPadding.calculateBottomPadding()
    val groups = viewModel.soundboardGroups
    val selectedGroupIndex = viewModel.currentSoundboardGroupIndex().coerceIn(0, groups.lastIndex.coerceAtLeast(0))
    val layoutMode = viewModel.currentSoundboardLayout(isLandscape)
    val gridMode = layoutMode.isGrid
    val fullWidthGrid = gridMode && viewModel.uiState.soundboardGridFullWidth
    val groupHintState = rememberGroupSwitchHintState()
    val performKeyHaptic = rememberKigttsKeyHaptic()
    var editTarget by remember { mutableStateOf<SoundboardItemActionTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<SoundboardItemActionTarget?>(null) }
    var groupEditTargetId by remember { mutableStateOf<Long?>(null) }
    fun locateItem(target: SoundboardItemActionTarget?): Triple<Int, Int, SoundboardItem>? {
        target ?: return null
        val groupIndex = groups.indexOfFirst { it.id == target.groupId }
        if (groupIndex < 0) return null
        val itemIndex = groups[groupIndex].items.indexOfFirst { it.id == target.itemId }
        if (itemIndex < 0) return null
        return Triple(groupIndex, itemIndex, groups[groupIndex].items[itemIndex])
    }
    fun selectSoundboardGroupWithHint(index: Int) {
        if (index !in groups.indices) return
        performKeyHaptic()
        if (index != selectedGroupIndex && isLandscape) {
            groupHintState.show(groups[index].title.ifBlank { "未命名分组" })
        }
        viewModel.selectSoundboardGroup(index)
    }
    val listContent: @Composable (Int, List<SoundboardItem>, Boolean) -> Unit =
        { targetGroupIndex, targetItems, targetGridMode ->
        val targetGroupId = groups.getOrNull(targetGroupIndex)?.id
        if (targetGroupId != null) {
            SoundboardMainItemRecycler(
                items = targetItems,
                playbackStates = viewModel.soundboardPlaybackStates,
                grid = targetGridMode,
                onPlay = viewModel::playSoundboardItem,
                onStop = viewModel::stopSoundboardItem,
                onItemsReordered = { reordered ->
                    val currentGroupIndex = viewModel.soundboardGroups.indexOfFirst {
                        it.id == targetGroupId
                    }
                    if (currentGroupIndex >= 0) {
                        viewModel.setSoundboardItems(currentGroupIndex, reordered)
                    }
                },
                onEditRequested = { itemId ->
                    editTarget = SoundboardItemActionTarget(targetGroupId, itemId)
                },
                onDeleteRequested = { itemId ->
                    deleteTarget = SoundboardItemActionTarget(targetGroupId, itemId)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    val contentCard: @Composable (Modifier) -> Unit = { cardModifier ->
        Card(
            modifier = cardModifier,
            shape = RoundedCornerShape(UiTokens.Radius),
            backgroundColor = md2CardContainerColor(),
            elevation = UiTokens.CardElevation
        ) {
            AnimatedContent(
                targetState = groups.getOrNull(selectedGroupIndex)?.id to layoutMode,
                transitionSpec = {
                    if (initialState.first == targetState.first && initialState.second != targetState.second) {
                        ContentTransform(
                            targetContentEnter = fadeIn(animationSpec = tween(150)),
                            initialContentExit = fadeOut(animationSpec = tween(120)),
                            sizeTransform = null
                        )
                    } else {
                        soundboardGroupSwitchTransform(
                            initialIndex = groups.indexOfFirst { it.id == initialState.first },
                            targetIndex = groups.indexOfFirst { it.id == targetState.first },
                            isLandscape = isLandscape
                        )
                    }
                },
                label = "soundboard_items_switch"
            ) { (targetGroupId, targetLayoutMode) ->
                val targetIndex = groups.indexOfFirst { it.id == targetGroupId }
                val targetItems = groups.getOrNull(targetIndex)?.items.orEmpty()
                if (targetItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "当前分组暂无音效，请进入编辑页添加",
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    listContent(targetIndex, targetItems, targetLayoutMode.isGrid)
                }
            }
        }
    }
    val tabsCard: @Composable (Modifier, Boolean) -> Unit = { tabsModifier, vertical ->
        val candidateGroups = groups.map { group ->
            QuickSubtitleGroup(
                id = group.id,
                title = group.title,
                icon = group.icon,
                items = emptyList()
            )
        }
        Card(
            modifier = tabsModifier,
            shape = RoundedCornerShape(UiTokens.Radius),
            backgroundColor = md2CardContainerColor(),
            elevation = UiTokens.CardElevation
        ) {
            if (vertical) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    QuickSubtitleCandidateGroupRecycler(
                        groups = candidateGroups,
                        selectedGroupId = groups.getOrNull(selectedGroupIndex)?.id,
                        vertical = true,
                        showLabels = useFullWidthLandscapeTabs,
                        onSelectGroup = ::selectSoundboardGroupWithHint,
                        onGroupsReordered = viewModel::reorderSoundboardGroupsByIds,
                        onEditRequested = { groupEditTargetId = it },
                        onGroupBoundsChanged = { _, _ -> },
                        onCanScrollForwardChanged = {},
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            KigttsIconButton(onClick = onOpenEditor) {
                                MsIcon(
                                    "edit",
                                    contentDescription = "编辑音效板",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickSubtitleCandidateGroupRecycler(
                        groups = candidateGroups,
                        selectedGroupId = groups.getOrNull(selectedGroupIndex)?.id,
                        vertical = false,
                        showLabels = true,
                        onSelectGroup = ::selectSoundboardGroupWithHint,
                        onGroupsReordered = viewModel::reorderSoundboardGroupsByIds,
                        onEditRequested = { groupEditTargetId = it },
                        onGroupBoundsChanged = { _, _ -> },
                        onCanScrollForwardChanged = {},
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(52.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            KigttsIconButton(onClick = onOpenEditor) {
                                MsIcon(
                                    "edit",
                                    contentDescription = "编辑音效板",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = if (fullWidthGrid) 0.dp else 16.dp)
                .widthIn(max = if (fullWidthGrid) Dp.Infinity else UiTokens.WideContentMaxWidth)
                .animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing))
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
        val pageModifier = Modifier
            .fillMaxSize()
            .padding(
                start = 10.dp,
                end = 10.dp,
                top = 12.dp,
                bottom = 12.dp + navBarsBottomInset
        )
        if (isLandscape) {
            Box(
                modifier = pageModifier,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        contentCard(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                    tabsCard(
                        Modifier
                            .width(landscapeTabRailWidth)
                            .fillMaxHeight(),
                        true
                    )
                }
                GroupSwitchHintCard(
                    state = groupHintState,
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = landscapeTabRailWidth + 10.dp)
                )
            }
        } else {
            Column(
                modifier = pageModifier,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                contentCard(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                tabsCard(Modifier.fillMaxWidth(), false)
            }
        }
        }
    }

    locateItem(editTarget)?.let { (groupIndex, itemIndex, item) ->
        SoundboardItemEditDialog(
            item = item,
            onDismiss = { editTarget = null },
            onSave = { title, wakeWord ->
                viewModel.updateSoundboardItem(groupIndex, itemIndex) { current ->
                    current.copy(title = title, wakeWord = wakeWord)
                }
                editTarget = null
            }
        )
    }
    locateItem(deleteTarget)?.let { (groupIndex, itemIndex, item) ->
        SoundboardItemDeleteDialog(
            item = item,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                viewModel.removeSoundboardItem(groupIndex, itemIndex)
                deleteTarget = null
            }
        )
    }
    groupEditTargetId?.let { groupId ->
        groups.firstOrNull { it.id == groupId }?.let { group ->
            QuickSubtitleCandidateGroupEditDialog(
                target = QuickSubtitleCandidateGroupEditTarget(
                    groupId = group.id,
                    title = group.title,
                    icon = group.icon
                ),
                iconChoices = SoundboardGroupIconChoices,
                onDismissRequest = { groupEditTargetId = null },
                onSave = { title, icon ->
                    val groupIndex = viewModel.soundboardGroups.indexOfFirst { it.id == groupId }
                    if (groupIndex >= 0) {
                        viewModel.updateSoundboardGroupMeta(groupIndex, title, icon)
                    }
                    groupEditTargetId = null
                }
            )
        }
    }
}

internal fun soundboardGroupSwitchTransform(
    initialIndex: Int,
    targetIndex: Int,
    isLandscape: Boolean
): ContentTransform {
    val forward = targetIndex >= initialIndex
    return if (isLandscape) {
        ContentTransform(
            targetContentEnter = fadeIn(animationSpec = tween(200)) +
                slideInVertically(
                    initialOffsetY = { full ->
                        val d = kotlin.math.min(full / 3, 56)
                        if (forward) d else -d
                    },
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ),
            initialContentExit = fadeOut(animationSpec = tween(170)) +
                slideOutVertically(
                    targetOffsetY = { full ->
                        val d = kotlin.math.min(full / 4, 42)
                        if (forward) -d else d
                    },
                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                ),
            sizeTransform = null
        )
    } else {
        ContentTransform(
            targetContentEnter = fadeIn(animationSpec = tween(200)) +
                slideInHorizontally(
                    initialOffsetX = { full ->
                        val d = kotlin.math.min(full / 3, 140)
                        if (forward) d else -d
                    },
                    animationSpec = tween(230, easing = FastOutSlowInEasing)
                ),
            initialContentExit = fadeOut(animationSpec = tween(170)) +
                slideOutHorizontally(
                    targetOffsetX = { full ->
                        val d = kotlin.math.min(full / 4, 104)
                        if (forward) -d else d
                    },
                    animationSpec = tween(190, easing = FastOutSlowInEasing)
                ),
            sizeTransform = null
        )
    }
}

internal class GroupSwitchHintState {
    var title by mutableStateOf("")
    var visible by mutableStateOf(false)
    var holdUntilRelease by mutableStateOf(false)
    var sequence by mutableLongStateOf(0L)

    fun show(nextTitle: String, hold: Boolean = false) {
        val normalized = nextTitle.ifBlank { "未命名分组" }
        title = normalized
        visible = true
        holdUntilRelease = hold
        sequence += 1L
    }

    fun beginHold() {
        holdUntilRelease = true
        sequence += 1L
    }

    fun release() {
        holdUntilRelease = false
        sequence += 1L
    }
}

@Composable
internal fun rememberGroupSwitchHintState(): GroupSwitchHintState {
    val state = remember { GroupSwitchHintState() }
    LaunchedEffect(state.sequence, state.holdUntilRelease) {
        if (state.visible && !state.holdUntilRelease) {
            delay(900)
            if (!state.holdUntilRelease) {
                state.visible = false
            }
        }
    }
    return state
}

@Composable
internal fun GroupSwitchHintCard(
    state: GroupSwitchHintState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.visible && state.title.isNotBlank(),
        modifier = modifier.zIndex(12f),
        enter = fadeIn(animationSpec = tween(140)) +
            scaleIn(
                initialScale = 0.92f,
                animationSpec = tween(180, easing = FastOutSlowInEasing)
            ),
        exit = fadeOut(animationSpec = tween(180)) +
            scaleOut(
                targetScale = 0.96f,
                animationSpec = tween(180, easing = FastOutSlowInEasing)
            )
    ) {
        Card(
            shape = RoundedCornerShape(UiTokens.Radius),
            backgroundColor = md2ElevatedCardContainerColor(UiTokens.MenuElevation),
            elevation = UiTokens.CardElevation
        ) {
            Text(
                text = state.title,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun SoundboardGridItem(
    item: SoundboardItem,
    playing: Boolean,
    progress: Float,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    dragged: Boolean,
    menuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val performKeyHaptic = rememberKigttsKeyHaptic()
    val dragElevation by animateDpAsState(
        targetValue = if (dragged) 10.dp else 0.dp,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "soundboard_grid_drag_elevation"
    )
    val dragScale by animateFloatAsState(
        targetValue = if (dragged) 1.02f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "soundboard_grid_drag_scale"
    )
    val contentColor = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(UiTokens.Radius)
    val darkTheme = currentAppDarkTheme()
    val baseContainerColor = md2CardContainerColor()
    val containerColor = if (darkTheme) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f).compositeOver(baseContainerColor)
    } else {
        baseContainerColor
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
                shadowElevation = dragElevation.toPx()
                this.shape = shape
            }
            .clip(shape)
            .background(containerColor)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                shape = shape
            )
            .clickable {
                performKeyHaptic()
                onPlay()
            }
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title.ifBlank { "未命名音效" },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (item.wakeWord.isNotBlank()) {
                            Text(
                                text = item.wakeWord,
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    KigttsIconButton(onClick = { if (playing) onStop() else onPlay() }) {
                        MsIcon(
                            if (playing) "stop" else "play_arrow",
                            contentDescription = if (playing) "停止音效" else "播放音效",
                            tint = contentColor
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            }
        }
        SoundboardItemActionMenu(
            expanded = menuExpanded,
            onDismissRequest = onDismissMenu,
            onEdit = onEdit,
            onDelete = onDelete
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun SoundboardListItem(
    item: SoundboardItem,
    playing: Boolean,
    progress: Float,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    dragged: Boolean,
    menuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val performKeyHaptic = rememberKigttsKeyHaptic()
    val shape = RoundedCornerShape(UiTokens.Radius)
    val dragElevation by animateDpAsState(
        targetValue = if (dragged) 10.dp else 0.dp,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "soundboard_list_drag_elevation"
    )
    val dragScale by animateFloatAsState(
        targetValue = if (dragged) 1.01f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "soundboard_list_drag_scale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
                shadowElevation = dragElevation.toPx()
                this.shape = shape
            }
            .clip(shape)
            .background(
                if (dragged) {
                    md2ElevatedCardContainerColor(10.dp)
                } else {
                    Color.Transparent
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    performKeyHaptic()
                    onPlay()
                }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title.ifBlank { "未命名音效" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (item.wakeWord.isNotBlank()) {
                        Text(
                            text = item.wakeWord,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
                        )
                    }
                }
                KigttsIconButton(onClick = { if (playing) onStop() else onPlay() }) {
                    MsIcon(
                        if (playing) "stop" else "play_arrow",
                        contentDescription = if (playing) "停止音效" else "播放音效"
                    )
                }
            }
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
        }
        SoundboardItemActionMenu(
            expanded = menuExpanded,
            onDismissRequest = onDismissMenu,
            onEdit = onEdit,
            onDelete = onDelete
        )
    }
}

@Composable
internal fun SoundboardLayoutDropdownRow(
    title: String,
    selected: SoundboardLayoutMode,
    options: List<SoundboardLayoutMode>,
    onSelected: (SoundboardLayoutMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(UiTokens.Radius))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selected.label, modifier = Modifier.weight(1f))
                MsIcon("keyboard_arrow_down", contentDescription = "切换排列方式")
            }
            Md2AnimatedOptionMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { mode ->
                    M2DropdownMenuItem(
                        onClick = {
                            expanded = false
                            onSelected(mode)
                        }
                    ) {
                        Text(
                            mode.label,
                            fontWeight = if (mode == selected) FontWeight.SemiBold else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun SoundboardEditorScreen(
    viewModel: MainViewModel,
    state: UiState,
    onBatchTopBarActionsChange: (EditorBatchTopBarActions?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val tabletLike = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600
    val tabletTwoPane = tabletLike && configuration.screenWidthDp >= 900
    val editorMaxWidth = if (tabletTwoPane) 1180.dp else UiTokens.WideContentMaxWidth
    val editorLeftColumnWidth = if (configuration.screenWidthDp < 720) 260.dp else 320.dp
    val editorBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
    val editorTwoPaneHeight = (
        configuration.screenHeightDp.dp - UiTokens.PageTopBlank - editorBottomPadding
    ).coerceAtLeast(420.dp)
    val groups = viewModel.soundboardGroups
    var selectedGroupIndex by remember(groups, viewModel.soundboardSelectedGroupId) {
        mutableIntStateOf(viewModel.currentSoundboardGroupIndex().coerceIn(0, groups.lastIndex.coerceAtLeast(0)))
    }
    val selectedGroup = groups.getOrNull(selectedGroupIndex)
    val iconChoices = remember { SoundboardGroupIconChoices }
    val listState = rememberLazyListState()
    val groupTabsScrollState = rememberScrollState()
    val groupTabsScrollScope = rememberCoroutineScope()
    val pageEdgeScrollScope = rememberCoroutineScope()
    var pendingScrollToNewGroup by remember { mutableIntStateOf(0) }
    var batchSelectionMode by remember { mutableStateOf(false) }
    var selectedItemIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }

    fun clearBatchSelection() {
        batchSelectionMode = false
        selectedItemIds = emptySet()
    }

    fun enterBatchSelection(itemId: Long) {
        batchSelectionMode = true
        selectedItemIds = selectedItemIds + itemId
    }

    fun toggleBatchSelection(itemId: Long) {
        selectedItemIds = if (itemId in selectedItemIds) {
            selectedItemIds - itemId
        } else {
            selectedItemIds + itemId
        }
        if (selectedItemIds.isEmpty()) {
            batchSelectionMode = false
        }
    }

    suspend fun scrollGroupTabsToEndWhenReady(request: Int) {
        repeat(12) {
            delay(16)
            val maxScroll = groupTabsScrollState.maxValue
            if (maxScroll > 0) {
                groupTabsScrollState.scrollTo(maxScroll)
                if (pendingScrollToNewGroup == request) pendingScrollToNewGroup = 0
                return
            }
        }
        groupTabsScrollState.scrollTo(groupTabsScrollState.maxValue)
        if (pendingScrollToNewGroup == request) pendingScrollToNewGroup = 0
    }

    LaunchedEffect(groups.size, pendingScrollToNewGroup) {
        if (pendingScrollToNewGroup <= 0 || groups.isEmpty()) return@LaunchedEffect
        scrollGroupTabsToEndWhenReady(pendingScrollToNewGroup)
    }

    LaunchedEffect(selectedGroup?.items, selectedGroupIndex) {
        val validIds = selectedGroup?.items?.map { it.id }?.toSet().orEmpty()
        selectedItemIds = selectedItemIds.filter { it in validIds }.toSet()
        if (selectedItemIds.isEmpty()) {
            batchSelectionMode = false
        }
    }

    LaunchedEffect(batchSelectionMode, selectedItemIds, groups.size, selectedGroupIndex) {
        if (batchSelectionMode) {
            onBatchTopBarActionsChange(
                EditorBatchTopBarActions(
                    onMove = { showBatchMoveDialog = true },
                    onDelete = { showBatchDeleteConfirm = true },
                    onClose = { clearBatchSelection() },
                    canMove = selectedItemIds.isNotEmpty() && groups.size > 1,
                    canDelete = selectedItemIds.isNotEmpty()
                )
            )
        } else {
            onBatchTopBarActionsChange(null)
        }
    }

    DisposableEffect(Unit) {
        onDispose { onBatchTopBarActionsChange(null) }
    }

    val settingsCard: @Composable () -> Unit = {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(UiTokens.Radius),
            backgroundColor = md2CardContainerColor(),
            elevation = UiTokens.CardElevation
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("使用触发词播放音效", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Md2Switch(
                        checked = state.soundboardKeywordTriggerEnabled,
                        onCheckedChange = { viewModel.setSoundboardKeywordTriggerEnabled(it) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("触发音效时不朗读文字", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Md2Switch(
                        checked = state.soundboardSuppressTtsOnKeyword,
                        onCheckedChange = { viewModel.setSoundboardSuppressTtsOnKeyword(it) }
                    )
                }
                Text(
                    "识别到音效触发词时只显示文字并播放音效，不再朗读这句话。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("关闭语音朗读", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Md2Switch(
                        checked = state.ttsDisabled,
                        onCheckedChange = { viewModel.setTtsDisabled(it) }
                    )
                }
            }
        }
    }

    val groupsCard: @Composable () -> Unit = {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(UiTokens.Radius),
            backgroundColor = md2CardContainerColor(),
            elevation = UiTokens.CardElevation
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Md2CardTitleText("分组", modifier = Modifier.weight(1f))
                    Md2TextButton(onClick = {
                        clearBatchSelection()
                        pendingScrollToNewGroup += 1
                        viewModel.addSoundboardGroup()
                        toast(context, "已新增分组")
                    }) {
                        MsIcon("add", contentDescription = "新增分组")
                        Spacer(Modifier.width(4.dp))
                        Text("新增")
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(groupTabsScrollState)
                ) {
                    Row(
                        modifier = Modifier.onSizeChanged {
                            val request = pendingScrollToNewGroup
                            if (request > 0) {
                                groupTabsScrollScope.launch {
                                    scrollGroupTabsToEndWhenReady(request)
                                }
                            }
                        },
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        groups.forEachIndexed { idx, group ->
                            val selected = idx == selectedGroupIndex
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(UiTokens.Radius))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                    .clickable {
                                        clearBatchSelection()
                                        selectedGroupIndex = idx
                                        viewModel.selectSoundboardGroup(idx)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val displayTitle = group.title.ifBlank { "未命名分组" }
                                MsIcon(group.icon, contentDescription = displayTitle)
                                Text(displayTitle)
                                Text("(${group.items.size})", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (selectedGroup != null) {
                    var groupNameFocused by remember(selectedGroupIndex) { mutableStateOf(false) }
                    Md2OutlinedField(
                        value = selectedGroup.title,
                        onValueChange = { viewModel.updateSoundboardGroupMeta(selectedGroupIndex, it, selectedGroup.icon) },
                        label = "分组名称",
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { groupNameFocused = it.isFocused },
                        trailingIcon = if (groupNameFocused && selectedGroup.title.isNotEmpty()) {
                            {
                                Md2ClearFieldButton {
                                    viewModel.updateSoundboardGroupMeta(selectedGroupIndex, "", selectedGroup.icon)
                                }
                            }
                        } else {
                            null
                        }
                    )
                    GroupIconPickerRow(
                        selectedIcon = selectedGroup.icon,
                        iconChoices = iconChoices,
                        onIconSelected = { icon ->
                            viewModel.updateSoundboardGroupMeta(selectedGroupIndex, selectedGroup.title, icon)
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MsIcon("hearing", contentDescription = "启用触发词")
                            Text("启用触发词", style = MaterialTheme.typography.bodySmall)
                        }
                        Md2Switch(
                            checked = selectedGroup.keywordWakeEnabled,
                            onCheckedChange = {
                                viewModel.setSoundboardGroupKeywordWakeEnabled(selectedGroupIndex, it)
                            }
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Md2IconButton(
                            icon = "arrow_back",
                            contentDescription = "分组左移",
                            onClick = {
                                if (selectedGroupIndex > 0) {
                                    clearBatchSelection()
                                    viewModel.moveSoundboardGroup(selectedGroupIndex, selectedGroupIndex - 1)
                                    selectedGroupIndex -= 1
                                }
                            },
                            enabled = selectedGroupIndex > 0
                        )
                        Md2IconButton(
                            icon = "arrow_forward",
                            contentDescription = "分组右移",
                            onClick = {
                                if (selectedGroupIndex < groups.lastIndex) {
                                    clearBatchSelection()
                                    viewModel.moveSoundboardGroup(selectedGroupIndex, selectedGroupIndex + 1)
                                    selectedGroupIndex += 1
                                }
                            },
                            enabled = selectedGroupIndex < groups.lastIndex
                        )
                        Md2IconButton(
                            icon = "delete",
                            contentDescription = "删除分组",
                            onClick = {
                                viewModel.removeSoundboardGroup(selectedGroupIndex)
                                selectedGroupIndex = viewModel.currentSoundboardGroupIndex()
                                clearBatchSelection()
                            },
                            enabled = groups.size > 1
                        )
                    }
                }
            }
        }
    }

    val itemsCard: @Composable (Boolean) -> Unit = { internalScroll ->
        if (groups.isNotEmpty()) {
            AnimatedContent(
                targetState = selectedGroupIndex.coerceIn(0, groups.lastIndex.coerceAtLeast(0)),
                transitionSpec = {
                    soundboardGroupSwitchTransform(
                        initialIndex = initialState,
                        targetIndex = targetState,
                        isLandscape = isLandscape
                    )
                },
                label = "soundboard_editor_items_switch"
            ) { targetIndex ->
                val targetGroup = groups.getOrNull(targetIndex)
                if (targetGroup != null) {
                    SoundboardItemsRecyclerCard(
                        modifier = if (internalScroll) Modifier.fillMaxHeight() else Modifier,
                        internalListScroll = internalScroll,
                        viewModel = viewModel,
                        state = viewModel.uiState,
                        groupIndex = targetIndex,
                        items = targetGroup.items,
                        selectionMode = batchSelectionMode,
                        selectedItemIds = selectedItemIds,
                        parentEdgeScrollBy = if (internalScroll) null else { delta ->
                            val canScroll = if (delta < 0) listState.canScrollBackward else listState.canScrollForward
                            if (canScroll) {
                                pageEdgeScrollScope.launch {
                                    listState.scrollBy(delta.toFloat())
                                }
                            }
                            canScroll
                        },
                        onAdd = {
                            viewModel.addSoundboardItem(targetIndex, it)
                            toast(context, "已添加音效")
                        },
                        onItemsChanged = { reordered -> viewModel.setSoundboardItems(targetIndex, reordered) },
                        onItemChanged = { itemIndex, updated ->
                            viewModel.updateSoundboardItem(targetIndex, itemIndex) { updated }
                        },
                        onEnterSelectionMode = { itemId -> enterBatchSelection(itemId) },
                        onToggleSelection = { itemId -> toggleBatchSelection(itemId) }
                    )
                }
            }
        }
    }

    CenteredPageBox(
        maxWidth = editorMaxWidth,
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = UiTokens.PageTopBlank,
                bottom = if (tabletTwoPane) editorBottomPadding else pageBottomBlankPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = !tabletTwoPane
        ) {
            if (tabletTwoPane) {
                item(key = "soundboard_editor_two_pane") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(editorTwoPaneHeight),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier
                                .width(editorLeftColumnWidth)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            settingsCard()
                            groupsCard()
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(4.dp)
                        ) {
                            itemsCard(true)
                        }
                    }
                }
            } else {
            item(key = "soundboard_settings_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(UiTokens.Radius),
                    backgroundColor = md2CardContainerColor(),
                    elevation = UiTokens.CardElevation
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("使用触发词播放音效", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Md2Switch(
                                checked = state.soundboardKeywordTriggerEnabled,
                                onCheckedChange = { viewModel.setSoundboardKeywordTriggerEnabled(it) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("触发音效时不朗读文字", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Md2Switch(
                                checked = state.soundboardSuppressTtsOnKeyword,
                                onCheckedChange = { viewModel.setSoundboardSuppressTtsOnKeyword(it) }
                            )
                        }
                        Text(
                            "识别到音效触发词时只显示文字并播放音效，不再朗读这句话。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("关闭语音朗读", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Md2Switch(
                                checked = state.ttsDisabled,
                                onCheckedChange = { viewModel.setTtsDisabled(it) }
                            )
                        }
                    }
                }
            }

            item(key = "soundboard_groups_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(UiTokens.Radius),
                    backgroundColor = md2CardContainerColor(),
                    elevation = UiTokens.CardElevation
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        Md2CardTitleText("分组", modifier = Modifier.weight(1f))
                        Md2TextButton(onClick = {
                            clearBatchSelection()
                            pendingScrollToNewGroup += 1
                            viewModel.addSoundboardGroup()
                            toast(context, "已新增分组")
                            }) {
                                MsIcon("add", contentDescription = "新增分组")
                                Spacer(Modifier.width(4.dp))
                                Text("新增")
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(groupTabsScrollState)
                        ) {
                            Row(
                                modifier = Modifier.onSizeChanged {
                                    val request = pendingScrollToNewGroup
                                    if (request > 0) {
                                        groupTabsScrollScope.launch {
                                            scrollGroupTabsToEndWhenReady(request)
                                        }
                                    }
                                },
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                groups.forEachIndexed { idx, group ->
                                    val selected = idx == selectedGroupIndex
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(UiTokens.Radius))
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                            )
                                            .clickable {
                                                clearBatchSelection()
                                                selectedGroupIndex = idx
                                                viewModel.selectSoundboardGroup(idx)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val displayTitle = group.title.ifBlank { "未命名分组" }
                                        MsIcon(group.icon, contentDescription = displayTitle)
                                        Text(displayTitle)
                                        Text("(${group.items.size})", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        if (selectedGroup != null) {
                            var groupNameFocused by remember(selectedGroupIndex) { mutableStateOf(false) }
                            Md2OutlinedField(
                                value = selectedGroup.title,
                                onValueChange = { viewModel.updateSoundboardGroupMeta(selectedGroupIndex, it, selectedGroup.icon) },
                                label = "分组名称",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { groupNameFocused = it.isFocused },
                                trailingIcon = if (groupNameFocused && selectedGroup.title.isNotEmpty()) {
                                    {
                                        Md2ClearFieldButton {
                                            viewModel.updateSoundboardGroupMeta(selectedGroupIndex, "", selectedGroup.icon)
                                        }
                                    }
                                } else {
                                    null
                                }
                            )
                            GroupIconPickerRow(
                                selectedIcon = selectedGroup.icon,
                                iconChoices = iconChoices,
                                onIconSelected = { icon ->
                                    viewModel.updateSoundboardGroupMeta(selectedGroupIndex, selectedGroup.title, icon)
                                }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    MsIcon("hearing", contentDescription = "启用触发词")
                                    Text("启用触发词", style = MaterialTheme.typography.bodySmall)
                                }
                                Md2Switch(
                                    checked = selectedGroup.keywordWakeEnabled,
                                    onCheckedChange = {
                                        viewModel.setSoundboardGroupKeywordWakeEnabled(selectedGroupIndex, it)
                                    }
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Md2IconButton(
                                    icon = "arrow_back",
                                    contentDescription = "分组左移",
                                    onClick = {
                                        if (selectedGroupIndex > 0) {
                                            clearBatchSelection()
                                            viewModel.moveSoundboardGroup(selectedGroupIndex, selectedGroupIndex - 1)
                                            selectedGroupIndex -= 1
                                        }
                                    },
                                    enabled = selectedGroupIndex > 0
                                )
                                Md2IconButton(
                                    icon = "arrow_forward",
                                    contentDescription = "分组右移",
                                    onClick = {
                                        if (selectedGroupIndex < groups.lastIndex) {
                                            clearBatchSelection()
                                            viewModel.moveSoundboardGroup(selectedGroupIndex, selectedGroupIndex + 1)
                                            selectedGroupIndex += 1
                                        }
                                    },
                                    enabled = selectedGroupIndex < groups.lastIndex
                                )
                                Md2IconButton(
                                    icon = "delete",
                                    contentDescription = "删除分组",
                                    onClick = {
                                        viewModel.removeSoundboardGroup(selectedGroupIndex)
                                        selectedGroupIndex = viewModel.currentSoundboardGroupIndex()
                                        clearBatchSelection()
                                    },
                                    enabled = groups.size > 1
                                )
                            }
                        }
                    }
                }
            }

            if (groups.isNotEmpty()) {
                item(key = "soundboard_items_card") {
                    AnimatedContent(
                        targetState = selectedGroupIndex.coerceIn(0, groups.lastIndex.coerceAtLeast(0)),
                        transitionSpec = {
                            soundboardGroupSwitchTransform(
                                initialIndex = initialState,
                                targetIndex = targetState,
                                isLandscape = isLandscape
                            )
                        },
                        label = "soundboard_editor_items_switch"
                    ) { targetIndex ->
                        val targetGroup = groups.getOrNull(targetIndex)
                        if (targetGroup != null) {
                            SoundboardItemsRecyclerCard(
                                viewModel = viewModel,
                                state = viewModel.uiState,
                                groupIndex = targetIndex,
                                items = targetGroup.items,
                                selectionMode = batchSelectionMode,
                                selectedItemIds = selectedItemIds,
                                parentEdgeScrollBy = { delta ->
                                    val canScroll = if (delta < 0) listState.canScrollBackward else listState.canScrollForward
                                    if (canScroll) {
                                        pageEdgeScrollScope.launch {
                                            listState.scrollBy(delta.toFloat())
                                        }
                                    }
                                    canScroll
                                },
                                onAdd = {
                                    viewModel.addSoundboardItem(targetIndex, it)
                                    toast(context, "已添加音效")
                                },
                                onItemsChanged = { reordered -> viewModel.setSoundboardItems(targetIndex, reordered) },
                                onItemChanged = { itemIndex, updated ->
                                    viewModel.updateSoundboardItem(targetIndex, itemIndex) { updated }
                                },
                                onEnterSelectionMode = { itemId -> enterBatchSelection(itemId) },
                                onToggleSelection = { itemId -> toggleBatchSelection(itemId) }
                            )
                        }
                    }
                }
            }
        }
        }
    }

    if (showBatchDeleteConfirm) {
        val count = selectedItemIds.size
        KigttsAlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("删除音效") },
            text = { Text("确定删除已选择的 $count 个音效吗？") },
            confirmButton = {
                Md2TextButton(onClick = {
                    val removed = viewModel.removeSoundboardItemsByIds(selectedGroupIndex, selectedItemIds)
                    showBatchDeleteConfirm = false
                    clearBatchSelection()
                    if (removed > 0) toast(context, "已删除 $removed 个音效")
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                Md2TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showBatchMoveDialog) {
        Md2ScrollableDialog(
            onDismissRequest = { showBatchMoveDialog = false },
            title = { Text("移动音效") },
            confirmButton = {},
            dismissButton = {
                Md2TextButton(onClick = { showBatchMoveDialog = false }) {
                    Text("取消")
                }
            },
            contentSpacing = 4.dp
        ) {
            Text("选择目标分组")
            groups.forEachIndexed { idx, group ->
                if (idx != selectedGroupIndex) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(UiTokens.Radius))
                            .clickable {
                                val moved = viewModel.moveSoundboardItemsToGroup(
                                    selectedGroupIndex,
                                    selectedItemIds,
                                    idx
                                )
                                showBatchMoveDialog = false
                                clearBatchSelection()
                                if (moved > 0) toast(context, "已移动 $moved 个音效")
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MsIcon(group.icon, contentDescription = group.title)
                        Text(
                            text = group.title.ifBlank { "未命名分组" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SoundboardItemsRecyclerCard(
    modifier: Modifier = Modifier,
    internalListScroll: Boolean = false,
    viewModel: MainViewModel,
    state: UiState,
    groupIndex: Int,
    items: List<SoundboardItem>,
    selectionMode: Boolean,
    selectedItemIds: Set<Long>,
    parentEdgeScrollBy: ((Int) -> Boolean)? = null,
    onAdd: (String) -> Unit,
    onItemsChanged: (List<SoundboardItem>) -> Unit,
    onItemChanged: (Int, SoundboardItem) -> Unit,
    onEnterSelectionMode: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit
) {
    val context = LocalContext.current
    var editTargetIndex by remember(items) { mutableStateOf<Int?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editWakeWord by remember { mutableStateOf("") }
    var editTitleFocused by remember { mutableStateOf(false) }
    var editWakeWordFocused by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var addTitle by remember { mutableStateOf("") }
    var addTitleFocused by remember { mutableStateOf(false) }
    var audioTargetIndex by remember { mutableStateOf<Int?>(null) }
    var clipSourceUri by remember { mutableStateOf<Uri?>(null) }
    var showBuiltinAudioPicker by remember { mutableStateOf(false) }
    var showBuiltinBatchAudioPicker by remember { mutableStateOf(false) }
    var deleteTargetItem by remember(items) { mutableStateOf<SoundboardItem?>(null) }
    val openFileManagerAfterPermission = rememberFileManagerPermissionGate()
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            clipSourceUri = uri
        } else {
            audioTargetIndex = null
            toast(context, "未选择音频")
        }
    }
    val batchAudioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importSoundboardAudioFiles(groupIndex, uris)
        } else {
            toast(context, "未选择音频")
        }
    }

    val cardColor = md2ElevatedCardContainerColor(UiTokens.CardElevation)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiTokens.Radius),
        backgroundColor = cardColor,
        elevation = UiTokens.CardElevation
    ) {
        Column(modifier = if (internalListScroll) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
                    .background(cardColor)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Md2CardTitleText("音效", modifier = Modifier.weight(1f))
                Md2TextButton(onClick = {
                    openFileManagerAfterPermission(SoundboardAudioFileExtensions) {
                        if (state.useBuiltinFileManager) {
                            showBuiltinBatchAudioPicker = true
                        } else {
                            batchAudioPicker.launch("audio/*")
                        }
                    }
                }) {
                    MsIcon("queue_music", contentDescription = "批量导入")
                    Spacer(Modifier.width(4.dp))
                    Text("批量导入")
                }
                Md2TextButton(onClick = {
                    addTitle = ""
                    showAddDialog = true
                }) {
                    MsIcon("add", contentDescription = "新增音效")
                    Spacer(Modifier.width(4.dp))
                    Text("新增")
                }
            }
            val listModifier = if (internalListScroll) {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            } else {
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 92.dp)
            }
            SoundboardItemsRecyclerList(
                modifier = listModifier
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                items = items,
                selectionMode = selectionMode,
                selectedItemIds = selectedItemIds,
                onItemsChanged = onItemsChanged,
                onEditRequested = { index, item ->
                    editTargetIndex = index
                    editTitle = item.title
                    editWakeWord = item.wakeWord
                },
                onAudioRequested = { index ->
                    openFileManagerAfterPermission(SoundboardAudioFileExtensions) {
                        audioTargetIndex = index
                        if (state.useBuiltinFileManager) {
                            showBuiltinAudioPicker = true
                        } else {
                            audioPicker.launch("audio/*")
                        }
                    }
                },
                onDeleteRequested = { _, item ->
                    deleteTargetItem = item
                },
                onEnterSelectionMode = onEnterSelectionMode,
                onToggleSelection = onToggleSelection,
                parentEdgeScrollBy = parentEdgeScrollBy,
                nestedScrollingEnabled = false,
                clipListBounds = false
            )
        }
    }

    if (showAddDialog) {
        KigttsAlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加音效") },
            text = {
                Md2DialogOutlinedField(
                    value = addTitle,
                    onValueChange = { addTitle = it },
                    label = "音效名称",
                    modifier = Modifier.onFocusChanged { addTitleFocused = it.isFocused },
                    singleLine = true,
                    trailingIcon = if (addTitleFocused && addTitle.isNotEmpty()) {
                        { Md2ClearFieldButton { addTitle = "" } }
                    } else {
                        null
                    }
                )
            },
            confirmButton = {
                Md2TextButton(
                    enabled = addTitle.trim().isNotEmpty(),
                    onClick = {
                        val title = addTitle.trim()
                        if (title.isNotEmpty()) {
                            onAdd(title)
                            showAddDialog = false
                            addTitle = ""
                        }
                    }
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                Md2TextButton(onClick = { showAddDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    deleteTargetItem?.let { target ->
        KigttsAlertDialog(
            onDismissRequest = { deleteTargetItem = null },
            title = { Text("删除音效") },
            text = { Text("确定删除“${target.title.ifBlank { "未命名音效" }}”吗？") },
            confirmButton = {
                Md2TextButton(onClick = {
                    viewModel.removeSoundboardItemsByIds(groupIndex, setOf(target.id))
                    deleteTargetItem = null
                    toast(context, "已删除音效")
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                Md2TextButton(onClick = { deleteTargetItem = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showBuiltinAudioPicker) {
        BuiltinFilePickerDialog(
            title = "选择音效音频",
            allowedExtensions = SoundboardAudioFileExtensions,
            onDismiss = {
                showBuiltinAudioPicker = false
                audioTargetIndex = null
            },
            onPicked = { uri ->
                showBuiltinAudioPicker = false
                clipSourceUri = uri
            },
            onOpenSystemPicker = {
                showBuiltinAudioPicker = false
                audioPicker.launch("audio/*")
            }
        )
    }

    if (showBuiltinBatchAudioPicker) {
        BuiltinFilePickerDialog(
            title = "批量添加音效",
            allowedExtensions = SoundboardAudioFileExtensions,
            multiSelect = true,
            onDismiss = { showBuiltinBatchAudioPicker = false },
            onPicked = { uri ->
                showBuiltinBatchAudioPicker = false
                viewModel.importSoundboardAudioFiles(groupIndex, listOf(uri))
            },
            onPickedMultiple = { uris ->
                showBuiltinBatchAudioPicker = false
                viewModel.importSoundboardAudioFiles(groupIndex, uris)
            },
            onOpenSystemPickerMultiple = {
                showBuiltinBatchAudioPicker = false
                batchAudioPicker.launch("audio/*")
            }
        )
    }

    val clipUri = clipSourceUri
    val targetIndex = audioTargetIndex
    if (clipUri != null && targetIndex != null && targetIndex in items.indices) {
        SoundboardAudioClipDialog(
            uri = clipUri,
            title = items[targetIndex].title.ifBlank { "音效" },
            onDismiss = {
                clipSourceUri = null
                audioTargetIndex = null
            },
            onImport = { startMs, endMs ->
                viewModel.importSoundboardAudioClip(groupIndex, targetIndex, clipUri, startMs, endMs)
                clipSourceUri = null
                audioTargetIndex = null
            }
        )
    }

    val editingIndex = editTargetIndex
    if (editingIndex != null && editingIndex in items.indices) {
        KigttsAlertDialog(
            onDismissRequest = { editTargetIndex = null },
            title = { Text("编辑音效") },
            text = {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Md2DialogOutlinedField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = "音效名称",
                        modifier = Modifier.onFocusChanged { editTitleFocused = it.isFocused },
                        singleLine = true,
                        topPadding = 0.dp,
                        trailingIcon = if (editTitleFocused && editTitle.isNotEmpty()) {
                            { Md2ClearFieldButton { editTitle = "" } }
                        } else {
                            null
                        }
                    )
                    Md2DialogOutlinedField(
                        value = editWakeWord,
                        onValueChange = { editWakeWord = it },
                        label = "触发词",
                        modifier = Modifier.onFocusChanged { editWakeWordFocused = it.isFocused },
                        singleLine = true,
                        topPadding = 0.dp,
                        trailingIcon = if (editWakeWordFocused && editWakeWord.isNotEmpty()) {
                            { Md2ClearFieldButton { editWakeWord = "" } }
                        } else {
                            null
                        }
                    )
                }
            },
            confirmButton = {
                Md2TextButton(onClick = {
                    val idx = editTargetIndex
                    if (idx != null && idx in items.indices) {
                        onItemChanged(
                            idx,
                            items[idx].copy(
                                title = editTitle.trim(),
                                wakeWord = editWakeWord.trim()
                            )
                        )
                    }
                    editTargetIndex = null
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                Md2TextButton(onClick = { editTargetIndex = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
internal fun SoundboardAudioClipDialog(
    uri: Uri,
    title: String,
    onDismiss: () -> Unit,
    onImport: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    val durationMs by produceState(initialValue = 0L, uri) {
        value = withContext(Dispatchers.IO) { SoundboardPresetIo.readDurationMs(context, uri) }
    }
    val durationForUi = durationMs.coerceAtLeast(1L)
    val durationAvailable = durationMs > 0L
    var clipRange by remember(uri, durationMs) {
        mutableStateOf(initialSoundboardClipRange(durationMs))
    }
    val startMs = clipRange.startMs
    val endMs = clipRange.endMs
    var player by remember(uri) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(uri) { mutableStateOf(false) }
    var previewMs by remember(uri) { mutableLongStateOf(0L) }

    fun stopPreview() {
        playing = false
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        previewMs = startMs
    }

    fun startPreview() {
        if (!durationAvailable || clipRange.durationMs <= 0L) return
        stopPreview()
        runCatching {
            MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                seekTo(startMs.toInt())
                start()
                player = this
                playing = true
                previewMs = startMs
            }
        }.onFailure {
            stopPreview()
            toast(context, "音频预览失败")
        }
    }

    LaunchedEffect(playing, startMs, endMs) {
        if (!playing) return@LaunchedEffect
        while (playing) {
            val current = runCatching { player?.currentPosition?.toLong() ?: startMs }.getOrDefault(startMs)
            previewMs = current.coerceIn(startMs, endMs)
            if (current >= endMs) {
                stopPreview()
                break
            }
            delay(48L)
        }
    }
    DisposableEffect(uri) {
        onDispose { stopPreview() }
    }

    KigttsAlertDialog(
        onDismissRequest = {
            stopPreview()
            onDismiss()
        },
        title = { Text("音频剪辑") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(
                    progress = ((previewMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L).toFloat())
                        .coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
                RangeSlider(
                    value = startMs.toFloat()..endMs.toFloat(),
                    onValueChange = { range ->
                        val adjustedRange = adjustSoundboardClipRange(
                            previous = clipRange,
                            requestedStartMs = range.start.toLong(),
                            requestedEndMs = range.endInclusive.toLong(),
                            durationMs = durationMs
                        )
                        stopPreview()
                        clipRange = adjustedRange
                        previewMs = adjustedRange.startMs
                    },
                    valueRange = 0f..durationForUi.toFloat(),
                    enabled = durationAvailable
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("开始时间：${formatDurationMs(startMs)}", style = MaterialTheme.typography.bodySmall)
                        Text("结束时间：${formatDurationMs(endMs)}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "片段时长：${formatDurationMs(clipRange.durationMs)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Md2TextButton(
                        enabled = durationAvailable,
                        onClick = { if (playing) stopPreview() else startPreview() }
                    ) {
                        MsIcon(if (playing) "pause" else "play_arrow", contentDescription = "预览范围")
                        Spacer(Modifier.width(4.dp))
                        Text(if (playing) "暂停" else "预览")
                    }
                }
            }
        },
        confirmButton = {
            Md2TextButton(
                enabled = durationAvailable && clipRange.durationMs > 0L,
                onClick = {
                    stopPreview()
                    onImport(startMs, endMs)
                }
            ) {
                Text("使用此片段")
            }
        },
        dismissButton = {
            Md2TextButton(onClick = {
                stopPreview()
                onDismiss()
            }) {
                Text("取消")
            }
        }
    )
}

internal fun formatDurationMs(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSec = safe / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    val frac = (safe % 1000L) / 100L
    return "%d:%02d.%d".format(Locale.US, min, sec, frac)
}

@Composable
internal fun SoundboardItemsRecyclerList(
    modifier: Modifier = Modifier,
    items: List<SoundboardItem>,
    selectionMode: Boolean,
    selectedItemIds: Set<Long>,
    onItemsChanged: (List<SoundboardItem>) -> Unit,
    onEditRequested: (Int, SoundboardItem) -> Unit,
    onAudioRequested: (Int) -> Unit,
    onDeleteRequested: (Int, SoundboardItem) -> Unit,
    onEnterSelectionMode: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    parentEdgeScrollBy: ((Int) -> Boolean)? = null,
    nestedScrollingEnabled: Boolean = false,
    clipListBounds: Boolean = false
) {
    val parentComposition = rememberCompositionContext()
    val onItemsChangedState = rememberUpdatedState(onItemsChanged)
    val onEditRequestedState = rememberUpdatedState(onEditRequested)
    val onAudioRequestedState = rememberUpdatedState(onAudioRequested)
    val onDeleteRequestedState = rememberUpdatedState(onDeleteRequested)
    val onEnterSelectionModeState = rememberUpdatedState(onEnterSelectionMode)
    val onToggleSelectionState = rememberUpdatedState(onToggleSelection)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val recycler = RecyclerView(ctx).apply {
                layoutManager = LinearLayoutManager(ctx)
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                clipToPadding = clipListBounds
                clipChildren = clipListBounds
                isNestedScrollingEnabled = nestedScrollingEnabled
                itemAnimator = DefaultItemAnimator().apply {
                    supportsChangeAnimations = false
                    addDuration = 120L
                    removeDuration = 120L
                    moveDuration = 160L
                    changeDuration = 0L
                }
            }
            val adapter = SoundboardItemRecyclerAdapter(
                parentComposition = parentComposition,
                onItemsChanged = { changed -> onItemsChangedState.value(changed) },
                onEditRequested = { index, item -> onEditRequestedState.value(index, item) },
                onAudioRequested = { index -> onAudioRequestedState.value(index) },
                onDeleteRequested = { index, item -> onDeleteRequestedState.value(index, item) },
                onEnterSelectionMode = { itemId -> onEnterSelectionModeState.value(itemId) },
                onToggleSelection = { itemId -> onToggleSelectionState.value(itemId) }
            )
            recycler.adapter = adapter
            val touchCallback = object : ItemTouchHelper.Callback() {
                private var moved = false
                private val edgeAutoScroller = DragEdgeAutoScroller()

                override fun isLongPressDragEnabled(): Boolean = false
                override fun isItemViewSwipeEnabled(): Boolean = false
                override fun getMovementFlags(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ): Int {
                    if (adapter.selectionMode) return makeMovementFlags(0, 0)
                    return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val ok = adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                    moved = moved || ok
                    return ok
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

                override fun onChildDraw(
                    c: android.graphics.Canvas,
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    dX: Float,
                    dY: Float,
                    actionState: Int,
                    isCurrentlyActive: Boolean
                ) {
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
                        edgeAutoScroller.update(recyclerView, viewHolder.itemView, dY, parentEdgeScrollBy)
                    } else {
                        edgeAutoScroller.stop()
                    }
                }

                override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                    edgeAutoScroller.stop()
                    super.clearView(recyclerView, viewHolder)
                    adapter.isDragging = false
                    adapter.clearDraggingItem()
                    if (moved) {
                        onItemsChangedState.value(adapter.snapshotItems())
                        moved = false
                    }
                }

                override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                    super.onSelectedChanged(viewHolder, actionState)
                    adapter.isDragging = actionState == ItemTouchHelper.ACTION_STATE_DRAG
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                        adapter.setDraggingPosition(viewHolder.bindingAdapterPosition)
                    } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                        edgeAutoScroller.stop()
                        adapter.clearDraggingItem()
                    }
                }
            }
            val touchHelper = ItemTouchHelper(touchCallback)
            touchHelper.attachToRecyclerView(recycler)
            adapter.onStartDrag = { vh -> touchHelper.startDrag(vh) }
            recycler
        },
        update = { recycler ->
            recycler.isNestedScrollingEnabled = nestedScrollingEnabled
            recycler.clipToPadding = clipListBounds
            recycler.clipChildren = clipListBounds
            val adapter = recycler.adapter as? SoundboardItemRecyclerAdapter ?: return@AndroidView
            adapter.updateSelection(selectionMode, selectedItemIds)
            adapter.submitFromState(items)
        }
    )
}

internal data class SoundboardEditableItem(
    val id: Long,
    var item: SoundboardItem
)

internal class SoundboardItemRecyclerAdapter(
    private val parentComposition: CompositionContext,
    private val onItemsChanged: (List<SoundboardItem>) -> Unit,
    private val onEditRequested: (Int, SoundboardItem) -> Unit,
    private val onAudioRequested: (Int) -> Unit,
    private val onDeleteRequested: (Int, SoundboardItem) -> Unit,
    private val onEnterSelectionMode: (Long) -> Unit,
    private val onToggleSelection: (Long) -> Unit
) : RecyclerView.Adapter<SoundboardItemRecyclerAdapter.ItemViewHolder>() {
    private val items = mutableListOf<SoundboardEditableItem>()
    var isDragging: Boolean = false
    var selectionMode: Boolean = false
        private set
    private var selectedItemIds: Set<Long> = emptySet()
    var onStartDrag: ((RecyclerView.ViewHolder) -> Unit)? = null
    private var draggingItemId: Long? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items[position].id
    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val composeView = ComposeView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setParentCompositionContext(parentComposition)
        }
        return ItemViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val row = items[position].item
        holder.bind(
            item = row,
            isDragged = draggingItemId == row.id,
            selectionMode = selectionMode,
            selected = row.id in selectedItemIds,
            canDelete = true,
            onDelete = {
                val idx = holder.bindingAdapterPosition
                if (idx in items.indices) {
                    onDeleteRequested(idx, items[idx].item)
                }
            },
            onEdit = {
                val idx = holder.bindingAdapterPosition
                if (idx in items.indices) onEditRequested(idx, items[idx].item)
            },
            onAudio = {
                val idx = holder.bindingAdapterPosition
                if (idx in items.indices) onAudioRequested(idx)
            },
            onEnterSelectionMode = {
                val idx = holder.bindingAdapterPosition
                if (idx in items.indices) onEnterSelectionMode(items[idx].item.id)
            },
            onToggleSelection = {
                val idx = holder.bindingAdapterPosition
                if (idx in items.indices) onToggleSelection(items[idx].item.id)
            },
            onStartDrag = {
                if (!selectionMode && holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onStartDrag?.invoke(holder)
                }
            }
        )
    }

    fun updateSelection(selectionMode: Boolean, selectedItemIds: Set<Long>) {
        val changed = this.selectionMode != selectionMode || this.selectedItemIds != selectedItemIds
        this.selectionMode = selectionMode
        this.selectedItemIds = selectedItemIds
        if (changed) notifyDataSetChanged()
    }

    fun submitFromState(newItems: List<SoundboardItem>) {
        if (isDragging) return
        items.clear()
        items.addAll(newItems.map { SoundboardEditableItem(id = it.id, item = it) })
        notifyDataSetChanged()
    }

    fun move(from: Int, to: Int): Boolean {
        if (from !in items.indices || to !in items.indices || from == to) return false
        val moved = items.removeAt(from)
        items.add(to, moved)
        notifyItemMoved(from, to)
        return true
    }

    fun snapshotItems(): List<SoundboardItem> = items.map { it.item }

    fun setDraggingPosition(position: Int) {
        draggingItemId = items.getOrNull(position)?.id
        notifyDataSetChanged()
    }

    fun clearDraggingItem() {
        draggingItemId = null
        notifyDataSetChanged()
    }

    class ItemViewHolder(
        private val composeView: ComposeView
    ) : RecyclerView.ViewHolder(composeView) {
        fun bind(
            item: SoundboardItem,
            isDragged: Boolean,
            selectionMode: Boolean,
            selected: Boolean,
            canDelete: Boolean,
            onDelete: () -> Unit,
            onEdit: () -> Unit,
            onAudio: () -> Unit,
            onEnterSelectionMode: () -> Unit,
            onToggleSelection: () -> Unit,
            onStartDrag: () -> Unit
        ) {
            composeView.setContent {
                KigttsFontScaleProvider {
                    SoundboardEditableRow(
                        item = item,
                        isDragged = isDragged,
                        selectionMode = selectionMode,
                        selected = selected,
                        canDelete = canDelete,
                        onDelete = onDelete,
                        onEdit = onEdit,
                        onAudio = onAudio,
                        onEnterSelectionMode = onEnterSelectionMode,
                        onToggleSelection = onToggleSelection,
                        onStartDrag = onStartDrag
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
internal fun SoundboardEditableRow(
    item: SoundboardItem,
    isDragged: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onAudio: () -> Unit,
    onEnterSelectionMode: () -> Unit,
    onToggleSelection: () -> Unit,
    onStartDrag: () -> Unit
) {
    val rowElevation by animateDpAsState(
        targetValue = if (isDragged) 10.dp else 0.dp,
        animationSpec = tween(
            durationMillis = if (isDragged) 120 else 160,
            easing = FastOutSlowInEasing
        ),
        label = "soundboard_item_elevation"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .heightIn(min = 72.dp)
            .combinedClickable(
                onClick = {
                    if (selectionMode) onToggleSelection()
                },
                onLongClick = onEnterSelectionMode
            ),
        shape = RoundedCornerShape(UiTokens.Radius),
        backgroundColor = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            md2CardContainerColor()
        },
        elevation = rowElevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AnimatedVisibility(visible = selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection() }
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { "未命名音效" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                val subtitle = buildList {
                    if (item.wakeWord.isNotBlank()) add("触发词：${item.wakeWord}")
                    if (item.audioPath.isNotBlank()) add(File(item.audioPath).name)
                }.joinToString(" · ").ifBlank { "未选择音频" }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AnimatedVisibility(
                visible = !selectionMode,
                enter = fadeIn(animationSpec = tween(120)) + expandHorizontally(animationSpec = tween(120)),
                exit = fadeOut(animationSpec = tween(100)) + shrinkHorizontally(animationSpec = tween(100))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Md2IconButton(
                        icon = "folder_open",
                        contentDescription = "选择音频",
                        onClick = onAudio
                    )
                    Md2IconButton(
                        icon = "edit",
                        contentDescription = "编辑条目",
                        onClick = onEdit
                    )
                    Md2IconButton(
                        icon = "drag_indicator",
                        contentDescription = "拖动排序",
                        onClick = {},
                        modifier = Modifier.pointerInteropFilter { ev ->
                            when (ev.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    onStartDrag()
                                    true
                                }
                                MotionEvent.ACTION_MOVE,
                                MotionEvent.ACTION_UP,
                                MotionEvent.ACTION_CANCEL -> true
                                else -> false
                            }
                        }
                    )
                    Md2IconButton(
                        icon = "delete",
                        contentDescription = "删除音效",
                        onClick = onDelete,
                        enabled = canDelete
                    )
                }
            }
        }
    }
}
