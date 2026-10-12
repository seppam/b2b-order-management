#!/usr/bin/env bash
# End-to-end smoke test against a running instance (default http://localhost:8080).
set -euo pipefail
BASE=${BASE:-http://localhost:8080}
J='Content-Type: application/json'
S=$RANDOM
field() { sed -E "s/.*\"$1\":\"?([^\",}]+)\"?.*/\1/"; }
reg() { curl -s -o /dev/null -X POST $BASE/api/v1/auth/register -H "$J" \
  -d "{\"username\":\"$1$S\",\"password\":\"Passw0rd!\",\"email\":\"$1$S@example.com\",\"role\":\"$2\"}"; }
login() { curl -s -X POST $BASE/api/v1/auth/login -H "$J" -d "{\"username\":\"$1$S\",\"password\":\"Passw0rd!\"}"; }

reg admin ROLE_ADMIN; reg buyer ROLE_CLIENT
A=$(login admin); B=$(login buyer)
AT=$(echo "$A" | field accessToken); BT=$(echo "$B" | field accessToken); BUYER=$(echo "$B" | field userId)

echo "== admin creates product";  P=$(curl -s -X POST $BASE/api/v1/products -H "$J" -H "Authorization: Bearer $AT" \
  -d "{\"name\":\"SaaS Seat\",\"sku\":\"SAAS-$S\",\"price\":299.00,\"stockQuantity\":50}"); echo "$P"; PID=$(echo "$P" | field id)
echo "== buyer cannot create product -> $(curl -s -o /dev/null -w '%{http_code}' -X POST $BASE/api/v1/products -H "$J" -H "Authorization: Bearer $BT" -d '{}')"
ORDER1="{\"userId\":$BUYER,\"productId\":$PID,\"quantity\":1}"
echo "== order with empty wallet -> $(curl -s -o /dev/null -w '%{http_code}' -X POST $BASE/api/v1/orders -H "$J" -H "Authorization: Bearer $BT" -d "$ORDER1")"
echo "== admin tops up wallet";   curl -s -X POST $BASE/api/v1/users/$BUYER/wallet/topup -H "$J" -H "Authorization: Bearer $AT" -d '{"amount":1000}'; echo
echo "== buyer orders 3 units";   curl -s -X POST $BASE/api/v1/orders -H "$J" -H "Authorization: Bearer $BT" -d "{\"userId\":$BUYER,\"productId\":$PID,\"quantity\":3}"; echo
echo "== order history";          curl -s $BASE/api/v1/orders/user/$BUYER -H "Authorization: Bearer $BT"; echo
echo "== wallet balance";         curl -s $BASE/api/v1/users/$BUYER/wallet -H "Authorization: Bearer $BT"; echo
echo "== product by SKU";         curl -s $BASE/api/v1/products/sku/SAAS-$S -H "Authorization: Bearer $BT"; echo
echo "== no token -> $(curl -s -o /dev/null -w '%{http_code}' $BASE/api/v1/products)"
