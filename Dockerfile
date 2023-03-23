FROM amazoncorretto:20-alpine3.15

COPY build/libs/app.jar app.jar

CMD ["java", "-jar", "app.jar"]
