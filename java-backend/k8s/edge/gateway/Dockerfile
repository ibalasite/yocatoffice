# yocat-gateway — 單一入口（nginx：client 靜態 + 反向代理 API/character）。
# Build context = <project-root>（repo root），因此可 COPY client/ 與 assets/。
# assets 先 COPY（124M anim + 61M sprites，變動少→layer 快取）；client/index.html 後 COPY（常改）。
FROM nginx:1.27-alpine

# 反向代理 + 靜態路由設定
COPY java-backend/k8s/edge/gateway/nginx.conf /etc/nginx/conf.d/default.conf

# 美術資源（變動少，放前面吃快取）
COPY assets/ /srv/assets/

# 遊戲本體（client 的 ../assets/* 在 / 服務時解析為 /assets/*）
COPY client/index.html /srv/index.html

EXPOSE 80
