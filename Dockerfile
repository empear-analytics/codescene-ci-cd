FROM alpine:latest
# python, curl and jq added only to be available for use in the surrounding build context
RUN apk --update add openjdk8-jre git python3 curl jq

ENV PATH /opt/codescene-ci-cd:$PATH

WORKDIR /opt/codescene-ci-cd

COPY target/codescene-ci-cd.standalone.jar .

COPY codescene-ci-cd.sh .
RUN chmod 755 codescene-ci-cd.sh

ENTRYPOINT ["codescene-ci-cd.sh"]
CMD ["--service"]
