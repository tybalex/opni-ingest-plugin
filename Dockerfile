FROM opensearchproject/opensearch:1.3.3

COPY ./Logging-OpniJsonDetector/build/distributions/opnijsondetector.zip /usr/share/opensearch/
RUN bin/opensearch-plugin install --batch file:///usr/share/opensearch/opnijsondetector.zip

COPY ./AIOps-OpniPreProcessor/build/distributions/opnipreprocessing.zip /usr/share/opensearch/
RUN bin/opensearch-plugin install --batch file:///usr/share/opensearch/opnipreprocessing.zip

