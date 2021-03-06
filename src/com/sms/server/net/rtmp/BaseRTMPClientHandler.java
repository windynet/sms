package com.sms.server.net.rtmp;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sms.io.utils.ObjectMap;
import com.sms.server.api.IConnection;
import com.sms.server.api.event.IEvent;
import com.sms.server.api.event.IEventDispatcher;
import com.sms.server.api.service.IPendingServiceCall;
import com.sms.server.api.service.IPendingServiceCallback;
import com.sms.server.api.service.IServiceCall;
import com.sms.server.api.service.IServiceInvoker;
import com.sms.server.api.so.IClientSharedObject;
import com.sms.server.api.stream.IClientStream;
import com.sms.server.messaging.IMessage;
import com.sms.server.net.rtmp.codec.RTMP;
import com.sms.server.net.rtmp.codec.RTMPCodecFactory;
import com.sms.server.net.rtmp.event.ChunkSize;
import com.sms.server.net.rtmp.event.ClientBW;
import com.sms.server.net.rtmp.event.Invoke;
import com.sms.server.net.rtmp.event.Notify;
import com.sms.server.net.rtmp.event.Ping;
import com.sms.server.net.rtmp.event.SWFResponse;
import com.sms.server.net.rtmp.event.ServerBW;
import com.sms.server.net.rtmp.message.Header;
import com.sms.server.service.Call;
import com.sms.server.service.MethodNotFoundException;
import com.sms.server.service.PendingCall;
import com.sms.server.service.ServiceInvoker;
import com.sms.server.so.ClientSharedObject;
import com.sms.server.so.SharedObjectMessage;
import com.sms.server.stream.AbstractClientStream;
import com.sms.server.stream.OutputStream;
import com.sms.server.stream.consumer.ConnectionConsumer;
import com.sms.server.util.SystemTimer;

public abstract class BaseRTMPClientHandler extends BaseRTMPHandler implements IRTMPClient {

	private static final Logger log = LoggerFactory.getLogger(BaseRTMPClientHandler.class);

	/**
	 * Connection scheme / protocol
	 */
	protected String scheme = "rtmp";

	/**
	 * Connection parameters
	 */
	protected Map<String, Object> connectionParams;

	/**
	 * Connect call arguments
	 */
	private Object[] connectArguments = null;

	/**
	 * Connection callback
	 */
	private IPendingServiceCallback connectCallback;

	/**
	 * Service provider
	 */
	private Object serviceProvider;

	/**
	 * Service invoker
	 */
	private IServiceInvoker serviceInvoker = new ServiceInvoker();

	/**
	 * Shared objects map
	 */
	private volatile ConcurrentMap<String, ClientSharedObject> sharedObjects = new ConcurrentHashMap<String, ClientSharedObject>();

	/**
	 * Net stream handling
	 */
	private volatile ConcurrentMap<Object, NetStreamPrivateData> streamDataMap = new ConcurrentHashMap<Object, NetStreamPrivateData>();

	/**
	 * Task to start on connection close
	 */
	private Runnable connectionClosedHandler;

	/**
	 * Task to start on connection errors
	 */
	private ClientExceptionHandler exceptionHandler;

	private RTMPCodecFactory codecFactory;

	private IEventDispatcher streamEventDispatcher;

	protected volatile RTMPConnection conn;
	
	/**
	 * Whether or not the bandwidth done has been invoked
	 */
	protected boolean bandwidthCheckDone;

	/**
	 * Whether or not the client is subscribed
	 */
	protected boolean subscribed;

	/**
	 * Whether or not to use swf verification
	 */
	private boolean swfVerification;

	private int bytesReadWindow = 2500000;

	private int bytesWrittenWindow = 2500000;

	protected BaseRTMPClientHandler() {
		codecFactory = new RTMPCodecFactory();
		codecFactory.init();
	}

	public void setConnectionClosedHandler(Runnable connectionClosedHandler) {
		this.connectionClosedHandler = connectionClosedHandler;
	}

	public void setExceptionHandler(ClientExceptionHandler exceptionHandler) {
		this.exceptionHandler = exceptionHandler;
	}

	/**
	 * Start network connection to server
	 * 
	 * @param server
	 *            Server
	 * @param port
	 *            Connection port
	 */
	protected abstract void startConnector(String server, int port);

