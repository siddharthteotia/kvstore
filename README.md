kvstore-rpc is a distributed inmemory key value store currently under development. I initially wrote this to complement a blog post on using [Netty for RPC](https://loonytek.com/2019/06/29/building-rpc-layer-in-a-distributed-system-using-netty-an-introductory-tutorial/) in a distributed system

# Build and Run KVStore

## Requirements

- Maven 3.6 or later
- Java 1.8 or later

My local testing was done on Mac OSX 10.14 

## Clone the source code
- git clone https://github.com/siddharthteotia/kvstore.git
- cd source-directory
- mvn clean install 

## Run KVStore locally

- cp <source director>/kvstore-distribution/engine/target/kvstore-distribution-engine-1.0-SNAPSHOT.tar.gz/install-directory
- cd install-directory
- tar xvf kvstore-distribution-engine-1.0-SNAPSHOT.tar.gz
- cd kvstore-distribution-engine-1.0-SNAPSHOT
- export JAVA_HOME=JDK-install-location
- export KVSTORE_HOME=kvstore-install-location
  
For example, if path to my install-directory is /abc/xyz/install_dir, then 
- export KVSTORE_HOME=/abc/xyz/install_dir/kvstore-distribution-engine-1.0-SNAPSHOT

Edit the configuration file to set rpc.demo.enabled to true if you want to run demo service as well

Start the KVStore daemon on local node as **sh kvstore-exec.sh start**

## Clustered Deployment 

On each node of the cluster, copy the tarball and extract it. Here is a sample process to start daemon on each node

- mkdir /opt/kvstore
- Copy kvstore-distribution-engine-1.0-SNAPSHOT.tar.gz to /opt/kvstore
- cd /opt/kvstore
- tar xvf kvstore-distribution-engine-1.0-SNAPSHOT.tar.gz
- cd kvstore-distribution-engine-1.0-SNAPSHOT/bin
- sh kvstore-exec start

## Demo Service

An optional application that is packaged into the distribution to demonstrate RPC messaging in the cluster. This is currently fully functional and tested on a 3 node EC2 cluster (and locally)

Follow the above steps for clustered deployment. Let's say you deployed on 3 nodes -- node1, node2, and node3

- Edit the configuration file of node1 and node 2 and set the value of rpc.demo.server.endpoint to address of node3
- Also set rpc.demo.enabled to true on node1 and node2
- sh kvstore-exec start

Node1 and Node2 will make RPC requests to Node3 that is hosting an in-memory data store.

Note: it is completely fine to set rpc.demo.enabled to true in conf file of node3. In that case, the demo service will issue RPC requests to the local endpoint

Please read this [blog post](https://loonytek.com/2019/06/29/building-rpc-layer-in-a-distributed-system-using-netty-an-introductory-tutorial/) to learn more about the idea and purpose behind this sytem, further work and the architectural plan
