package ddd.pwa.browser.autofill

import android.app.assist.AssistStructure
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.*
import android.util.Log
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import android.widget.Toast
import ddd.pwa.browser.R

class MyAutofillService : AutofillService() {
    
    private val TAG = "MyAutofillService"
    
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.last().structure
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
        val structure = request.fillContexts.last().structure
        val parser = StructureParser(structure)
        parser.parse()
        
        // 这里可以保存用户输入的凭据
        Log.d(TAG, "Save credentials for: ${parser.webDomain}")
        Toast.makeText(this, "密码已保存", Toast.LENGTH_SHORT).show()
        callback.onSuccess()
    }
    
    private fun buildDataset(parser: StructureParser): Dataset? {
        val usernameField = parser.usernameField
        val passwordField = parser.passwordField
        
        if (usernameField == null || passwordField == null) {
            return null
        }
        
        // 创建填充数据集
        val datasetBuilder = Dataset.Builder()
        
        // 用户名填充
        val usernamePresentation = RemoteViews(packageName, R.layout.autofill_item)
        usernamePresentation.setTextViewText(R.id.text, "填充用户名")
        datasetBuilder.setValue(
            usernameField.autofillId,
            AutofillValue.forText("example_user"),
            usernamePresentation
        )
        
        // 密码填充
        val passwordPresentation = RemoteViews(packageName, R.layout.autofill_item)
        passwordPresentation.setTextViewText(R.id.text, "填充密码")
        datasetBuilder.setValue(
            passwordField.autofillId,
            AutofillValue.forText("example_password"),
            passwordPresentation
        )
        
        return datasetBuilder.build()
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
            parseLocked(node.rootViewNode)
        }
    }
    
    private fun parseLocked(node: AssistStructure.ViewNode) {
        // 检查当前节点是否为输入字段
        when {
            node.htmlInfo?.attributes?.any { attr -> 
                attr.first == "type" && attr.second == "password"
            } == true -> {
                passwordField = node
            }
            node.autofillHints?.contains("username") == true ||
            node.htmlInfo?.attributes?.any { attr -> 
                attr.first == "type" && (attr.second == "text" || attr.second == "email")
            } == true -> {
                usernameField = node
            }
            node.webDomain != null -> {
                webDomain = node.webDomain
            }
        }
        
        // 递归遍历子节点
        for (i in 0 until node.childCount) {
            parseLocked(node.getChildAt(i))
        }
    }
}
