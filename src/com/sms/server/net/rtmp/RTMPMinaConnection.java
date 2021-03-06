package com.sms.server.net.rtmp;

import java.beans.ConstructorProperties;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;

import javax.management.ObjectName;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sms.jmx.JMXAgent;
import com.sms.jmx.JMXFactory;
import com.sms.jmx.mxbeans.RTMPMinaConnectionMXBean;
import com.sms.server.Configuration;
import com.sms.server.api.IScope;
import com.sms.server.net.rtmp.codec.RTMP;
import com.sms.server.net.rtmp.event.ClientBW;
import com.sms.server.net.rtmp.event.ServerBW;
import com.sms.server.net.rtmp.message.Packet;
import com.sms.server.net.rtmp.protocol.ProtocolState;

public class RTMPMinaConnection extends RTMPConnection implements RTMPMinaConnectionMXBean {

	protected static Logger log = LoggerFactory
			.getLogger(RTMPMinaConnection.class);

	/**
	 * MINA I/O session, connection between two end points
	 */
	private volatile IoSession ioSession;
	
	protected int defaultServerBandwidth = 10000000;

	protected int defaultClientBandwidth = 10000000;
	
	protected boolean bandwidthDetection = true;
	
	/**
	 * MBean object name used for de/registration purposes.
	 */
	private volatile ObjectName oName;

	{
		log.debug("RTMPMinaConnection created");
	}

	/** Constructs a new RTMPMinaConnection. */
	@ConstructorProperties(value = { "persistent" })
	public RTMPMinaConnection() {
		super(PERSISTENT);
		defaultClientBandwidth = Configuration.RTMP_DEFAULT_CLIENT_BANDWIDTH;
		defaultServerBandwidth = Configuration.RTMP_DEFAULT_SERVER_BANDWIDTH;
		limitType = Configuration.RTMP_CLIENT_BANDWIDTH_LIMIT_TYPE;
		bandwidthDetection = Configuration.RTMP_BANDWIDTH_DETECTION;
		setPingInterval(Configuration.RTMP_PING_INTERVAL);
		setMaxInactivity(Configuration.RTMP_MAX_INACTIVITY);
		setMaxHandshakeTimeout(Configuration.RTMP_MAX_HANDSHAKE_TIMEOUT);
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		super.close();
		log.debug("IO Session closing: {}", (ioSession != null ? ioSession.isClosing() : null));
		if (ioSession != null) {
			// accept no further incoming data
			ioSession.suspendRead();
			IoFilterChain filters = ioSession.getFilterChain();
			// check if it exists and remove
			if (filters.contains("bandwidthFilter")) {
				ioSession.getFilterChain().remove("bandwidthFilter");
			}
			// update our state
			if (ioSession.containsAttribute(ProtocolState.SESSION_KEY)) {
				RTMP rtmp = (RTMP) ioSession.getAttribute(ProtocolState.SESSION_KEY);
				if (rtmp != null) {
					log.debug("RTMP state: {}", rtmp);
					rtmp.setState(RTMP.STATE_DISCONNECTING);
				}
			}
			
			// close now, no flushing, no waiting
			final CloseFuture future = ioSession.close(true);
			IoFutureListener<CloseFuture> listener = new IoFutureListener<CloseFuture>() {
				public void operationComplete(CloseFuture future) {
					if (future.isClosed()) {
						log.debug("Connection is closed");
					} else {
						log.debug("Connection is not yet closed");
					}
					future.removeListener(this);
				}
			};
			future.addListener(listener);
			//de-register with JMX
			JMXAgent.unregisterMBean(oName);
		}
	}

	@SuppressWarnings("cast")
	@Override
	public boolean connect(IScope newScope, Object[] params) {
		log.debug("Connect scope: {}", newScope);
		boolean success = super.connect(newScope, params);
		if (success) {
			// tell the flash player how fast we want data and how fast we shall send it
			getChannel(2).write(new ServerBW(defaultServerBandwidth));
			// second param is the limit type (0=hard,1=soft,2=dynamic)
			getChannel(2).write(new ClientBW(defaultClientBandwidth, (byte) limitType));
			//if the client is null for some reason, skip the jmx registration
			if (client != null) {
				// perform bandwidth detection
				if (bandwidthDetection && !client.isBandwidthChecked()) {
					client.checkBandwidth();
				}
				
				// register with jmx
				try {
					String cName = this.getClass().getName();
					if (cName.indexOf('.') != -1) {
						cName = cName.substring(cName.lastIndexOf('.')).replaceFirst("[\\.]", "");
					}
					String hostStr = host;
					int port = 1935;
					if (host != null && host.indexOf(":") > -1) {
						String[] arr = host.split(":");
						hostStr = arr[0];
						port = Integer.parseInt(arr[1]);
					} else {
						hostStr = "localhost";
					}
					// Create a new mbean for this instance
					oName = JMXFactory.createObjectName("type", cName, "connectionType", type, "host", hostStr, "port", port + "", "clientId", client.getId());
					JMXAgent.registerMBean(this, this.getClass().getName(), RTMPMinaConnectionMXBean.class, oName);
				} catch (Exception e) {
					log.warn("Exception registering mbean", e);
				}
			} else {
				log.warn("Client was null");
			}
		}
		return success;
	}

