#!/bin/sh
set -e

# On platforms like Railway there is no local file to point DOCUSIGN_PRIVATE_KEY_PATH
# at. If DOCUSIGN_PRIVATE_KEY_BASE64 is set instead, decode it to a file here and
# point DOCUSIGN_PRIVATE_KEY_PATH at that file before starting the JVM.
if [ -n "$DOCUSIGN_PRIVATE_KEY_BASE64" ]; then
  echo "$DOCUSIGN_PRIVATE_KEY_BASE64" | base64 -d > /tmp/docusign_private_key.pem
  export DOCUSIGN_PRIVATE_KEY_PATH=/tmp/docusign_private_key.pem
fi

exec java -jar /app/app.jar
