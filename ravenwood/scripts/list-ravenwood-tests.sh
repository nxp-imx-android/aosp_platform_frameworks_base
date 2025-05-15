#!/bin/bash
# Copyright (C) 2024 The Android Open Source Project
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

# List all the ravenwood test modules.

set -e

fbr_only=0
while getopts "r" opt; do
case "$opt" in
    r)
        # Only print tests under frameworks/base/ravenwood/
        fbr_only=1
        ;;
    '?')
        exit 1
        ;;
esac
done
shift $(($OPTIND - 1))

in="$OUT/module-info.json"
cache="$OUT/ravenwood-test-list-fbr${fbr_only}.cached.txt"
cache_temp="${cache}.tmp"

extra_select=""
if (( $fbr_only )) ; then
    extra_select='| select( .value.path.[] | startswith("frameworks/base/ravenwood"))'
fi

# If module-info.json or this script itself is newer than the cache file,
# then re-generate it.
if [[ "$in" -nt "$cache" ]] || [[ "$0" -nt "$cache" ]] ; then
    rm -f "$cache_temp" "$cache"

    # First, create to a temp file, and once it's completed, rename it
    # to the actual cache file, so that if the command failed or is interrupted,
    # we don't update the cache.
    jq -r 'to_entries[] | select( .value.compatibility_suites | index("ravenwood-tests") ) '"$extra_select"' | .key' \
            "$OUT/module-info.json" | sort > "$cache_temp"
    mv "$cache_temp" "$cache"
fi

cat "$cache"
