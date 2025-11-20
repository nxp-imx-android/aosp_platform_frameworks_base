/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package android.appwidget

import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.view.size
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.frameworks.coretests.R
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Future
import kotlin.test.assertIs
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for AppWidgetHostView
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class AppWidgetHostViewTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val remoteViews = RemoteViews(context.packageName, R.layout.remote_views_test)
    private val viewAddListener = ViewAddListener()
    private val hostView = AppWidgetHostView(context).apply {
        setAppWidget(0, AppWidgetManager.getInstance(context).getInstalledProviders().first())
        setOnHierarchyChangeListener(viewAddListener)
    }

    @Test
    fun syncInflation() {
        hostView.updateAppWidget(remoteViews)
        assertThat(hostView.findViewById<View>(R.id.image)).isNotNull()
    }

    @Test
    fun asyncInflation() {
        val executor = RunnableList()
        hostView.setExecutor(executor)

        hostView.updateAppWidget(remoteViews)
        assertThat(hostView.findViewById<View>(R.id.image)).isNull()

        // Task queued.
        assertThat(executor.size).isEqualTo(1)

        // Execute the pending task
        executor.first().run()
        viewAddListener.addLatch.await()
        assertThat(hostView.findViewById<View>(R.id.image)).isNotNull()
    }

    @Test
    fun asyncInflation_cancelled() {
        val executor = RunnableList()
        hostView.setExecutor(executor)

        hostView.updateAppWidget(remoteViews.clone())
        hostView.updateAppWidget(remoteViews.clone())
        assertThat(hostView.findViewById<View>(R.id.image)).isNull()

        // Tasks queued.
        assertThat(executor.size).isEqualTo(2)
        // First task cancelled
        assertThat((executor[0] as Future<*>).isCancelled).isTrue()

        // Execute the pending task
        executor.forEach { it.run() }
        viewAddListener.addLatch.await()
        assertThat(hostView.findViewById<View>(R.id.image)).isNotNull()
    }

    @Test
    fun default_views_updated() {
        hostView.setAppWidget(
            0,
            AppWidgetManager.getInstance(context)
                .getInstalledProvidersForPackage(PKG_WIDGET_APP, Process.myUserHandle())
                .first()
        )
        hostView.updateAppWidget(null)

        // Default view added
        assertThat(hostView.size).isEqualTo(1)
        val textView = assertIs<TextView>(hostView.getChildAt(0))
        assertThat(textView.text.toString()).isEqualTo("Test string")
    }

    private class RunnableList : ArrayList<Runnable>(), Executor {
        override fun execute(runnable: Runnable) {
            add(runnable)
        }
    }

    private class ViewAddListener : ViewGroup.OnHierarchyChangeListener {
        val addLatch: CountDownLatch = CountDownLatch(1)

        override fun onChildViewAdded(parent: View?, child: View?) {
            addLatch.countDown()
        }

        override fun onChildViewRemoved(parent: View?, child: View?) {
        }
    }

    companion object {
        private const val PKG_WIDGET_APP = "android.app.coretests.noupdateappwidgettestapp"
    }
}
