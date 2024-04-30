FROM amazoncorretto:22-alpine3.19

COPY build/libs/app.jar app.jar

CMD ["java", "-jar", "app.jar"]
