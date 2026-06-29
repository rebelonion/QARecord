package dev.rebelonion.qarecord.qarecorder.capture

import android.view.View
import android.view.ViewGroup

object WindowRootRegistry {
    fun entries(): List<WindowRootEntry> {
        return runCatching {
            val globalClass = Class.forName("android.view.WindowManagerGlobal")
            val instance = globalClass.getMethod("getInstance").invoke(null)
            val viewsField = globalClass.getDeclaredField("mViews").apply {
                isAccessible = true
            }
            val rootsField = globalClass.getDeclaredField("mRoots").apply {
                isAccessible = true
            }
            @Suppress("UNCHECKED_CAST")
            val views = viewsField.get(instance) as? List<View>
            val roots = rootsField.get(instance) as? List<*>
            views.orEmpty()
                .filterIsInstance<ViewGroup>()
                .mapIndexed { index, view ->
                    WindowRootEntry(
                        view = view,
                        viewRootImpl = roots?.getOrNull(index),
                    )
                }
        }.getOrDefault(emptyList())
    }
}

data class WindowRootEntry(
    val view: View,
    val viewRootImpl: Any?,
)