	/**
	 * Return MINA I/O session.
	 * 
	 * @return MINA O/I session, connection between two end-points
	 */
	public IoSession getIoSession() {
		return ioSession;
	}
	
	/**
	 * @return the defaultServerBandwidth
	 */
	public int getDefaultServerBandwidth() {
		return defaultServerBandwidth;
	}

	/**
	 * @param defaultServerBandwidth the defaultServerBandwidth to set
	 */
	public void setDefaultServerBandwidth(int defaultServerBandwidth) {
		this.defaultServerBandwidth = defaultServerBandwidth;
	}

	/**
	 * @return the defaultClientBandwidth
	 */
	public int getDefaultClientBandwidth() {
		return defaultClientBandwidth;
	}

	/**
	 * @param defaultClientBandwidth the defaultClientBandwidth to set
	 */
	public void setDefaultClientBandwidth(int defaultClientBandwidth) {
		this.defaultClientBandwidth = defaultClientBandwidth;
	}

	/**
	 * @return the limitType
	 */
	public int getLimitType() {
		return limitType;
	}

	/**
	 * @param limitType the limitType to set
	 */
	public void setLimitType(int limitType) {
		this.limitType = limitType;
	}

	/**
	 * @return the bandwidthDetection
	 */
	public boolean isBandwidthDetection() {
		return bandwidthDetection;
	}

	/**
	 * @param bandwidthDetection the bandwidthDetection to set
	 */
	public void setBandwidthDetection(boolean bandwidthDetection) {
		this.bandwidthDetection = bandwidthDetection;
	}
	
	/** {@inheritDoc} */
	@Override
	public boolean isReaderIdle() {
		if (ioSession != null) {
			return ioSession.isReaderIdle();
		}
		return true;
	}
	
	/** {@inheritDoc} */
	@Override
	public boolean isWriterIdle() {
		if (ioSession != null) {
			return ioSession.isWriterIdle();
		}
		return true;
	}	
	
	/** {@inheritDoc} */
	@Override
	public long getPendingMessages() {
		if (ioSession != null) {
			return ioSession.getScheduledWriteMessages();
		}
		return 0;
	}

	/** {@inheritDoc} */
	@Override
	public long getReadBytes() {
		if (ioSession != null) {
			return ioSession.getReadBytes();
		}
		return 0;
	}

	/** {@inheritDoc} */
	@Override
	public long getWrittenBytes() {
		if (ioSession != null) {
			return ioSession.getWrittenBytes();
		}
		return 0;
	}

	public void invokeMethod(String method) {
		invoke(method);
	}

	/** {@inheritDoc} */
	@Override
	public boolean isConnected() {
		// XXX Paul: not sure isClosing is actually working as we expect here
		return super.isConnected() && (ioSession != null)
				&& ioSession.isConnected(); // && !ioSession.isClosing();
	}
	
	/** {@inheritDoc} */
	@Override
	public boolean isIdle() {
		if (ioSession != null) {
			log.debug("Connection idle - read: {} write: {}", ioSession.isReaderIdle(), ioSession.isWriterIdle());
			return super.isIdle() && ioSession.isBothIdle();
		} 
		return super.isIdle();
	}

	/** {@inheritDoc} */
	@Override
	protected void onInactive() {
		this.close();
	}

	/** {@inheritDoc} */
	@Override
	public void rawWrite(IoBuffer out) {
		if (ioSession != null) {
			ioSession.write(out);
		}
	}

	/**
	 * Setter for MINA I/O session (connection).
	 * 
	 * @param protocolSession
	 *            Protocol session
	 */
	public void setIoSession(IoSession protocolSession) {
		SocketAddress remote = protocolSession.getRemoteAddress();
		if (remote instanceof InetSocketAddress) {
			remoteAddress = ((InetSocketAddress) remote).getAddress()
					.getHostAddress();
			remotePort = ((InetSocketAddress) remote).getPort();
		} else {
			remoteAddress = remote.toString();
			remotePort = -1;
		}
		remoteAddresses = new ArrayList<String>(1);
		remoteAddresses.add(remoteAddress);
		remoteAddresses = Collections.unmodifiableList(remoteAddresses);
		this.ioSession = protocolSession;
		sessionId = String.valueOf(ioSession.getId());
	}

	/** {@inheritDoc} */
	@Override
	public void write(Packet out) {
		if (ioSession != null) {
			writingMessage(out);
			ioSession.write(out);
		}
	}
}
