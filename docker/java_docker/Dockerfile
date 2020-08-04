FROM openjdk:11-slim
MAINTAINER zajac.thomas1992@gmail.com

COPY ModelPolisher-2.1.jar /
# from https://stackoverflow.com/a/23961144
RUN mkdir -p /.java/.systemPrefs && mkdir /.java/.userPrefs && chmod -R 777 /.java
ENTRYPOINT ["java", "-jar", "/ModelPolisher-2.1.jar"]
CMD ["--help"]
