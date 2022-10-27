package edu.usfca.cs272;

/**
 * A thread-safe version of {@link IndexedSet} using a read/write lock.
 *
 * @param <E> element type
 * @see IndexedSet
 * @see ReadWriteLock
 *
 * @author CS 272 Software Development (University of San Francisco)
 * @version Fall 2022
 */
public class ThreadSafeIndexedSet<E> extends IndexedSet<E> {
	/** The lock used to protect concurrent access to the underlying set. */
	private final ReadWriteLock lock;

	/**
	 * Initializes an unsorted thread-safe indexed set.
	 */
	public ThreadSafeIndexedSet() {
		this(false);
	}

	/**
	 * Initializes a thread-safe indexed set.
	 *
	 * @param sorted whether the set should be sorted
	 */
	public ThreadSafeIndexedSet(boolean sorted) {
		super(sorted);
		lock = new ReadWriteLock();
	}

	/**
	 * Returns the identity hashcode of the lock object. Not particularly useful.
	 *
	 * @return the identity hashcode of the lock object
	 */
	public int lockCode() {
		return System.identityHashCode(lock);
	}

	/*
	 * TODO Override methods as necessary to make this class thread-safe using the
	 * simple read write lock.
	 */
}
