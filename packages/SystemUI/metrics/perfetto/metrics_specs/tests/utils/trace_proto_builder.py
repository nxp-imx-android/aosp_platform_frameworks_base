#!/usr/bin/env python3
# Copyright (C) 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from perfetto.protos.perfetto.trace.perfetto_trace_pb2 import Trace
from typing import Optional

class TraceProtoBuilder(object):
    """
    Helper class to build a trace proto for testing.
    """

    def __init__(self, trace):
        self.trace = trace
        self.proc_map = {}
        self.proc_map[0] = 'idle_thread'

    def add_ftrace_packet(self, cpu: int):
        """
        Adds a new packet with ftrace events for a specific CPU.
        """
        self.packet = self.trace.packet.add()
        self.packet.ftrace_events.cpu = cpu

    def add_packet(self, ts: Optional[int] = None):
        """
        Adds a new generic packet to the trace.
        """
        self.packet = self.trace.packet.add()
        if ts is not None:
            self.packet.timestamp = ts
        return self.packet

    def __add_ftrace_event(self, ts: int, tid: int):
        ftrace = self.packet.ftrace_events.event.add()
        ftrace.timestamp = ts
        ftrace.pid = tid
        return ftrace

    def add_package_list(self, ts: int, name: str, uid: int, version_code: int):
        """
        Adds a package list packet to the trace.

        Args:
            ts: Timestamp for the package list packet.
            name: The package name (e.g., com.google.android.apps.example).
            uid: The User ID associated with the package.
            version_code: The version code of the package.
        """
        packet = self.add_packet()
        packet.timestamp = ts
        plist = packet.packages_list
        pinfo = plist.packages.add()
        pinfo.name = name
        pinfo.uid = uid
        pinfo.version_code = version_code

    def add_process(self, pid: int, ppid: int, cmdline: str, uid: Optional[int] = None):
        """
        Adds a process to the process tree in the current packet.

        Args:
            pid: Process ID.
            ppid: Parent Process ID.
            cmdline: The command line or name of the process.
            uid: Optional User ID of the process.
        """
        process = self.packet.process_tree.processes.add()
        process.pid = pid
        process.ppid = ppid
        process.cmdline.append(cmdline)
        if uid is not None:
          process.uid = uid
        self.proc_map[pid] = cmdline

    def add_print(self, ts: int, tid: int, buf: str):
        """
        Adds an ftrace print event to the current ftrace packet.

        Args:
            ts: Timestamp of the event.
            tid: Thread ID of the event.
            buf: The content of the print buffer.
        """
        ftrace = self.__add_ftrace_event(ts, tid)
        print_event = getattr(ftrace, 'print')
        print_event.buf = buf

    def add_atrace_counter(self, ts: int, pid: int, tid: int, buf: str, cnt: int):
        """
        Adds new counter track to the trace via an ftrace print event.
        """
        self.add_print(ts, tid, 'C|{}|{}|{}'.format(pid, buf, cnt))

