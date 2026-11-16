ARG ANDROID_BUILD_PLATFORM=linux/amd64
FROM --platform=$ANDROID_BUILD_PLATFORM ghcr.io/cirruslabs/android-sdk:36

# The base image includes JDK 21 and Build Tools 36.0.0.
RUN sdkmanager --install "platforms;android-36.1"

WORKDIR /workspace

# Stream a CRLF checkout's wrapper as LF without changing files in the mounted repo.
ENTRYPOINT ["bash", "-lc", "tr -d '\\r' < gradlew | bash -s -- \"$@\"", "--"]
CMD [":app:lintDebug", ":app:assembleDebug", "--no-daemon", "--console=plain"]
