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
import json
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

def flag_str(flag):
  """Convert bind flags into a string."""
  return activitymanagerservice_pb2.ConnectionRecordProto.Flag.Name(flag)

def make_nodes(procs):
  """Make a list of all the nodes."""
  nodes = [{"id": str(p.pid), "name": make_name(p)} for p in procs]
  return nodes

def make_edges(services):
  """Make a list of all the edges."""
  edges = []
  SKIP_FLAGS = { "AUTO_CREATE" }
  for sbu in services.active_services.services_by_users:
    for s in sbu.service_records:
      src = f"{s.pid}"
      for c in s.connections:
        dst = c.client_pid
        attrs = []

        flags_full = [flag_str(f) for f in c.flags]
        flags = [f for f in flags_full if f not in SKIP_FLAGS]
        flags_str = '|'.join(flags)

        # Note that these are "reversed".  AMS tracks and dumps the connections
        # from the client perspective, while people more often think of the
        # bindings in the other direction.
        edge = {
            "source": str(dst),
            "target": str(src),
            "flags_full": flags_full,
            "flags": flags_str,
        }
        edges.append(edge)
  return edges

def make_attr(name, value):
  """Make a dot attribute string."""
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

def print_dot_nodes(nodes):
  """Print all the nodes based on processes."""
  # Label all the process nodes.
  for n in nodes:
    name = n["name"]
    attrs = [
      make_attr("label", name)
    ]
    colour = colourize_name(name)
    if colour:
      attrs.append(make_attr("fillcolor", colour))
      attrs.append(make_attr("style", "filled"))
    print(f"  {n["id"]} [" + " ".join(attrs) + "]")

def print_dot_edges(edges, bindflags, highlight):
  """Print all the edges based on service connections."""
  for e in edges:
    source = e["source"]
    target = e["target"]
    attrs = []
    if bindflags:
      attrs.append(make_attr("label", e["flags"]))
    if highlight:
      flags = e["flags"].split("|")
      if 'IMPORTANT' in flags:
        attrs.append(make_attr("color", RED))
      elif 'WAIVE_PRIORITY' in flags:
        attrs.append(make_attr("color", LIGHT_YELLOW))

    print(f"  {source} -> {target} [" + " ".join(attrs) + "]")

def print_dot(nodes, edges, args):
  """Print dot file of process graph."""
  print("digraph processes {")
  print(f"  layout={args.layout};")
  print("  overlap=false;")
  print("  splines=true;")

  print_dot_nodes(nodes)
  print_dot_edges(edges, bindflags=args.bindflags, highlight=args.highlight)

  print("}")

def print_json(nodes, edges):
  """Print json file of process graph."""
  data = {
    "nodes": nodes,
    "links": edges,
  }
  print(json.dumps(data, indent=2))

def parse_args():
  """Parse command-line arguments."""
  parser = argparse.ArgumentParser(
      description="Create dot of AMS process graph")
  parser.add_argument("--layout", help="Layout engine", default="neato")
  parser.add_argument("--bindflags",
                      help="Label bind flags", action="store_true")
  parser.add_argument("--no-highlight", dest="highlight",
                      help="Highlight connections", action="store_false")
  parser.add_argument("--format", help="Format type", default="dot",
                      choices=["dot", "json"])
  parser.add_argument("filename", help="Input file dumpsys activity --proto")
  return parser.parse_args()

def main():
  """Generate a dot graph from an AMS protobuf dump."""
  args = parse_args()
  ams = read_activity_proto(args.filename)

  nodes = make_nodes(ams.processes.procs)
  edges = make_edges(ams.services)

  if args.format == "dot":
    print_dot(nodes, edges, args)
  elif args.format == "json":
    print_json(nodes, edges)

if __name__ == "__main__":
  main()

