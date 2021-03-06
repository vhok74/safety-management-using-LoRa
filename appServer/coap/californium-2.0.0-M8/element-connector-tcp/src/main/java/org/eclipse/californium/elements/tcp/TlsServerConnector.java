/*******************************************************************************
 * Copyright (c) 2016, 2017 Amazon Web Services and others.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * <p>
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.html.
 * <p>
 * Contributors:
 * Joe Magerramov (Amazon Web Services) - CoAP over TCP support.
 * Achim Kraus (Bosch Software Innovations GmbH) - create "remote aware" SSLEngine
 * Achim Kraus (Bosch Software Innovations GmbH) - introduce protocol,
 *                                                 remove scheme
 * Achim Kraus (Bosch Software Innovations GmbH) - add client authentication mode.
 * Bosch Software Innovations GmbH - migrate to SLF4J
 ******************************************************************************/
package org.eclipse.californium.elements.tcp;

import io.netty.channel.Channel;
import io.netty.handler.ssl.SslHandler;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A TLS server connector that accepts inbound TLS connections.
 */
public class TlsServerConnector extends TcpServerConnector {

	private static final Logger LOGGER = LoggerFactory.getLogger(TlsServerConnector.class.getName());

	public static enum ClientAuthMode {
		NONE, WANTED, NEEDED
	}

	private final SSLContext sslContext;
	private final ClientAuthMode clientAuthMode;

	/**
	 * Initializes SSLEngine with specified SSL engine and client authentication
	 * mode.
	 */
	public TlsServerConnector(SSLContext sslContext, ClientAuthMode clientAuthMode, InetSocketAddress socketAddress,
			int numberOfThreads, int idleTimeout) {
		super(socketAddress, numberOfThreads, idleTimeout);
		this.sslContext = sslContext;
		this.clientAuthMode = clientAuthMode;
	}

	/**
	 * Initializes SSLEngine with specified SSL engine.
	 */
	public TlsServerConnector(SSLContext sslContext, InetSocketAddress socketAddress, int numberOfThreads,
			int idleTimeout) {
		super(socketAddress, numberOfThreads, idleTimeout);
		this.sslContext = sslContext;
		this.clientAuthMode = ClientAuthMode.NONE;
	}

	/**
	 * Initializes SSLEngine with specified SSL key management factory.
	 */
	public TlsServerConnector(KeyManagerFactory keyManagerFactory, InetSocketAddress socketAddress, int numberOfThreads,
			int idleTimeout) {
		super(socketAddress, numberOfThreads, idleTimeout);
		this.clientAuthMode = ClientAuthMode.NONE;

		try {
			this.sslContext = SSLContext.getInstance("TLS");
			this.sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
		} catch (KeyManagementException | NoSuchAlgorithmException e) {
			throw new RuntimeException("Unable to initialize SSL engine", e);
		}
	}

	@Override
	protected void onNewChannelCreated(Channel ch) {
		SSLEngine sslEngine = createSllEngineForChannel(ch);
		switch (clientAuthMode) {
		case NONE:
			break;
		case WANTED:
			sslEngine.setWantClientAuth(true);
			break;
		case NEEDED:
			sslEngine.setNeedClientAuth(true);
			break;
		}
		sslEngine.setUseClientMode(false);
		ch.pipeline().addFirst(new SslHandler(sslEngine));
	}

	@Override
	public String getProtocol() {
		return "TLS";
	}

	/**
	 * Create SSL engine for channel.
	 * 
	 * @param ch channel to determine remote host
	 * @return created SSL engine
	 */
	private SSLEngine createSllEngineForChannel(Channel ch) {
		SocketAddress remoteAddress = ch.remoteAddress();
		if (remoteAddress instanceof InetSocketAddress) {
			InetSocketAddress remote = (InetSocketAddress) remoteAddress;
			LOGGER.info("Connection from inet {}", remote);
			return sslContext.createSSLEngine(remote.getHostString(), remote.getPort());
		} else {
			LOGGER.info("Connection from {}", remoteAddress);
			return sslContext.createSSLEngine();
		}
	}

}
