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

# Tool to graph AMS process dependencies.
# m graph_ams
# adb shell dumpsys activity --proto > activity.bin
# graph_ams activity.bin > activity.dot
# dot -Tsvg activity.dot -o activity.svg
# dot -Tpdf activity.dot -o activity.pdf
# dot -Tpng -Gdpii=100 activity.dot -o activity.png

import argparse
import re
import sys

from frameworks.base.core.proto.android.server import activitymanagerservice_pb2

# Colours from Google Material.
BLUE = "#4285f4"
LIGHT_BLUE = "#77bdff"
RED = "#ea4335"
LIGHT_RED = "#ff5252"
YELLOW = "#fbbc05"
LIGHT_YELLOW = "#ffcc50"
GREEN = "#34a853"
LIGHT_GREEN = "#57bb8a"

def read_activity_proto(filename):
  """Read the AMS proto."""
  ams = activitymanagerservice_pb2.ActivityManagerServiceProto()
  with open(filename, "rb") as f:
    ams.ParseFromString(f.read())
  return ams

def make_name(p):
  """Make a pretty process name."""
  return f"{p.pid}:{p.process_name}/{p.uid}"

def make_attr(name, value):
  """Make a dot attrbute string."""
  return f"{name}=\"{value}\""

def colourize_name(name):
  """Colourize important process names."""
  COLOUR_MAP = [
    [r"chrome.*SandboxedProcess", LIGHT_BLUE],
    [r"chrome", BLUE],
    [r":system/", GREEN],
    [r":com.google.android.gms", LIGHT_GREEN],
  ]

  # Search for the first regex that matches.
  for regex, colour in COLOUR_MAP:
    if re.search(regex, name):
      return colour
  return None

def print_nodes(procs):
  """Print all the nodes based on processes."""
  # Label all the process nodes.
  for p in procs:
    name = make_name(p)
    attrs = [
      make_attr("label", name)
    ]
    colour = colourize_name(name)
    if colour:
      attrs.append(make_attr("fillcolor", colour))
      attrs.append(make_attr("style", "filled"))

    print(f"  {p.pid} [" + " ".join(attrs) + "]")

def flag_str(flag):
  """Convert bind flags into a string."""
  return activitymanagerservice_pb2.ConnectionRecordProto.Flag.Name(flag)

def print_edges(services, bindflags, highlight):
  """Print all the edges based on service connections."""
  SKIP_FLAGS = { "AUTO_CREATE" }
  for sbu in services.active_services.services_by_users:
    for s in sbu.service_records:
      src = f"{s.pid}"
      for c in s.connections:
        dst = c.client_pid
        attrs = []

        flags = [flag_str(f) for f in c.flags]
        if bindflags:
          flags = [f for f in flags if f not in SKIP_FLAGS]
          flags_str = '|'.join(flags)
          attrs.append(make_attr("label", flags_str))
        if highlight:
          if 'IMPORTANT' in flags:
            attrs.append(make_attr("color", RED))
          elif 'WAIVE_PRIORITY' in flags:
            attrs.append(make_attr("color", LIGHT_YELLOW))

        # Note that these are "reversed".  AMS tracks and dumps the connections
        # from the client perspective, while people more often think of the
        # bindings in the other direction.
        print(f"  {dst} -> {src} [" + " ".join(attrs) + "]")

def parse_args():
  """Parse command-line arguments."""
  parser = argparse.ArgumentParser(
      description="Create dot of AMS process graph")
  parser.add_argument("--layout", help="Layout engine", default="neato")
  parser.add_argument("--bindflags",
                      help="Label bind flags", action="store_true")
  parser.add_argument("--no-highlight", dest="highlight",
                      help="Highlight connections", action="store_false")
  parser.add_argument("filename", help="Input file dumpsys activity --proto")
  return parser.parse_args()

def main():
  """Generate a dot graph from an AMS protobuf dump."""
  args = parse_args()
  ams = read_activity_proto(args.filename)
  services = ams.services
  procs = ams.processes.procs

  print("digraph processes {")
  print(f"  layout={args.layout};")
  print("  overlap=false;")
  print("  splines=true;")

  print_nodes(procs)
  print_edges(services, bindflags=args.bindflags, highlight=args.highlight)

  print("}")

if __name__ == "__main__":
  main()

