FROM tapdata-docker.pkg.coding.net/tapdata/tldp/runtime:0.1

COPY manager /tapdata/apps/manager
COPY iengine /tapdata/apps/iengine
RUN mkdir -p /tapdata/apps/plugin-kit /tapdata/apps/connectors-common /tapdata/apps/connectors /tapdata/apps/build
COPY plugin-kit /tapdata/apps/plugin-kit
COPY connectors-common /tapdata/apps/connectors-common
COPY connectors /tapdata/apps/connectors
COPY build /tapdata/apps/build

COPY docker-entrypoint.sh /tapdata/docker-entrypoint.sh
RUN chmod +x /tapdata/docker-entrypoint.sh

COPY tapshell /tapdata/apps/tapshell
RUN cd /tapdata/apps/tapshell && pip3 install -r requirements.txt

RUN mkdir -p /tapdata/database-init/
COPY database-init/ /tapdata/database-init/
RUN mkdir -p /tapdata/data/db/

ENTRYPOINT ["bash", "-c", "/tapdata/docker-entrypoint.sh"]
