FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

# System toolchain: JDK 21 (required by AGP 9.x / Gradle 9.5) plus git, curl, unzip.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        openjdk-21-jdk-headless \
        git \
        curl \
        unzip \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Android SDK is a large, stable system dependency, so it lives in the image.
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}

# compileSdk/targetSdk 37 -> platforms;android-37.0 + build-tools;37.0.0.
# build-tools;36.0.0 is preinstalled because AGP 9.3.1 selects it as its default.
ARG CMDLINE_TOOLS_URL=https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && curl -fsSL -o /tmp/cmdline-tools.zip "${CMDLINE_TOOLS_URL}" \
    && unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm /tmp/cmdline-tools.zip \
    && yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;android-37.0" \
        "build-tools;37.0.0" \
        "build-tools;36.0.0" \
    && chown -R ubuntu:ubuntu ${ANDROID_HOME}

USER ubuntu
