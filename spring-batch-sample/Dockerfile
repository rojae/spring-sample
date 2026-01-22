FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# JAR 복사
COPY build/libs/spring-batch-sample-*.jar app.jar

# 환경 변수
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC"
ENV SPRING_PROFILES_ACTIVE=prod

# 실행
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
