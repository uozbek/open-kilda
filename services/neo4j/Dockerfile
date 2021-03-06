# Copyright 2017 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

ARG base_image=kilda/base-ubuntu
FROM ${base_image}

ARG neo4j_version=3.3.9
ARG apoc_version="3.3.0.4"
ARG graph_algo_version="3.3.5.0"
ENV NEO4j_VERSION="${neo4j_version}"
ENV APOC_VERSION=${apoc_version}
ENV GRAPH_ALGO_VERSION=${graph_algo_version}

RUN wget -q \
      https://neo4j.com/artifact.php?name=neo4j-community-${neo4j_version}-unix.tar.gz -O /tmp/neo4j-community-${neo4j_version}-unix.tar.gz \
      && tar xzf /tmp/neo4j-community-${neo4j_version}-unix.tar.gz -C /opt/ && rm -f /tmp/neo4j-community-${neo4j_version}-unix.tar.gz

RUN apt-get update \
    && apt-get autoremove -yq \
    && pip install \
        cycli \
    && wget -q \
        https://github.com/neo4j-contrib/neo4j-apoc-procedures/releases/download/${APOC_VERSION}/apoc-${APOC_VERSION}-all.jar \
        -O /opt/neo4j-community-${neo4j_version}/plugins/apoc-${APOC_VERSION}-all.jar \
    && wget -q \
        https://github.com/neo4j-contrib/neo4j-graph-algorithms/releases/download/${GRAPH_ALGO_VERSION}/graph-algorithms-algo-${GRAPH_ALGO_VERSION}.jar \
        -O /opt/neo4j-community-${neo4j_version}/plugins/graph-algorithms-algo-${GRAPH_ALGO_VERSION}.jar \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

WORKDIR /var/lib/neo4j

COPY neo4j-config/neo4j.conf /neo4j-community-${neo4j_version}/conf/neo4j.conf
COPY init/neo4j-queries.cql /app/neo4j-queries.cql
COPY init/init.sh /app/init.sh

CMD /app/init.sh
