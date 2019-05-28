package com.sms.compatibility.messages;

/**
 * Constants for the flex compatibility messages.
 */
public class Constants {

	/** Operation id of register command. */
	public static final int SUBSCRIBE_OPERATION = 0;

	public static final int UNSUBSCRIBE_OPERATION = 1;

	/** Operation id of poll command. */
	public static final int POLL_OPERATION = 2;

	/** Update given attributes from a data message. */
	public static final int DATA_OPERATION_UPDATE_ATTRIBUTES = 3;

	public static final int CLIENT_SYNC_OPERATION = 4;

	/** Operation id of ping commands. */
	public static final int CLIENT_PING_OPERATION = 5;

	/** Update destination based on nested DataMessage packet. */
	public static final int DATA_OPERATION_UPDATE = 7;

	public static final int CLUSTER_REQUEST_OPERATION = 7;

	/** Operation id of authentication commands. */
	public static final int LOGIN_OPERATION = 8;

	public static final int LOGOUT_OPERATION = 9;

	/** Set all attributes from a data message. */
	public static final int DATA_OPERATION_SET = 10;

	public static final int SUBSCRIPTION_INVALIDATE_OPERATION = 10;

	public static final int MULTI_SUBSCRIBE_OPERATION = 11;

	public static final int UNKNOWN_OPERATION = 10000;

}