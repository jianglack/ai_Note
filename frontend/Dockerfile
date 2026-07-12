# 构建阶段
FROM node:24-alpine@sha256:a0b9bf06e4e6193cf7a0f58816cc935ff8c2a908f81e6f1a95432d679c54fbfd AS builder
WORKDIR /app
COPY package*.json .
RUN npm ci
COPY . .
RUN npm run build

# 运行阶段
FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:ee7b7bc02a4af72c98d4365151f92d9b380c482c48d8c84bbbe44bd3192ba463
COPY --from=builder --chown=101:101 /app/dist /usr/share/nginx/html
COPY --chown=101:101 nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
