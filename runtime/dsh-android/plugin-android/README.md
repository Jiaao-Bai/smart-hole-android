# dsh-plugin-android

The first-party Android capability bridge for this project. It contributes one
compact `android_system` tool for common device, package, component, settings,
and service operations, plus the native-only `smartHoleStatus/balance` Remote.
The balance service resolves the existing DSH credential per operation, calls
DeepSeek's official balance endpoint inside the Host, and returns only sanitized
currency totals to the app. The balance response never contains the secret.

Every Android command is selected by the plugin and receives arguments
positionally; it does not interpolate model input into a shell.

The DSH Android profile runs as uid 0. The ordinary DSH `bash` and filesystem
tools remain the full-capability escape hatch for operations outside this
initial typed surface.
