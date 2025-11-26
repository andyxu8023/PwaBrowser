package ddd.pwa.browser.autofill

import android.app.assist.AssistStructure
import android.service.autofill.*
import android.os.CancellationSignal
import android.util.Log
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import ddd.pwa.browser.R

class MyAutofillService : AutofillService() {
    
    private val TAG = "MyAutofillService"
    
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        Log.d(TAG, "Fill request received")
        
        val contexts = request.fillContexts
        if (contexts.isEmpty()) {
            callback.onSuccess(null)
            return
        }
        
        val context = contexts.last()
        val structure = context.structure
        val parser = StructureParser(structure)
        parser.parse()
        
        val dataset = buildDataset(parser)
        if (dataset != null) {
            val response = FillResponse.Builder()
                .addDataset(dataset)
                .build()
            callback.onSuccess(response)
        } else {
            callback.onSuccess(null)
        }
    }
    
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        Log.d(TAG, "Save request received")
        callback.onSuccess()
    }
    
    private fun buildDataset(parser: StructureParser): Dataset? {
        val usernameField = parser.usernameField
        val passwordField = parser.passwordField
        
        // 检查字段是否有效
        if (usernameField == null || passwordField == null) {
            return null
        }
        
        val usernameId = usernameField.autofillId
        val passwordId = passwordField.autofillId
        
        // 确保 AutofillId 不为空
        if (usernameId == null || passwordId == null) {
            return null
        }
        
        return try {
            val datasetBuilder = Dataset.Builder()
            
            // 用户名填充
            val usernamePresentation = RemoteViews(packageName, android.R.layout.simple_list_item_1)
            usernamePresentation.setTextViewText(android.R.id.text1, "填充用户名")
            datasetBuilder.setValue(
                usernameId,
                AutofillValue.forText("example_user"),
                usernamePresentation
            )
            
            // 密码填充
            val passwordPresentation = RemoteViews(packageName, android.R.layout.simple_list_item_1)
            passwordPresentation.setTextViewText(android.R.id.text1, "填充密码")
            datasetBuilder.setValue(
                passwordId,
                AutofillValue.forText("example_password"),
                passwordPresentation
            )
            
            datasetBuilder.build()
        } catch (e: Exception) {
            Log.e(TAG, "Build dataset failed", e)
            null
        }
    }
}

class StructureParser(private val structure: AssistStructure) {
    var usernameField: AssistStructure.ViewNode? = null
    var passwordField: AssistStructure.ViewNode? = null
    var webDomain: String? = null
    
    fun parse() {
        val windowNodes = structure.windowNodeCount
        for (i in 0 until windowNodes) {
            val node = structure.getWindowNodeAt(i)
            parseViewNode(node.rootViewNode)
        }
    }
    
    private fun parseViewNode(node: AssistStructure.ViewNode) {
        // 检查 HTML 属性
        node.htmlInfo?.attributes?.forEach { attr ->
            when {
                attr.first == "type" && attr.second == "password" -> {
                    passwordField = node
                }
                attr.first == "type" && (attr.second == "text" || attr.second == "email") -> {
                    usernameField = node
                }
            }
        }
        
        // 检查自动填充提示
        node.autofillHints?.forEach { hint ->
            when (hint) {
                "username", "email" -> usernameField = node
                "password" -> passwordField = node
            }
        }
        
        // 获取网页域名
        if (node.webDomain != null) {
            webDomain = node.webDomain
        }
        
        // 递归遍历子节点
        for (i in 0 until node.childCount) {
            parseViewNode(node.getChildAt(i))
        }
    }
}
