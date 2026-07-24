FROM maven:3.9-eclipse-temurin-21

WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw -q -DskipTests dependency:go-offline
COPY src src

# Models and fixtures are deliberately mounted at runtime, never copied into this image.
ENTRYPOINT ["./mvnw", "-q", "-Dtest=YoloXInferenceProofTests,LocalVisionEndToEndProofTests", "test", "-Deden.vision.proof.enabled=true"]
