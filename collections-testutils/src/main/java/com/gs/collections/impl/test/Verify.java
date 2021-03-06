/*
 * Copyright 2013 Goldman Sachs.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gs.collections.impl.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.Callable;

import com.gs.collections.api.PrimitiveIterable;
import com.gs.collections.api.block.predicate.Predicate;
import com.gs.collections.api.collection.ImmutableCollection;
import com.gs.collections.api.list.ImmutableList;
import com.gs.collections.api.list.MutableList;
import com.gs.collections.api.map.ImmutableMap;
import com.gs.collections.api.map.MutableMap;
import com.gs.collections.api.multimap.Multimap;
import com.gs.collections.api.set.ImmutableSet;
import com.gs.collections.api.set.MutableSet;
import com.gs.collections.impl.block.factory.Comparators;
import com.gs.collections.impl.block.factory.Predicates;
import com.gs.collections.impl.factory.Lists;
import com.gs.collections.impl.factory.Sets;
import com.gs.collections.impl.map.mutable.UnifiedMap;
import com.gs.collections.impl.set.mutable.UnifiedSet;
import com.gs.collections.impl.tuple.ImmutableEntry;
import com.gs.collections.impl.utility.ArrayIterate;
import com.gs.collections.impl.utility.Iterate;
import org.apache.commons.codec.binary.Base64;
import org.junit.Assert;

/**
 * An extension of the {@link Assert} class, which adds useful additional "assert" methods.
 * You can import this class instead of Assert, and use it thus, e.g.:
 * <pre>
 *     Verify.assertEquals("fred", name);  // from original Assert class
 *     Verify.assertContains("fred", nameList);  // from new extensions
 *     Verify.assertBefore("fred", "jim", orderedNamesList);  // from new extensions
 * </pre>
 */
public final class Verify extends Assert
{
    private static final int MAX_DIFFERENCES = 5;
    private static final byte[] LINE_SEPARATOR = {'\n'};

    private Verify()
    {
        throw new AssertionError("Suppress default constructor for noninstantiability");
    }

    /**
     * Mangles the stack trace of {@link AssertionError} so that it looks like its been thrown from the line that
     * called to a custom assertion.
     * <p/>
     * <p>This method behaves identically to {@link #throwMangledException(AssertionError, int)} and is provided
     * for convenience for assert methods that only want to pop two stack frames. The only time that you would want to
     * call the other {@link #throwMangledException(AssertionError, int)} method is if you have a custom assert
     * that calls another custom assert i.e. the source line calling the custom asserts is more than two stack frames
     * away</p>
     *
     * @param e The exception to mangle.
     * @see #throwMangledException(AssertionError, int)
     */
    public static void throwMangledException(AssertionError e)
    {
        /*
         * Note that we actually remove 3 frames from the stack trace because
         * we wrap the real method doing the work: e.fillInStackTrace() will
         * include us in the exceptions stack frame.
         */
        Verify.throwMangledException(e, 3);
    }

    /**
     * <p>Mangles the stack trace of {@link AssertionError} so that it looks like
     * its been thrown from the line that called to a custom assertion.</p>
     * <p/>
     * <p>This is useful for when you are in a debugging session and you want to go to the source
     * of the problem in the test case quickly. The regular use case for this would be something
     * along the lines of:</p>
     * <pre>
     * 1  public class TestFoo extends junit.framework.TestCase {
     * 2     public void testFoo() throws Exception {
     * 3        Foo foo = new Foo();
     * 4        ...
     * 5        assertFoo(foo);
     * 6     }
     * 7
     * 8     // Custom assert
     * 9     private static void assertFoo(Foo foo) {
     * 10       try {
     * 11           assertEquals(...);
     * 12           ....
     * 13           assertSame(...);
     * 14       } catch (AssertionFailedException e) {
     * 15           AssertUtils.throwMangledException(e, 2);
     * 16       }
     * 17    }
     * 18 }
     * </pre>
     * <p/>
     * <p>Without the {@code try ... catch} block around lines 11-13 the stack trace following a test failure
     * would look a little like:
     * <p/>
     * <pre>
     * java.lang.AssertionError: ...
     *  at TestFoo.assertFoo(TestFoo.java:11)
     *  at TestFoo.testFoo(TestFoo.java:5)
     *  at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
     *  at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:39)
     *  at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:25)
     *  at java.lang.reflect.Method.invoke(Method.java:324)
     *  ...
     * </pre>
     * <p/>
     * <p>Note that the source of the error isn't readily apparent as the first line in the stack trace
     * is the code within the custom assert. If we were debugging the failure we would be more interested
     * in the second line of the stack trace which shows us where in our tests the assert failed.</p>
     * <p/>
     * <p>With the {@code try ... catch} block around lines 11-13 the stack trace would look like the
     * following:</p>
     * <p/>
     * <pre>
     * java.lang.AssertionError: ...
     *  at TestFoo.testFoo(TestFoo.java:5)
     *  at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
     *  at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:39)
     *  at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:25)
     *  at java.lang.reflect.Method.invoke(Method.java:324)
     *  ...
     * </pre>
     * <p/>
     * <p>Here the source of the error is more visible as we can instantly see that the testFoo test is
     * failing at line 5.</p>
     *
     * @param e           The exception to mangle.
     * @param framesToPop The number of frames to remove from the stack trace.
     * @throws AssertionError that was given as an argument with its stack trace mangled.
     */
    public static void throwMangledException(AssertionError e, int framesToPop)
    {
        e.fillInStackTrace();
        StackTraceElement[] stackTrace = e.getStackTrace();
        StackTraceElement[] newStackTrace = new StackTraceElement[stackTrace.length - framesToPop];
        System.arraycopy(stackTrace, framesToPop, newStackTrace, 0, newStackTrace.length);
        e.setStackTrace(newStackTrace);
        throw e;
    }

    public static void fail(String message, Throwable cause)
    {
        AssertionError failedException = new AssertionError(message);
        failedException.initCause(cause);
        Verify.throwMangledException(failedException);
    }

