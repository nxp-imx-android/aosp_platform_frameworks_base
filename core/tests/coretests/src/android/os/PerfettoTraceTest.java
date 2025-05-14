/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.os;

import static android.os.PerfettoTrace.Category;

import static com.google.common.truth.Truth.assertThat;

import static perfetto.protos.ChromeLatencyInfoOuterClass.ChromeLatencyInfo.LatencyComponentType.COMPONENT_INPUT_EVENT_LATENCY_BEGIN_RWH;
import static perfetto.protos.ChromeLatencyInfoOuterClass.ChromeLatencyInfo.LatencyComponentType.COMPONENT_INPUT_EVENT_LATENCY_SCROLL_UPDATE_ORIGINAL;

import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.ArraySet;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.protobuf.ExtensionRegistryLite;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import perfetto.protos.AndroidTrackEventOuterClass;
import perfetto.protos.AndroidTrackEventOuterClass.AndroidMessageQueue;
import perfetto.protos.AndroidTrackEventOuterClass.AndroidTrackEvent;
import perfetto.protos.ChromeLatencyInfoOuterClass.ChromeLatencyInfo;
import perfetto.protos.ChromeLatencyInfoOuterClass.ChromeLatencyInfo.ComponentInfo;
import perfetto.protos.DataSourceConfigOuterClass.DataSourceConfig;
import perfetto.protos.DebugAnnotationOuterClass.DebugAnnotation;
import perfetto.protos.DebugAnnotationOuterClass.DebugAnnotationName;
import perfetto.protos.InternedDataOuterClass.InternedData;
import perfetto.protos.SourceLocationOuterClass.SourceLocation;
import perfetto.protos.TraceConfigOuterClass.TraceConfig;
import perfetto.protos.TraceConfigOuterClass.TraceConfig.BufferConfig;
import perfetto.protos.TraceConfigOuterClass.TraceConfig.DataSource;
import perfetto.protos.TraceConfigOuterClass.TraceConfig.TriggerConfig;
import perfetto.protos.TraceConfigOuterClass.TraceConfig.TriggerConfig.Trigger;
import perfetto.protos.TraceOuterClass.Trace;
import perfetto.protos.TracePacketOuterClass.TracePacket;
import perfetto.protos.TrackDescriptorOuterClass.TrackDescriptor;
import perfetto.protos.TrackEventConfigOuterClass.TrackEventConfig;
import perfetto.protos.TrackEventOuterClass.EventCategory;
import perfetto.protos.TrackEventOuterClass.EventName;
import perfetto.protos.TrackEventOuterClass.TrackEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This class is used to test the native tracing support. Run this test
 * while tracing on the emulator and then run traceview to view the trace.
 */