	/**
	 * Connect RTMP client to server's application via given port
	 * 
	 * @param server Server
	 * @param port Connection port
	 * @param application Application at that server
	 */
	public void connect(String server, int port, String application) {
		log.debug("connect server: {} port {} application {}", new Object[] { server, port, application });
		connect(server, port, application, null);
	}

	/**
	 * Connect RTMP client to server's application via given port with given
	 * connection callback
	 * 
	 * @param server Server
	 * @param port Connection port
	 * @param application Application at that server
	 * @param connectCallback Connection callback
	 */
	public void connect(String server, int port, String application, IPendingServiceCallback connectCallback) {
		log.debug("connect server: {} port {} application {} connectCallback {}", new Object[] { server, port, application, connectCallback });
		connect(server, port, makeDefaultConnectionParams(server, port, application), connectCallback);
	}

	/**
	 * @param server Server
	 * @param port Connection port
	 * @param application Application at that server
	 * @return default connection parameters
	 */
	public Map<String, Object> makeDefaultConnectionParams(String server, int port, String application) {
		Map<String, Object> params = new ObjectMap<String, Object>();
		params.put("app", application);
		params.put("objectEncoding", Integer.valueOf(0));
		params.put("fpad", Boolean.FALSE);
		params.put("flashVer", "WIN 11,2,202,235");
		params.put("audioCodecs", Integer.valueOf(3575));
		params.put("videoFunction", Integer.valueOf(1));
		params.put("pageUrl", null);
		params.put("path", application);
		params.put("capabilities", Integer.valueOf(15));
		params.put("swfUrl", null);
		params.put("videoCodecs", Integer.valueOf(252));
		return params;
	}

	/**
	 * Connect RTMP client to server via given port and with given connection
	 * parameters
	 * 
	 * @param server Server
	 * @param port Connection port
	 * @param connectionParams Connection parameters
	 */
	public void connect(String server, int port, Map<String, Object> connectionParams) {
		log.debug("connect server: {} port {} connectionParams {}", new Object[] { server, port, connectionParams });
		connect(server, port, connectionParams, null);
	}

	/**
	 * Connect RTMP client to server's application via given port
	 * 
	 * @param server Server
	 * @param port Connection port
	 * @param connectionParams Connection parameters
	 * @param connectCallback Connection callback
	 */
	public void connect(String server, int port, Map<String, Object> connectionParams, IPendingServiceCallback connectCallback) {
		connect(server, port, connectionParams, connectCallback, null);
	}

	/**
	 * Connect RTMP client to server's application via given port
	 * 
	 * @param server Server
	 * @param port Connection port
	 * @param connectionParams Connection parameters
	 * @param connectCallback Connection callback
	 * @param connectCallArguments Arguments for 'connect' call
	 */
	public void connect(String server, int port, Map<String, Object> connectionParams, IPendingServiceCallback connectCallback, Object[] connectCallArguments) {
		log.debug("connect server: {} port {} connect - params: {} callback: {} args: {}",
				new Object[] { server, port, connectionParams, connectCallback, Arrays.toString(connectCallArguments) });
		log.info("{}://{}:{}/{}", new Object[] { scheme, server, port, connectionParams.get("app") });
		this.connectionParams = connectionParams;
		this.connectArguments = connectCallArguments;

		if (!connectionParams.containsKey("objectEncoding")) {
			connectionParams.put("objectEncoding", 0);
		}

		this.connectCallback = connectCallback;

		startConnector(server, port);
	}

	/**
	 * Register object that provides methods that can be called by the server.
	 * 
	 * @param serviceProvider Service provider
	 */
	public void setServiceProvider(Object serviceProvider) {
		this.serviceProvider = serviceProvider;
	}

	/**
	 * Connect to client shared object.
	 * 
	 * @param name Client shared object name
	 * @param persistent SO persistence flag
	 * @return Client shared object instance
	 */
	public synchronized IClientSharedObject getSharedObject(String name, boolean persistent) {
		log.debug("getSharedObject name: {} persistent {}", new Object[] { name, persistent });
		ClientSharedObject result = sharedObjects.get(name);
		if (result != null) {
			if (result.isPersistent() != persistent) {
				throw new RuntimeException("Already connected to a shared object with this name, but with different persistence.");
			}
			return result;
		}

		result = new ClientSharedObject(name, persistent);
		sharedObjects.put(name, result);
		return result;
	}

