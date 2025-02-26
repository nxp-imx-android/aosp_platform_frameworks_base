package com.android.systemui.shared.condition

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ConditionExtensionsTest : SysuiTestCase() {
    private lateinit var testScope: TestScope
    private val testCallback =
        Condition.Callback {
            // This is a no-op
        }

    @Before
    fun setUp() {
        testScope = TestScope(UnconfinedTestDispatcher())
    }

    @Test
    fun flowInitiallyTrue() =
        testScope.runTest {
            val flow = flowOf(true)
            val condition = flow.toCondition(scope = this, Condition.START_EAGERLY)

            assertThat(condition.isConditionSet).isFalse()

            condition.testStart()
            assertThat(condition.isConditionSet).isTrue()
            assertThat(condition.isConditionMet).isTrue()
        }

    @Test
    fun flowInitiallyFalse() =
        testScope.runTest {
            val flow = flowOf(false)
            val condition = flow.toCondition(scope = this, Condition.START_EAGERLY)

            assertThat(condition.isConditionSet).isFalse()

            condition.testStart()
            assertThat(condition.isConditionSet).isTrue()
            assertThat(condition.isConditionMet).isFalse()
        }

    @Test
    fun emptyFlowWithNoInitialValue() =
        testScope.runTest {
            val flow = emptyFlow<Boolean>()
            val condition = flow.toCondition(scope = this, Condition.START_EAGERLY)
            condition.testStop()

            assertThat(condition.isConditionSet).isFalse()
            assertThat(condition.isConditionMet).isFalse()
        }

    @Test
    fun emptyFlowWithInitialValueOfTrue() =
        testScope.runTest {
            val flow = emptyFlow<Boolean>()
            val condition =
                flow.toCondition(
                    scope = this,
                    strategy = Condition.START_EAGERLY,
                    initialValue = true,
                )
            condition.testStart()

            assertThat(condition.isConditionSet).isTrue()
            assertThat(condition.isConditionMet).isTrue()
        }

    @Test
    fun emptyFlowWithInitialValueOfFalse() =
        testScope.runTest {
            val flow = emptyFlow<Boolean>()
            val condition =
                flow.toCondition(
                    scope = this,
                    strategy = Condition.START_EAGERLY,
                    initialValue = false,
                )
            condition.testStart()

            assertThat(condition.isConditionSet).isTrue()
            assertThat(condition.isConditionMet).isFalse()
        }

    @Test
    fun conditionUpdatesWhenFlowEmitsNewValue() =
        testScope.runTest {
            val flow = MutableStateFlow(false)
            val condition = flow.toCondition(scope = this, strategy = Condition.START_EAGERLY)
            condition.testStart()

            assertThat(condition.isConditionSet).isTrue()
            assertThat(condition.isConditionMet).isFalse()

            flow.value = true
            assertThat(condition.isConditionMet).isTrue()

            flow.value = false
            assertThat(condition.isConditionMet).isFalse()

            condition.testStop()
        }

    @Test
    fun stoppingConditionUnsubscribesFromFlow() =
        testScope.runTest {
            val flow = MutableSharedFlow<Boolean>()
            val condition = flow.toCondition(scope = this, strategy = Condition.START_EAGERLY)
            assertThat(flow.subscriptionCount.value).isEqualTo(0)

            condition.testStart()
            assertThat(flow.subscriptionCount.value).isEqualTo(1)

            condition.testStop()
            assertThat(flow.subscriptionCount.value).isEqualTo(0)
        }

    fun Condition.testStart() {
        addCallback(testCallback)
    }

    fun Condition.testStop() {
        removeCallback(testCallback)
    }
}
