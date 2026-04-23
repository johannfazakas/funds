#!/bin/sh
cat > /usr/share/nginx/html/config.js << EOF
window.FUNDS_CONFIG = {
  userServiceUrl: "${USER_SERVICE_URL:-http://localhost:5247}",
  fundServiceUrl: "${FUND_SERVICE_URL:-http://localhost:5253}",
  importServiceUrl: "${IMPORT_SERVICE_URL:-http://localhost:5207}",
  analyticsServiceUrl: "${ANALYTICS_SERVICE_URL:-http://localhost:5219}",
  expenseReportViewId: "${EXPENSE_REPORT_VIEW_ID:-}"
};
EOF

exec nginx -g 'daemon off;'