	/** {@inheritDoc} */
	@Override
	protected void onChunkSize(RTMPConnection conn, Channel channel, Header source, ChunkSize chunkSize) {
		log.debug("onChunkSize");
		// set read and write chunk sizes
		RTMP state = conn.getState();
		state.setReadChunkSize(chunkSize.getSize());
		state.setWriteChunkSize(chunkSize.getSize());
		log.info("ChunkSize is not implemented yet: {}", chunkSize);
	}

	/** {@inheritDoc} */
	@Override
	protected void onPing(RTMPConnection conn, Channel channel, Header source, Ping ping) {
		log.debug("onPing");
		switch (ping.getEventType()) {
			case Ping.PING_CLIENT:
			case Ping.STREAM_BEGIN:
			case Ping.RECORDED_STREAM:
			case Ping.STREAM_PLAYBUFFER_CLEAR:
				// The server wants to measure the RTT
				Ping pong = new Ping();
				pong.setEventType(Ping.PONG_SERVER);
				pong.setValue2((int) (SystemTimer.currentTimeMillis() & 0xffffffff));
				conn.ping(pong);
				break;
			case Ping.STREAM_DRY:
				log.debug("Stream indicates there is no data available");
				break;
			case Ping.CLIENT_BUFFER:
				//set the client buffer
				IClientStream stream = null;
				//get the stream id
				int streamId = ping.getValue2();
				//get requested buffer size in milliseconds
				int buffer = ping.getValue3();
				log.debug("Client sent a buffer size: {} ms for stream id: {}", buffer, streamId);
				if (streamId != 0) {
					// The client wants to set the buffer time
					stream = conn.getStreamById(streamId);
					if (stream != null) {
						stream.setClientBufferDuration(buffer);
						log.info("Setting client buffer on stream: {}", buffer);
					}
				}
				//catch-all to make sure buffer size is set
				if (stream == null) {
					// Remember buffer time until stream is created
					conn.rememberStreamBufferDuration(streamId, buffer);
					log.info("Remembering client buffer on stream: {}", buffer);
				}
				break;
			case Ping.PING_SWF_VERIFY:
				// TODO get the swf verification bytes from the handshake
				SWFResponse swfPong = new SWFResponse(new byte[42]);
				conn.ping(swfPong);
				break;
			default:
				log.warn("Unhandled ping: {}", ping);
		}
	}
	
	/** {@inheritDoc} */
	@Override
	protected void onServerBandwidth(RTMPConnection conn, Channel channel, ServerBW message) {
		log.trace("onServerBandwidth");
		// if the size is not equal to our read size send a client bw control message
		int bandwidth = message.getBandwidth();
		if (bandwidth != bytesReadWindow) {
			ClientBW clientBw = new ClientBW(bandwidth, (byte) 2);
			channel.write(clientBw);
		}
	}

	/** {@inheritDoc} */
	@Override
	protected void onClientBandwidth(RTMPConnection conn, Channel channel, ClientBW message) {
		log.trace("onClientBandwidth");
		// if the size is not equal to our write size send a server bw control message
		int bandwidth = message.getBandwidth();
		if (bandwidth != bytesWrittenWindow) {
			ServerBW serverBw = new ServerBW(bandwidth);
			channel.write(serverBw);
		}
	}

	/** {@inheritDoc} */
	@Override
	protected void onSharedObject(RTMPConnection conn, Channel channel, Header source, SharedObjectMessage object) {
		log.debug("onSharedObject");
		ClientSharedObject so = sharedObjects.get(object.getName());
		if (so == null) {
			log.error("Ignoring request for non-existend SO: {}", object);
			return;
		}
		if (so.isPersistent() != object.isPersistent()) {
			log.error("Ignoring request for wrong-persistent SO: {}", object);
			return;
		}
		log.debug("Received SO request: {}", object);
		so.dispatchEvent(object);
	}
	
	/**
	 * Called when negotiating bandwidth.
	 * 
	 * @param params
	 */
	public void onBWCheck(Object params) {
		log.debug("onBWCheck: {}", params);
	}

