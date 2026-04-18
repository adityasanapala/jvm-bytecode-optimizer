# ── Java 21 is required — Soot 4.6.0 supports up to class file version 65 ──
FROM eclipse-temurin:21-jdk-jammy

WORKDIR /app

# Copy the soot jar from the project's lib folder
COPY ../lib/soot-4.6.0-jar-with-dependencies.jar lib/soot-4.6.0-jar-with-dependencies.jar

# Copy benchmark sources and tami-outs
COPY GetSootArgs.java .
COPY Main.java .
COPY SampleSceneTransform.java .
COPY tami-outs/ tami-outs/

# dacapo jar must be placed in PA4-Bench/ before building
COPY dacapo-9.12-MR1-bach.jar .

# Compile the three benchmark source files
RUN javac -cp dacapo-9.12-MR1-bach.jar:lib/soot-4.6.0-jar-with-dependencies.jar \
      GetSootArgs.java SampleSceneTransform.java Main.java

# Default benchmark to run (override with --env BENCH=out-batik etc.)
ENV BENCH=out-batik

CMD java -cp .:dacapo-9.12-MR1-bach.jar:lib/soot-4.6.0-jar-with-dependencies.jar \
      Main tami-outs/$BENCH
