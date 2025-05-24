#!/usr/bin/env python3
#
# Copyright (C) 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Tool to bulk-toggle entire test classes for Ravenwood.

To enable all tests that passed:
$ RAVENWOOD_RUN_DISABLED_TESTS=1 atest SystemUiRavenTests
$ ./toggle_test.py enable /path/to/src /tmp/Ravenwood-stats_SystemUiRavenTestsRavenwood_latest.csv

To disable all tests that failed:
$ atest SystemUiRavenTests
$ ./toggle_test.py disable /path/to/src /tmp/Ravenwood-stats_SystemUiRavenTestsRavenwood_latest.csv
"""

import csv
import pathlib
import sys


Path = pathlib.Path
ENABLED_ANNOTATION = "android.platform.test.annotations.EnabledOnRavenwood"
DISABLED_ANNOTATION = "android.platform.test.annotations.DisabledOnRavenwood"


class SourceFile:
  """A source file. We assume the filename's stem is the class name."""
  path: Path
  lines: list[str]
  class_name: str

  def __init__(self, path: Path):
    self.path = path
    self.class_name = path.stem
    with open(path, "r") as f:
      self.lines = f.readlines()

  def find_annotation(self) -> (int, [(str, int)]):
    """
    Find the class level annotations in the file, and returns
    the indexes (index = line number - 1) of them.

    (We assume class level annotations don't have any indents.)
    """
    result = []
    for idx in range(len(self.lines)):
      # Find the class line
      if f"class {self.class_name}" in self.lines[idx]:
        curr_idx = idx - 1
        while True:
          line = self.lines[curr_idx].strip()
          if line.startswith("@"):
            annotation_class = line[1:].split("(", 1)[0]
            result.append((annotation_class, curr_idx))
            curr_idx -= 1
          else:
            break
        return (idx, result)

  def remove_annotation(self, annotation: str):
    """Removes an annotation (ignoring the package name) from the source file."""
    for (annot, idx) in self.find_annotation()[1]:
      if annot.split(".")[-1] == annotation.split(".")[-1]:
        self.lines.pop(idx)
        break

  def add_annotation(self, annotation: str):
    """Adds an annotation to the source file, if it doesn't have it already."""
    (class_idx, annot_list) = self.find_annotation()
    for annot, _ in annot_list:
      if annot.split(".")[-1] == annotation.split(".")[-1]:
        # The annotation is already present.
        return
    self.lines.insert(class_idx, f"@{annotation}\n")

  def write(self):
    """Writes the source file to disk."""
    with open(self.path, "w") as f:
      f.writelines(self.lines)

  def print(self):
    """Prints the source file."""
    for line in self.lines:
      print(line, end="")


def find_passed_tests(csv_file: str) -> list[str]:
  """Finds all test classes that passed from a test result CSV file."""
  test = []
  with open(csv_file) as f:
    reader = csv.DictReader(f)
    for row in reader:
      if int(row["Failed"]) == 0:
        test.append(row["Class"])
  return test


def find_failed_tests(csv_file: str) -> list[str]:
  """Finds all test classes with at least one failure from a test result CSV file."""
  test = []
  with open(csv_file) as f:
    reader = csv.DictReader(f)
    for row in reader:
      if int(row["Failed"]) > 0:
        test.append(row["Class"])
  return test


def load_test_files(src_root: str, tests: list[str]) -> list[SourceFile]:
  """
  Find all source files of the given list of classes e.g. ["com.android.mytest.MyTestClass", ...]
  in a root directory, and load each of as SourceFile and return a list of them.
  """
  files = []
  src_root = Path(src_root)
  for test in tests:
    components = test.split(".")
    src_dir = Path(src_root, *components[:-1])
    java_src = src_dir / f"{components[-1]}.java"
    kt_src = src_dir / f"{components[-1]}.kt"
    if java_src.exists():
      files.append(SourceFile(java_src))
    elif kt_src.exists():
      files.append(SourceFile(kt_src))
    else:
      print("Cannot find source for test", test)
  return files


def main():
  enable = False
  if "enable" in sys.argv:
    enable = True
    test_classes = find_passed_tests(sys.argv[-1])
  elif "disable" in sys.argv:
    test_classes = find_failed_tests(sys.argv[-1])
  else:
    print("Usage: toggle_test.py <enable|disable> <src_root> <csv_file>")
    exit(1)

  test_sources = load_test_files(sys.argv[-2], test_classes)

  if enable:
    for src in test_sources:
      src.remove_annotation(DISABLED_ANNOTATION)
      src.add_annotation(ENABLED_ANNOTATION)
      src.write()
  else:
    for src in test_sources:
      src.remove_annotation(ENABLED_ANNOTATION)
      src.add_annotation(DISABLED_ANNOTATION)
      src.write()

if __name__ == "__main__":
  main()