	/**
	 * Called when bandwidth has been configured.
	 */
	public void onBWDone(Object params) {
		log.debug("onBWDone: {}", params);
		bandwidthCheckDone = true;
	}
	
	/**
	 * Called when bandwidth has been configured.
	 */
	public void onBWDone() {
		log.debug("onBWDone");
		bandwidthCheckDone = true;
	}

	/**
	 * Invoke a method on the server.
	 * 
	 * @param method Method name
	 * @param callback Callback handler
	 */
	public void invoke(String method, IPendingServiceCallback callback) {
		log.debug("invoke method: {} params {} callback {}", new Object[] { method, callback });
		// get it from the conn manager
		if (conn != null) {
			conn.invoke(method, callback);
		} else {
			log.info("Connection was null");
            PendingCall result = new PendingCall(method);
            result.setStatus(Call.STATUS_NOT_CONNECTED);
            callback.resultReceived(result);
		}
	}

	/**
	 * Invoke a method on the server and pass parameters.
	 * 
	 * @param method Method
	 * @param params Method call parameters
	 * @param callback Callback object
	 */
	public void invoke(String method, Object[] params, IPendingServiceCallback callback) {
		log.debug("invoke method: {} params {} callback {}", new Object[] { method, params, callback });
		if (conn != null) {
            conn.invoke(method, params, callback);
		} else {
			log.info("Connection was null");
            PendingCall result = new PendingCall(method, params);
            result.setStatus(Call.STATUS_NOT_CONNECTED);
            callback.resultReceived(result);
		}
	}

	/**
	 * Disconnect the first connection in the connection map
	 */
	public void disconnect() {
		log.debug("disconnect");
		if (conn != null) {
			streamDataMap.clear();
			conn.close();
		} else {
			log.info("Connection was null");
		}
	}

	public void createStream(IPendingServiceCallback callback) {
		log.debug("createStream - callback: {}", callback);
		IPendingServiceCallback wrapper = new CreateStreamCallBack(callback);
		invoke("createStream", null, wrapper);
	}

	public void publish(int streamId, String name, String mode, INetStreamEventHandler handler) {
		log.debug("publish - stream id: {}, name: {}, mode: {}", new Object[] { streamId, name, mode });
		Object[] params = new Object[2];
		params[0] = name;
		params[1] = mode;
		PendingCall pendingCall = new PendingCall("publish", params);
		conn.invoke(pendingCall, getChannelForStreamId(streamId));
		if (handler != null) {
			NetStreamPrivateData streamData = streamDataMap.get(streamId);
			if (streamData != null) {
				log.debug("Setting handler on stream data - handler: {}", handler);
				streamData.handler = handler;
			} else {
				log.debug("Stream data not found for stream id: {}", streamId);
			}
		}
	}

	public void unpublish(int streamId) {
		log.debug("unpublish stream {}", streamId);
		PendingCall pendingCall = new PendingCall("publish", new Object[] { false });
		conn.invoke(pendingCall, getChannelForStreamId(streamId));
	}

	public void publishStreamData(int streamId, IMessage message) {
		NetStreamPrivateData streamData = streamDataMap.get(streamId);
		log.debug("publishStreamData - stream data map: {}",	streamDataMap);
		if (streamData != null) {
			if (streamData.connConsumer != null) {
				streamData.connConsumer.pushMessage(null, message);
			} else {
				log.warn("Connection consumer was not found for stream id: {}", streamId);
			}
		} else {
			log.warn("Stream data not found for stream id: {}", streamId);
		}
	}

	public void play(int streamId, String name, int start, int length) {
		log.debug("play stream {}, name: {}, start {}, length {}", new Object[] { streamId, name, start, length });
		if (conn != null) {
			Object[] params = new Object[3];
			params[0] = name;
			params[1] = start;
			params[2] = length;
			PendingCall pendingCall = new PendingCall("play", params);
			conn.invoke(pendingCall, getChannelForStreamId(streamId));
		} else {
			log.info("Connection was null ?");
		}
	}
	
