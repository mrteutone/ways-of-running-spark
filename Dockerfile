FROM eed3si9n/sbt:sbt1.3.8-jdk11-alpine

RUN wget https://github.com/mrteutone/ways-of-running-spark/archive/master.tar.gz \
 && mkdir ways-of-running-spark \
 && tar -xvzf master.tar.gz -C ways-of-running-spark --strip-components=1 \
 && rm master.tar.gz

WORKDIR ways-of-running-spark
RUN sbt assembly

# Override entrypoint: https://stackoverflow.com/a/41207910
ENTRYPOINT ["/usr/bin/env"]

