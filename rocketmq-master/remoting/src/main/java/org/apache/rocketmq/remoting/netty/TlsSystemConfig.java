/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.remoting.netty;

import io.netty.handler.ssl.SslContext;
import org.apache.rocketmq.remoting.common.TlsMode;

public class TlsSystemConfig {
    public static final String TLS_SERVER_MODE = "tls.server.mode";
    public static final String TLS_ENABLE = "tls.enable";
    public static final String TLS_CONFIG_FILE = "tls.config.file";
    public static final String TLS_TEST_MODE_ENABLE = "tls.test.mode.enable";

    public static final String TLS_SERVER_NEED_CLIENT_AUTH = "tls.server.need.client.auth";
    public static final String TLS_SERVER_KEYPATH = "tls.server.keyPath";
    public static final String TLS_SERVER_KEYPASSWORD = "tls.server.keyPassword";
    public static final String TLS_SERVER_CERTPATH = "tls.server.certPath";
    public static final String TLS_SERVER_AUTHCLIENT = "tls.server.authClient";
    public static final String TLS_SERVER_TRUSTCERTPATH = "tls.server.trustCertPath";

    public static final String TLS_CLIENT_KEYPATH = "tls.client.keyPath";
    public static final String TLS_CLIENT_KEYPASSWORD = "tls.client.keyPassword";
    public static final String TLS_CLIENT_CERTPATH = "tls.client.certPath";
    public static final String TLS_CLIENT_AUTHSERVER = "tls.client.authServer";
    public static final String TLS_CLIENT_TRUSTCERTPATH = "tls.client.trustCertPath";


    /**
     * To determine whether use SSL in client-side, include SDK client and BrokerOuterAPI
     * 
     * <p> 要确定是否在客户端使用SSL，请包括SDK客户端和BrokerOuterAPI
     */
    public static boolean tlsEnable = Boolean.parseBoolean(System.getProperty(TLS_ENABLE, "false"));

    /**
     * To determine whether use test mode when initialize TLS context
     * 
     * <p> 确定在初始化TLS上下文时是否使用测试模式
     */
    public static boolean tlsTestModeEnable = Boolean.parseBoolean(System.getProperty(TLS_TEST_MODE_ENABLE, "true"));

    /**
     * Indicates the state of the {@link javax.net.ssl.SSLEngine} with respect to client authentication.
     * This configuration item really only applies when building the server-side {@link SslContext},
     * and can be set to none, require or optional.
     * 
     * <p> 指示与客户端身份验证有关的javax.net.ssl.SSLEngine的状态。 
     * 此配置项实际上仅在构建服务器端SslContext时适用，并且可以设置为none，require或optional。
     */
    public static String tlsServerNeedClientAuth = System.getProperty(TLS_SERVER_NEED_CLIENT_AUTH, "none");
    
    /**
     * The store path of server-side private key
     * 
     * <p> 服务器端私钥的存储路径
     */
    public static String tlsServerKeyPath = System.getProperty(TLS_SERVER_KEYPATH, null);

    /**
     * The  password of the server-side private key
     * 
     * <p> 服务器端私钥的密码
     */
    public static String tlsServerKeyPassword = System.getProperty(TLS_SERVER_KEYPASSWORD, null);

    /**
     * The store path of server-side X.509 certificate chain in PEM format
     * 
     * <p> PEM格式的服务器端X.509证书链的存储路径
     */
    public static String tlsServerCertPath = System.getProperty(TLS_SERVER_CERTPATH, null);

    /**
     * To determine whether verify the client endpoint's certificate strictly
     * 
     * <p> 确定是否严格验证客户端端点的证书
     */
    public static boolean tlsServerAuthClient = Boolean.parseBoolean(System.getProperty(TLS_SERVER_AUTHCLIENT, "false"));

    /**
     * The store path of trusted certificates for verifying the client endpoint's certificate
     * 
     * <p> 用于验证客户端端点证书的可信证书的存储路径
     */
    public static String tlsServerTrustCertPath = System.getProperty(TLS_SERVER_TRUSTCERTPATH, null);

    /**
     * The store path of client-side private key
     * 
     * <p> 客户端私钥的存储路径
     */
    public static String tlsClientKeyPath = System.getProperty(TLS_CLIENT_KEYPATH, null);

    /**
     * The  password of the client-side private key
     * 
     * <p> 客户端私钥的密码
     */
    public static String tlsClientKeyPassword = System.getProperty(TLS_CLIENT_KEYPASSWORD, null);

    /**
     * The store path of client-side X.509 certificate chain in PEM format
     * 
     * <p> PEM格式的客户端X.509证书链的存储路径
     */
    public static String tlsClientCertPath = System.getProperty(TLS_CLIENT_CERTPATH, null);

    /**
     * To determine whether verify the server endpoint's certificate strictly
     * 
     * <p> 确定是否严格验证服务器端点的证书
     */
    public static boolean tlsClientAuthServer = Boolean.parseBoolean(System.getProperty(TLS_CLIENT_AUTHSERVER, "false"));

    /**
     * The store path of trusted certificates for verifying the server endpoint's certificate
     * 
     * <p> 用于验证服务器端点证书的可信证书的存储路径
     */
    public static String tlsClientTrustCertPath = System.getProperty(TLS_CLIENT_TRUSTCERTPATH, null);

    /**
     * For server, three SSL modes are supported: disabled, permissive and enforcing.
     * For client, use {@link TlsSystemConfig#tlsEnable} to determine whether use SSL.
     * 
     * <p> 对于服务器，支持三种SSL模式：禁用，允许和强制。 对于客户端，使用TlsSystemConfig.tlsEnable确定是否使用SSL。
     * 
     * <ol>
     *     <li><strong>disabled:</strong> SSL is not supported; any incoming SSL handshake will be rejected, causing connection closed.</li>
     *     <li><strong>disabled:</strong> 不支持SSL; 任何传入的SSL握手都将被拒绝，导致连接关闭。</li>
     *     
     *     <li><strong>permissive:</strong> SSL is optional, aka, server in this mode can serve client connections with or without SSL;</li>
     *     <li><strong>permissive:</strong> SSL是可选的，也就是说，这种模式下的服务器可以使用或不使用SSL来提供客户端连接;</li>
     *     
     *     <li><strong>enforcing:</strong> SSL is required, aka, non SSL connection will be rejected.</li>
     *     <li><strong>enforcing:</strong> SSL是必需的，也就是说，非SSL连接将被拒绝。</li>
     * </ol>
     */
    public static TlsMode tlsMode = TlsMode.parse(System.getProperty(TLS_SERVER_MODE, "permissive"));

    /**
     * A config file to store the above TLS related configurations,
     * except {@link TlsSystemConfig#tlsMode} and {@link TlsSystemConfig#tlsEnable}
     * 
     * <p> 用于存储上述TLS相关配置的配置文件，TlsSystemConfig.tlsMode和TlsSystemConfig.tlsEnable除外
     */
    public static String tlsConfigFile = System.getProperty(TLS_CONFIG_FILE, "/etc/rocketmq/tls.properties");
}
