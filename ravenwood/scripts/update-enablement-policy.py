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

"""Tool to update Ravenwood test enablement policy.

Update a policy file:
$ ./run-ravenwood-tests.sh -R SystemUiRavenTests
$ ./update-enablement-policy.py ../texts/enablement-policy-sysui.txt
"""

import csv
import operator
import pathlib
import sys

Path = pathlib.Path


class PolicyFile:
  """An enablement policy file"""

  def __init__(self, path: Path):
    self.path = path
    with open(path, "r") as f:
      lines = f.readlines()

    # Find module name
    self.module = lines[0].split(" ", 1)[1].strip()

    # Extract header
    for i, line in enumerate(lines):
      if line.startswith("# AUTO-GENERATED START"):
        self.header = lines[:i]
        break

    # Start with empty policies
    self.policies: dict[str, bool] = {}

    # Class stats
    self.classes: dict[str, bool] = {}

    # Stats
    self.class_total = 0
    self.class_cannot_init = 0
    self.class_skipped = 0
    self.method_total = 0
    self.method_passed = 0
    self.method_failed = 0
    self.method_skipped = 0

  def update_with_csv(self, csv_file: Path):
    """Clear existing policies and update from csv test stats."""
    self.policies = {}

    with open(csv_file) as f:
      reader = csv.DictReader(f)
      for row in reader:
        if row["Type"] == "c":
          self.class_total += 1
          continue

        class_name = row["Class"]
        failed = int(row["Failed"]) > 0
        skipped = int(row["Skipped"]) > 0
        passed = int(row["Passed"]) > 0

        if row["RawMethodName"] == "<init>":
          # Something happened at the class-runner level
          if failed:
            self.class_cannot_init += 1
            self.policies[class_name] = False
          elif skipped:
            self.class_skipped += 1
        else:
          # An actual test method run
          self.method_total += 1

          # Default to True, will be set to False whenever a failure happens
          if class_name not in self.classes:
            self.classes[class_name] = True

          # Record method-level result
          method = f"{row['Class']}#{row['RawMethodName']}"
          if passed:
            self.method_passed += 1
            # It's possible that the same method failed with different parameters.
            # If any of its variant failed, we keep the policy disabled.
            if method not in self.policies:
              self.policies[method] = True
          elif failed:
            self.method_failed += 1
            self.policies[method] = False
            self.classes[class_name] = False
          elif skipped:
            self.method_skipped += 1

  def write(self):
    """Writes the enablement policy file to disk."""
    tmp = Path(f"{self.path}.tmp")
    with open(tmp, "w") as f:
      f.writelines(self.header)

      class_ran = len(self.classes)
      all_pass = operator.countOf(self.classes.values(), True)

      f.write("# AUTO-GENERATED START\n")
      f.write("\n")
      f.write("# Class-level stats:\n")
      f.write(
          f"# Total={self.class_total} Ran={class_ran} Passed={all_pass}"
          f" CannotInit={self.class_cannot_init} Skipped={self.class_skipped}\n"
      )
      f.write("# Method-level stats:\n")
      f.write(
          f"# Total={self.method_total} Passed={self.method_passed}"
          f" Failed={self.method_failed} Skipped={self.method_skipped}\n"
      )
      f.write("\n")

      for class_method, policy in sorted(self.policies.items()):
        p = "enable" if policy else "disable"
        f.write(f"{class_method} {p}\n")

    tmp.rename(self.path)


def usage():
  print(f"Usage: {sys.argv[0]} <policy_file>")
  exit(1)


def update_policy(path: Path):
  file = PolicyFile(path)

  csv_file = Path(f"/tmp/Ravenwood-stats_{file.module}_latest.csv")
  if not csv_file.exists():
    print(f"{csv_file} not found")
    return

  file.update_with_csv(csv_file)
  file.write()


if __name__ == "__main__":
  if len(sys.argv) < 2:
    usage()

  update_policy(Path(sys.argv[1]))
