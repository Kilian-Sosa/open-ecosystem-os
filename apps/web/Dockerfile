FROM node:24-alpine AS builder
WORKDIR /app
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL

RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM node:24-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV HOSTNAME=0.0.0.0
ENV PORT=3000

RUN addgroup -S -g 10001 openecosystem && adduser -S -D -H -u 10001 -G openecosystem openecosystem

COPY --from=builder --chown=openecosystem:openecosystem /app/public ./public
COPY --from=builder --chown=openecosystem:openecosystem /app/.next/standalone ./
COPY --from=builder --chown=openecosystem:openecosystem /app/.next/static ./.next/static

USER 10001:10001
EXPOSE 3000
CMD ["node", "server.js"]
