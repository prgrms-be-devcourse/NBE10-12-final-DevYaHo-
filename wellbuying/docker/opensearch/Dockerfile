FROM opensearchproject/opensearch:2.19.1
RUN /usr/share/opensearch/bin/opensearch-plugin install --batch analysis-nori
RUN /usr/share/opensearch/bin/opensearch-plugin install -b https://github.com/opensearch-project/opensearch-prometheus-exporter/releases/download/2.19.1.0/prometheus-exporter-2.19.1.0.zip
