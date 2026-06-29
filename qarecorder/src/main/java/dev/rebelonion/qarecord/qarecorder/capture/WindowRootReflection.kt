package dev.rebelonion.qarecord.qarecorder.capture

import android.graphics.Point
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView

fun View.findReflectedWindow(): Window? {
    var currentClass: Class<*>? = javaClass
    while (currentClass != null) {
        currentClass.declaredFields.forEach { field ->
            if (Window::class.java.isAssignableFrom(field.type)) {
                val value = runCatching {
                    field.isAccessible = true
                    field.get(this)
                }.getOrNull()
                if (value is Window) return value
            }
        }
        currentClass = currentClass.superclass
    }

    return null
}

fun View.isComposePopupLayout(): Boolean {
    return javaClass.name == "androidx.compose.ui.window.PopupLayout"
}

fun View.shortDescription(): String {
    return "${javaClass.name}@${Integer.toHexString(System.identityHashCode(this))}"
}

fun View.diagnosticDescription(): String {
    return buildString {
        append(shortDescription())
        append(", hasWindow=")
        append(findReflectedWindow() != null)
        append(", childCount=")
        append((this@diagnosticDescription as? ViewGroup)?.childCount ?: 0)
        append(", composeHosts=")
        append(countByClassName("androidx.compose.ui.platform.AndroidComposeView"))
    }
}

fun View.clickableCandidatesDescription(): String {
    val candidates = mutableListOf<String>()

    fun visit(view: View) {
        val label = view.readableLabel()
        if (view.isClickable || label != null) {
            candidates += buildString {
                append(view.shortDescription())
                if (label != null) append(" label=\"$label\"")
                if (view.isClickable) append(" clickable")
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                visit(view.getChildAt(index))
            }
        }
    }

    visit(this)
    return candidates.take(20).joinToString(" | ").ifBlank { "none" }
}

fun View.countByClassName(className: String): Int {
    var count = if (javaClass.name == className) 1 else 0
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            count += getChildAt(index).countByClassName(className)
        }
    }
    return count
}

fun Any.viewRootDiagnosticDescription(): String {
    val inputFields = allFields()
        .filter { field ->
            val name = field.name.lowercase()
            val typeName = field.type.name.lowercase()
            name.contains("input") ||
                name.contains("receiver") ||
                name.contains("stage") ||
                typeName.contains("input") ||
                typeName.contains("receiver") ||
                typeName.contains("stage")
        }
        .take(30)
        .joinToString(", ") { field ->
            val value = runCatching {
                field.isAccessible = true
                field.get(this)
            }.getOrNull()
            "${field.name}:${field.type.simpleName}=${value?.javaClass?.name ?: "null"}"
        }
        .ifBlank { "none" }

    val inputMethods = allMethods()
        .filter { method ->
            val name = method.name.lowercase()
            name.contains("input") ||
                name.contains("touch") ||
                name.contains("motion") ||
                name.contains("dispatch")
        }
        .map { method ->
            "${method.name}(${method.parameterTypes.joinToString(",") { it.simpleName }})"
        }
        .distinct()
        .take(40)
        .joinToString(", ")
        .ifBlank { "none" }

    return "${javaClass.name}@${Integer.toHexString(System.identityHashCode(this))}, fields=[$inputFields], methods=[$inputMethods]"
}

fun Any.lastTouchPoint(): Point? {
    return runCatching {
        val point = Point()
        val method = javaClass.getDeclaredMethod("getLastTouchPoint", Point::class.java).apply {
            isAccessible = true
        }
        method.invoke(this, point)
        point
    }.getOrNull()
}

private fun View.readableLabel(): String? {
    if (this is TextView) {
        text?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        hint?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
    }

    contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
    return null
}

private fun Any.allFields(): List<java.lang.reflect.Field> {
    val fields = mutableListOf<java.lang.reflect.Field>()
    var current: Class<*>? = javaClass
    while (current != null) {
        fields += current.declaredFields
        current = current.superclass
    }
    return fields
}

private fun Any.allMethods(): List<java.lang.reflect.Method> {
    val methods = mutableListOf<java.lang.reflect.Method>()
    var current: Class<*>? = javaClass
    while (current != null) {
        methods += current.declaredMethods
        current = current.superclass
    }
    return methods
}
