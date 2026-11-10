#!/usr/bin/env bash
# Push the Neon Noir theme to the CleanYourTV launcher already installed on the TV.
# Usage: TV_HOST=192.168.1.50 CLEANYOURTV=~/cleanyourtv scripts/push-theme.sh
set -euo pipefail
: "${TV_HOST:?Set TV_HOST to the TV IP address}"
: "${CLEANYOURTV:?Set CLEANYOURTV to your CleanYourTV checkout}"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
# Pushes the theme folder kept in this repo; CleanYourTV restarts Home with it.
TV_HOST="$TV_HOST" "$CLEANYOURTV/tools/theme.sh" "$HERE/theme/neon-noir"
