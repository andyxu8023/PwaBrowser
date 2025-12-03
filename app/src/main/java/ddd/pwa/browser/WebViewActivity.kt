package ddd.pwa.browser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.ActivityManager.TaskDescription
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.autofill.AutofillManager
import android.webkit.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class WebViewActivity : AppCompatActivity() {
    val mTAG: String = "WebViewActivity"
    lateinit var mySharedPreferences: SharedPreferences
    private lateinit var myWebView: WebView
    private lateinit var myLinearLayout: LinearLayout
    private lateinit var myImageLogo: ImageView
    private val statusBarHeight: Int = 40
    var firstUpdated: Boolean = true
    var hostUrl: String = ""
    private var mode: Int? = null
    var name: String = ""
    var nameOK: Boolean = false
    var logo: Bitmap? = null
    var logoOK: Boolean = false
    var bgColor: Int = 0
    var bgColorOK: Boolean = false
    private var isFull: Boolean = true
    var fullOK: Boolean = false
    var mFilePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var autofillManager: AutofillManager

    val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val data = if (result.data != null) arrayOf(result.data!!.data!!) else emptyArray()
            Log.e(mTAG, "获取选择的文件: $data")
            mFilePathCallback?.onReceiveValue(data)
        } else {
            mFilePathCallback?.onReceiveValue(null)
        }
        mFilePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val url = intent.getStringExtra("url")
        mode = intent.getIntExtra("mode", LAUNCH_MODE.SHOW_URL_PAGE.intValue)
        name = intent.getStringExtra("name") ?: "沉浸浏览"
        isFull = intent.getBooleanExtra("full", true)
        @Suppress("DEPRECATION")
        setTaskDescription(TaskDescription(name))
        if (url == null) {
            finish()
            return
        } else {
            hostUrl = url
        }
        
        mySharedPreferences = getSharedPreferences("mySharedPreferences", MODE_PRIVATE)
        val parsedUrl = URL(url)
        bgColor = mySharedPreferences.getInt("${parsedUrl.host}:${parsedUrl.port}bg_color", 
            ContextCompat.getColor(this, R.color.logo_bg))
        setActivityColor()
        logo = getBitmapFromCache()
        super.onCreate(savedInstanceState)
        initializeServiceWorker()
        setContentView(R.layout.activity_web_view)
        autofillManager = getSystemService(AutofillManager::class.java)
        initializeWebView()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && myWebView.canGoBack()) {
            myWebView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun returnMain() {
        if (logoOK && nameOK && bgColorOK && fullOK) {
            if (mode == LAUNCH_MODE.GET_URL_DETAIL.intValue) {
                Log.e(mTAG, "returnMain: url, $hostUrl")
                Log.e(mTAG, "returnMain: name, $name")
                val resultIntent = Intent()
                resultIntent.putExtra("url", hostUrl)
                resultIntent.putExtra("name", name)
                resultIntent.putExtra("logo", logo)
                resultIntent.putExtra("full", isFull)
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                @Suppress("DEPRECATION") 
                val taskDescription = TaskDescription(name, logo)
                setTaskDescription(taskDescription)
            }
        }
    }

    fun setNotFull() {
        isFull = false
        val param = myWebView.layoutParams as MarginLayoutParams
        param.setMargins(0, (statusBarHeight * resources.displayMetrics.density).toInt(), 0, 0)
    }

    fun hideBgLogo() {
        val logo: LinearLayout = findViewById(R.id.bg_logo)
        if (mode == LAUNCH_MODE.GET_URL_DETAIL.intValue) {
            logo.visibility = View.GONE
        } else {
            val alphaAnimation = ObjectAnimator.ofFloat(logo, "alpha", 1.0f, 0.0f)
            alphaAnimation.duration = 1000
            alphaAnimation.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    logo.visibility = View.GONE
                }
            })
            alphaAnimation.start()
        }
    }

    private fun convertStreamToString(inputStream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = StringBuilder()
        var line: String?
        try {
            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line).append("\n")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                inputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return stringBuilder.toString()
    }

    fun convertColorString(colorString: String): String {
        var convertedColorString = colorString.trim('"', '\'').lowercase(Locale.ROOT)
        if (convertedColorString.startsWith("#")) {
            return convertedColorString
        }
        if (convertedColorString.startsWith("rgba")) {
            val matchResult = Regex("""\d+\.?\d*""").findAll(convertedColorString)
            val rgbaValues = matchResult.map { it.value.toFloat() }.toList()
            val alpha = (rgbaValues[3] * 255).toInt()
            val hexValues = rgbaValues.subList(0, 3).map { String.format("%02X", it.toInt()) }
            convertedColorString = "#${String.format("%02X", alpha)}${hexValues.joinToString("")}"
        } else if (convertedColorString.startsWith("rgb")) {
            val matchResult = Regex("""\d+""").findAll(convertedColorString)
            val rgbValues = matchResult.map { it.value.toInt() }.toList()
            val hexValues = rgbValues.map { String.format("%02X", it) }
            convertedColorString = "#${hexValues.joinToString(separator = "")}"
        } else if (convertedColorString.startsWith("hsl")) {
            val matchResult = Regex("""\d+\.?\d*%?""").findAll(convertedColorString)
            val hslValues = matchResult.map { it.value }.toList()
            val h = hslValues[0].toFloat() / 360
            val s = hslValues[1].removeSuffix("%").toFloat() / 100
            val l = hslValues[2].removeSuffix("%").toFloat() / 100
            val c = (1 - kotlin.math.abs(2 * l - 1)) * s
            val x = c * (1 - kotlin.math.abs((h * 6) % 2 - 1))
            val m = l - c / 2
            var (r, g, b) = when {
                h < 1 / 6f -> listOf(c, x, 0f)
                h < 2 / 6f -> listOf(x, c, 0f)
                h < 3 / 6f -> listOf(0f, c, x)
                h < 4 / 6f -> listOf(0f, x, c)
                h < 5 / 6f -> listOf(x, 0f, c)
                else -> listOf(c, 0f, x)
            }
            r += m
            g += m
            b += m
            if (convertedColorString.startsWith("hsla")) {
                val alpha = hslValues[3].removeSuffix("%").toFloat() / 100
                r *= alpha
                g *= alpha
                b *= alpha
            }
            convertedColorString = "#${String.format("%02X%02X%02X", (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())}"
        } else {
            convertedColorString = ""
        }
        return convertedColorString
    }

    private fun encode(data: String): String {
        return Base64.getEncoder().encodeToString(data.toByteArray())
    }

    @Suppress("unused")
    private fun decode(base64: String): String {
        return String(Base64.getDecoder().decode(base64))
    }

    fun saveBitmapToCache(bitmap: Bitmap?) {
        if (bitmap == null) {
            return
        }
        try {
            val cacheDir = cacheDir
            val file = File(cacheDir, encode(hostUrl))
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun getBitmapFromCache(): Bitmap? {
        try {
            val cacheDir = cacheDir
            val file = File(cacheDir, encode(hostUrl))
            val fis = FileInputStream(file)
            val bitmap = BitmapFactory.decodeStream(fis)
            fis.close()
            return bitmap
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    fun replaceCss(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (url.endsWith(".css")) {
            val inputStream: InputStream?
            try {
                val connection: HttpURLConnection =
                    URL(request.url.toString()).openConnection() as HttpURLConnection
                inputStream = connection.inputStream
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
            var cssString = convertStreamToString(inputStream!!)
            val safe = "env(safe-area-inset-top)"
            if (cssString.indexOf(safe) > -1) {
                cssString = cssString.replace(safe, "" + statusBarHeight + "px")
                Log.w(
                    mTAG,
                    "replaceCss, 替换成功: " + request.isForMainFrame + ": " + request.url
                )
            }
            return WebResourceResponse(
                "text/css",
                "UTF-8",
                ByteArrayInputStream(cssString.toByteArray())
            )
        }
        return null
    }

    fun setActivityColor() {
        window.setBackgroundDrawable(ColorDrawable(bgColor))
        val isLight = Color.red(bgColor) * 0.299 + Color.green(bgColor) * 0.587 + Color.blue(bgColor) * 0.114 >= 186
        if (isLight) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = 0
        }
    }

    private fun initializeServiceWorker() {
        val swController = ServiceWorkerController.getInstance()
        swController.serviceWorkerWebSettings.allowContentAccess = true
        swController.setServiceWorkerClient(object : ServiceWorkerClient() {
            override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                Log.e(
                    mTAG,
                    "serviceWorker, shouldInterceptRequest: " + request.isForMainFrame + ": " + request.url
                )
                val response = replaceCss(request)
                return if (response !== null) {
                    response
                } else {
                    super.shouldInterceptRequest(request)
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initializeWebView() {
        myWebView = findViewById(R.id.webview_ding)
        myWebView.setBackgroundColor(bgColor)
        if (!isFull) {
            setNotFull()
        }
        myLinearLayout = findViewById(R.id.bg_logo)
        myLinearLayout.setBackgroundColor(bgColor)
        myImageLogo = findViewById(R.id.image_logo)
        myImageLogo.setBackgroundColor(bgColor)
        if (logo != null) {
            myImageLogo.setImageBitmap(logo)
        }

        WebView.setWebContentsDebuggingEnabled(true)
        val settings: WebSettings = myWebView.settings
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.useWideViewPort = true
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        
        myWebView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES_AUTO_DETECT
        
        myWebView.webViewClient = WVViewClient(this, this@WebViewActivity)
        myWebView.webChromeClient = WVChromeClient(this, this@WebViewActivity)
        myImageLogo.setOnClickListener {
            myWebView.stopLoading()
            myWebView.clearCache(true)
            myWebView.loadUrl(hostUrl)
            Toast.makeText(applicationContext,"正在刷新..", Toast.LENGTH_SHORT).show()
        }

        myWebView.loadUrl(hostUrl)
    }

    fun commitAutofillContext(currentUrl: String?) {
        if (::autofillManager.isInitialized && autofillManager.isEnabled && !currentUrl.isNullOrBlank()) {
            autofillManager.commit()
            Log.d(mTAG, "已为URL提交自动填充上下文: $currentUrl")
        } else {
            if (!autofillManager.isEnabled) {
                Log.d(mTAG, "自动填充服务未启用")
            }
        }
    }
}

@Suppress("unused")
private class WVChromeClient(private val _context: Context, private val _m: WebViewActivity):
    WebChromeClient() {
    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        _m.name = title ?: "PWA"
        Log.d(_m.mTAG, "onReceivedTitle: ${_m.name}")
        _m.nameOK = true
        _m.returnMain()
    }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        super.onReceivedIcon(view, icon)
        _m.logo = icon
        _m.saveBitmapToCache(icon)
        Log.d(_m.mTAG, "onReceivedIcon: ok")
        _m.logoOK = true
        _m.returnMain()
    }

    @SuppressLint("QueryPermissionsNeeded")
    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        val intent = fileChooserParams?.createIntent()
        _m.launcher.launch(intent)
        _m.mFilePathCallback = filePathCallback
        return true
    }
}

private class WVViewClient(private val _context: Context, private val _m: WebViewActivity):
    WebViewClient() {
    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.proceed()
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (Uri.parse(url).host?.let { _m.hostUrl.indexOf(it) }!! > -1 &&
            Uri.parse(url).path?.matches(Regex(".*/api/v\\d+/system/logging$")) != true) {
            return false
        }
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            if (view != null) {
                ContextCompat.startActivity(_context, this, null)
            }
        }
        return true
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        _m.firstUpdated = false
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest
    ): WebResourceResponse? {
        Log.e(
            _m.mTAG,
            "WebView, shouldInterceptRequest: " + request.isForMainFrame + ": " + request.url
        )
        val response = _m.replaceCss(request)
        return if (response !== null) {
            response
        } else {
            super.shouldInterceptRequest(view, request)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        _m.commitAutofillContext(url)

        if (_m.firstUpdated) {
            _m.firstUpdated = false
            _m.hideBgLogo()
        }
        
        var js = "javascript:window.getComputedStyle(document.body).backgroundColor;"
        view?.evaluateJavascript(js) { result ->
            val newResult = _m.convertColorString(result)
            Log.e(_m.mTAG, "onPageFinished: $result  $newResult  $url")
            if (newResult != "") {
                _m.bgColor = Color.parseColor(newResult)
                _m.setActivityColor()
                val parsedUrl = URL(url)
                _m.mySharedPreferences.edit().putInt("${parsedUrl.host}:${parsedUrl.port}bg_color", _m.bgColor).apply()
            }
            Log.e(_m.mTAG, "onPageFinished: ${_m.bgColor}")
            _m.bgColorOK = true
            _m.returnMain()
        }
        
        js = "javascript:(function() {" +
                "var metas = document.getElementsByTagName('meta');" +
                    "for (var i = 0; i < metas.length; i++) {" +
                        "if (metas[i].getAttribute('name') === 'viewport') {" +
                            "var content = metas[i].getAttribute('content');" +
                            "if (content.indexOf('viewport-fit=cover') > -1) {" +
                                "return true;" +
                            "}" +
                        "}" +
                    "}" +
                "return false;" +
                "})()"
                
        view?.evaluateJavascript(js) { result ->
            Log.e(_m.mTAG, "onPageFinished: result")
            _m.fullOK = true
            if (result != "true") {
                _m.setNotFull()
            }
            _m.returnMain()
        }
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        if (!isReload) {
            _m.commitAutofillContext(url)
        }
    }
}