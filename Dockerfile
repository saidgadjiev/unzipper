FROM ubuntu:20.04

RUN useradd -ms /bin/bash bot
USER bot

RUN mkdir -p /home/bot/app
WORKDIR /home/bot/app

USER root

ENV DEBIAN_FRONTEND noninteractive
ENV USE_SANDBOX false

RUN apt-get update -y && \
apt-get install -y openjdk-11-jre p7zip-rar locales rar zip unzip

# Locale
ENV LC_ALL C.UTF-8

RUN apt-get clean -y && apt-get autoclean -y && apt-get autoremove -y && rm -rf /var/lib/apt/lists/* /var/tmp/*

USER bot

COPY ./target/app.jar .


EXPOSE 8080
ENTRYPOINT ["java"]
CMD ["-jar", "app.jar"]
#CMD ["-jar", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "app.jar"]
