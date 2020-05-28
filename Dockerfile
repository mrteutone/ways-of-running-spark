FROM eed3si9n/sbt:sbt1.3.8-jdk11-alpine

COPY . .
RUN sbt assembly

# Override entrypoint: https://stackoverflow.com/a/41207910
ENTRYPOINT ["/usr/bin/env"]
