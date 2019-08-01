/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package com.ca.apiml.security.error;

/**
 * Exception thrown when an API Gateway Service is not accessible
 */
public class GatewayNotFoundException extends RuntimeException {

    public GatewayNotFoundException(String message) {
        super(message);
    }

    public GatewayNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}