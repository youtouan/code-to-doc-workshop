FROM maven:3.9.9-eclipse-temurin-8 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -q -DskipTests verify

FROM tomcat:9.0.121-jdk8-temurin AS web
COPY runtime/tomcat/server.xml /usr/local/tomcat/conf/server.xml
COPY runtime/tomcat/context.xml /usr/local/tomcat/conf/context.xml
COPY --from=build /workspace/wholesale-web/target/wholesale.war /usr/local/tomcat/webapps/wholesale.war
RUN useradd --system --uid 10001 --gid root wholesale && chown -R 10001:0 /usr/local/tomcat
USER 10001
ENV CATALINA_OPTS="-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Tokyo -Dhttp.port=8080 -Dhttp.address=0.0.0.0"
EXPOSE 8080
CMD ["catalina.sh", "run"]

FROM eclipse-temurin:8-jre-jammy AS batch
WORKDIR /opt/wholesale
COPY --from=build /workspace/wholesale-batch/target/wholesale-batch-1.0.0-standalone.jar /opt/wholesale/batch.jar
RUN useradd --system --uid 10001 --gid root wholesale
USER 10001
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Duser.timezone=Asia/Tokyo", "-jar", "/opt/wholesale/batch.jar"]
CMD ["help"]