	/**
	 * Dynamic streaming play method.
	 * 
	 * The following properties are supported on the play options:
	 * <pre>
		streamName: String. The name of the stream to play or the new stream to switch to.
		oldStreamName: String. The name of the initial stream that needs to be switched out. This is not needed and ignored 
		               when play2 is used for just playing the stream and not switching to a new stream.
		start: Number. The start time of the new stream to play, just as supported by the existing play API. and it has the 
		               same defaults. This is ignored when the method is called for switching (in other words, the transition 
		               is either NetStreamPlayTransition.SWITCH or NetStreamPlayTransitions.SWAP)
		len: Number. The duration of the playback, just as supported by the existing play API and has the same defaults.
		transition: String. The transition mode for the playback command. It could be one of the following:
							NetStreamPlayTransitions.RESET
							NetStreamPlayTransitions.APPEND
							NetStreamPlayTransitions.SWITCH
							NetStreamPlayTransitions.SWAP
		</pre>
		NetStreamPlayTransitions:
		<pre>					
			APPEND : String = "append" - Adds the stream to a playlist and begins playback with the first stream.
 	 		APPEND_AND_WAIT : String = "appendAndWait" - Builds a playlist without starting to play it from the first stream.
 	 		RESET : String = "reset" - Clears any previous play calls and plays the specified stream immediately.
 	 		RESUME : String = "resume" - Requests data from the new connection starting from the point at which the previous connection ended.
 	 		STOP : String = "stop" - Stops playing the streams in a playlist.
 	 		SWAP : String = "swap" - Replaces a content stream with a different content stream and maintains the rest of the playlist.
 	 		SWITCH : String = "switch" - Switches from playing one stream to another stream, typically with streams of the same content.			
	   </pre>
	   @see <a href="http://www.adobe.com/devnet/flashmediaserver/articles/dynstream_actionscript.html">ActionScript guide to dynamic streaming</a>
	   @see <a href="http://help.adobe.com/en_US/FlashPlatform/reference/actionscript/3/flash/net/NetStreamPlayTransitions.html">NetStreamPlayTransitions</a>
	 */
	public void play2(int streamId, Map<String, ?> playOptions) {
		log.debug("play2 options: {}", playOptions.toString());
		/* { streamName=streams/new.flv,
		    oldStreamName=streams/old.flv, 
			start=0, len=-1,
			offset=12.195, 
			transition=switch } */
		// get the transition type
		String transition = (String) playOptions.get("transition");
		if (conn != null) {
			if ("NetStreamPlayTransitions.STOP".equals(transition)) {
				PendingCall pendingCall = new PendingCall("play", new Object[] { Boolean.FALSE });
				conn.invoke(pendingCall, getChannelForStreamId(streamId));
			} else if ("NetStreamPlayTransitions.RESET".equals(transition)) {
				// just reset the currently playing stream
				
			} else {
    			Object[] params = new Object[6];
    			params[0] = playOptions.get("streamName").toString();
    			Object o = playOptions.get("start");
    			params[1] = o instanceof Integer ? (Integer) o : Integer.valueOf((String) o);
    			o = playOptions.get("len");
    			params[2] = o instanceof Integer ? (Integer) o : Integer.valueOf((String) o);
    			// new parameters for playback
    			params[3] = transition;
    			params[4] = playOptions.get("offset");
    			params[5] = playOptions.get("oldStreamName");
    			// do call
    			PendingCall pendingCall = new PendingCall("play2", params);
    			conn.invoke(pendingCall, getChannelForStreamId(streamId));
			}
		} else {
			log.info("Connection was null ?");
		}
	}

	/** {@inheritDoc} */
	@Override
	public void connectionOpened(RTMPConnection conn, RTMP state) {
		log.debug("connectionOpened");
		// Send "connect" call to the server
		Channel channel = conn.getChannel((byte) 3);
		PendingCall pendingCall = new PendingCall("connect");
		pendingCall.setArguments(connectArguments);
		Invoke invoke = new Invoke(pendingCall);
		invoke.setConnectionParams(connectionParams);
		invoke.setInvokeId(1);
		if (connectCallback != null) {
			pendingCall.registerCallback(connectCallback);
		}
		conn.registerPendingCall(invoke.getInvokeId(), pendingCall);
		log.debug("Writing 'connect' invoke: {}, invokeId: {}", invoke, invoke.getInvokeId());
		channel.write(invoke);
	}

	@Override
	public void connectionClosed(RTMPConnection conn, RTMP state) {
		log.debug("connectionClosed");
		super.connectionClosed(conn, state);
		if (connectionClosedHandler != null) {
			connectionClosedHandler.run();
		}
	}

