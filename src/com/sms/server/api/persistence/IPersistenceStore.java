package com.sms.server.api.persistence;

import java.util.Collection;
import java.util.Set;

public interface IPersistenceStore {

	/**
	 * Persist given object.
	 *  
	 * @param obj Object to store
     * @return     <code>true</code> on success, <code>false</code> otherwise
	 */
	public boolean save(IPersistable obj);

	/**
	 * Load a persistent object with the given name.  The object must provide
	 * either a constructor that takes an input stream as only parameter or an
	 * empty constructor so it can be loaded from the persistence store.
	 * 
	 * @param name the name of the object to load
	 * @return The loaded object or <code>null</code> if no such object was
	 *         found
	 */
	public IPersistable load(String name);

	/**
	 * Load state of an already instantiated persistent object.
	 * 
	 * @param obj the object to initializ
	 * @return true if the object was initialized, false otherwise
	 */
	public boolean load(IPersistable obj);

	/**
	 * Delete the passed persistent object.
	 *  
	 * @param obj the object to delete
     * @return        <code>true</code> if object was persisted and thus can be removed, <code>false</code> otherwise
	 */
	public boolean remove(IPersistable obj);

	/**
	 * Delete the persistent object with the given name.
	 *  
	 * @param name the name of the object to delete
     * @return        <code>true</code> if object was persisted and thus can be removed, <code>false</code> otherwise
	 */
	public boolean remove(String name);

	/**
	 * Return iterator over the names of all already loaded objects in the
	 * storage.
	 * 
	 * @return Set of all object names
	 */
	public Set<String> getObjectNames();

	/**
	 * Return iterator over the already loaded objects in the storage.
	 * 
	 * @return Set of all objects
	 */
	public Collection<IPersistable> getObjects();

	/**
	 * Notify store that it's being closed. This allows the store to write
	 * any pending objects to disk.
	 */
	public void notifyClose();
}
