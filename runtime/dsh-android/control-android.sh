#!/system/bin/sh
set -eu

action=${1:-}
app_uid=${2:-}
runtime_root=${DSH_ANDROID_RUNTIME_ROOT:-/data/adb/dsh-android}
pid_file="$runtime_root/state/host.pid"
log_file="$runtime_root/logs/host.log"

case "$app_uid" in
  ''|*[!0-9]*) echo 'detail=Invalid Android app UID'; exit 2 ;;
esac

firewall_delete_uid() {
  rule_uid="$1"
  while iptables -w 2 -C OUTPUT -o lo -p tcp --dport 3080 -m owner ! --uid-owner "$rule_uid" -m comment --comment dsh-android-loopback -j REJECT 2>/dev/null; do
    iptables -w 2 -D OUTPUT -o lo -p tcp --dport 3080 -m owner ! --uid-owner "$rule_uid" -m comment --comment dsh-android-loopback -j REJECT
  done
}

firewall_delete() {
  # Android may assign the APK a different UID after uninstall/reinstall. Remove
  # every rule owned by this runtime's unique comment before installing the
  # rule for the current UID, otherwise an older rule rejects the new APK.
  rule_uids="$(
    iptables -w 2 -S OUTPUT 2>/dev/null |
      awk '
        /--dport 3080/ && /--comment dsh-android-loopback/ {
          for (field = 1; field <= NF; field += 1) {
            if ($field == "--uid-owner" && $(field + 1) ~ /^[0-9]+$/) {
              print $(field + 1)
            }
          }
        }
      '
  )"
  for rule_uid in $rule_uids "$app_uid"; do
    case "$rule_uid" in
      ''|*[!0-9]*) continue ;;
    esac
    firewall_delete_uid "$rule_uid"
  done
}

firewall_install() {
  firewall_delete
  iptables -w 2 -I OUTPUT 1 -o lo -p tcp --dport 3080 -m owner ! --uid-owner "$app_uid" -m comment --comment dsh-android-loopback -j REJECT
}

start_host() {
  mkdir -p "$runtime_root/state" "$runtime_root/logs"
  if [ ! -x "$runtime_root/runtime/bin/node" ] || [ ! -x "$runtime_root/runtime/start-android.sh" ]; then
    echo 'detail=Runtime payload is incomplete'
    exit 1
  fi
  firewall_install
  if [ -r "$pid_file" ]; then
    old_pid="$(cat "$pid_file" 2>/dev/null || true)"
    case "$old_pid" in
      ''|*[!0-9]*) ;;
      *) if kill -0 "$old_pid" 2>/dev/null; then echo 'status=already-running'; exit 0; fi ;;
    esac
  fi
  nohup "$runtime_root/runtime/start-android.sh" >"$log_file" 2>&1 </dev/null &
  host_pid="$!"
  printf '%s\n' "$host_pid" >"$pid_file"
  sleep 1
  if kill -0 "$host_pid" 2>/dev/null; then
    echo 'status=started'
    exit 0
  fi
  echo 'detail=Host exited during startup'
  tail -n 8 "$log_file" 2>/dev/null || true
  firewall_delete
  exit 1
}

stop_host() {
  if [ ! -r "$pid_file" ]; then
    firewall_delete
    echo 'status=already-stopped'
    exit 0
  fi
  host_pid="$(cat "$pid_file" 2>/dev/null || true)"
  case "$host_pid" in
    ''|*[!0-9]*) echo 'detail=Invalid Host pid file'; exit 1 ;;
  esac
  if ! kill -0 "$host_pid" 2>/dev/null; then
    rm -f "$pid_file"
    firewall_delete
    echo 'status=already-stopped'
    exit 0
  fi
  cmdline="$(tr '\000' ' ' < "/proc/$host_pid/cmdline" 2>/dev/null || true)"
  case "$cmdline" in
    *deepseek-ai/dsh/lib/bin.js*) ;;
    *) echo 'detail=Refusing to stop an unrecognized process'; exit 1 ;;
  esac
  kill -TERM "$host_pid"
  attempts=0
  while kill -0 "$host_pid" 2>/dev/null && [ "$attempts" -lt 30 ]; do
    sleep 0.1
    attempts="$((attempts + 1))"
  done
  if kill -0 "$host_pid" 2>/dev/null; then
    echo 'detail=Host did not stop after SIGTERM'
    exit 1
  fi
  rm -f "$pid_file"
  firewall_delete
  echo 'status=stopped'
}

case "$action" in
  start) start_host ;;
  stop) stop_host ;;
  *) echo 'detail=Usage: control-android.sh start|stop APP_UID'; exit 2 ;;
esac
