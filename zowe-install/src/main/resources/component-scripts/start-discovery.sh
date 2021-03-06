#!/bin/sh

################################################################################
# This program and the accompanying materials are made available under the terms of the
# Eclipse Public License v2.0 which accompanies this distribution, and is available at
# https://www.eclipse.org/legal/epl-v20.html
#
# SPDX-License-Identifier: EPL-2.0
#
# Copyright IBM Corporation 2019
################################################################################

DISCOVERY_CODE=AD
_BPX_JOBNAME=${ZOWE_PREFIX}${DISCOVERY_CODE} java -Xms32m -Xmx256m -Xquickstart \
    -Dibm.serversocket.recover=true \
    -Dfile.encoding=UTF-8 \
    -Djava.io.tmpdir=/tmp \
    -Dspring.profiles.active=https \
    -Dspring.profiles.include=$LOG_LEVEL \
    -Dserver.address=0.0.0.0 \
    -Dapiml.discovery.userid=eureka \
    -Dapiml.discovery.password=password \
    -Dapiml.discovery.allPeersUrls=${ZWE_DISCOVERY_SERVICES_LIST} \
    -Dapiml.logs.location=${WORKSPACE_DIR}/api-mediation/logs \
    -Dapiml.service.hostname=${ZOWE_EXPLORER_HOST} \
    -Dapiml.service.port=${DISCOVERY_PORT} \
    -Dapiml.service.ipAddress=${ZOWE_IP_ADDRESS} \
    -Dapiml.service.preferIpAddress=${APIML_PREFER_IP_ADDRESS} \
    -Dapiml.discovery.staticApiDefinitionsDirectories=${APIML_STATIC_DEF} \
    -Dapiml.security.ssl.verifySslCertificatesOfServices=${VERIFY_CERTIFICATES} \
    -Dserver.ssl.enabled=true \
    -Dserver.ssl.keyStore=${KEYSTORE} \
    -Dserver.ssl.keyStoreType=${KEYSTORE_TYPE} \
    -Dserver.ssl.keyStorePassword=${KEYSTORE_PASSWORD} \
    -Dserver.ssl.keyAlias=${KEY_ALIAS} \
    -Dserver.ssl.keyPassword=${KEYSTORE_PASSWORD} \
    -Dserver.ssl.trustStore=${TRUSTSTORE} \
    -Dserver.ssl.trustStoreType=${KEYSTORE_TYPE} \
    -Dserver.ssl.trustStorePassword=${KEYSTORE_PASSWORD} \
    -Djava.protocol.handler.pkgs=com.ibm.crypto.provider \
    -jar ${ROOT_DIR}"/components/api-mediation/discovery-service.jar" &
export DISCOVERY_PID=$?
