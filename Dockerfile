FROM alpine:latest
RUN apk --update add openjdk8-jre git python3

ENV PATH /codescene-ci-cd:$PATH
WORKDIR /codescene-ci-cd
COPY target/uberjar/codescene-ci-cd-0.1.0-SNAPSHOT-standalone.jar codescene-ci-cd-standalone.jar

ENTRYPOINT ["java","-jar","/codescene-ci-cd/codescene-ci-cd-standalone.jar"]
CMD ["--help"]