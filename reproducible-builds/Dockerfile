FROM debian:trixie-20260202

RUN apt update
RUN apt install -y git openjdk-21-jdk-headless unzip wget sdkmanager

ENV ANDROID_HOME /opt/android-sdk
COPY install-android-sdk /
RUN bash /install-android-sdk

# create a writable home folder
RUN mkdir -p /home/user
RUN chmod 777 /home/user
ENV HOME /home/user

# make sure we can write to the android-sdk folder
RUN chmod 777 /opt/android-sdk


RUN git config --global --add safe.directory /olvid

