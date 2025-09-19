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

    # Stats
    self.total = 0
    self.passed = 0
    self.failed = 0
    self.skipped = 0

  def update_with_csv(self, csv_file: Path):
    """Clear existing policies and update from csv test stats."""
    self.policies = {}

    with open(csv_file) as f:
      reader = csv.DictReader(f)
      for row in reader:
        if row["Type"] == "c":
          continue

        self.total += 1

        if row["RawMethodName"] == "<init>":
          # Something happened at the class-level
          if int(row["Failed"]) > 0:
            self.failed += 1
            self.policies[row["Class"]] = False
          elif int(row["Skipped"]) > 0:
            self.skipped += 1
        else:
          # Record method-level result
          method = f"{row['Class']}#{row['RawMethodName']}"
          if int(row["Passed"]) > 0:
            # It's possible that the same method failed with different parameters.
            # If any of its variant failed, we keep it disabled.
            if method in self.policies:
              if not self.policies[method]:
                self.failed += 1
              else:
                self.passed += 1
            else:
              self.passed += 1
              self.policies[method] = True
          elif int(row["Failed"]) > 0:
            self.failed += 1
            self.policies[method] = False
          elif int(row["Skipped"]) > 0:
            self.skipped += 1

  def write(self):
    """Writes the enablement policy file to disk."""
    tmp = Path(f"{self.path}.tmp")
    with open(tmp, "w") as f:
      f.writelines(self.header)

      f.write("# AUTO-GENERATED START\n")
      f.write("\n")
      f.write(
          f"# Total={self.total} Passed={self.passed} Failed={self.failed} Skipped={self.skipped}\n"
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
