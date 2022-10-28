ReadWriteLock
=================================================

![Points](../../blob/badges/points.svg)

For this homework, you will create a conditional read write lock and a thread safe indexed set using that lock.

## Hints ##

Below are some hints that may help with this homework assignment:

  - There are some differences between the `IndexedSet` version presented in lecture and the one provided here. You will have to determine if those differences change which methods should or shouldn't be overridden!

  - The Javadoc comments for the `SimpleReadWriteLock` are written to give clues on the conditions the code should use for the `wait()` or `notifyAll()` calls.

  - The lecture notes discuss a simple read write lock, but the one you must create is more complicated. In addition to tracking the number of readers and writers, it must track the **active** writer as well.

      This includes *setting the active writer* when a thread acquires the write lock and *unsetting the active writer* when the lock is *fully* released. The active writer can acquire additional read or write locks as long as it is the active writer.

      **Leave this part for the end, after you have a simple read write lock already working.**

  - If your code is timing out, the best way to debug is to use logging. You need to figure out where your code is getting stuck, and debug from there.

  - If your code inconsistently passes tests, there is a multithreading problem somewhere (it just doesn't always pop up when you run due to the nondeterminism of multithreading code). Logging can help debug these cases.

  - The thread-safe lock tests are not comprehensive! They will catch a few places locks are not used properly, but not necessarily everywhere.

These hints are *optional*. There may be multiple approaches to solving this homework.

## Instructions ##

Use the "Tasks" view in Eclipse to find the `TODO` comments for what need to be implemented and the "Javadoc" view to see additional details.

The tests are provided in the `src/test/` directory; do not modify any of the files in that directory. Check the run details on GitHub Actions for how many points each test group is worth. 

See the [Homework Guides](https://usf-cs272-fall2022.github.io/guides/homework/) for additional details on homework requirements and submission.
