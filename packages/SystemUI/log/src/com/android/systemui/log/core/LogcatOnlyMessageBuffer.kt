/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.log.core

import android.util.Log
import com.android.systemui.log.LogMessageImpl
import kotlin.collections.ArrayDeque

/**
 * A simple implementation of [MessageBuffer] that forwards messages to [android.util.Log]
 * immediately. This defeats the intention behind [LogBuffer] and should only be used when
 * [LogBuffer]s are unavailable in a certain context.
 */
class LogcatOnlyMessageBuffer(
    val targetLogLevel: LogLevel,
    val maxMessageCount: Int = DEFAULT_MESSAGE_MAX_COUNT,
) : MessageBuffer {
    private val messages = ArrayDeque<LogMessageImpl>(maxMessageCount)

    override fun obtain(
        tag: String,
        level: LogLevel,
        messagePrinter: MessagePrinter,
        exception: Throwable?,
    ): LogMessage {
        val message =
            synchronized(messages) { messages.removeFirstOrNull() }
                ?: LogMessageImpl.Factory.create()
        message.reset(tag, level, System.currentTimeMillis(), messagePrinter, exception)
        return message
    }

    override fun commit(message: LogMessage) {
        if (message.level >= targetLogLevel) {
            val strMessage = message.messagePrinter(message)
            when (message.level) {
                LogLevel.VERBOSE -> Log.v(message.tag, strMessage, message.exception)
                LogLevel.DEBUG -> Log.d(message.tag, strMessage, message.exception)
                LogLevel.INFO -> Log.i(message.tag, strMessage, message.exception)
                LogLevel.WARNING -> Log.w(message.tag, strMessage, message.exception)
                LogLevel.ERROR -> Log.e(message.tag, strMessage, message.exception)
                LogLevel.WTF -> Log.wtf(message.tag, strMessage, message.exception)
            }
        }

        if (message is LogMessageImpl) {
            message.clear()
            synchronized(messages) {
                if (messages.size < maxMessageCount) {
                    messages.addLast(message)
                }
            }
        }
    }

    companion object {
        val DEFAULT_MESSAGE_MAX_COUNT = 4
    }
}
