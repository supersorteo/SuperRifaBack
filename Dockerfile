# ══════════════════════════════════════════
#  Stage 1 — Build
# ══════════════════════════════════════════
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copiar wrapper + pom primero para cachear dependencias
COPY mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY pom.xml ./

RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copiar fuentes y compilar
COPY src ./src
RUN ./mvnw clean package -DskipTests -q

# ══════════════════════════════════════════
#  Stage 2 — Runtime
# ══════════════════════════════════════════
FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S rifas && adduser -S rifas -G rifas

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

RUN chown rifas:rifas app.jar
USER rifas

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
