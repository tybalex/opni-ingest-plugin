FROM opensearchproject/opensearch:1.3.3

COPY ./build/distributions/opnipreprocessing.zip /usr/share/opensearch/
RUN bin/opensearch-plugin install --batch file:///usr/share/opensearch/opnipreprocessing.zip

