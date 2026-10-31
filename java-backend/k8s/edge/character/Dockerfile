# yocat-character — NPC 大腦服務（Node，零依賴）。單一入口的 /character/* 後端。
FROM node:20-alpine
WORKDIR /app
COPY server.js ./
ENV PORT=3001
# LLM_BASE 由 k8s env 覆寫（host.docker.internal 觸達 host 上的 vLLM）
EXPOSE 3001
USER node
CMD ["node", "server.js"]
