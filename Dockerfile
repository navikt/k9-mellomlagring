FROM gcr.io/distroless/java17

COPY build/libs/*.jar ./

CMD ["app.jar"]