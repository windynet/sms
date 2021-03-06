package com.sms.io.flv.impl;


import org.apache.mina.core.buffer.IoBuffer;

import com.sms.io.ITag;

/**
 * A Tag represents the contents or payload of a FLV file.
 * 
 * @see <a href="http://osflash.org/flv#flv_tag">OSFlash (external)</a>
 */
public class Tag implements ITag {
	/**
	 * Tag type
	 */
	private byte type;

	/**
	 * Tag data type
	 */
	private byte dataType;

	/**
	 * Timestamp
	 */
	private int timestamp;

	/**
	 * Tag body size
	 */
	private int bodySize;

	/**
	 * Tag body as byte buffer
	 */
	private IoBuffer body;

	/**
	 * Previous tag size
	 */
	private int previuosTagSize;

	/**
	 * Bit flags
	 */
	private byte bitflags;

	/**
	 * TagImpl Constructor
	 * 
	 * @param dataType              Tag data type
	 * @param timestamp             Timestamp
	 * @param bodySize              Tag body size
	 * @param body                  Tag body
	 * @param previousTagSize       Previous tag size information
	 */
	public Tag(byte dataType, int timestamp, int bodySize, IoBuffer body, int previousTagSize) {
		this.dataType = dataType;
		this.timestamp = timestamp;
		this.bodySize = bodySize;
		this.body = body;
		this.previuosTagSize = previousTagSize;
	}

	/** Constructs a new Tag. */
	public Tag() {

	}

	/**
	 * Getter for bit flags
	 *
	 * @return Value for bit flags
	 */
	public byte getBitflags() {
		return bitflags;
	}

	/**
	 * Setter for bit flags
	 *
	 * @param bitflags  Bit flags
	 */
	public void setBitflags(byte bitflags) {
		this.bitflags = bitflags;
	}

	/**
	 * Getter for previous tag size
	 *
	 * @return Value for previous tag size
	 */
	public int getPreviuosTagSize() {
		return previuosTagSize;
	}

	/**
	 * Setter for previous tag size
	 *
	 * @param previuosTagSize Value to set for previous tag size
	 */
	public void setPreviuosTagSize(int previuosTagSize) {
		this.previuosTagSize = previuosTagSize;
	}

	/** {@inheritDoc}
	 */
	public IoBuffer getData() {
		return null;
	}

	/**
	 * Return the body IoBuffer
	 * 
	 * @return         Tag body
	 */
	public IoBuffer getBody() {
		return body;
	}

	/**
	 * Return the size of the body
	 * 
	 * @return                Tag body size
	 */
	public int getBodySize() {
		return bodySize;
	}

	/**
	 * Get the data type
	 * 
	 * @return               Tag data type
	 */
	public byte getDataType() {
		return dataType;
	}

	/**
	 * Return the timestamp
	 * 
	 * @return                Tag timestamp
	 */
	public int getTimestamp() {
		return timestamp;
	}

	/**
	 * Return previous tag size
	 * 
	 * @return                Previous tag size
	 */
	public int getPreviousTagSize() {
		return previuosTagSize;
	}

	/**
	 * Prints out the contents of the tag
	 * 
	 * @return  Tag contents
	 */
	@Override
	public String toString() {
		String ret = "Data Type\t=" + dataType + "\n";
		ret += "Prev. Tag Size\t=" + previuosTagSize + "\n";
		ret += "Body size\t=" + bodySize + "\n";
		ret += "timestamp\t=" + timestamp + "\n";
		ret += "Body Data\t=" + body + "\n";
		return ret;
	}

	/**
	 * Getter for tag type
	 *
	 * @return  Tag type
	 */
	public byte getType() {
		return type;
	}

	/**
	 * Setter for tag type
	 *
	 * @param type Tag type
	 */
	public void setType(byte type) {
		this.type = type;
	}

	/** {@inheritDoc} */
	public void setBody(IoBuffer body) {
		this.body = body;
	}

	/** {@inheritDoc} */
	public void setBodySize(int bodySize) {
		this.bodySize = bodySize;
	}

	/** {@inheritDoc} */
	public void setDataType(byte dataType) {
		this.dataType = dataType;
	}

	/** {@inheritDoc} */
	public void setTimestamp(int timestamp) {
		this.timestamp = timestamp;
	}

	/**
	 * Setter for tag data. Empty method.
	 */
	public void setData() {
	}

	/** {@inheritDoc} */
	public void setPreviousTagSize(int size) {
		this.previuosTagSize = size;
	}

}
