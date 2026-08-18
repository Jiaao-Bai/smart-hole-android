#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

cd "$script_dir"
npm ci --ignore-scripts --omit=optional

patch --batch --forward -p1 \
  -d node_modules/@deepseek-ai/dsh-subprocess-local \
  < patches/dsh-subprocess-local-android.patch
patch --batch --forward -p1 \
  -d node_modules/@deepseek-ai/dsh-bash-local \
  < patches/dsh-bash-local-android.patch
patch --batch --forward -p1 \
  -d node_modules/@deepseek-ai/dsh-attachment-local \
  < patches/dsh-attachment-local-android.patch

subprocess_file=node_modules/@deepseek-ai/dsh-subprocess-local/lib/index.js
bash_file=node_modules/@deepseek-ai/dsh-bash-local/lib/index.js
attachment_file=node_modules/@deepseek-ai/dsh-attachment-local/lib/index.js

! grep -q '^import \* as nodePty from "node-pty";$' "$subprocess_file"
grep -q 'await import("node-pty")' "$subprocess_file"
[[ $(grep -c 'process.platform === "android" ? "/system/bin/sh" : "bash"' "$bash_file") -eq 2 ]]
! grep -q '^import sharp from "sharp";$' "$attachment_file"
[[ $(grep -c 'const { default: sharp } = await import("sharp");' "$attachment_file") -eq 2 ]]

node -e 'const p=require("./node_modules/@deepseek-ai/dsh/package.json"); if(p.version!=="0.1.0-rc.7") process.exit(1)'
node node_modules/@deepseek-ai/dsh/lib/bin.js --version
