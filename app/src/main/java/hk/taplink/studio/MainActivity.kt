package hk.taplink.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat

private val light = lightColorScheme(
    primary = Color(0xff005c50), onPrimary = Color.White, primaryContainer = Color(0xffc6eedb),
    onPrimaryContainer = Color(0xff073f34), secondary = Color(0xff95502c), secondaryContainer = Color(0xfffbe0bf),
    tertiaryContainer = Color(0xfff9ddc5), onTertiaryContainer = Color(0xff57311f),
    background = Color(0xfffaf7ef), surface = Color(0xfffaf7ef), surfaceContainer = Color(0xfff2eee3),
    surfaceVariant = Color(0xffe7e9de), onSurface = Color(0xff182d2a), onBackground = Color(0xff182d2a),
    onSurfaceVariant = Color(0xff485952), outlineVariant = Color(0xffc7cfc3))
private val dark = darkColorScheme(
    primary = Color(0xff88dbbc), primaryContainer = Color(0xff144f42), secondary = Color(0xffffba8c),
    background = Color(0xff10211f), surface = Color(0xff10211f), surfaceContainer = Color(0xff1c312c),
    surfaceVariant = Color(0xff263e36), onSurface = Color(0xffedf2e6), onBackground = Color(0xffedf2e6))

class MainActivity : ComponentActivity() {
    private val controller: WalletController by viewModels()
    private var walletIntent by mutableIntStateOf(0)
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.action == Platform.ACTION_OPEN_WALLET) walletIntent++
    }
    override fun onResume() { super.onResume(); if (controller.wallet != null) controller.refresh() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme = controller.wallet?.prefs?.theme
            val night = theme.equals("Dark", true) || (!theme.equals("Light", true) && isSystemInDarkTheme())
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = !night
                }
            }
            MaterialTheme(colorScheme = if (night) dark else light) { TapLinkApp(controller, walletIntent) }
        }
    }
}

@Composable
private fun TapLinkApp(controller: WalletController, walletIntent: Int) {
    var route by rememberSaveable { mutableStateOf("Wallet") }
    var lastWalletIntent by rememberSaveable { mutableIntStateOf(walletIntent) }
    LaunchedEffect(walletIntent) {
        if (walletIntent != lastWalletIntent) {
            route = "Wallet"
            lastWalletIntent = walletIntent
        }
    }
    val holder = rememberSaveableStateHolder()
    BackHandler(route != "Wallet") { route = "Wallet" }
    val routes = listOf("Wallet", "Activity", "Explore", "Settings")
    Scaffold(topBar = {
        Row(Modifier.fillMaxWidth().background(Color(0xff063e37)).statusBarsPadding()
            .height(50.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.mark), null, Modifier.size(31.dp))
            Spacer(Modifier.width(8.dp))
            Text("TapLink", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 23.sp)
            Spacer(Modifier.weight(1f))
            Text("SIMULATED WALLET", color = Color(0xffc4dacb), fontSize = 10.sp, letterSpacing = 1.1.sp)
        }
    }, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            routes.forEachIndexed { index, item ->
                NavigationBarItem(selected = route == item || (item == "Wallet" && route == "Passes"),
                    onClick = { route = item },
                    icon = { NavIcon(index) }, label = { Text(item) })
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (controller.wallet == null) {
                if (controller.busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else Button(onClick = controller::refresh, modifier = Modifier.align(Alignment.Center)) { Text("Open wallet") }
            } else holder.SaveableStateProvider(route) {
                when (route) {
                    "Wallet" -> WalletScreen(controller) { route = it }
                    "Explore" -> ExploreScreen(controller)
                    "Settings" -> SettingsScreen(controller)
                    "Passes" -> ExtrasScreen(controller)
                    else -> ActivityScreen(controller)
                }
            }
            if (controller.busy && controller.wallet != null) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    controller.error?.let { message ->
        AlertDialog(onDismissRequest = controller::clearError, title = { Text("Please check") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = controller::clearError) { Text("OK") } })
    }
}

@Composable
private fun NavIcon(index: Int) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(23.dp)) {
        val u = size.width / 24f
        fun line(x: Int,y: Int,a: Int,b: Int) =
            drawLine(color, androidx.compose.ui.geometry.Offset(x*u,y*u), androidx.compose.ui.geometry.Offset(a*u,b*u), 1.8f*u)
        fun ring(x: Int,y: Int,r: Int) = drawCircle(color, r*u, androidx.compose.ui.geometry.Offset(x*u,y*u),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.8f*u))
        when (index) {
            0 -> { drawRoundRect(color, androidx.compose.ui.geometry.Offset(2*u,5*u), androidx.compose.ui.geometry.Size(20*u,15*u),
                androidx.compose.ui.geometry.CornerRadius(2*u), style=androidx.compose.ui.graphics.drawscope.Stroke(1.8f*u)); line(3,10,21,10) }
            1 -> { line(3,20,21,20); line(6,17,6,11); line(12,17,12,4); line(18,17,18,8) }
            2 -> { ring(12,12,9); line(15,8,13,13); line(13,13,8,16); line(8,16,10,11); line(10,11,15,8) }
            else -> { line(3,7,21,7); line(3,17,21,17); ring(8,7,3); ring(16,17,3) }
        }
    }
}
