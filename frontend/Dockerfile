# ---- build ----
FROM node:20-alpine AS build
WORKDIR /src
COPY package*.json ./
RUN npm ci || npm install
COPY . .
RUN npx ng build --configuration production

# ---- serve ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist/prep-hub/browser /usr/share/nginx/html
EXPOSE 80
