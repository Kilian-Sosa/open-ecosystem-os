FROM node:24-alpine AS builder
WORKDIR /app

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

RUN addgroup -S openecosystem && adduser -S openecosystem -G openecosystem

COPY --from=builder --chown=openecosystem:openecosystem /app/public ./public
COPY --from=builder --chown=openecosystem:openecosystem /app/.next/standalone ./
COPY --from=builder --chown=openecosystem:openecosystem /app/.next/static ./.next/static

USER openecosystem
EXPOSE 3000
CMD ["node", "server.js"]
