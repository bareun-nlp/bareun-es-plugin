FROM docker.elastic.co/elasticsearch/elasticsearch:8.5.2

# copy config 
COPY config.properties /usr/share/elasticsearch/data

# copy plugin file
COPY target/releases/elasticsearch-analysis-baikal-8.5.2.zip /usr/share/elasticsearch/data

# no authorization 
RUN echo "xpack.security.enabled: false"  >> /usr/share/elasticsearch/config/elasticsearch.yml

WORKDIR /usr/share/elasticsearch
# install plugin
RUN bin/elasticsearch-plugin install analysis-nori
RUN bin/elasticsearch-plugin install --batch file:///usr/share/elasticsearch/data/elasticsearch-analysis-baikal-8.5.2.zip
