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
 * limitations under the License.
 */

package com.android.compose.animation.scene

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compose.animation.scene.TestScenes.SceneA
import com.android.compose.animation.scene.TestScenes.SceneB
import com.android.compose.animation.scene.TestScenes.SceneC
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun disableSwipesWhenScrolling() {
        lateinit var layoutImpl: SceneTransitionLayoutImpl
        rule.setContent {
            SceneTransitionLayoutForTesting(
                remember { MutableSceneTransitionLayoutStateForTests(SceneA) },
                onLayoutImpl = { layoutImpl = it },
            ) {
                scene(SceneA) {
                    Box(
                        Modifier.fillMaxSize()
                            .disableSwipesWhenScrolling()
                            .scrollable(rememberScrollableState { it }, Orientation.Vertical)
                    )
                }
            }
        }

        val content = layoutImpl.content(SceneA)
        assertThat(content.areNestedSwipesAllowed()).isTrue()
        rule.onRoot().performTouchInput {
            down(topLeft)
            moveBy(bottomLeft)
        }

        assertThat(content.areNestedSwipesAllowed()).isFalse()
        rule.onRoot().performTouchInput { up() }
        assertThat(content.areNestedSwipesAllowed()).isTrue()
    }

    @Test
    fun disableSwipesWhenScrolling_outerDragDisabled() {
        val state = rule.runOnUiThread { MutableSceneTransitionLayoutStateForTests(SceneA) }
        var consumeScrolls = true
        var touchSlop = 0f

        rule.setContent {
            touchSlop = LocalViewConfiguration.current.touchSlop
            SceneTransitionLayout(state) {
                scene(SceneA, mapOf(Swipe.Down to SceneB)) {
                    Box(
                        Modifier.fillMaxSize()
                            .disableSwipesWhenScrolling()
                            .scrollable(
                                rememberScrollableState { if (consumeScrolls) it else 0f },
                                Orientation.Vertical,
                            )
                    )
                }
                scene(SceneB) { Box(Modifier.fillMaxSize()) }
            }
        }

        // Draw down. The whole drag is consumed by the scrollable and the STL should still be idle.
        rule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, touchSlop + 10f))
        }
        assertThat(state.currentTransition).isNull()

        // Continue dragging down but don't consume the scrolls. The STL should still be idle given
        // that we use disableSwipesWhenScrolling().
        consumeScrolls = false
        rule.onRoot().performTouchInput { moveBy(Offset(0f, 10f)) }
        assertThat(state.currentTransition).isNull()
    }

    @Test
    fun lifecycle() {
        @Composable
        fun OnLifecycle(f: (Lifecycle?) -> Unit) {
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            DisposableEffect(lifecycle) {
                f(lifecycle)
                onDispose { f(null) }
            }
        }

        val state = rule.runOnUiThread { MutableSceneTransitionLayoutStateForTests(SceneA) }
        var lifecycleA: Lifecycle? = null
        var lifecycleB: Lifecycle? = null
        var lifecycleC: Lifecycle? = null

        val parentLifecycleOwner =
            rule.runOnUiThread {
                object : LifecycleOwner {
                    override val lifecycle = LifecycleRegistry(this)

                    init {
                        lifecycle.currentState = Lifecycle.State.RESUMED
                    }
                }
            }

        var composeContent by mutableStateOf(true)
        rule.setContent {
            if (!composeContent) return@setContent

            CompositionLocalProvider(LocalLifecycleOwner provides parentLifecycleOwner) {
                SceneTransitionLayout(state) {
                    scene(SceneA) { OnLifecycle { lifecycleA = it } }
                    scene(SceneB) { OnLifecycle { lifecycleB = it } }
                    scene(SceneC, alwaysCompose = true) { OnLifecycle { lifecycleC = it } }
                }
            }
        }

        // currentScene = A. B is not composed, C is CREATED.
        val parentLifecycle = parentLifecycleOwner.lifecycle
        assertThat(lifecycleA).isSameInstanceAs(parentLifecycle)
        assertThat(lifecycleA?.currentState).isEqualTo(Lifecycle.State.RESUMED)
        assertThat(lifecycleB).isNull()
        assertThat(lifecycleC?.currentState).isEqualTo(Lifecycle.State.CREATED)

        // currentScene = B. A is not composed, C is CREATED.
        rule.runOnUiThread { state.snapTo(SceneB) }
        rule.waitForIdle()
        assertThat(lifecycleA).isNull()
        assertThat(lifecycleB).isSameInstanceAs(parentLifecycle)
        assertThat(lifecycleB?.currentState).isEqualTo(Lifecycle.State.RESUMED)
        assertThat(lifecycleC?.currentState).isEqualTo(Lifecycle.State.CREATED)

        // currentScene = C. A and B are not composed, C is RESUMED.
        rule.runOnUiThread { state.snapTo(SceneC) }
        rule.waitForIdle()
        assertThat(lifecycleA).isNull()
        assertThat(lifecycleB).isNull()
        assertThat(lifecycleC?.currentState).isEqualTo(Lifecycle.State.RESUMED)

        // parentLifecycle = STARTED. A and B are not composed, C is STARTED.
        rule.runOnUiThread { parentLifecycleOwner.lifecycle.currentState = Lifecycle.State.STARTED }
        rule.waitForIdle()
        assertThat(lifecycleA?.currentState).isEqualTo(null)
        assertThat(lifecycleB?.currentState).isEqualTo(null)
        assertThat(lifecycleC?.currentState).isEqualTo(Lifecycle.State.STARTED)

        // currentScene = A. B is not composed, C is CREATED.
        rule.runOnUiThread { state.snapTo(SceneA) }
        rule.waitForIdle()
        assertThat(lifecycleA).isSameInstanceAs(parentLifecycle)
        assertThat(lifecycleA?.currentState).isEqualTo(Lifecycle.State.STARTED)
        assertThat(lifecycleB).isNull()
        assertThat(lifecycleC?.currentState).isEqualTo(Lifecycle.State.CREATED)

        // Remove the STL from composition. The lifecycle of scene C should be destroyed.
        val lastLifecycleC = lifecycleC
        composeContent = false
        rule.waitForIdle()
        assertThat(lifecycleC).isNull()
        assertThat(lastLifecycleC?.currentState).isEqualTo(Lifecycle.State.DESTROYED)
    }
}
