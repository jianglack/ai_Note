# 构建阶段
FROM maven:3.9-eclipse-temurin-17@sha256:1ed5d1f54416b706707b4f3238f63a20bb06aab27c6d240090a2bb9ad895ed45 AS builder
WORKDIR /app
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.wagon.http.retryHandler.count=5 dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.wagon.http.retryHandler.count=5 package -DskipTests

# 运行阶段
FROM eclipse-temurin:17-jre-alpine@sha256:02320dd4ce20e243dfb915c686089cf9315c763084fafbb12d5c9993aee18b57
WORKDIR /app
RUN addgroup -S ainote && adduser -S -G ainote ainote
COPY --from=builder --chown=ainote:ainote /app/target/*.jar app.jar
USER ainote
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