    /**
     * Assert that two items are not the same. If one item is null, the the other must be non-null.
     *
     * @deprecated in 3.0. Use {@link Assert#assertNotEquals(String, Object, Object)} in JUnit 4.11 instead.
     */
    @Deprecated
    public static void assertNotEquals(String itemsName, Object item1, Object item2)
    {
        try
        {
            if (Comparators.nullSafeEquals(item1, item2) || Comparators.nullSafeEquals(item2, item1))
            {
                Assert.fail(itemsName + " should not be equal, item1:<" + item1 + ">, item2:<" + item2 + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that two items are not the same. If one item is null, the the other must be non-null.
     *
     * @deprecated in 3.0. Use {@link Assert#assertNotEquals(Object, Object)} in JUnit 4.11 instead.
     */
    @Deprecated
    public static void assertNotEquals(Object item1, Object item2)
    {
        try
        {
            Verify.assertNotEquals("items", item1, item2);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two Strings are not equal.
     *
     * @deprecated in 3.0. Use {@link Assert#assertNotEquals(String, Object, Object)} in JUnit 4.11 instead.
     */
    @Deprecated
    public static void assertNotEquals(String itemName, String notExpected, String actual)
    {
        try
        {
            if (Comparators.nullSafeEquals(notExpected, actual))
            {
                Assert.fail(itemName + " should not equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two Strings are not equal.
     *
     * @deprecated in 3.0. Use {@link Assert#assertNotEquals(Object, Object)} in JUnit 4.11 instead.
     */
    @Deprecated
    public static void assertNotEquals(String notExpected, String actual)
    {
        try
        {
            Verify.assertNotEquals("string", notExpected, actual);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two doubles are not equal concerning a delta. If the expected value is infinity then the delta value
     * is ignored.
     *
     * @deprecated in 3.0. Use {@link Assert#assertNotEquals(String, double, double, double)} in JUnit 4.11 instead.
     */
    @Deprecated
    public static void assertNotEquals(String itemName, double notExpected, double actual, double delta)
    {
        // handle infinity specially since subtracting to infinite values gives NaN and the
        // the following test fails
        try
        {
            //noinspection FloatingPointEquality
            if (Double.isInfinite(notExpected) && notExpected == actual || Math.abs(notExpected - actual) <= delta)
            {
                Assert.fail(itemName + " should not be equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two doubles are not equal concerning a delta. If the expected value is infinity then the delta value
     * is ignored.
     *
     * @deprecated in 3.0. Use {@link Assert#assertNotEquals(double, double, double)} in JUnit 4.11 instead.
     */
    @Deprecated
    public static void assertNotEquals(double notExpected, double actual, double delta)
    {
        try
        {
            Verify.assertNotEquals("double", notExpected, actual, delta);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two floats are not equal concerning a delta. If the expected value is infinity then the delta value
     * is ignored.
     */
    public static void assertNotEquals(String itemName, float notExpected, float actual, float delta)
    {
        try
        {
            // handle infinity specially since subtracting to infinite values gives NaN and the
            // the following test fails
            //noinspection FloatingPointEquality
            if (Float.isInfinite(notExpected) && notExpected == actual || Math.abs(notExpected - actual) <= delta)
            {
                Assert.fail(itemName + " should not be equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two floats are not equal concerning a delta. If the expected value is infinity then the delta value
     * is ignored.
     */
    public static void assertNotEquals(float expected, float actual, float delta)
    {
        try
        {
            Verify.assertNotEquals("float", expected, actual, delta);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two longs are not equal.
     *
     * @deprecated in 3.0. Use {@link Assert#assertNotEquals(String, long, long)} in JUnit 4.11 instead.
     */
    @Deprecated
    public static void assertNotEquals(String itemName, long notExpected, long actual)
    {
        try
        {
            if (notExpected == actual)
            {
                Assert.fail(itemName + " should not be equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two longs are not equal.
     *
     * @deprecated in 3.0. Use {@link Assert#assertNotEquals(long, long)} in JUnit 4.11 instead.
     */
    @Deprecated
    public static void assertNotEquals(long notExpected, long actual)
    {
        try
        {
            Verify.assertNotEquals("long", notExpected, actual);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two booleans are not equal.
     */
    public static void assertNotEquals(String itemName, boolean notExpected, boolean actual)
    {
        try
        {
            if (notExpected == actual)
            {
                Assert.fail(itemName + " should not be equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two booleans are not equal.
     */
    public static void assertNotEquals(boolean notExpected, boolean actual)
    {
        try
        {
            Verify.assertNotEquals("boolean", notExpected, actual);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two bytes are not equal.
     */
    public static void assertNotEquals(String itemName, byte notExpected, byte actual)
    {
        try
        {
            if (notExpected == actual)
            {
                Assert.fail(itemName + " should not be equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two bytes are not equal.
     */
    public static void assertNotEquals(byte notExpected, byte actual)
    {
        try
        {
            Verify.assertNotEquals("byte", notExpected, actual);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two chars are not equal.
     */
    public static void assertNotEquals(String itemName, char notExpected, char actual)
    {
        try
        {
            if (notExpected == actual)
            {
                Assert.fail(itemName + " should not be equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two chars are not equal.
     */
    public static void assertNotEquals(char notExpected, char actual)
    {
        try
        {
            Verify.assertNotEquals("char", notExpected, actual);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two shorts are not equal.
     */
    public static void assertNotEquals(String itemName, short notExpected, short actual)
    {
        try
        {
            if (notExpected == actual)
            {
                Assert.fail(itemName + " should not be equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two shorts are not equal.
     */
    public static void assertNotEquals(short notExpected, short actual)
    {
        try
        {
            Verify.assertNotEquals("short", notExpected, actual);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two ints are not equal.
     *
     * @deprecated in 3.0. Use {@link Assert#assertNotEquals(String, long, long)} in JUnit 4.11 instead.
     */
    @Deprecated
    public static void assertNotEquals(String itemName, int notExpected, int actual)
    {
        try
        {
            if (notExpected == actual)
            {
                Assert.fail(itemName + " should not be equal:<" + notExpected + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that two ints are not equal.
     *
     * @deprecated in 3.0. Use {@link Assert#assertNotEquals(long, long)} in JUnit 4.11 instead.
     */
    @Deprecated
    public static void assertNotEquals(int notExpected, int actual)
    {
        try
        {
            Verify.assertNotEquals("int", notExpected, actual);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} is empty.
     */
    public static void assertEmpty(Collection<?> actualCollection)
    {
        try
        {
            Verify.assertEmpty("collection", actualCollection);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} is empty.
     */
    public static void assertEmpty(String collectionName, Collection<?> actualCollection)
    {
        try
        {
            Verify.assertObjectNotNull(collectionName, actualCollection);

            if (!actualCollection.isEmpty())
            {
                Assert.fail(collectionName + " should be empty; actual size:<" + actualCollection.size() + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link PrimitiveIterable} is empty.
     */
    public static void assertEmpty(PrimitiveIterable primitiveIterable)
    {
        try
        {
            Verify.assertEmpty("primitiveIterable", primitiveIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link PrimitiveIterable} is empty.
     */
    public static void assertEmpty(String iterableName, PrimitiveIterable primitiveIterable)
    {
        try
        {
            Verify.assertObjectNotNull(iterableName, primitiveIterable);

            if (!primitiveIterable.isEmpty())
            {
                Assert.fail(iterableName + " should be empty; actual size:<" + primitiveIterable.size() + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Iterable} is empty.
     */
    public static void assertIterableEmpty(Iterable<?> iterable)
    {
        try
        {
            Verify.assertIterableEmpty("iterable", iterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Iterable} is empty.
     */
    public static void assertIterableEmpty(String iterableName, Iterable<?> iterable)
    {
        try
        {
            Verify.assertObjectNotNull(iterableName, iterable);

            if (!Iterate.isEmpty(iterable))
            {
                Assert.fail(iterableName + " should be empty; actual size:<" + Iterate.sizeOf(iterable) + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given object is an instanceof expectedClassType.
     */
    public static void assertInstanceOf(Class<?> expectedClassType, Object actualObject)
    {
        try
        {
            Verify.assertInstanceOf(actualObject.getClass().getName(), expectedClassType, actualObject);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given object is an instanceof expectedClassType.
     */
    public static void assertInstanceOf(String objectName, Class<?> expectedClassType, Object actualObject)
    {
        try
        {
            if (!expectedClassType.isInstance(actualObject))
            {
                Assert.fail(objectName + " is not an instance of " + expectedClassType.getName());
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} is empty.
     */
    public static void assertEmpty(Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertEmpty("map", actualMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Multimap} is empty.
     */
    public static void assertEmpty(Multimap<?, ?> actualMultimap)
    {
        try
        {
            Verify.assertEmpty("multimap", actualMultimap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Multimap} is empty.
     */
    public static void assertEmpty(String multimapName, Multimap<?, ?> actualMultimap)
    {
        try
        {
            Verify.assertObjectNotNull(multimapName, actualMultimap);

            if (!actualMultimap.isEmpty())
            {
                Assert.fail(multimapName + " should be empty; actual size:<" + actualMultimap.size() + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} is empty.
     */
    public static void assertEmpty(String mapName, Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertObjectNotNull(mapName, actualMap);

            if (!actualMap.isEmpty())
            {
                Assert.fail(mapName + " should be empty; actual size:<" + actualMap.size() + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertEmpty(ImmutableMap<?, ?> actualImmutableMap)
    {
        try
        {
            Verify.assertEmpty("immutable map", actualImmutableMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertEmpty(String mapName, ImmutableMap<?, ?> actualImmutableMap)
    {
        try
        {
            Verify.assertObjectNotNull(mapName, actualImmutableMap);

            if (!actualImmutableMap.isEmpty())
            {
                Assert.fail(mapName + " should be empty; actual size:<" + actualImmutableMap.size() + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} is <em>not</em> empty.
     */
    public static void assertNotEmpty(Collection<?> actualCollection)
    {
        try
        {
            Verify.assertNotEmpty("collection", actualCollection);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} is <em>not</em> empty.
     */
    public static void assertNotEmpty(String collectionName, Collection<?> actualCollection)
    {
        try
        {
            Verify.assertObjectNotNull(collectionName, actualCollection);
            Assert.assertFalse(collectionName + " should be non-empty, but was empty", actualCollection.isEmpty());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link PrimitiveIterable} is <em>not</em> empty.
     */
    public static void assertNotEmpty(PrimitiveIterable primitiveIterable)
    {
        try
        {
            Verify.assertNotEmpty("primitiveIterable", primitiveIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link PrimitiveIterable} is <em>not</em> empty.
     */
    public static void assertNotEmpty(String iterableName, PrimitiveIterable primitiveIterable)
    {
        try
        {
            Verify.assertObjectNotNull(iterableName, primitiveIterable);
            Assert.assertFalse(iterableName + " should be non-empty, but was empty", primitiveIterable.isEmpty());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Iterable} is <em>not</em> empty.
     */
    public static void assertIterableNotEmpty(Iterable<?> iterable)
    {
        try
        {
            Verify.assertIterableNotEmpty("iterable", iterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Iterable} is <em>not</em> empty.
     */
    public static void assertIterableNotEmpty(String iterableName, Iterable<?> iterable)
    {
        try
        {
            Verify.assertObjectNotNull(iterableName, iterable);
            Assert.assertFalse(iterableName + " should be non-empty, but was empty", Iterate.isEmpty(iterable));
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} is <em>not</em> empty.
     */
    public static void assertNotEmpty(Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertNotEmpty("map", actualMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} is <em>not</em> empty.
     */
    public static void assertNotEmpty(String mapName, Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertObjectNotNull(mapName, actualMap);
            Assert.assertFalse(mapName + " should be non-empty, but was empty", actualMap.isEmpty());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Multimap} is <em>not</em> empty.
     */
    public static void assertNotEmpty(Multimap<?, ?> actualMultimap)
    {
        try
        {
            Verify.assertNotEmpty("multimap", actualMultimap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Multimap} is <em>not</em> empty.
     */
    public static void assertNotEmpty(String multimapName, Multimap<?, ?> actualMultimap)
    {
        try
        {
            Verify.assertObjectNotNull(multimapName, actualMultimap);
            Assert.assertTrue(multimapName + " should be non-empty, but was empty", actualMultimap.notEmpty());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertNotEmpty(String itemsName, T[] items)
    {
        try
        {
            Verify.assertObjectNotNull(itemsName, items);
            Verify.assertNotEquals(itemsName, 0, items.length);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertNotEmpty(T[] items)
    {
        try
        {
            Verify.assertNotEmpty("items", items);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given array.
     */
    public static void assertSize(int expectedSize, Object[] actualArray)
    {
        try
        {
            Verify.assertSize("array", expectedSize, actualArray);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given array.
     */
    public static void assertSize(String arrayName, int expectedSize, Object[] actualArray)
    {
        try
        {
            Assert.assertNotNull(arrayName + " should not be null", actualArray);

            int actualSize = actualArray.length;
            if (actualSize != expectedSize)
            {
                Assert.fail("Incorrect size for "
                        + arrayName
                        + "; expected:<"
                        + expectedSize
                        + "> but was:<"
                        + actualSize
                        + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link Collection}.
     */
    public static void assertSize(int expectedSize, Collection<?> actualCollection)
    {
        try
        {
            Verify.assertSize("collection", expectedSize, actualCollection);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link Collection}.
     */
    public static void assertSize(
            String collectionName,
            int expectedSize,
            Collection<?> actualCollection)
    {
        try
        {
            Verify.assertObjectNotNull(collectionName, actualCollection);

            int actualSize = actualCollection.size();
            if (actualSize != expectedSize)
            {
                Assert.fail("Incorrect size for "
                        + collectionName
                        + "; expected:<"
                        + expectedSize
                        + "> but was:<"
                        + actualSize
                        + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link PrimitiveIterable}.
     */
    public static void assertSize(int expectedSize, PrimitiveIterable primitiveIterable)
    {
        try
        {
            Verify.assertSize("primitiveIterable", expectedSize, primitiveIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link PrimitiveIterable}.
     */
    public static void assertSize(
            String primitiveIterableName,
            int expectedSize,
            PrimitiveIterable actualPrimitiveIterable)
    {
        try
        {
            Verify.assertObjectNotNull(primitiveIterableName, actualPrimitiveIterable);

            int actualSize = actualPrimitiveIterable.size();
            if (actualSize != expectedSize)
            {
                Assert.fail("Incorrect size for "
                        + primitiveIterableName
                        + "; expected:<"
                        + expectedSize
                        + "> but was:<"
                        + actualSize
                        + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link Iterable}.
     */
    public static void assertIterableSize(int expectedSize, Iterable<?> actualIterable)
    {
        try
        {
            Verify.assertIterableSize("iterable", expectedSize, actualIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link Iterable}.
     */
    public static void assertIterableSize(
            String iterableName,
            int expectedSize,
            Iterable<?> actualIterable)
    {
        try
        {
            Verify.assertObjectNotNull(iterableName, actualIterable);

            int actualSize = Iterate.sizeOf(actualIterable);
            if (actualSize != expectedSize)
            {
                Assert.fail("Incorrect size for "
                        + iterableName
                        + "; expected:<"
                        + expectedSize
                        + "> but was:<"
                        + actualSize
                        + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link Map}.
     */
    public static void assertSize(String mapName, int expectedSize, Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertSize(mapName, expectedSize, actualMap.keySet());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link Map}.
     */
    public static void assertSize(int expectedSize, Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertSize("map", expectedSize, actualMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link Multimap}.
     */
    public static void assertSize(int expectedSize, Multimap<?, ?> actualMultimap)
    {
        try
        {
            Verify.assertSize("multimap", expectedSize, actualMultimap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link Multimap}.
     */
    public static void assertSize(String multimapName, int expectedSize, Multimap<?, ?> actualMultimap)
    {
        try
        {
            int actualSize = actualMultimap.size();
            if (actualSize != expectedSize)
            {
                Assert.fail("Incorrect size for "
                        + multimapName
                        + "; expected:<"
                        + expectedSize
                        + "> but was:<"
                        + actualSize
                        + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link ImmutableMap}.
     */
    public static void assertSize(int expectedSize, ImmutableMap<?, ?> actualImmutableMap)
    {
        try
        {
            Verify.assertSize("immutable map", expectedSize, actualImmutableMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link ImmutableMap}.
     */
    public static void assertSize(String immutableMapName, int expectedSize, ImmutableMap<?, ?> actualImmutableMap)
    {
        try
        {
            int actualSize = actualImmutableMap.size();
            if (actualSize != expectedSize)
            {
                Assert.fail("Incorrect size for "
                        + immutableMapName
                        + "; expected:<"
                        + expectedSize
                        + "> but was:<"
                        + actualSize
                        + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link ImmutableSet}.
     */
    public static void assertSize(int expectedSize, ImmutableSet<?> actualImmutableSet)
    {
        try
        {
            Verify.assertSize("immutable set", expectedSize, actualImmutableSet);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the size of the given {@link ImmutableSet}.
     */
    public static void assertSize(String immutableSetName, int expectedSize, ImmutableSet<?> actualImmutableSet)
    {
        try
        {
            int actualSize = actualImmutableSet.size();
            if (actualSize != expectedSize)
            {
                Assert.fail("Incorrect size for "
                        + immutableSetName
                        + "; expected:<"
                        + expectedSize
                        + "> but was:<"
                        + actualSize
                        + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@code stringToFind} is contained within the {@code stringToSearch}.
     */
    public static void assertContains(String stringToFind, String stringToSearch)
    {
        try
        {
            Verify.assertContains("string", stringToFind, stringToSearch);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@code unexpectedString} is <em>not</em> contained within the {@code stringToSearch}.
     */
    public static void assertNotContains(String unexpectedString, String stringToSearch)
    {
        try
        {
            Verify.assertNotContains("string", unexpectedString, stringToSearch);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@code stringToFind} is contained within the {@code stringToSearch}.
     */
    public static void assertContains(String stringName, String stringToFind, String stringToSearch)
    {
        try
        {
            Assert.assertNotNull("stringToFind should not be null", stringToFind);
            Assert.assertNotNull("stringToSearch should not be null", stringToSearch);

            if (!stringToSearch.contains(stringToFind))
            {
                Assert.fail(stringName
                        + " did not contain stringToFind:<"
                        + stringToFind
                        + "> in stringToSearch:<"
                        + stringToSearch
                        + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@code unexpectedString} is <em>not</em> contained within the {@code stringToSearch}.
     */
    public static void assertNotContains(String stringName, String unexpectedString, String stringToSearch)
    {
        try
        {
            Assert.assertNotNull("unexpectedString should not be null", unexpectedString);
            Assert.assertNotNull("stringToSearch should not be null", stringToSearch);

            if (stringToSearch.contains(unexpectedString))
            {
                Assert.fail(stringName
                        + " contains unexpectedString:<"
                        + unexpectedString
                        + "> in stringToSearch:<"
                        + stringToSearch
                        + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertCount(
            int expectedCount,
            Iterable<T> iterable,
            Predicate<? super T> predicate)
    {
        Assert.assertEquals(expectedCount, Iterate.count(iterable, predicate));
    }

    public static <T> void assertAllSatisfy(Iterable<T> iterable, Predicate<? super T> predicate)
    {
        try
        {
            Verify.assertAllSatisfy("The following items failed to satisfy the condition", iterable, predicate);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <K, V> void assertAllSatisfy(Map<K, V> map, Predicate<? super V> predicate)
    {
        try
        {
            Verify.assertAllSatisfy(map.values(), predicate);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertAllSatisfy(String message, Iterable<T> iterable, Predicate<? super T> predicate)
    {
        try
        {
            MutableList<T> unnacceptable = Lists.mutable.of();
            for (T each : iterable)
            {
                if (!predicate.accept(each))
                {
                    unnacceptable.add(each);
                }
            }
            if (unnacceptable.notEmpty())
            {
                Assert.fail(message + " <" + unnacceptable + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertAnySatisfy(Iterable<T> iterable, Predicate<? super T> predicate)
    {
        try
        {
            Verify.assertAnySatisfy("No items satisfied the condition", iterable, predicate);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <K, V> void assertAnySatisfy(Map<K, V> map, Predicate<? super V> predicate)
    {
        try
        {
            Verify.assertAnySatisfy(map.values(), predicate);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertAnySatisfy(String message, Iterable<T> iterable, Predicate<? super T> predicate)
    {
        try
        {
            Assert.assertTrue(message, Predicates.<T>anySatisfy(predicate).accept(iterable));
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertNoneSatisfy(Iterable<T> iterable, Predicate<? super T> predicate)
    {
        try
        {
            Verify.assertNoneSatisfy("The following items satisfied the condition", iterable, predicate);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <K, V> void assertNoneSatisfy(Map<K, V> map, Predicate<? super V> predicate)
    {
        try
        {
            Verify.assertNoneSatisfy(map.values(), predicate);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertNoneSatisfy(String message, Iterable<T> iterable, Predicate<? super T> predicate)
    {
        try
        {
            MutableList<T> unnacceptable = Lists.mutable.of();
            for (T each : iterable)
            {
                if (predicate.accept(each))
                {
                    unnacceptable.add(each);
                }
            }
            if (unnacceptable.notEmpty())
            {
                Assert.fail(message + " <" + unnacceptable + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} contains all of the given keys and values.
     */
    public static void assertContainsAllKeyValues(Map<?, ?> actualMap, Object... keyValues)
    {
        try
        {
            Verify.assertContainsAllKeyValues("map", actualMap, keyValues);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} contains all of the given keys and values.
     */
    public static void assertContainsAllKeyValues(
            String mapName,
            Map<?, ?> actualMap,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            if (expectedKeyValues.length % 2 != 0)
            {
                Assert.fail("Odd number of keys and values (every key must have a value)");
            }

            Verify.assertObjectNotNull(mapName, actualMap);
            Verify.assertMapContainsKeys(mapName, actualMap, expectedKeyValues);
            Verify.assertMapContainsValues(mapName, actualMap, expectedKeyValues);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link ImmutableMap} contains all of the given keys and values.
     */
    public static void assertContainsAllKeyValues(ImmutableMap<?, ?> actualImmutableMap, Object... keyValues)
    {
        try
        {
            Verify.assertContainsAllKeyValues("immutable map", actualImmutableMap, keyValues);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link ImmutableMap} contains all of the given keys and values.
     */
    public static void assertContainsAllKeyValues(
            String immutableMapName,
            ImmutableMap<?, ?> actualImmutableMap,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            if (expectedKeyValues.length % 2 != 0)
            {
                Assert.fail("Odd number of keys and values (every key must have a value)");
            }

            Verify.assertObjectNotNull(immutableMapName, actualImmutableMap);
            Verify.assertMapContainsKeys(immutableMapName, actualImmutableMap, expectedKeyValues);
            Verify.assertMapContainsValues(immutableMapName, actualImmutableMap, expectedKeyValues);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void denyContainsAny(Collection<?> actualCollection, Object... items)
    {
        try
        {
            Verify.denyContainsAny("collection", actualCollection, items);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertContainsNone(Collection<?> actualCollection, Object... items)
    {
        try
        {
            Verify.denyContainsAny("collection", actualCollection, items);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} contains the given item.
     */
    public static void assertContains(Object expectedItem, Collection<?> actualCollection)
    {
        try
        {
            Verify.assertContains("collection", expectedItem, actualCollection);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} contains the given item.
     */
    public static void assertContains(
            String collectionName,
            Object expectedItem,
            Collection<?> actualCollection)
    {
        try
        {
            Verify.assertObjectNotNull(collectionName, actualCollection);

            if (!actualCollection.contains(expectedItem))
            {
                Assert.fail(collectionName + " did not contain expectedItem:<" + expectedItem + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link ImmutableCollection} contains the given item.
     */
    public static void assertContains(Object expectedItem, ImmutableCollection<?> actualImmutableCollection)
    {
        try
        {
            Verify.assertContains("ImmutableCollection", expectedItem, actualImmutableCollection);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link ImmutableCollection} contains the given item.
     */
    public static void assertContains(
            String immutableCollectionName,
            Object expectedItem,
            ImmutableCollection<?> actualImmutableCollection)
    {
        try
        {
            Verify.assertObjectNotNull(immutableCollectionName, actualImmutableCollection);

            if (!actualImmutableCollection.contains(expectedItem))
            {
                Assert.fail(immutableCollectionName + " did not contain expectedItem:<" + expectedItem + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertContainsAll(
            Iterable<?> iterable,
            Object... items)
    {
        try
        {
            Verify.assertContainsAll("iterable", iterable, items);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertContainsAll(
            String collectionName,
            final Iterable<?> iterable,
            Object... items)
    {
        try
        {
            Verify.assertObjectNotNull(collectionName, iterable);

            Verify.assertNotEmpty("Expected items in assertion", items);

            Predicate<Object> containsPredicate = new Predicate<Object>()
            {
                public boolean accept(Object each)
                {
                    return Iterate.contains(iterable, each);
                }
            };

            if (!ArrayIterate.allSatisfy(items, containsPredicate))
            {
                ImmutableList<Object> result = Lists.immutable.of(items).newWithoutAll(iterable);
                Assert.fail(collectionName + " did not contain these items" + ":<" + result + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertListsEqual(List<?> expectedList, List<?> actualList)
    {
        try
        {
            Verify.assertListsEqual("list", expectedList, actualList);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertListsEqual(String listName, List<?> expectedList, List<?> actualList)
    {
        try
        {
            Verify.assertIterablesEqual(listName, expectedList, actualList);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertSetsEqual(Set<?> expectedSet, Set<?> actualSet)
    {
        try
        {
            Verify.assertSetsEqual("set", expectedSet, actualSet);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertSetsEqual(String setName, Set<?> expectedSet, Set<?> actualSet)
    {
        try
        {
            if (expectedSet == null)
            {
                Assert.assertNull(setName + " should be null", actualSet);
                return;
            }

            Verify.assertObjectNotNull(setName, actualSet);
            Verify.assertSize(setName, expectedSet.size(), actualSet);

            if (!actualSet.equals(expectedSet))
            {
                MutableSet<?> inExpectedOnlySet = UnifiedSet.newSet(expectedSet);
                inExpectedOnlySet.removeAll(actualSet);

                int numberDifferences = inExpectedOnlySet.size();
                String message = setName + ": " + numberDifferences + " elements different.";

                if (numberDifferences > MAX_DIFFERENCES)
                {
                    Assert.fail(message);
                }

                MutableSet<?> inActualOnlySet = UnifiedSet.newSet(actualSet);
                inActualOnlySet.removeAll(expectedSet);

                junit.framework.Assert.failNotEquals(message, inExpectedOnlySet, inActualOnlySet);
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertSortedSetsEqual(SortedSet<?> expectedSet, SortedSet<?> actualSet)
    {
        try
        {
            Verify.assertSortedSetsEqual("sortedSets", expectedSet, actualSet);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertSortedSetsEqual(String setName, SortedSet<?> expectedSet, SortedSet<?> actualSet)
    {
        try
        {
            Verify.assertIterablesEqual(setName, expectedSet, actualSet);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertIterablesEqual(Iterable<?> expectedIterable, Iterable<?> actualIterable)
    {
        try
        {
            Verify.assertIterablesEqual("iterables", expectedIterable, actualIterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertIterablesEqual(String iterableName, Iterable<?> expectedIterable, Iterable<?> actualIterable)
    {
        try
        {
            if (expectedIterable == null)
            {
                Assert.assertNull(iterableName + " should be null", actualIterable);
                return;
            }

            Verify.assertObjectNotNull(iterableName, actualIterable);

            Iterator<?> expectedIterator = expectedIterable.iterator();
            Iterator<?> actualIterator = actualIterable.iterator();
            int index = 0;

            while (expectedIterator.hasNext() && actualIterator.hasNext())
            {
                Object eachExpected = expectedIterator.next();
                Object eachActual = actualIterator.next();

                if (!Comparators.nullSafeEquals(eachExpected, eachActual))
                {
                    junit.framework.Assert.failNotEquals(iterableName + " first differed at element [" + index + "];", eachExpected, eachActual);
                }
                index++;
            }

            Assert.assertFalse("Actual " + iterableName + " had " + index + " elements but expected " + iterableName + " had more.", expectedIterator.hasNext());
            Assert.assertFalse("Expected " + iterableName + " had " + index + " elements but actual " + iterableName + " had more.", actualIterator.hasNext());
        }
        catch (AssertionError e)
        {
            throwMangledException(e);
        }
    }

    public static void assertMapsEqual(Map<?, ?> expectedMap, Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertMapsEqual("map", expectedMap, actualMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertMapsEqual(String mapName, Map<?, ?> expectedMap, Map<?, ?> actualMap)
    {
        try
        {
            if (expectedMap == null)
            {
                Assert.assertNull(mapName + " should be null", actualMap);
                return;
            }

            Assert.assertNotNull(mapName + " should not be null", actualMap);

            Verify.assertSetsEqual(mapName + " keys", expectedMap.keySet(), actualMap.keySet());
            Verify.assertSetsEqual(mapName + " entries", expectedMap.entrySet(), actualMap.entrySet());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    private static void assertMapContainsKeys(
            String mapName,
            Map<?, ?> actualMap,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            MutableList<Object> expectedKeys = Lists.mutable.of();
            for (int i = 0; i < expectedKeyValues.length; i += 2)
            {
                expectedKeys.add(expectedKeyValues[i]);
            }

            Verify.assertContainsAll(mapName + ".keySet()", actualMap.keySet(), expectedKeys.toArray());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    private static void assertMapContainsValues(
            String mapName,
            Map<?, ?> actualMap,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            MutableMap<Object, String> missingEntries = UnifiedMap.newMap();
            int i = 0;
            while (i < expectedKeyValues.length)
            {
                Object expectedKey = expectedKeyValues[i++];
                Object expectedValue = expectedKeyValues[i++];
                Object actualValue = actualMap.get(expectedKey);
                if (!Comparators.nullSafeEquals(expectedValue, actualValue))
                {
                    missingEntries.put(expectedKey,
                            "expectedValue:<" + expectedValue + ">, actualValue:<" + actualValue + '>');
                }
            }
            if (!missingEntries.isEmpty())
            {
                StringBuilder buf = new StringBuilder(mapName + " has incorrect values for keys:[");
                for (Map.Entry<Object, String> expectedEntry : missingEntries.entrySet())
                {
                    buf.append("key:<")
                            .append(expectedEntry.getKey())
                            .append(',')
                            .append(expectedEntry.getValue())
                            .append("> ");
                }
                buf.append(']');
                Assert.fail(buf.toString());
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    private static void assertMapContainsKeys(
            String immutableMapName,
            ImmutableMap<?, ?> actualImmutableMap,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            MutableList<Object> expectedKeys = Lists.mutable.of();
            for (int i = 0; i < expectedKeyValues.length; i += 2)
            {
                expectedKeys.add(expectedKeyValues[i]);
            }

            Verify.assertContainsAll(immutableMapName + ".keysView()", actualImmutableMap.keysView(), expectedKeys.toArray());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    private static void assertMapContainsValues(
            String immutableMapName,
            ImmutableMap<?, ?> actualImmutableMap,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            MutableList<Object> expectedValues = Lists.mutable.of();
            for (int i = 1; i < expectedKeyValues.length; i += 2)
            {
                expectedValues.add(expectedKeyValues[i]);
            }

            Verify.assertContainsAll(immutableMapName + ".valuesView()", actualImmutableMap.valuesView(), expectedValues.toArray());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Multimap} contains an entry with the given key and value.
     */
    public static <K, V> void assertContainsEntry(
            K expectedKey,
            V expectedValue,
            Multimap<K, V> actualMultimap)
    {
        try
        {
            Verify.assertContainsEntry("multimap", expectedKey, expectedValue, actualMultimap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Multimap} contains an entry with the given key and value.
     */
    public static <K, V> void assertContainsEntry(
            String multimapName,
            K expectedKey,
            V expectedValue,
            Multimap<K, V> actualMultimap)
    {
        try
        {
            Assert.assertNotNull(multimapName, actualMultimap);

            if (!actualMultimap.containsKeyAndValue(expectedKey, expectedValue))
            {
                Assert.fail(multimapName + " did not contain entry: <" + expectedKey + ", " + expectedValue + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the given {@link Multimap} contains all of the given keys and values.
     */
    public static void assertContainsAllEntries(Multimap<?, ?> actualMultimap, Object... keyValues)
    {
        try
        {
            Verify.assertContainsAllEntries("multimap", actualMultimap, keyValues);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert the given {@link Multimap} contains all of the given keys and values.
     */
    public static void assertContainsAllEntries(
            String multimapName,
            Multimap<?, ?> actualMultimap,
            Object... expectedKeyValues)
    {
        try
        {
            Verify.assertNotEmpty("Expected keys/values in assertion", expectedKeyValues);

            if (expectedKeyValues.length % 2 != 0)
            {
                Assert.fail("Odd number of keys and values (every key must have a value)");
            }

            Verify.assertObjectNotNull(multimapName, actualMultimap);

            MutableList<Map.Entry<?, ?>> missingEntries = Lists.mutable.of();
            for (int i = 0; i < expectedKeyValues.length; i += 2)
            {
                Object expectedKey = expectedKeyValues[i];
                Object expectedValue = expectedKeyValues[i + 1];

                if (!actualMultimap.containsKeyAndValue(expectedKey, expectedValue))
                {
                    missingEntries.add(new ImmutableEntry<Object, Object>(expectedKey, expectedValue));
                }
            }

            if (!missingEntries.isEmpty())
            {
                Assert.fail(multimapName + " is missing entries: " + missingEntries);
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void denyContainsAny(
            String collectionName,
            Collection<?> actualCollection,
            Object... items)
    {
        try
        {
            Verify.assertNotEmpty("Expected items in assertion", items);

            Verify.assertObjectNotNull(collectionName, actualCollection);

            MutableSet<Object> intersection = Sets.intersect(UnifiedSet.newSet(actualCollection), UnifiedSet.newSetWith(items));
            if (intersection.notEmpty())
            {
                Assert.fail(collectionName
                        + " has an intersection with these items and should not :<" + intersection + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} contains an entry with the given key.
     */
    public static void assertContainsKey(Object expectedKey, Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertContainsKey("map", expectedKey, actualMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} contains an entry with the given key.
     */
    public static void assertContainsKey(String mapName, Object expectedKey, Map<?, ?> actualMap)
    {
        try
        {
            Assert.assertNotNull(mapName, actualMap);

            if (!actualMap.containsKey(expectedKey))
            {
                Assert.fail(mapName + " did not contain expectedKey:<" + expectedKey + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link ImmutableMap} contains an entry with the given key.
     */
    public static void assertContainsKey(Object expectedKey, ImmutableMap<?, ?> actualImmutableMap)
    {
        try
        {
            Verify.assertContainsKey("immutable map", expectedKey, actualImmutableMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link ImmutableMap} contains an entry with the given key.
     */
    public static void assertContainsKey(
            String immutableMapName,
            Object expectedKey,
            ImmutableMap<?, ?> actualImmutableMap)
    {
        try
        {
            Assert.assertNotNull(immutableMapName, actualImmutableMap);

            if (!actualImmutableMap.containsKey(expectedKey))
            {
                Assert.fail(immutableMapName + " did not contain expectedKey:<" + expectedKey + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Deny that the given {@link Map} contains an entry with the given key.
     */
    public static void denyContainsKey(Object unexpectedKey, Map<?, ?> actualMap)
    {
        try
        {
            Verify.denyContainsKey("map", unexpectedKey, actualMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Deny that the given {@link Map} contains an entry with the given key.
     */
    public static void denyContainsKey(String mapName, Object unexpectedKey, Map<?, ?> actualMap)
    {
        try
        {
            Assert.assertNotNull(mapName, actualMap);

            if (actualMap.containsKey(unexpectedKey))
            {
                Assert.fail(mapName + " contained unexpectedKey:<" + unexpectedKey + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} contains an entry with the given key and value.
     */
    public static void assertContainsKeyValue(
            Object expectedKey,
            Object expectedValue,
            Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertContainsKeyValue("map", expectedKey, expectedValue, actualMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Map} contains an entry with the given key and value.
     */
    public static void assertContainsKeyValue(
            String mapName,
            Object expectedKey,
            Object expectedValue,
            Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertContainsKey(mapName, expectedKey, actualMap);

            Object actualValue = actualMap.get(expectedKey);
            if (!Comparators.nullSafeEquals(actualValue, expectedValue))
            {
                Assert.fail(
                        mapName
                                + " entry with expectedKey:<"
                                + expectedKey
                                + "> "
                                + "did not contain expectedValue:<"
                                + expectedValue
                                + ">, "
                                + "but had actualValue:<"
                                + actualValue
                                + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link ImmutableMap} contains an entry with the given key and value.
     */
    public static void assertContainsKeyValue(
            Object expectedKey,
            Object expectedValue,
            ImmutableMap<?, ?> actualImmutableMap)
    {
        try
        {
            Verify.assertContainsKeyValue("immutable map", expectedKey, expectedValue, actualImmutableMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link ImmutableMap} contains an entry with the given key and value.
     */
    public static void assertContainsKeyValue(
            String immutableMapName,
            Object expectedKey,
            Object expectedValue,
            ImmutableMap<?, ?> actualImmutableMap)
    {
        try
        {
            Verify.assertContainsKey(immutableMapName, expectedKey, actualImmutableMap);

            Object actualValue = actualImmutableMap.get(expectedKey);
            if (!Comparators.nullSafeEquals(actualValue, expectedValue))
            {
                Assert.fail(
                        immutableMapName
                                + " entry with expectedKey:<"
                                + expectedKey
                                + "> "
                                + "did not contain expectedValue:<"
                                + expectedValue
                                + ">, "
                                + "but had actualValue:<"
                                + actualValue
                                + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} does <em>not</em> contain the given item.
     */
    public static void assertNotContains(Object unexpectedItem, Collection<?> actualCollection)
    {
        try
        {
            Verify.assertNotContains("collection", unexpectedItem, actualCollection);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} does <em>not</em> contain the given item.
     */
    public static void assertNotContains(
            String collectionName,
            Object unexpectedItem,
            Collection<?> actualCollection)
    {
        try
        {
            Verify.assertObjectNotNull(collectionName, actualCollection);

            if (actualCollection.contains(unexpectedItem))
            {
                Assert.fail(collectionName + " should not contain unexpectedItem:<" + unexpectedItem + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Iterable} does <em>not</em> contain the given item.
     */
    public static void assertNotContains(Object unexpectedItem, Iterable<?> iterable)
    {
        try
        {
            Verify.assertNotContains("iterable", unexpectedItem, iterable);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Iterable} does <em>not</em> contain the given item.
     */
    public static void assertNotContains(
            String collectionName,
            Object unexpectedItem,
            Iterable<?> iterable)
    {
        try
        {
            Verify.assertObjectNotNull(collectionName, iterable);

            if (Iterate.contains(iterable, unexpectedItem))
            {
                Assert.fail(collectionName + " should not contain unexpectedItem:<" + unexpectedItem + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} does <em>not</em> contain the given item.
     */
    public static void assertNotContainsKey(Object unexpectedKey, Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertNotContainsKey("map", unexpectedKey, actualMap);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@link Collection} does <em>not</em> contain the given item.
     */
    public static void assertNotContainsKey(String mapName, Object unexpectedKey, Map<?, ?> actualMap)
    {
        try
        {
            Verify.assertObjectNotNull(mapName, actualMap);

            if (actualMap.containsKey(unexpectedKey))
            {
                Assert.fail(mapName + " should not contain unexpectedItem:<" + unexpectedKey + '>');
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the formerItem appears before the latterItem in the given {@link Collection}.
     * Both the formerItem and the latterItem must appear in the collection, or this assert will fail.
     */
    public static void assertBefore(Object formerItem, Object latterItem, List<?> actualList)
    {
        try
        {
            Verify.assertBefore("list", formerItem, latterItem, actualList);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the formerItem appears before the latterItem in the given {@link Collection}.
     * {@link #assertContains(String, Object, Collection)} will be called for both the formerItem and the
     * latterItem, prior to the "before" assertion.
     */
    public static void assertBefore(
            String listName,
            Object formerItem,
            Object latterItem,
            List<?> actualList)
    {
        try
        {
            Verify.assertObjectNotNull(listName, actualList);
            Verify.assertNotEquals(
                    "Bad test, formerItem and latterItem are equal, listName:<" + listName + '>',
                    formerItem,
                    latterItem);
            Verify.assertContainsAll(listName, actualList, formerItem, latterItem);
            int formerPosition = actualList.indexOf(formerItem);
            int latterPosition = actualList.indexOf(latterItem);
            if (latterPosition < formerPosition)
            {
                Assert.fail("Items in "
                        + listName
                        + " are in incorrect order; "
                        + "expected formerItem:<"
                        + formerItem
                        + "> "
                        + "to appear before latterItem:<"
                        + latterItem
                        + ">, but didn't");
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertObjectNotNull(String objectName, Object actualObject)
    {
        try
        {
            Assert.assertNotNull(objectName + " should not be null", actualObject);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@code item} is at the {@code index} in the given {@link List}.
     */
    public static void assertItemAtIndex(Object expectedItem, int index, List<?> list)
    {
        try
        {
            Verify.assertItemAtIndex("list", expectedItem, index, list);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@code item} is at the {@code index} in the given {@code array}.
     */
    public static void assertItemAtIndex(Object expectedItem, int index, Object[] array)
    {
        try
        {
            Verify.assertItemAtIndex("array", expectedItem, index, array);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertStartsWith(T[] array, T... items)
    {
        try
        {
            Verify.assertNotEmpty("Expected items in assertion", items);

            for (int i = 0; i < items.length; i++)
            {
                T item = items[i];
                Verify.assertItemAtIndex("array", item, i, array);
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertStartsWith(List<T> list, T... items)
    {
        try
        {
            Verify.assertStartsWith("list", list, items);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertStartsWith(String listName, List<T> list, T... items)
    {
        try
        {
            Verify.assertNotEmpty("Expected items in assertion", items);

            for (int i = 0; i < items.length; i++)
            {
                T item = items[i];
                Verify.assertItemAtIndex(listName, item, i, list);
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertEndsWith(List<T> list, T... items)
    {
        try
        {
            Verify.assertNotEmpty("Expected items in assertion", items);

            for (int i = 0; i < items.length; i++)
            {
                T item = items[i];
                Verify.assertItemAtIndex("list", item, list.size() - items.length + i, list);
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static <T> void assertEndsWith(T[] array, T... items)
    {
        try
        {
            Verify.assertNotEmpty("Expected items in assertion", items);

            for (int i = 0; i < items.length; i++)
            {
                T item = items[i];
                Verify.assertItemAtIndex("array", item, array.length - items.length + i, array);
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@code item} is at the {@code index} in the given {@link List}.
     */
    public static void assertItemAtIndex(
            String listName,
            Object expectedItem,
            int index,
            List<?> list)
    {
        try
        {
            Verify.assertObjectNotNull(listName, list);

            Object actualItem = list.get(index);
            if (!Comparators.nullSafeEquals(expectedItem, actualItem))
            {
                Assert.assertEquals(
                        listName + " has incorrect element at index:<" + index + '>',
                        expectedItem,
                        actualItem);
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that the given {@code item} is at the {@code index} in the given {@link List}.
     */
    public static void assertItemAtIndex(
            String arrayName,
            Object expectedItem,
            int index,
            Object[] array)
    {
        try
        {
            Assert.assertNotNull(array);
            Object actualItem = array[index];
            if (!Comparators.nullSafeEquals(expectedItem, actualItem))
            {
                Assert.assertEquals(
                        arrayName + " has incorrect element at index:<" + index + '>',
                        expectedItem,
                        actualItem);
            }
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertPostSerializedEqualsAndHashCode(Object object)
    {
        try
        {
            Object deserialized = SerializeTestHelper.serializeDeserialize(object);
            Verify.assertEqualsAndHashCode("objects", object, deserialized);
            Assert.assertNotSame("not same object", object, deserialized);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertPostSerializedIdentity(Object object)
    {
        try
        {
            Object deserialized = SerializeTestHelper.serializeDeserialize(object);
            Verify.assertEqualsAndHashCode("objects", object, deserialized);
            Assert.assertSame("same object", object, deserialized);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertSerializedForm(String expectedBase64Form, Object actualObject)
    {
        try
        {
            Verify.assertInstanceOf(Serializable.class, actualObject);
            Assert.assertEquals(
                    "Serialization was broken.",
                    expectedBase64Form,
                    encodeObject(actualObject));
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertSerializedForm(
            long expectedSerialVersionUID,
            String expectedBase64Form,
            Object actualObject)
    {
        try
        {
            Verify.assertInstanceOf(Serializable.class, actualObject);

            Assert.assertEquals(
                    "Serialization was broken.",
                    expectedBase64Form,
                    Verify.encodeObject(actualObject));

            Object decodeToObject = Verify.decodeObject(expectedBase64Form);

            Assert.assertEquals(
                    "serialVersionUID's differ",
                    expectedSerialVersionUID,
                    ObjectStreamClass.lookup(decodeToObject.getClass()).getSerialVersionUID());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    private static Object decodeObject(String expectedBase64Form)
    {
        try
        {
            byte[] bytes = Base64.decodeBase64(expectedBase64Form);
            return new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject();
        }
        catch (IOException e)
        {
            throw new AssertionError(e);
        }
        catch (ClassNotFoundException e)
        {
            throw new AssertionError(e);
        }
    }

    private static String encodeObject(Object actualObject)
    {
        try
        {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(actualObject);
            objectOutputStream.flush();
            objectOutputStream.close();

            String string = new Base64(76, LINE_SEPARATOR, false).encodeAsString(byteArrayOutputStream.toByteArray());
            String trimmedString = Verify.removeFinalNewline(string);
            return Verify.addFinalNewline(trimmedString);
        }
        catch (IOException e)
        {
            throw new AssertionError(e);
        }
    }

    private static String removeFinalNewline(String string)
    {
        return string.substring(0, string.length() - 1);
    }

    private static String addFinalNewline(String string)
    {
        if (string.length() % 77 == 76)
        {
            return string + '\n';
        }
        return string;
    }

    /**
     * Assert that {@code objectA} and {@code objectB} are equal (via the {@link Object#equals(Object)} method,
     * and that they both return the same {@link Object#hashCode()}.
     */
    public static void assertEqualsAndHashCode(Object objectA, Object objectB)
    {
        try
        {
            Verify.assertEqualsAndHashCode("objects", objectA, objectB);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that a value is negative.
     */
    public static void assertNegative(int value)
    {
        try
        {
            Assert.assertTrue(value + " is not negative", value < 0);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that a value is positive.
     */
    public static void assertPositive(int value)
    {
        try
        {
            Assert.assertTrue(value + " is not positive", value > 0);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Asserts that a value is positive.
     */
    public static void assertZero(int value)
    {
        try
        {
            Assert.assertEquals(0, value);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Assert that {@code objectA} and {@code objectB} are equal (via the {@link Object#equals(Object)} method,
     * and that they both return the same {@link Object#hashCode()}.
     */
    public static void assertEqualsAndHashCode(String itemNames, Object objectA, Object objectB)
    {
        try
        {
            if (objectA == null || objectB == null)
            {
                Assert.fail("Neither item should be null: <" + objectA + "> <" + objectB + '>');
            }

            Assert.assertFalse("Neither item should equal null", objectA.equals(null));
            Assert.assertFalse("Neither item should equal null", objectB.equals(null));
            Verify.assertNotEquals("Neither item should equal new Object()", objectA.equals(new Object()));
            Verify.assertNotEquals("Neither item should equal new Object()", objectB.equals(new Object()));
            Assert.assertEquals("Expected " + itemNames + " to be equal.", objectA, objectA);
            Assert.assertEquals("Expected " + itemNames + " to be equal.", objectB, objectB);
            Assert.assertEquals("Expected " + itemNames + " to be equal.", objectA, objectB);
            Assert.assertEquals("Expected " + itemNames + " to be equal.", objectB, objectA);
            Assert.assertEquals(
                    "Expected " + itemNames + " to have the same hashCode().",
                    objectA.hashCode(),
                    objectB.hashCode());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertShallowClone(Cloneable object)
    {
        try
        {
            Verify.assertShallowClone("object", object);
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertShallowClone(String itemName, Cloneable object)
    {
        try
        {
            Method method = Object.class.getDeclaredMethod("clone", (Class<?>[]) null);
            method.setAccessible(true);
            Object clone = method.invoke(object);
            String prefix = itemName + " and its clone";
            Assert.assertNotSame(prefix, object, clone);
            Verify.assertEqualsAndHashCode(prefix, object, clone);
        }
        catch (IllegalArgumentException e)
        {
            throw new AssertionError(e.getLocalizedMessage());
        }
        catch (InvocationTargetException e)
        {
            throw new AssertionError(e.getLocalizedMessage());
        }
        catch (SecurityException e)
        {
            throw new AssertionError(e.getLocalizedMessage());
        }
        catch (NoSuchMethodException e)
        {
            throw new AssertionError(e.getLocalizedMessage());
        }
        catch (IllegalAccessException e)
        {
            throw new AssertionError(e.getLocalizedMessage());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    public static void assertError(Class<? extends Error> expectedErrorClass, Runnable code)
    {
        try
        {
            code.run();
        }
        catch (Error ex)
        {
            try
            {
                Assert.assertSame(
                        "Caught error of type <"
                                + ex.getClass().getName()
                                + ">, expected one of type <"
                                + expectedErrorClass.getName()
                                + '>',
                        expectedErrorClass,
                        ex.getClass());
                return;
            }
            catch (AssertionError e)
            {
                Verify.throwMangledException(e);
            }
        }

        try
        {
            Assert.fail("Block did not throw an error of type " + expectedErrorClass.getName());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Runs the {@link Callable} {@code code} and asserts that it throws an {@code Exception} of the type
     * {@code expectedExceptionClass}.
     * <p/>
     * {@code Callable} is most appropriate when a checked exception will be thrown.
     * If a subclass of {@link RuntimeException} will be thrown, the form
     * {@link #assertThrows(Class, Runnable)} may be more convenient.
     * <p/>
     * <p/>
     * e.g.
     * <pre>
     * Verify.<b>assertThrows</b>(StringIndexOutOfBoundsException.class, new Callable&lt;String&gt;()
     * {
     *    public String call() throws Exception
     *    {
     *        return "Craig".substring(42, 3);
     *    }
     * });
     * </pre>
     *
     * @see #assertThrows(Class, Runnable)
     */
    public static void assertThrows(
            Class<? extends Exception> expectedExceptionClass,
            Callable<?> code)
    {
        try
        {
            code.call();
        }
        catch (Exception ex)
        {
            try
            {
                Assert.assertSame(
                        "Caught exception of type <"
                                + ex.getClass().getName()
                                + ">, expected one of type <"
                                + expectedExceptionClass.getName()
                                + '>'
                                + '\n'
                                + "Exception Message: " + ex.getMessage()
                                + '\n',
                        expectedExceptionClass,
                        ex.getClass());
                return;
            }
            catch (AssertionError e)
            {
                Verify.throwMangledException(e);
            }
        }

        try
        {
            Assert.fail("Block did not throw an exception of type " + expectedExceptionClass.getName());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Runs the {@link Runnable} {@code code} and asserts that it throws an {@code Exception} of the type
     * {@code expectedExceptionClass}.
     * <p/>
     * {@code Runnable} is most appropriate when a subclass of {@link RuntimeException} will be thrown.
     * If a checked exception will be thrown, the form {@link #assertThrows(Class, Callable)} may be more
     * convenient.
     * <p/>
     * <p/>
     * e.g.
     * <pre>
     * Verify.<b>assertThrows</b>(NullPointerException.class, new Runnable()
     * {
     *    public void run()
     *    {
     *        final Integer integer = null;
     *        LOGGER.info(integer.toString());
     *    }
     * });
     * </pre>
     *
     * @see #assertThrows(Class, Callable)
     */
    public static void assertThrows(
            Class<? extends Exception> expectedExceptionClass,
            Runnable code)
    {
        try
        {
            code.run();
        }
        catch (RuntimeException ex)
        {
            try
            {
                Assert.assertSame(
                        "Caught exception of type <"
                                + ex.getClass().getName()
                                + ">, expected one of type <"
                                + expectedExceptionClass.getName()
                                + '>'
                                + '\n'
                                + "Exception Message: " + ex.getMessage()
                                + '\n',
                        expectedExceptionClass,
                        ex.getClass());
                return;
            }
            catch (AssertionError e)
            {
                Verify.throwMangledException(e);
            }
        }

        try
        {
            Assert.fail("Block did not throw an exception of type " + expectedExceptionClass.getName());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Runs the {@link Callable} {@code code} and asserts that it throws an {@code Exception} of the type
     * {@code expectedExceptionClass}, which contains a cause of type expectedCauseClass.
     * <p/>
     * {@code Callable} is most appropriate when a checked exception will be thrown.
     * If a subclass of {@link RuntimeException} will be thrown, the form
     * {@link #assertThrowsWithCause(Class, Class, Runnable)} may be more convenient.
     * <p/>
     * <p/>
     * e.g.
     * <pre>
     * Verify.assertThrowsWithCause(RuntimeException.class, IOException.class, new Callable<Void>()
     * {
     *    public Void call() throws Exception
     *    {
     *        try
     *        {
     *            new File("").createNewFile();
     *        }
     *        catch (final IOException e)
     *        {
     *            throw new RuntimeException("Uh oh!", e);
     *        }
     *        return null;
     *    }
     * });
     * </pre>
     *
     * @see #assertThrowsWithCause(Class, Class, Runnable)
     */
    public static void assertThrowsWithCause(
            Class<? extends Exception> expectedExceptionClass,
            Class<? extends Throwable> expectedCauseClass,
            Callable<?> code)
    {
        try
        {
            code.call();
        }
        catch (Exception ex)
        {
            try
            {
                Assert.assertSame(
                        "Caught exception of type <"
                                + ex.getClass().getName()
                                + ">, expected one of type <"
                                + expectedExceptionClass.getName()
                                + '>',
                        expectedExceptionClass,
                        ex.getClass());
                Throwable actualCauseClass = ex.getCause();
                Assert.assertNotNull(
                        "Caught exception with null cause, expected cause of type <"
                                + expectedCauseClass.getName()
                                + '>',
                        actualCauseClass);
                Assert.assertSame(
                        "Caught exception with cause of type<"
                                + actualCauseClass.getClass().getName()
                                + ">, expected cause of type <"
                                + expectedCauseClass.getName()
                                + '>',
                        expectedCauseClass,
                        actualCauseClass.getClass());
                return;
            }
            catch (AssertionError e)
            {
                Verify.throwMangledException(e);
            }
        }

        try
        {
            Assert.fail("Block did not throw an exception of type " + expectedExceptionClass.getName());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }

    /**
     * Runs the {@link Runnable} {@code code} and asserts that it throws an {@code Exception} of the type
     * {@code expectedExceptionClass}, which contains a cause of type expectedCauseClass.
     * <p/>
     * {@code Runnable} is most appropriate when a subclass of {@link RuntimeException} will be thrown.
     * If a checked exception will be thrown, the form {@link #assertThrowsWithCause(Class, Class, Callable)}
     * may be more convenient.
     * <p/>
     * <p/>
     * e.g.
     * <pre>
     * Verify.assertThrowsWithCause(RuntimeException.class, StringIndexOutOfBoundsException.class, new Runnable()
     * {
     *    public void run()
     *    {
     *        try
     *        {
     *            LOGGER.info("Craig".substring(42, 3));
     *        }
     *        catch (final StringIndexOutOfBoundsException e)
     *        {
     *            throw new RuntimeException("Uh oh!", e);
     *        }
     *    }
     * });
     * </pre>
     *
     * @see #assertThrowsWithCause(Class, Class, Callable)
     */
    public static void assertThrowsWithCause(
            Class<? extends Exception> expectedExceptionClass,
            Class<? extends Throwable> expectedCauseClass,
            Runnable code)
    {
        try
        {
            code.run();
        }
        catch (RuntimeException ex)
        {
            try
            {
                Assert.assertSame(
                        "Caught exception of type <"
                                + ex.getClass().getName()
                                + ">, expected one of type <"
                                + expectedExceptionClass.getName()
                                + '>',
                        expectedExceptionClass,
                        ex.getClass());
                Throwable actualCauseClass = ex.getCause();
                Assert.assertNotNull(
                        "Caught exception with null cause, expected cause of type <"
                                + expectedCauseClass.getName()
                                + '>',
                        actualCauseClass);
                Assert.assertSame(
                        "Caught exception with cause of type<"
                                + actualCauseClass.getClass().getName()
                                + ">, expected cause of type <"
                                + expectedCauseClass.getName()
                                + '>',
                        expectedCauseClass,
                        actualCauseClass.getClass());
                return;
            }
            catch (AssertionError e)
            {
                Verify.throwMangledException(e);
            }
        }

        try
        {
            Assert.fail("Block did not throw an exception of type " + expectedExceptionClass.getName());
        }
        catch (AssertionError e)
        {
            Verify.throwMangledException(e);
        }
    }
}