	/** {@inheritDoc} */
	@Override
	protected void onInvoke(RTMPConnection conn, Channel channel, Header source, Notify invoke, RTMP rtmp) {
		if (invoke.getType() == IEvent.Type.STREAM_DATA) {
			log.debug("Ignoring stream data notify with header: {}", source);
			return;
		}
		log.debug("onInvoke: {}, invokeId: {}", invoke, invoke.getInvokeId());
		final IServiceCall call = invoke.getCall();
		String methodName = call.getServiceMethodName();
		if ("_result".equals(methodName) || "_error".equals(methodName)) {
			final IPendingServiceCall pendingCall = conn.getPendingCall(invoke.getInvokeId());
			log.debug("Received result for pending call {}", pendingCall);
			if (pendingCall != null) {
				if ("connect".equals(pendingCall.getServiceMethodName())) {
					Integer encoding = (Integer) connectionParams.get("objectEncoding");
					if (encoding != null && encoding.intValue() == 3) {
						log.debug("Setting encoding to AMF3");
						rtmp.setEncoding(IConnection.Encoding.AMF3);
					}
				}
			}
			handlePendingCallResult(conn, invoke);
			return;
		}

		// potentially used twice so get the value once
		boolean onStatus = call.getServiceMethodName().equals("onStatus");
		log.debug("onStatus {}", onStatus);
		if (onStatus) {
			// XXX better to serialize ObjectMap to Status object
			ObjectMap<?, ?> objMap = (ObjectMap<?, ?>) call.getArguments()[0];
			// should keep this as an Object to stay compatible with FMS3 etc
			Object clientId = objMap.get("clientid");
			log.debug("Client id at onStatus: {}", clientId);
			if (clientId == null) {
				clientId = source.getStreamId();
			}

			log.debug("Client/stream id: {}", clientId);
			if (clientId != null) {
				// try lookup by client id first
				NetStreamPrivateData streamData = streamDataMap.get(clientId);
				// if null try to supply the first one in the map
				if (streamData == null) {
					log.debug("Stream data map: {}", streamDataMap);
					streamData = streamDataMap.get(1);
				}
				if (streamData == null) {
					log.warn("Stream data was null for client id: {}", clientId);
				}
				if (streamData != null && streamData.handler != null) {
					streamData.handler.onStreamEvent(invoke);
				}
			}
		}

		if (serviceProvider == null) {
			// Client doesn't support calling methods on him
			call.setStatus(Call.STATUS_METHOD_NOT_FOUND);
			call.setException(new MethodNotFoundException(call.getServiceMethodName()));
		} else {
			serviceInvoker.invoke(call, serviceProvider);
		}

		if (call instanceof IPendingServiceCall) {
			IPendingServiceCall psc = (IPendingServiceCall) call;
			Object result = psc.getResult();
			log.debug("Pending call result is: {}", result);
			if (result instanceof DeferredResult) {
				DeferredResult dr = (DeferredResult) result;
				dr.setInvokeId(invoke.getInvokeId());
				dr.setServiceCall(psc);
				dr.setChannel(channel);
				conn.registerDeferredResult(dr);
			} else if (!onStatus) {
				if ("onBWCheck".equals(methodName)) {
					onBWCheck(call.getArguments().length > 0 ? call.getArguments()[0] : null);
					Invoke reply = new Invoke();
					reply.setCall(call);
					reply.setInvokeId(invoke.getInvokeId());
					channel.write(reply);
				} else if ("onBWDone".equals(methodName)) {
					onBWDone(call.getArguments().length > 0 ? call.getArguments()[0] : null);
				} else {
					Invoke reply = new Invoke();
					reply.setCall(call);
					reply.setInvokeId(invoke.getInvokeId());
					log.debug("Sending empty call reply: {}", reply);
					channel.write(reply);
				}
			}
		}
	}

	/**
	 * Setter for codec factory
	 * 
	 * @param factory Codec factory to use
	 */
	public void setCodecFactory(RTMPCodecFactory factory) {
		this.codecFactory = factory;
	}

	/**
	 * Getter for codec factory
	 * 
	 * @return Codec factory
	 */
	public RTMPCodecFactory getCodecFactory() {
		return this.codecFactory;
	}

