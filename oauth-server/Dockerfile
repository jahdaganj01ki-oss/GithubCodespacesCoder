FROM gradle:8.13-jdk17 AS build
WORKDIR /workspace
COPY oauth-server/ ./
RUN gradle --no-daemon installDist

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/install/gitcodera-oauth-server/ /app/
RUN mkdir -p /data && chown -R 10001:10001 /app /data
USER 10001:10001
ENV PORT=8080
ENV DATABASE_URL=jdbc:h2:file:/data/gitcodera;DB_CLOSE_ON_EXIT=FALSE
EXPOSE 8080
VOLUME ["/data"]
ENTRYPOINT ["/app/bin/oauth-server"]
