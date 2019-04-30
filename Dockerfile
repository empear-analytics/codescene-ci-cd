FROM alpine:latest
RUN apk --update add openjdk8-jre git python3

ENV PATH /codescene-gitlab:$PATH
WORKDIR /codescene-gitlab
COPY target/uberjar/codescene-gitlab-0.1.0-SNAPSHOT-standalone.jar codescene-gitlab-standalone.jar

ENTRYPOINT ["java","-jar","/codescene-gitlab/codescene-gitlab-standalone.jar"]
CMD ["--help"]