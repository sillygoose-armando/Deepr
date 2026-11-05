#!/bin/bash
# Fails if a Kotlin file exists under the same package path in both :app and :ui/:shared commonMain
# (a moved file whose original wasn't deleted → duplicate class, which only breaks the Android build in CI).
cd "$(git rev-parse --show-toplevel)" || exit 1
rc=0
for mod in ui shared; do
  for f in $(cd $mod/src/commonMain/kotlin 2>/dev/null && find . -name "*.kt" | sed 's|^\./||'); do
    [ -f "app/src/main/java/$f" ] && { echo "DUPLICATE: app/src/main/java/$f and $mod/src/commonMain/kotlin/$f"; rc=1; }
  done
done
[ $rc = 0 ] && echo "no duplicates"
exit $rc
