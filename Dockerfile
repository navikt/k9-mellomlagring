FROM amazoncorretto:24-alpine3.20

COPY build/libs/app.jar app.jar

CMD ["java", "-jar", "app.jar"]
