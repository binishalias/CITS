#Install OPEN JDK
FROM adoptopenjdk/openjdk11:latest
LABEL maintainer="Binish Alias"
#Setting ENV Variables
ENV PROJ=""
ENV RELEASE=""
ENV TESTSET=""
RUN mkdir /CITSworkspace
WORKDIR /CITSworkspace
COPY . /root/CITSworkspace
RUN chmod -R 755 ./
CMD ./Run.command -run -project_location ${PROJ} -release ${RELEASE} -testset ${TESTSET} -setEnv "run.RemoteGridURL=${GRIDURL}"