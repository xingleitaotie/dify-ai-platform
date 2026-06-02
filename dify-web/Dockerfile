FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install --legacy-peer-deps
COPY . .
RUN npm run build:docker

FROM nginx:alpine
# 复制自定义 nginx 配置（添加这一行）
COPY nginx.conf /etc/nginx/conf.d/default.conf
# 复制静态文件
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]