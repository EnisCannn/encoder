# ffmpeg'i apt yerine statik build'den aliyoruz.
# Ubuntu 22.04'un apt paketinde libvmaf ve libsvtav1 yok; ikisi de bu imajda var.
# (libvmaf = kalite olcumu, libsvtav1 = AV1 preset'i)
FROM mwader/static-ffmpeg:7.1 AS ffmpeg

FROM eclipse-temurin:21-jdk-jammy

# Statik ffmpeg font tasimadigi icin altyaziyi goruntuye yakma (subtitles filtresi,
# libass) fontsuz kalir. apt ffmpeg'i bunlari bagimlilik olarak getiriyordu.
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*

COPY --from=ffmpeg /ffmpeg /usr/local/bin/ffmpeg
COPY --from=ffmpeg /ffprobe /usr/local/bin/ffprobe

WORKDIR /app
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
