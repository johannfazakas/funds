#!/bin/sh
cat > /usr/share/nginx/html/config.js << EOF
window.FUNDS_CONFIG = {
  userServiceUrl: "${USER_SERVICE_URL:-http://localhost:5247}",
  fundServiceUrl: "${FUND_SERVICE_URL:-http://localhost:5253}"
};
EOF

exec nginx -g 'daemon off;'
