package edu.usfca.cs272;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentest4j.AssertionFailedError;

/**
 * Tests the {@link MultiReaderLock} and {@link ThreadSafeIndexedSet} classes.
 *
 * @author CS 272 Software Development (University of San Francisco)
 * @version Spring 2023
 */
@TestClassOrder(ClassOrderer.ClassName.class)
public class MultiReaderLockTest {
	/*
	 * TODO: Unless you are using logging, it will be difficult to determine why
	 * your code is failing the tests!
	 */

	/**
	 * Tests the {@link MultiReaderLock#readLock()} lock.
	 */
	@Nested
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	public class A_ReadLockTests {
		/**
		 * Does not run tests unless the read lock is implemented.
		 */
		@BeforeAll
		public static void readLockImplemented() {
			var lock = newLock();
			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT);
			Assertions.assertTimeoutPreemptively(timeout, () -> {
				try {
					lock.readLock().lock();
					lock.readLock().unlock();
				}
				catch (UnsupportedOperationException e) {
					Assertions.fail("Implement the read lock to enable these tests.");
				}
			});
		}

		/**
		 * Tests multiple readers locking concurrently.
		 *
		 * @param readers the number of readers
		 * @throws Throwable if there is an exception
		 */
		@Order(1)
		@ParameterizedTest(name = "{0} readers")
		@MethodSource("edu.usfca.cs272.MultiReaderLockTest#numThreads")
		public void testReadersConcurrent(int readers) throws Throwable {
			List<String> expected = new ArrayList<>();
			List<String> actual = new ArrayList<>();

			var lock = newLock();
			ArrayList<Executable> actions = new ArrayList<>();
			CountDownLatch nullLatch = new CountDownLatch(0);

			// add multiple readers
			expected.addAll(Collections.nCopies(readers, "Locked Read"));
			expected.addAll(Collections.nCopies(readers, "Unlocking Read"));

			for (int i = 0; i < readers; i++) {
				actions.add(new LockAction(actual, lock.readLock(), nullLatch, nullLatch));
			}

			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT * actions.size());
			testActions(timeout, actions, expected, actual);
		}

		/**
		 * Tests multiple readers locking sequentially.
		 *
		 * @param readers the number of readers
		 * @throws Throwable if there is an exception
		 */
		@Order(2)
		@ParameterizedTest(name = "{0} readers")
		@MethodSource("edu.usfca.cs272.MultiReaderLockTest#numThreads")
		public void testReadersSequential(int readers) throws Throwable {
			List<String> expected = new ArrayList<>();
			List<String> actual = new ArrayList<>();

			var lock = newLock();
			ArrayList<Executable> actions = new ArrayList<>();
			Semaphore semaphore = new Semaphore(1);

			// add multiple readers
			for (int i = 0; i < readers; i++) {
				expected.add("Locked Read");
				expected.add("Unlocking Read");

				actions.add(new Executable() {
					@Override
					public void execute() throws Throwable {
						semaphore.acquire();

						lock.readLock().lock();
						output(actual, "Locked Read");

						Thread.sleep(WORKER_SLEEP);

						output(actual, "Unlocking Read");
						lock.readLock().unlock();

						semaphore.release();
					}
				});
			}

			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT * actions.size());
			testActions(timeout, actions, expected, actual);
		}

		/**
		 * Tests that tests pass consistently.
		 * @throws Throwable if there is an exception
		 */
		@Order(3)
		@RepeatedTest(3)
		public void testReadersConsistency() throws Throwable {
			testReadersConcurrent(3);
		}
	}

	/**
	 * Tests the {@link MultiReaderLock#readLock()} lock.
	 */
	@Nested
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	public class B_WriteLockTests {
		/**
		 * Does not run tests unless the write lock is implemented.
		 */
		@BeforeAll
		public static void writeLockImplemented() {
			var lock = newLock();
			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT);
			Assertions.assertTimeoutPreemptively(timeout, () -> {
				try {
					lock.writeLock().lock();
					lock.writeLock().unlock();
				}
				catch (UnsupportedOperationException e) {
					Assertions.fail("Implement the write lock to enable these tests.");
				}
			});
		}

		/**
		 * Tests multiple writers locking concurrently.
		 *
		 * @param writers the number of writers
		 * @throws Throwable if there is an exception
		 */
		@Order(1)
		@ParameterizedTest(name = "{0} writers")
		@MethodSource("edu.usfca.cs272.MultiReaderLockTest#numThreads")
		public void testWritersConcurrent(int writers) throws Throwable {
			List<String> expected = new ArrayList<>();
			List<String> actual = new ArrayList<>();

			var lock = newLock();
			ArrayList<Executable> actions = new ArrayList<>();
			CountDownLatch nullLatch = new CountDownLatch(0);

			// add multiple writers
			for (int i = 0; i < writers; i++) {
				expected.add("Locked Write");
				expected.add("Unlocking Write");

				actions.add(new LockAction(actual, lock.writeLock(), nullLatch, nullLatch));
			}

			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT * actions.size());
			testActions(timeout, actions, expected, actual);
		}

		/**
		 * Tests multiple writers locking sequentially.
		 *
		 * @param writers the number of readers
		 * @throws Throwable if there is an exception
		 */
		@Order(2)
		@ParameterizedTest(name = "{0} writers")
		@MethodSource("edu.usfca.cs272.MultiReaderLockTest#numThreads")
		public void testWritersSequential(int writers) throws Throwable {
			List<String> expected = new ArrayList<>();
			List<String> actual = new ArrayList<>();

			var lock = newLock();
			ArrayList<Executable> actions = new ArrayList<>();
			Semaphore semaphore = new Semaphore(1);

			// add multiple readers
			for (int i = 0; i < writers; i++) {
				expected.add("Locked Write");
				expected.add("Unlocking Write");

				actions.add(new Executable() {
					@Override
					public void execute() throws Throwable {
						semaphore.acquire();

						lock.writeLock().lock();
						output(actual, "Locked Write");

						Thread.sleep(WORKER_SLEEP);

						output(actual, "Unlocking Write");
						lock.writeLock().unlock();

						semaphore.release();
					}
				});
			}

			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT * actions.size());
			testActions(timeout, actions, expected, actual);
		}

		/**
		 * Tests that tests pass consistently.
		 * @throws Throwable if there is an exception
		 */
		@Order(3)
		@RepeatedTest(3)
		public void testWritersConsistency() throws Throwable {
			testWritersConcurrent(3);
		}
	}

	/**
	 * Tests the {@link MultiReaderLock} read and write locks (and active writer).
	 */
	@Nested
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	public class C_MixedLockTests {
		/**
		 * Does not run tests unless the locks are implemented.
		 */
		@BeforeAll
		public static void bothLocksImplemented() {
			Assertions.assertAll(
					A_ReadLockTests::readLockImplemented,
					B_WriteLockTests::writeLockImplemented);
		}

		/**
		 * Tests multiple readers followed by one writer.
		 *
		 * @param readers the number of readers
		 * @throws Throwable if there is an exception
		 */
		@Order(1)
		@ParameterizedTest(name = "{0} readers 1 writer")
		@MethodSource("edu.usfca.cs272.MultiReaderLockTest#numThreads")
		public void testManyReadersOneWriter(int readers) throws Throwable {
			List<String> expected = new ArrayList<>();
			List<String> actual = new ArrayList<>();

			var lock = newLock();
			ArrayList<Executable> actions = new ArrayList<>();
			CountDownLatch readFirst = new CountDownLatch(1);
			CountDownLatch nullLatch = new CountDownLatch(0);

			// add multiple readers
			expected.addAll(Collections.nCopies(readers, "Locked Read"));
			expected.addAll(Collections.nCopies(readers, "Unlocking Read"));

			for (int i = 0; i < readers; i++) {
				actions.add(new LockAction(actual, lock.readLock(), nullLatch, readFirst));
			}

			// add one writer
			expected.add("Locked Write");
			expected.add("Unlocking Write");
			actions.add(new LockAction(actual, lock.writeLock(), readFirst, nullLatch));

			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT * actions.size());
			testActions(timeout, actions, expected, actual);
		}

		/**
		 * Tests one writer followed by multiple readers.
		 *
		 * @param readers the number of readers
		 * @throws Throwable if there is an exception
		 */
		@Order(2)
		@ParameterizedTest(name = "1 writer {0} readers")
		@MethodSource("edu.usfca.cs272.MultiReaderLockTest#numThreads")
		public void testOneWriterManyReader(int readers) throws Throwable {
			List<String> expected = new ArrayList<>();
			List<String> actual = new ArrayList<>();

			var lock = newLock();
			ArrayList<Executable> actions = new ArrayList<>();
			CountDownLatch writeFirst = new CountDownLatch(1);
			CountDownLatch nullLatch = new CountDownLatch(0);

			// add one writer
			expected.add("Locked Write");
			expected.add("Unlocking Write");
			actions.add(new LockAction(actual, lock.writeLock(), nullLatch, writeFirst));

			// add multiple readers
			expected.addAll(Collections.nCopies(readers, "Locked Read"));
			expected.addAll(Collections.nCopies(readers, "Unlocking Read"));

			for (int i = 0; i < readers; i++) {
				actions.add(new LockAction(actual, lock.readLock(), writeFirst, nullLatch));
			}

			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT * actions.size());
			testActions(timeout, actions, expected, actual);
		}

		/**
		 * Tests multiple writers followed by one reader.
		 *
		 * @param writers the number of writers
		 * @throws Throwable if there is an exception
		 */
		@Order(3)
		@ParameterizedTest(name = "{0} writers 1 reader")
		@MethodSource("edu.usfca.cs272.MultiReaderLockTest#numThreads")
		public void testManyWritersOneReader(int writers) throws Throwable {
			List<String> expected = new ArrayList<>();
			List<String> actual = new ArrayList<>();

			var lock = newLock();
			ArrayList<Executable> actions = new ArrayList<>();
			CountDownLatch writeFirst = new CountDownLatch(writers);
			CountDownLatch nullLatch = new CountDownLatch(0);

			// add multiple writers
			for (int i = 0; i < writers; i++) {
				expected.add("Locked Write");
				expected.add("Unlocking Write");
				actions.add(new LockAction(actual, lock.writeLock(), nullLatch, writeFirst));
			}

			// add one reader
			expected.add("Locked Read");
			expected.add("Unlocking Read");
			actions.add(new LockAction(actual, lock.readLock(), writeFirst, nullLatch));

			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT * actions.size());
			testActions(timeout, actions, expected, actual);
		}

		/**
		 * Tests one reader followed by multiple writers.
		 *
		 * @param writers the number of writers
		 * @throws Throwable if there is an exception
		 */
		@Order(4)
		@ParameterizedTest(name = "1 reader {0} writers")
		@MethodSource("edu.usfca.cs272.MultiReaderLockTest#numThreads")
		public void testOneReaderManyWriters(int writers) throws Throwable {
			List<String> expected = new ArrayList<>();
			List<String> actual = new ArrayList<>();

			var lock = newLock();
			ArrayList<Executable> actions = new ArrayList<>();
			CountDownLatch readFirst = new CountDownLatch(1);
			CountDownLatch nullLatch = new CountDownLatch(0);

			// add one reader
			expected.add("Locked Read");
			expected.add("Unlocking Read");
			actions.add(new LockAction(actual, lock.readLock(), nullLatch, readFirst));

			// add multiple writers
			for (int i = 0; i < writers; i++) {
				expected.add("Locked Write");
				expected.add("Unlocking Write");
				actions.add(new LockAction(actual, lock.writeLock(), readFirst, nullLatch));
			}

			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT * actions.size());
			testActions(timeout, actions, expected, actual);
		}

		/**
		 * Tests that tests pass consistently.
		 * @throws Throwable if there is an exception
		 */
		@Order(5)
		@RepeatedTest(3)
		public void testManyReadersConsistency() throws Throwable {
			testManyReadersOneWriter(3);
		}

		/**
		 * Tests that tests pass consistently.
		 * @throws Throwable if there is an exception
		 */
		@Order(6)
		@RepeatedTest(3)
		public void testManyWritersConsistency() throws Throwable {
			testOneReaderManyWriters(3);
		}
	}

	/**
	 * Tests the {@link MultiReaderLock} lock tracking of the active writer.
	 */
	@Nested
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	public class D_ActiveWriterTests {
		/**
		 * Does not run tests unless the locks are implemented.
		 */
		@BeforeAll
		public static void bothLocksImplemented() {
			Assertions.assertAll(
					A_ReadLockTests::readLockImplemented,
					B_WriteLockTests::writeLockImplemented);
		}

		/**
		 * Tests that a single thread can acquire a write lock then a read lock without
		 * releasing first the write lock.
		 * @throws Throwable if an exception occurs
		 */
		@Order(1)
		@RepeatedTest(3)
		public void testWriteThenRead() throws Throwable {
			List<String> expected = List.of(
					"Locked Write", "Locked Read", "Locked Read",
					"Unlocking Read", "Unlocking Read", "Unlocking Write");
			List<String> actual = new ArrayList<>();

			var lock = newLock();
			Executable action = () -> {
				lock.writeLock().lock();
				output(actual, "Locked Write");

				lock.readLock().lock();
				output(actual, "Locked Read");

				lock.readLock().lock();
				output(actual, "Locked Read");

				Thread.sleep(WORKER_SLEEP);

				output(actual, "Unlocking Read");
				lock.readLock().unlock();

				output(actual, "Unlocking Read");
				lock.readLock().unlock();

				output(actual, "Unlocking Write");
				lock.writeLock().unlock();
			};

			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT);
			testActions(timeout, List.of(action), expected, actual);
		}

		/**
		 * Tests that a single thread can acquire a write lock then a read lock and
		 * additional write locks without releasing first the write lock.
		 * @throws Throwable if an exception occurs
		 */
		@Order(2)
		@RepeatedTest(3)
		public void testWriteThenMixed() throws Throwable {
			List<String> expected = List.of(
					"Locked Write", "Locked Read", "Locked Write",
					"Unlocking Write", "Unlocking Read", "Unlocking Write");
			List<String> actual = new ArrayList<>();

			var lock = newLock();
			Executable action = () -> {
				lock.writeLock().lock();
				output(actual, "Locked Write");

				lock.readLock().lock();
				output(actual, "Locked Read");

				lock.writeLock().lock();
				output(actual, "Locked Write");

				Thread.sleep(WORKER_SLEEP);

				output(actual, "Unlocking Write");
				lock.writeLock().unlock();

				output(actual, "Unlocking Read");
				lock.readLock().unlock();

				output(actual, "Unlocking Write");
				lock.writeLock().unlock();
			};

			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT);
			testActions(timeout, List.of(action), expected, actual);
		}

		/**
		 * Tests that a single thread can acquire a write lock then a read lock without
		 * releasing first the write lock. Often fails when the active writer is not
		 * being unset properly.
		 *
		 * @throws Throwable if an exception occurs
		 */
		@Order(3)
		@RepeatedTest(3)
		public void testOtherWriter() throws Throwable {
			List<String> expected = List.of(
					"Locked Write", "Unlocking Write",
					"Locked Read", "Unlocking Read",
					"Locked Write", "Unlocking Write");
			List<String> actual = new ArrayList<>();

			var lock = newLock();
			CountDownLatch waitWrite = new CountDownLatch(1);
			CountDownLatch waitRead = new CountDownLatch(1);

			Executable writer = () -> {
				lock.writeLock().lock();
				output(actual, "Locked Write");

				output(actual, "Unlocking Write");
				lock.writeLock().unlock();

				waitWrite.countDown();
				waitRead.await();

				lock.writeLock().lock();
				output(actual, "Locked Write");

				output(actual, "Unlocking Write");
				lock.writeLock().unlock();
			};

			Executable reader = () -> {
				waitWrite.await();

				lock.readLock().lock();
				output(actual, "Locked Read");

				waitRead.countDown();
				Thread.sleep(WORKER_SLEEP);

				output(actual, "Unlocking Read");
				lock.readLock().unlock();
			};

			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT * 2);
			testActions(timeout, List.of(writer, reader), expected, actual);
		}
	}

	/**
	 * Tests the {@link MultiReaderLock} read and write lock exceptions.
	 */
	@Nested
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	public class E_LockExceptionTests {
		/**
		 * Does not run tests unless the locks are implemented.
		 */
		@BeforeAll
		public static void bothLocksImplemented() {
			Assertions.assertAll(
					A_ReadLockTests::readLockImplemented,
					B_WriteLockTests::writeLockImplemented);
		}

		/**
		 * Tests an {@link IllegalStateException} is thrown as expected.
		 */
		@Order(1)
		@Test
		public void testReadOnlyUnlock() {
			var lock = newLock();
			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT);

			List<Executable> actions = List.of(() -> {
				lock.readLock().unlock();
			});

			Executable test = () -> timeoutActions(timeout, actions);
			Assertions.assertThrows(IllegalStateException.class, test);
		}

		/**
		 * Tests an {@link IllegalStateException} is thrown as expected.
		 */
		@Order(2)
		@Test
		public void testReadDoubleUnlock() {
			var lock = newLock();
			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT);

			List<Executable> actions = List.of(() -> {
				lock.readLock().lock();
				lock.readLock().unlock();
				lock.readLock().unlock();
			});

			Executable test = () -> timeoutActions(timeout, actions);
			Assertions.assertThrows(IllegalStateException.class, test);
		}

		/**
		 * Tests an {@link IllegalStateException} is thrown as expected.
		 */
		@Order(3)
		@Test
		public void testWriteOnlyUnlock() {
			var lock = newLock();
			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT);

			List<Executable> actions = List.of(() -> {
				lock.writeLock().unlock();
			});

			Executable test = () -> timeoutActions(timeout, actions);
			Assertions.assertThrows(IllegalStateException.class, test);
		}

		/**
		 * Tests an {@link IllegalStateException} is thrown as expected.
		 */
		@Order(4)
		@Test
		public void testWriteDoubleUnlock() {
			var lock = newLock();
			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT);

			List<Executable> actions = List.of(() -> {
				lock.writeLock().lock();
				lock.writeLock().unlock();
				lock.writeLock().unlock();
			});

			Executable test = () -> timeoutActions(timeout, actions);
			Assertions.assertThrows(IllegalStateException.class, test);
		}

		/**
		 * Tests an {@link IllegalStateException} is thrown as expected.
		 */
		@Order(5)
		@Test
		public void testReadLockWriteUnlock() {
			var lock = newLock();
			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT);

			List<Executable> actions = List.of(() -> {
				lock.readLock().lock();
				lock.writeLock().unlock();
			});

			Executable test = () -> timeoutActions(timeout, actions);
			Assertions.assertThrows(IllegalStateException.class, test);
		}

		/**
		 * Tests an {@link IllegalStateException} is thrown as expected.
		 */
		@Order(6)
		@Test
		public void testReadUnlockWriteLock() {
			var lock = newLock();
			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT);

			List<Executable> actions = List.of(() -> {
				lock.writeLock().lock();
				lock.readLock().unlock();
			});

			Executable test = () -> timeoutActions(timeout, actions);
			Assertions.assertThrows(IllegalStateException.class, test);
		}

		/**
		 * Tests an {@link ConcurrentModificationException} is thrown as expected. More
		 * complicated since runtime exceptions of threads are not caught or handled
		 * normally.
		 */
		@Order(7)
		@Test
		public void testWrongWriter() {
			var lock = newLock();
			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT);
			CountDownLatch lockFirst = new CountDownLatch(1);

			Executable lockAction = () -> {
				lock.writeLock().lock();
				lockFirst.countDown();
			};

			Executable unlockAction = () -> {
				lockFirst.await();
				lock.writeLock().unlock();
			};

			List<Executable> actions = List.of(lockAction, unlockAction);
			Executable test = () -> timeoutActions(timeout, actions);
			Assertions.assertThrows(ConcurrentModificationException.class, test);
		}

		/**
		 * Tests an {@link ConcurrentModificationException} is thrown as expected. More
		 * complicated since runtime exceptions of threads are not caught or handled
		 * normally.
		 *
		 * Catches cases when the active writer is not properly unset.
		 */
		@Order(8)
		@Test
		public void testWrongWriterDouble() {
			var lock = newLock();
			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT);
			CountDownLatch lockFirst = new CountDownLatch(1);

			Executable lockAction = () -> {
				lock.writeLock().lock();
				lock.writeLock().lock();
				lock.writeLock().unlock();
				lockFirst.countDown();
			};

			Executable unlockAction = () -> {
				lockFirst.await();
				lock.writeLock().unlock();
			};

			List<Executable> actions = List.of(lockAction, unlockAction);
			Executable test = () -> timeoutActions(timeout, actions);
			Assertions.assertThrows(ConcurrentModificationException.class, test);
		}
	}

	/**
	 * Tests the {@link ThreadSafeIndexedSet} use of locks.
	 */
	@Nested
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	public class F_ThreadSafeLockTests {
		/** Serial indexed set placeholder. */
		public IndexedSet<Integer> serialSet;

		/** Thread-safe indexed set placeholder. */
		public ThreadSafeIndexedSet<Integer> threadSet;

		/**
		 * Does not run tests unless the locks are implemented.
		 */
		@BeforeAll
		public static void bothLocksImplemented() {
			Assertions.assertAll(
					A_ReadLockTests::readLockImplemented,
					B_WriteLockTests::writeLockImplemented);
		}

		/**
		 * Setup the placeholder indexed sets.
		 */
		@BeforeEach
		public void setup() {
			serialSet = new IndexedSet<>(true);
			threadSet = new ThreadSafeIndexedSet<>(true);
		}

		/**
		 * Tests that {@link ThreadSafeIndexedSet} is not using {@code synchronized}
		 * methods. This test will not detect if {@code synchronized} is used WITHIN a
		 * method, but it should not!
		 */
		@Order(1)
		@Test
		public void testSynchronized() {
			Method[] threadMethods = ThreadSafeIndexedSet.class.getMethods();

			Set<String> syncMethods = Arrays.stream(threadMethods)
					.filter(method -> Modifier.isSynchronized(method.getModifiers()))
					.map(method -> methodName(method))
					.collect(Collectors.toSet());

			String debug = "%nThese methods should NOT be synchronized (use locks instead): %s%n";
			Assertions.assertTrue(syncMethods.isEmpty(), debug.formatted(syncMethods.toString()));
		}

		/**
		 * Tests that all of the required methods are overridden. This test will not
		 * detect whether the methods were overridden correctly however!
		 */
		@Order(2)
		@Test
		public void testOverridden() {
			Method[] singleMethods = IndexedSet.class.getDeclaredMethods();
			Method[] threadMethods = ThreadSafeIndexedSet.class.getDeclaredMethods();

			Set<String> expected = Arrays.stream(singleMethods)
					.map(method -> methodName(method))
					.filter(method -> !(method.endsWith("copy(boolean)") || method.endsWith("checkEmpty()")))
					.collect(Collectors.toSet());

			Set<String> actual = Arrays.stream(threadMethods)
					.map(method -> methodName(method))
					.collect(Collectors.toSet());

			// remove any method from actual that was in expected
			// anything leftover in expected was not overridden
			expected.removeAll(actual);

			String debug = "%nThe following methods were not properly overridden: %s%n";
			Assertions.assertTrue(expected.isEmpty(), () -> debug.formatted(expected.toString()));
		}

		/**
		 * Tests that all of the required methods are overridden. This test will not
		 * detect whether the methods were overridden correctly however!
		 */
		@Order(3)
		@Test
		public void testNotOverridden() {
			Method[] singleMethods = IndexedSet.class.getDeclaredMethods();
			Method[] threadMethods = ThreadSafeIndexedSet.class.getDeclaredMethods();

			Set<String> expected = Arrays.stream(singleMethods)
					.map(method -> methodName(method))
					.filter(method -> method.endsWith("copy(boolean)") || method.endsWith("checkEmpty()"))
					.collect(Collectors.toSet());

			Set<String> actual = Arrays.stream(threadMethods)
					.map(method -> methodName(method))
					.filter(method -> expected.contains(method))
					.collect(Collectors.toSet());

			String debug = "%nThe following methods should not be overridden: %s%n";
			Assertions.assertTrue(actual.isEmpty(), () -> debug.formatted(actual.toString()));
		}

		/**
		 * Does not let this test group pass if locks are not implemented.
		 */
		@Order(4)
		@Test
		public void testLocksImplemented() {
			Assertions.assertAll(
					A_ReadLockTests::readLockImplemented,
					B_WriteLockTests::writeLockImplemented);
		}

		/**
		 * Does not let this test group pass if the built-in lock or the
		 * synchronized keyword is used for synchronization.
		 * @throws IOException if unable to read source code
		 */
		@Order(5)
		@Test
		public void testApproach() throws IOException {
			Path code = Path.of("src", "main", "java", "edu", "usfca", "cs272");
			String name = ThreadSafeIndexedSet.class.getSimpleName() + ".java";
			String text = Files.readString(code.resolve(name));

			String importRegex = "\\bimport\\s+java.util.concurrent.locks\\b";
			String lockRegex = "\\bReentrantReadWriteLock\\b";
			String synchRegex = "\\bsynchronized\\b";

			Pattern regex = Pattern.compile(String.join("|", importRegex, lockRegex, synchRegex));
			Matcher matcher = regex.matcher(text);

			String debug = "You may not use the synchronized keyword or built-in lock objects!";
			Assertions.assertFalse(matcher.find(), debug);
		}

		/**
		 * Tests a thread-safe indexed set method.
		 * @param threads the number of threads to use
		 * @throws Throwable if any exceptions occur
		 */
		@Order(6)
		@ParameterizedTest
		@MethodSource("edu.usfca.cs272.MultiReaderLockTest#numThreads")
		public void testAdd(int threads) throws Throwable {
			int chunk = 100;
			int last = chunk * threads;

			IntStream.range(0, last).boxed().forEach(serialSet::add);
			ArrayList<Executable> actions = new ArrayList<>();

			for (int i = 0; i < threads; i++) {
				int start = i * chunk;
				int end = start + chunk;

				actions.add(() -> {
					IntStream.range(start, end).boxed().forEach(threadSet::add);
				});
			}

			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT * actions.size());
			Assertions.assertAll(
					() -> timeoutActions(timeout, actions),
					() -> Assertions.assertEquals(serialSet.size(), threadSet.size()),
					() -> Assertions.assertEquals(serialSet.toString(), threadSet.toString())
			);
		}

		/**
		 * Tests a thread-safe indexed set method.
		 * @param threads the number of threads to use
		 * @throws Throwable if any exceptions occur
		 */
		@Order(7)
		@ParameterizedTest
		@MethodSource("edu.usfca.cs272.MultiReaderLockTest#numThreads")
		public void testAddAll(int threads) throws Throwable {
			int chunk = 1000;
			int last = chunk * threads;

			List<Integer> nums = IntStream.range(0, last).boxed().toList();
			serialSet.addAll(nums);

			ArrayList<Executable> actions = new ArrayList<>();

			for (int i = 0; i < threads; i++) {
				int start = i * chunk;
				int end = start + chunk;

				actions.add(() -> {
					List<Integer> subset = IntStream.range(start, end).boxed().toList();
					threadSet.addAll(subset);
				});
			}

			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT * actions.size());
			Assertions.assertAll(
					() -> timeoutActions(timeout, actions),
					() -> Assertions.assertEquals(serialSet.size(), threadSet.size()),
					() -> Assertions.assertEquals(serialSet.toString(), threadSet.toString())
			);
		}

		/**
		 * Tests a thread-safe indexed set method.
		 * @param threads the number of threads to use
		 * @throws Throwable if any exceptions occur
		 */
		@Order(8)
		@ParameterizedTest
		@MethodSource("edu.usfca.cs272.MultiReaderLockTest#numThreads")
		public void testCopyAdd(int threads) throws Throwable {
			int chunk = 100;
			int last = chunk * threads;

			List<Integer> nums = IntStream.range(0, last).boxed().toList();
			threadSet.addAll(nums);

			ArrayList<Executable> actions = new ArrayList<>();

			for (int i = 0; i < threads; i++) {
				actions.add(() -> {
					IndexedSet<Integer> copied = threadSet.copy(false);
					threadSet.addAll(copied);
					Assertions.assertTrue(copied.size() == last);
				});
			}

			Duration timeout = Duration.ofMillis(WORKER_TIMEOUT * actions.size());
			timeoutActions(timeout, actions);
		}
	}

	/** Amount of time workers will sleep. */
	public static final long WORKER_SLEEP = 250;

	/** Amount of buffer to provide per worker. */
	public static final long WORKER_TIMEOUT = Math.round(WORKER_SLEEP * 2);

	/** Used to provide debugging output if enabled. */
	public static final Logger log = LogManager.getLogger();

	/** Returns a new lock. Used to make it easy to swap lock types.
	 *
	 * @return a new lock object
	 */
	public static MultiReaderLock newLock() {
		return new MultiReaderLock();
	}

	/**
	 * Saves the message to the provided {@link StringBuffer} and logs the message
	 * as well.
	 *
	 * @param actual the buffer to save the message
	 * @param message the message to save and debug
	 */
	public static void output(List<String> actual, String message) {
		log.debug(message);

		synchronized (actual) {
			actual.add(message);
		}
	}

	/**
	 * Returns a method name and its parameters without the enclosing class.
	 *
	 * @param method the method to get the name
	 * @return the name and parameters without the enclosing class
	 */
	public static String methodName(Method method) {
		String parameters = Arrays
				.stream(method.getParameters())
				.map(p -> p.getType().getSimpleName())
				.collect(Collectors.joining(", "));

		return String.format("%s(%s)", method.getName(), parameters);
	}

	/**
	 * The number of threads to test for parameterized tests
	 * @return the number of threads to use
	 */
	public static Stream<Integer> numThreads() {
		return Stream.of(1, 2, 3, 5);
	}

	/**
	 * Tests the actions produce the expected output. Exceptions will cause the test
	 * to fail, including timeout exceptions and uncaught runtime exceptions
	 * occurring within a thread's run method.
	 *
	 * @param timeout the timeout
	 * @param actions the actions
	 * @param expected the expected output
	 * @param actual the actual output
	 * @throws Throwable if any exceptions occur
	 */
	public static void testActions(Duration timeout,
			List<Executable> actions, List<String> expected,
			List<String> actual) throws Throwable {
		Assertions.assertAll(
				() -> timeoutActions(timeout, actions),
				() -> Assertions.assertLinesMatch(expected, actual)
		);
	}

	/**
	 * Tests the actions produce the expected output. Exceptions will cause the test
	 * to fail, including timeout exceptions and uncaught runtime exceptions
	 * occurring within a thread's run method.
	 *
	 * @param timeout the timeout
	 * @param actions the actions
	 * @param expected the expected output
	 * @param actual the actual output
	 * @throws Throwable if any exceptions occur
	 */
	public static void testActions(Duration timeout,
			List<Executable> actions, Object expected,
			Object actual) throws Throwable {
		Assertions.assertAll(
				() -> timeoutActions(timeout, actions),
				() -> Assertions.assertEquals(expected.toString(), actual.toString())
		);
	}

	/**
	 * Tests the action runs within the timeout and without any exceptions.
	 *
	 * @param timeout the timeout
	 * @param actions the actions
	 * @throws Throwable if any exceptions occur
	 */
	public static void timeoutActions(Duration timeout, List<Executable> actions) throws Throwable {
		ExceptionCollector handler = new ExceptionCollector();

		List<Worker> workers = actions.stream()
				.map(action -> new Worker(action, handler))
				.toList();

		try {
			Assertions.assertTimeoutPreemptively(timeout, () -> {
				for (Worker worker : workers) {
					worker.start();
				}
				log.debug("started {} workers...", workers.size());

				for (Thread thread : workers) {
					thread.join();
				}

				log.debug("joined {} workers...", workers.size());
			});
		}
		catch (Throwable thrown) {
			// throw handler exceptions first if exist
			handler.throwExceptions();

			// if no handler exceptions throw this one
			throw thrown;
		}

		// make sure weren't any uncaught exceptions
		handler.throwExceptions();
	}

	/** Collects uncaught exceptions from threads. */
	public static class ExceptionCollector
			implements UncaughtExceptionHandler {
		/** List of uncaught exceptions. */
		public final List<Throwable> uncaught;

		/** Initializes this class. */
		public ExceptionCollector() {
			uncaught = Collections.synchronizedList(new ArrayList<>());
		}

		@Override
		public void uncaughtException(Thread thread, Throwable exception) {
			if (exception.getClass().isAssignableFrom(AssertionFailedError.class)) {
				Throwable cause = exception.getCause();
				if (cause != null) {
					exception = cause;
				}
			}

			log.debug(thread.getName() + " threw an exception", exception);
			uncaught.add(exception);
		}

		@Override
		public String toString() {
			return uncaught.stream()
					.map(t -> t.getClass().getSimpleName())
					.collect(Collectors.joining(", "));
		}

		/**
		 * Throws any uncaught exceptions
		 *
		 * @throws Throwable if handled uncaught exceptions
		 */
		public void throwExceptions() throws Throwable {
			if (!uncaught.isEmpty()) {
				Throwable rethrow = uncaught.get(0);

				if (uncaught.size() > 1) {
					String error = "Found " + uncaught.size() + " exceptions: " + toString();
					rethrow = new Exception(error, rethrow);
				}

				throw rethrow;
			}
		}
	}

	/** Worker thread for testing. */
	public static class Worker extends Thread {
		/** The handler for runtime exceptions */
		private final Executable action;

		/**
		 * Initializes this worker thread.
		 *
		 * @param action the action to run
		 * @param handler the handler for uncaught runtime exceptions
		 */
		public Worker(Executable action, ExceptionCollector handler) {
			this.action = action;
			this.setUncaughtExceptionHandler(handler);
		}

		@Override
		public void run() {
			try {
				action.execute();
			}
			catch (Throwable e) {
				Assertions.fail("Worker thread failed.", e);
			}
		}
	}

	/** Used to test locks. */
	public static class LockAction implements Executable {
		/** Buffer for output */
		private final List<String> actual;

		/** Lock being tested */
		private final MultiReaderLock.SimpleLock lock;

		/** Latch for before locking */
		private final CountDownLatch beforeLock;

		/** Latch for after locking */
		private final CountDownLatch afterLock;

		/** Type of lock being tested */
		private final String lockType;

		/**
		 * Initializes this object.
		 *
		 * @param actual the buffer for output
		 * @param lock the lock being tested
		 * @param beforeLock the latch for before locking
		 * @param afterLock the latch for after locking
		 */
		public LockAction(List<String> actual, MultiReaderLock.SimpleLock lock,
				CountDownLatch beforeLock, CountDownLatch afterLock) {
			this.actual = actual;
			this.lock = lock;
			this.beforeLock = beforeLock;
			this.afterLock = afterLock;
			this.lockType = switch (lock.getClass().getSimpleName()) {
				case "ReadLock" -> "Read";
				case "WriteLock" -> "Write";
				default -> "Unknown";
			};
		}

		@Override
		public void execute() throws Throwable {
			beforeLock.await();

			lock.lock();
			output(actual, "Locked " + lockType);

			afterLock.countDown();
			Thread.sleep(WORKER_SLEEP);

			output(actual, "Unlocking " + lockType);
			lock.unlock();
		}
	}
}
