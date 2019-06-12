#!/usr/bin/bash

if [ "$KVSTORE_HOME" = "" ]; then
  echo "Error: KVSTORE_HOME is not set. Set it to root installation folder"
  exit 1
fi

if [ "$JAVA_HOME" = "" ]; then
  echo "Error: JAVA_HOME is not set. KVStore requires Java 1.8 or later"
  exit 1
fi

usage="Usage: kvstore-exec start|stop"
if [ $# -lt 1 ]; then
  echo $usage
  exit 1
fi

KVSTORE_CONF="${KVSTORE_HOME}/conf"
KVSTORE_CLASSPATH="$KVSTORE_CONF"
KVSTORE_CLASSPATH="$KVSTORE_CLASSPATH:$KVSTORE_HOME/jars/*"
KVSTORE_CLASSPATH="$KVSTORE_CLASSPATH:$KVSTORE_HOME/jars/external/*"

export KVSTORE_CONF
export KVSTORE_CLASSPATH

command=$1

if [ "$command" == "start" ]
then
  echo "starting kvstore"
  exec $JAVA_HOME/bin/java -cp "$KVSTORE_CLASSPATH" com.kvstore.daemon.KVStoreDaemon
elif [ "$command" == "stop" ]
then
  PID=`ps -eaf | grep -i 'kvstore' | grep -v grep | awk '{print $2}'`
  if [[ "" !=  "$PID" ]]; then
    echo "stopping kvstore"
    kill $PID
  else
    echo "kvstore not running. nothing to stop"
  fi
else
  echo $usage
  exit 1
fi