	public void handleException(Throwable throwable) {
		if (exceptionHandler != null) {
			exceptionHandler.handleException(throwable);
		} else {
			log.error("Connection exception", throwable);
			throw new RuntimeException(throwable);
		}
	}

	/**
	 * Returns a channel based on the given stream id.
	 * 
	 * @param streamId
	 * @return the channel for this stream id
	 */
	protected int getChannelForStreamId(int streamId) {
		return (streamId - 1) * 5 + 4;
	}

	/**
	 * Sets a reference to the connection associated with this client handler.
	 * 
	 * @param conn
	 */
	public void setConnection(RTMPConnection conn) {
		this.conn = conn;
	}
	
	/**
	 * Returns the connection associated with this client.
	 * 
	 * @return conn
	 */
	public RTMPConnection getConnection() {
		return conn;
	}
	
	/**
	 * Enables or disables SWF verification.
	 * 
	 * @param enabled
	 */
	public void setSwfVerification(boolean enabled) {
		swfVerification = enabled;
	}

	/**
	 * Returns true if swf verification is enabled
	 * 
	 * @return the swfVerification
	 */
	public boolean isSwfVerification() {
		return swfVerification;
	}

	/**
	 * Returns true if bandwidth done has been invoked
	 * 
	 * @return the bandwidthCheckDone
	 */
	public boolean isBandwidthCheckDone() {
		return bandwidthCheckDone;
	}

	/**
	 * Returns true if this client is subscribed
	 * 
	 * @return subscribed
	 */
	public boolean isSubscribed() {
		return subscribed;
	}

	/**
	 * Setter for stream event dispatcher (useful for saving playing stream to
	 * file)
	 * 
	 * @param streamEventDispatcher event dispatcher
	 */
	public void setStreamEventDispatcher(IEventDispatcher streamEventDispatcher) {
		this.streamEventDispatcher = streamEventDispatcher;
	}

	private static class NetStream extends AbstractClientStream implements IEventDispatcher {
		private IEventDispatcher dispatcher;

		public NetStream(IEventDispatcher dispatcher) {
			this.dispatcher = dispatcher;
		}

		public void close() {
			log.debug("NetStream close");
		}

		public void start() {
			log.debug("NetStream start");
		}

		public void stop() {
			log.debug("NetStream stop");
		}

		public void dispatchEvent(IEvent event) {
			log.debug("NetStream dispatchEvent: {}", event);
			if (dispatcher != null) {
				dispatcher.dispatchEvent(event);
			}
		}
	}

	private class CreateStreamCallBack implements IPendingServiceCallback {
		private IPendingServiceCallback wrapped;

		public CreateStreamCallBack(IPendingServiceCallback wrapped) {
			log.debug("CreateStreamCallBack {}", wrapped.getClass().getName());
			this.wrapped = wrapped;
		}

		public void resultReceived(IPendingServiceCall call) {
			Integer streamIdInt = (Integer) call.getResult();
			log.debug("Stream id: {}", streamIdInt);
			//RTMPConnection conn = RTMPClientConnManager.getInstance().getConnection(clientId);
			log.debug("Connection: {}", conn);
			log.debug("CreateStreamCallBack resultReceived - stream id: {}", streamIdInt);
			if (conn != null && streamIdInt != null) {
				log.debug("Setting new net stream");
				NetStream stream = new NetStream(streamEventDispatcher);
				stream.setConnection(conn);
				stream.setStreamId(streamIdInt);
				conn.addClientStream(stream);
				NetStreamPrivateData streamData = new NetStreamPrivateData();
				streamData.outputStream = conn.createOutputStream(streamIdInt);
				streamData.connConsumer = new ConnectionConsumer(conn, streamData.outputStream.getVideo().getId(), streamData.outputStream.getAudio().getId(),
						streamData.outputStream.getData().getId());
				streamDataMap.put(streamIdInt, streamData);
				log.debug("streamDataMap: {}", streamDataMap);
			}
			wrapped.resultReceived(call);
		}
	}

	private static class NetStreamPrivateData {
		public volatile INetStreamEventHandler handler;

		public volatile OutputStream outputStream;

		public volatile ConnectionConsumer connConsumer;
	}
}