@RunWith(AndroidJUnit4.class)
@DisabledOnRavenwood(blockedBy = PerfettoTrace.class)
public class PerfettoTraceTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation());

    private static final String TAG = "PerfettoTraceTest";
    private static final String FOO = "foo";
    private static final String BAR = "bar";
    private static final String TEXT_ABOVE_4K_SIZE =
            new String(new char[8192]).replace('\0', 'a');

    private static final Category FOO_CATEGORY = new Category(FOO);
    private static final int MESSAGE = 1234567;
    private static final int MESSAGE_DELAYED = 7654321;

    private final Set<String> mCategoryNames = new ArraySet<>();
    private final Set<String> mEventNames = new ArraySet<>();
    private final Set<String> mDebugAnnotationNames = new ArraySet<>();
    private final Set<String> mTrackNames = new ArraySet<>();

    @Before
    public void setUp() {
        PerfettoTrace.register(true);
        FOO_CATEGORY.register();

        mCategoryNames.clear();
        mEventNames.clear();
        mDebugAnnotationNames.clear();
        mTrackNames.clear();
    }

    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V2)
    public void testDebugAnnotations() throws Exception {
        TraceConfig traceConfig = getTraceConfig(FOO);

        PerfettoTrace.Session session = new PerfettoTrace.Session(true, traceConfig.toByteArray());

        PerfettoTrace.instant(FOO_CATEGORY, "event")
                .setFlow(2)
                .setTerminatingFlow(3)
                .addArg("long_val", 10000000000L)
                .addArg("bool_val", true)
                .addArg("double_val", 3.14)
                .addArg("string_val", FOO)
                .emit();

        byte[] traceBytes = session.close();

        Trace trace = Trace.parseFrom(traceBytes);

        boolean hasTrackEvent = false;
        boolean hasDebugAnnotations = false;
        for (TracePacket packet: trace.getPacketList()) {
            TrackEvent event;
            if (packet.hasTrackEvent()) {
                hasTrackEvent = true;
                event = packet.getTrackEvent();

                if (TrackEvent.Type.TYPE_INSTANT.equals(event.getType())
                        && event.getDebugAnnotationsCount() == 4 && event.getFlowIdsCount() == 1
                        && event.getTerminatingFlowIdsCount() == 1) {
                    hasDebugAnnotations = true;

                    List<DebugAnnotation> annotations = event.getDebugAnnotationsList();

                    assertThat(annotations.get(0).getIntValue()).isEqualTo(10000000000L);
                    assertThat(annotations.get(1).getBoolValue()).isTrue();
                    assertThat(annotations.get(2).getDoubleValue()).isEqualTo(3.14);
                    assertThat(annotations.get(3).getStringValue()).isEqualTo(FOO);
                }
            }

            collectInternedData(packet);
        }

        assertThat(hasTrackEvent).isTrue();
        assertThat(hasDebugAnnotations).isTrue();
        assertThat(mCategoryNames).contains(FOO);

        assertThat(mDebugAnnotationNames).contains("long_val");
        assertThat(mDebugAnnotationNames).contains("bool_val");
        assertThat(mDebugAnnotationNames).contains("double_val");
        assertThat(mDebugAnnotationNames).contains("string_val");
    }

    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V2)
    public void testNamedTrack() throws Exception {
        TraceConfig traceConfig = getTraceConfig(FOO);

        PerfettoTrace.Session session = new PerfettoTrace.Session(true, traceConfig.toByteArray());

        PerfettoTrace.begin(FOO_CATEGORY, "event")
                .usingNamedTrack(PerfettoTrace.getProcessTrackUuid(), FOO)
                .emit();


        PerfettoTrace.end(FOO_CATEGORY)
                .usingNamedTrack(PerfettoTrace.getThreadTrackUuid(Process.myTid()), "bar")
                .emit();

        Trace trace = Trace.parseFrom(session.close());

        boolean hasTrackEvent = false;
        boolean hasTrackUuid = false;
        for (TracePacket packet: trace.getPacketList()) {
            TrackEvent event;
            if (packet.hasTrackEvent()) {
                hasTrackEvent = true;
                event = packet.getTrackEvent();

                if (TrackEvent.Type.TYPE_SLICE_BEGIN.equals(event.getType())
                        && event.hasTrackUuid()) {
                    hasTrackUuid = true;
                }

                if (TrackEvent.Type.TYPE_SLICE_END.equals(event.getType())
                        && event.hasTrackUuid()) {
                    hasTrackUuid &= true;
                }
            }

            collectInternedData(packet);
            collectTrackNames(packet);
        }

        assertThat(hasTrackEvent).isTrue();
        assertThat(hasTrackUuid).isTrue();
        assertThat(mCategoryNames).contains(FOO);
        assertThat(mTrackNames).contains(FOO);
        assertThat(mTrackNames).contains("bar");
    }

    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V2)
    public void testProcessThreadNamedTrack() throws Exception {
        TraceConfig traceConfig = getTraceConfig(FOO);

        PerfettoTrace.Session session = new PerfettoTrace.Session(true, traceConfig.toByteArray());

        PerfettoTrace.begin(FOO_CATEGORY, "event")
                .usingProcessNamedTrack(FOO)
                .emit();


        PerfettoTrace.end(FOO_CATEGORY)
                .usingThreadNamedTrack(Process.myTid(), "bar")
                .emit();

        Trace trace = Trace.parseFrom(session.close());

        boolean hasTrackEvent = false;
        boolean hasTrackUuid = false;
        for (TracePacket packet: trace.getPacketList()) {
            TrackEvent event;
            if (packet.hasTrackEvent()) {
                hasTrackEvent = true;
                event = packet.getTrackEvent();

                if (TrackEvent.Type.TYPE_SLICE_BEGIN.equals(event.getType())
                        && event.hasTrackUuid()) {
                    hasTrackUuid = true;
                }

                if (TrackEvent.Type.TYPE_SLICE_END.equals(event.getType())
                        && event.hasTrackUuid()) {
                    hasTrackUuid &= true;
                }
            }

            collectInternedData(packet);
            collectTrackNames(packet);
        }

        assertThat(hasTrackEvent).isTrue();
        assertThat(hasTrackUuid).isTrue();
        assertThat(mCategoryNames).contains(FOO);
        assertThat(mTrackNames).contains(FOO);
        assertThat(mTrackNames).contains("bar");
    }

    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V2)
    public void testCounterSimple() throws Exception {
        TraceConfig traceConfig = getTraceConfig(FOO);

        PerfettoTrace.Session session = new PerfettoTrace.Session(true, traceConfig.toByteArray());

        PerfettoTrace.counter(FOO_CATEGORY, 16, FOO).emit();

        PerfettoTrace.counter(FOO_CATEGORY, 3.14, "bar").emit();

        Trace trace = Trace.parseFrom(session.close());

        boolean hasTrackEvent = false;
        boolean hasCounterValue = false;
        boolean hasDoubleCounterValue = false;
        for (TracePacket packet : trace.getPacketList()) {
            TrackEvent event;
            if (packet.hasTrackEvent()) {
                hasTrackEvent = true;
                event = packet.getTrackEvent();

                if (TrackEvent.Type.TYPE_COUNTER.equals(event.getType())
                        && event.getCounterValue() == 16) {
                    hasCounterValue = true;
                }

                if (TrackEvent.Type.TYPE_COUNTER.equals(event.getType())
                        && event.getDoubleCounterValue() == 3.14) {
                    hasDoubleCounterValue = true;
                }
            }

            collectTrackNames(packet);
        }

        assertThat(hasTrackEvent).isTrue();
        assertThat(hasCounterValue).isTrue();
        assertThat(hasDoubleCounterValue).isTrue();
        assertThat(mTrackNames).contains(FOO);
        assertThat(mTrackNames).contains(BAR);
    }

    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V2)
    public void testCounter() throws Exception {
        TraceConfig traceConfig = getTraceConfig(FOO);

        PerfettoTrace.Session session = new PerfettoTrace.Session(true, traceConfig.toByteArray());

        PerfettoTrace.counter(FOO_CATEGORY, 16)
                .usingCounterTrack(PerfettoTrace.getProcessTrackUuid(), FOO).emit();

        PerfettoTrace.counter(FOO_CATEGORY, 3.14)
                .usingCounterTrack(PerfettoTrace.getThreadTrackUuid(Process.myTid()),
                                   "bar").emit();

        Trace trace = Trace.parseFrom(session.close());

        boolean hasTrackEvent = false;
        boolean hasCounterValue = false;
        boolean hasDoubleCounterValue = false;
        for (TracePacket packet: trace.getPacketList()) {
            TrackEvent event;
            if (packet.hasTrackEvent()) {
                hasTrackEvent = true;
                event = packet.getTrackEvent();

                if (TrackEvent.Type.TYPE_COUNTER.equals(event.getType())
                        && event.getCounterValue() == 16) {
                    hasCounterValue = true;
                }

                if (TrackEvent.Type.TYPE_COUNTER.equals(event.getType())
                        && event.getDoubleCounterValue() == 3.14) {
                    hasDoubleCounterValue = true;
                }
            }

            collectTrackNames(packet);
        }

        assertThat(hasTrackEvent).isTrue();
        assertThat(hasCounterValue).isTrue();
        assertThat(hasDoubleCounterValue).isTrue();
        assertThat(mTrackNames).contains(FOO);
        assertThat(mTrackNames).contains("bar");
    }

    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V2)
    public void testProcessThreadCounter() throws Exception {
        TraceConfig traceConfig = getTraceConfig(FOO);

        PerfettoTrace.Session session = new PerfettoTrace.Session(true, traceConfig.toByteArray());

        PerfettoTrace.counter(FOO_CATEGORY, 16).usingProcessCounterTrack(FOO).emit();

        PerfettoTrace.counter(FOO_CATEGORY, 3.14)
                .usingThreadCounterTrack(Process.myTid(), "bar").emit();

        Trace trace = Trace.parseFrom(session.close());

        boolean hasTrackEvent = false;
        boolean hasCounterValue = false;
        boolean hasDoubleCounterValue = false;
        for (TracePacket packet: trace.getPacketList()) {
            TrackEvent event;
            if (packet.hasTrackEvent()) {
                hasTrackEvent = true;
                event = packet.getTrackEvent();

                if (TrackEvent.Type.TYPE_COUNTER.equals(event.getType())
                        && event.getCounterValue() == 16) {
                    hasCounterValue = true;
                }

                if (TrackEvent.Type.TYPE_COUNTER.equals(event.getType())
                        && event.getDoubleCounterValue() == 3.14) {
                    hasDoubleCounterValue = true;
                }
            }

            collectTrackNames(packet);
        }

        assertThat(hasTrackEvent).isTrue();
        assertThat(hasCounterValue).isTrue();
        assertThat(hasDoubleCounterValue).isTrue();
        assertThat(mTrackNames).contains(FOO);
        assertThat(mTrackNames).contains("bar");
    }

    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V2)
    public void testProto() throws Exception {
        TraceConfig traceConfig = getTraceConfig(FOO);

        PerfettoTrace.Session session = new PerfettoTrace.Session(true, traceConfig.toByteArray());

        PerfettoTrace.instant(FOO_CATEGORY, "event_proto")
                .beginProto()
                .beginNested(33L)
                .addField(4L, 2L)
                .addField(3, "ActivityManagerService.java:11489")
                .endNested()
                .addField(2001, "AIDL::IActivityManager")
                .endProto()
                .emit();

        byte[] traceBytes = session.close();

        Trace trace = Trace.parseFrom(traceBytes);

        boolean hasTrackEvent = false;
        boolean hasSourceLocation = false;
        for (TracePacket packet: trace.getPacketList()) {
            TrackEvent event;
            if (packet.hasTrackEvent()) {
                hasTrackEvent = true;
                event = packet.getTrackEvent();

                if (TrackEvent.Type.TYPE_INSTANT.equals(event.getType())
                        && event.hasSourceLocation()) {
                    SourceLocation loc = event.getSourceLocation();
                    if ("ActivityManagerService.java:11489".equals(loc.getFunctionName())
                            && loc.getLineNumber() == 2) {
                        hasSourceLocation = true;
                    }
                }
            }

            collectInternedData(packet);
        }

        assertThat(hasTrackEvent).isTrue();
        assertThat(hasSourceLocation).isTrue();
        assertThat(mCategoryNames).contains(FOO);
    }

    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V2)
    public void testProtoWithSlowPath() throws Exception {
        TraceConfig traceConfig = getTraceConfig(FOO);

        PerfettoTrace.Session session = new PerfettoTrace.Session(true, traceConfig.toByteArray());

        PerfettoTrace.instant(FOO_CATEGORY, "event_proto")
                .beginProto()
                .beginNested(33L)
                .addField(4L, 2L)
                .addField(3, TEXT_ABOVE_4K_SIZE)
                .endNested()
                .addField(2001, "AIDL::IActivityManager")
                .endProto()
                .emit();

        byte[] traceBytes = session.close();

        Trace trace = Trace.parseFrom(traceBytes);

        boolean hasTrackEvent = false;
        boolean hasSourceLocation = false;
        for (TracePacket packet: trace.getPacketList()) {
            TrackEvent event;
            if (packet.hasTrackEvent()) {
                hasTrackEvent = true;
                event = packet.getTrackEvent();

                if (TrackEvent.Type.TYPE_INSTANT.equals(event.getType())
                        && event.hasSourceLocation()) {
                    SourceLocation loc = event.getSourceLocation();
                    if (TEXT_ABOVE_4K_SIZE.equals(loc.getFunctionName())
                            && loc.getLineNumber() == 2) {
                        hasSourceLocation = true;
                    }
                }
            }

            collectInternedData(packet);
        }

        assertThat(hasTrackEvent).isTrue();
        assertThat(hasSourceLocation).isTrue();
        assertThat(mCategoryNames).contains(FOO);
    }

    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V2)
    public void testProtoNested() throws Exception {
        TraceConfig traceConfig = getTraceConfig(FOO);

        PerfettoTrace.Session session = new PerfettoTrace.Session(true, traceConfig.toByteArray());

        PerfettoTrace.instant(FOO_CATEGORY, "event_proto_nested")
                .beginProto()
                .beginNested(29L)
                .beginNested(4L)
                .addField(1L, 2)
                .addField(2L, 20000)
                .endNested()
                .beginNested(4L)
                .addField(1L, 1)
                .addField(2L, 40000)
                .endNested()
                .endNested()
                .endProto()
                .emit();

        byte[] traceBytes = session.close();

        Trace trace = Trace.parseFrom(traceBytes);

        boolean hasTrackEvent = false;
        boolean hasChromeLatencyInfo = false;

        for (TracePacket packet: trace.getPacketList()) {
            TrackEvent event;
            if (packet.hasTrackEvent()) {
                hasTrackEvent = true;
                event = packet.getTrackEvent();

                if (TrackEvent.Type.TYPE_INSTANT.equals(event.getType())
                        && event.hasChromeLatencyInfo()) {
                    ChromeLatencyInfo latencyInfo = event.getChromeLatencyInfo();
                    if (latencyInfo.getComponentInfoCount() == 2) {
                        hasChromeLatencyInfo = true;
                        ComponentInfo cmpInfo1 = latencyInfo.getComponentInfo(0);
                        assertThat(cmpInfo1.getComponentType())
                                .isEqualTo(COMPONENT_INPUT_EVENT_LATENCY_SCROLL_UPDATE_ORIGINAL);
                        assertThat(cmpInfo1.getTimeUs()).isEqualTo(20000);

                        ComponentInfo cmpInfo2 = latencyInfo.getComponentInfo(1);
                        assertThat(cmpInfo2.getComponentType())
                                .isEqualTo(COMPONENT_INPUT_EVENT_LATENCY_BEGIN_RWH);
                        assertThat(cmpInfo2.getTimeUs()).isEqualTo(40000);
                    }
                }
            }

            collectInternedData(packet);
        }

        assertThat(hasTrackEvent).isTrue();
        assertThat(hasChromeLatencyInfo).isTrue();
        assertThat(mCategoryNames).contains(FOO);
    }

    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V2)
    public void testActivateTrigger() throws Exception {
        TraceConfig traceConfig = getTriggerTraceConfig(FOO, FOO);

        PerfettoTrace.Session session = new PerfettoTrace.Session(true, traceConfig.toByteArray());

        PerfettoTrace.instant(FOO_CATEGORY, "event_trigger").emit();

        PerfettoTrace.activateTrigger(FOO, 1000);

        byte[] traceBytes = session.close();

        Trace trace = Trace.parseFrom(traceBytes);

        boolean hasTrackEvent = false;
        boolean hasChromeLatencyInfo = false;

        for (TracePacket packet: trace.getPacketList()) {
            TrackEvent event;
            if (packet.hasTrackEvent()) {
                hasTrackEvent = true;
            }

            collectInternedData(packet);
        }

        assertThat(mCategoryNames).contains(FOO);
    }

    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V2)
    public void testRegister() throws Exception {
        TraceConfig traceConfig = getTraceConfig(BAR);

        Category barCategory = new Category(BAR);
        PerfettoTrace.Session session = new PerfettoTrace.Session(true, traceConfig.toByteArray());

        PerfettoTrace.instant(barCategory, "event")
                .addArg("before", 1)
                .emit();

        barCategory.register();

        PerfettoTrace.instant(barCategory, "event")
                .addArg("after", 1)
                .emit();

        byte[] traceBytes = session.close();

        Trace trace = Trace.parseFrom(traceBytes);

        boolean hasTrackEvent = false;
        for (TracePacket packet: trace.getPacketList()) {
            TrackEvent event;
            if (packet.hasTrackEvent()) {
                hasTrackEvent = true;
                event = packet.getTrackEvent();
            }

            collectInternedData(packet);
        }

        assertThat(hasTrackEvent).isTrue();
        assertThat(mCategoryNames).contains(BAR);

        assertThat(mDebugAnnotationNames).contains("after");
        assertThat(mDebugAnnotationNames).doesNotContain("before");
    }

    @Test
    @RequiresFlagsEnabled(android.os.Flags.FLAG_PERFETTO_SDK_TRACING_V2)
    public void testMessageQueue() throws Exception {
        PerfettoTrace.MQ_CATEGORY.register();
        final String mqReceiverThreadName = "mq_test_thread";
        final String mqSenderThreadName = Thread.currentThread().getName();
        final HandlerThread thread = new HandlerThread(mqReceiverThreadName);
        thread.start();
        final Handler handler = thread.getThreadHandler();
        final CountDownLatch latch = new CountDownLatch(1);

        PerfettoTrace.Session session = new PerfettoTrace.Session(true,
                getTraceConfig("mq").toByteArray());

        final int eventsCount = 4;

        handler.sendEmptyMessage(MESSAGE);
        handler.sendEmptyMessageDelayed(MESSAGE_DELAYED, 10);
        handler.sendEmptyMessage(MESSAGE);
        handler.postDelayed(() -> {
            latch.countDown();
        }, 20);
        assertThat(latch.await(100, TimeUnit.MILLISECONDS)).isTrue();

        ExtensionRegistryLite registry = ExtensionRegistryLite.newInstance();
        AndroidTrackEventOuterClass.registerAllExtensions(registry);

        Trace trace = Trace.parseFrom(session.close(), registry);

        int counterCount = 0;
        int sliceEndEventCount = 0;

        Long counterTrackUuid = null;
        ArrayList<AndroidMessageQueue> messageQueueSendEvents = new ArrayList<>();
        ArrayList<Long> messageQueueSendFlowIds = new ArrayList<>();
        ArrayList<AndroidMessageQueue> messageQueueReceiveEvents = new ArrayList<>();
        ArrayList<Long> messageQueueReceiveTerminateFlowIds = new ArrayList<>();

        for (TracePacket packet : trace.getPacketList()) {
            if (packet.hasTrackDescriptor()) {
                TrackDescriptor trackDescriptor = packet.getTrackDescriptor();
                if (trackDescriptor.getName().equals(mqReceiverThreadName)
                        && trackDescriptor.hasCounter()) {
                    counterTrackUuid = trackDescriptor.getUuid();
                }
            } else if (packet.hasTrackEvent()) {
                TrackEvent event = packet.getTrackEvent();
                if (event.getType() == TrackEvent.Type.TYPE_INSTANT) {
                    if (event.hasExtension(AndroidTrackEvent.messageQueue)) {
                        AndroidMessageQueue mqEvent =
                                event.getExtension(AndroidTrackEvent.messageQueue);
                        messageQueueSendEvents.add(mqEvent);
                        assertThat(event.getFlowIdsCount()).isEqualTo(1);
                        messageQueueSendFlowIds.add(event.getFlowIds(0));
                    }
                } else if (event.getType() == TrackEvent.Type.TYPE_SLICE_BEGIN) {
                    if (event.hasExtension(AndroidTrackEvent.messageQueue)) {
                        AndroidMessageQueue mqEvent =
                                event.getExtension(AndroidTrackEvent.messageQueue);
                        messageQueueReceiveEvents.add(mqEvent);
                        assertThat(event.getTerminatingFlowIdsCount()).isEqualTo(1);
                        messageQueueReceiveTerminateFlowIds.add(event.getTerminatingFlowIds(0));
                    }
                } else if (event.getType() == TrackEvent.Type.TYPE_SLICE_END) {
                    sliceEndEventCount++;
                } else if (event.getType() == TrackEvent.Type.TYPE_COUNTER) {
                    if (counterTrackUuid != null && event.getTrackUuid() == counterTrackUuid) {
                        counterCount++;
                    }
                }
            }
            collectInternedData(packet);
        }

        assertThat(mCategoryNames).containsExactly("mq");
        assertThat(mEventNames).containsExactly("message_queue_send", "message_queue_receive");
        assertThat(counterTrackUuid).isNotNull();
        assertThat(counterCount).isAtLeast(eventsCount);
        assertThat(sliceEndEventCount).isEqualTo(eventsCount);

        assertThat(messageQueueSendEvents).hasSize(eventsCount);
        assertThat(messageQueueSendFlowIds).hasSize(eventsCount);

        assertThat(messageQueueReceiveEvents).hasSize(eventsCount);
        assertThat(messageQueueReceiveTerminateFlowIds).hasSize(eventsCount);

        assertThat(messageQueueSendEvents.get(0).getMessageCode()).isEqualTo(MESSAGE);
        assertThat(messageQueueSendEvents.get(0).getMessageDelayMs()).isEqualTo(0);
        assertThat(messageQueueSendEvents.get(0).getReceivingThreadName()).isEqualTo(
                mqReceiverThreadName);

        assertThat(messageQueueSendEvents.get(1).getMessageCode()).isEqualTo(MESSAGE_DELAYED);
        assertThat(messageQueueSendEvents.get(1).getMessageDelayMs()).isEqualTo(10);
        assertThat(messageQueueSendEvents.get(1).getReceivingThreadName()).isEqualTo(
                mqReceiverThreadName);

        assertThat(messageQueueSendEvents.get(2).getMessageCode()).isEqualTo(MESSAGE);
        assertThat(messageQueueSendEvents.get(2).getMessageDelayMs()).isEqualTo(0);
        assertThat(messageQueueSendEvents.get(2).getReceivingThreadName()).isEqualTo(
                mqReceiverThreadName);

        assertThat(messageQueueSendEvents.get(3).getMessageCode()).isEqualTo(0);
        assertThat(messageQueueSendEvents.get(3).getMessageDelayMs()).isEqualTo(20);
        assertThat(messageQueueSendEvents.get(3).getReceivingThreadName()).isEqualTo(
                mqReceiverThreadName);

        assertThat(messageQueueReceiveEvents.get(0).getSendingThreadName()).isEqualTo(
                mqSenderThreadName);
        assertThat(messageQueueReceiveEvents.get(1).getSendingThreadName()).isEqualTo(
                mqSenderThreadName);
        assertThat(messageQueueReceiveEvents.get(2).getSendingThreadName()).isEqualTo(
                mqSenderThreadName);
        assertThat(messageQueueReceiveEvents.get(3).getSendingThreadName()).isEqualTo(
                mqSenderThreadName);

        // The second message was send with a delay, and was received after the third one,
        // so we assert that the terminating flow Id of the third message is equal to the flow Id
        // of the second and the terminating flow id of the second is equal to the flow Id of the
        // third.
        assertThat(messageQueueSendFlowIds.get(0)).isEqualTo(
                messageQueueReceiveTerminateFlowIds.get(0));
        assertThat(messageQueueSendFlowIds.get(1)).isEqualTo(
                messageQueueReceiveTerminateFlowIds.get(2));
        assertThat(messageQueueSendFlowIds.get(2)).isEqualTo(
                messageQueueReceiveTerminateFlowIds.get(1));
        assertThat(messageQueueSendFlowIds.get(3)).isEqualTo(
                messageQueueReceiveTerminateFlowIds.get(3));
    }

    private TraceConfig getTraceConfig(String cat) {
        BufferConfig bufferConfig = BufferConfig.newBuilder().setSizeKb(1024).build();
        TrackEventConfig trackEventConfig = TrackEventConfig
                .newBuilder()
                .addEnabledCategories(cat)
                .build();
        DataSourceConfig dsConfig = DataSourceConfig
                .newBuilder()
                .setName("track_event")
                .setTargetBuffer(0)
                .setTrackEventConfig(trackEventConfig)
                .build();
        DataSource ds = DataSource.newBuilder().setConfig(dsConfig).build();
        TraceConfig traceConfig = TraceConfig
                .newBuilder()
                .addBuffers(bufferConfig)
                .addDataSources(ds)
                .build();
        return traceConfig;
    }

    private TraceConfig getTriggerTraceConfig(String cat, String triggerName) {
        BufferConfig bufferConfig = BufferConfig.newBuilder().setSizeKb(1024).build();
        TrackEventConfig trackEventConfig = TrackEventConfig
                .newBuilder()
                .addEnabledCategories(cat)
                .build();
        DataSourceConfig dsConfig = DataSourceConfig
                .newBuilder()
                .setName("track_event")
                .setTargetBuffer(0)
                .setTrackEventConfig(trackEventConfig)
                .build();
        DataSource ds = DataSource.newBuilder().setConfig(dsConfig).build();
        Trigger trigger = Trigger.newBuilder().setName(triggerName).build();
        TriggerConfig triggerConfig = TriggerConfig
                .newBuilder()
                .setTriggerMode(TriggerConfig.TriggerMode.STOP_TRACING)
                .setTriggerTimeoutMs(1000)
                .addTriggers(trigger)
                .build();
        TraceConfig traceConfig = TraceConfig
                .newBuilder()
                .addBuffers(bufferConfig)
                .addDataSources(ds)
                .setTriggerConfig(triggerConfig)
                .build();
        return traceConfig;
    }

    private void collectInternedData(TracePacket packet) {
        if (!packet.hasInternedData()) {
            return;
        }

        InternedData data = packet.getInternedData();

        for (EventCategory cat : data.getEventCategoriesList()) {
            mCategoryNames.add(cat.getName());
        }
        for (EventName ev : data.getEventNamesList()) {
            mEventNames.add(ev.getName());
        }
        for (DebugAnnotationName dbg : data.getDebugAnnotationNamesList()) {
            mDebugAnnotationNames.add(dbg.getName());
        }
    }

    private void collectTrackNames(TracePacket packet) {
        if (!packet.hasTrackDescriptor()) {
            return;
        }
        TrackDescriptor desc = packet.getTrackDescriptor();
        mTrackNames.add(desc.getName());
    }
}
