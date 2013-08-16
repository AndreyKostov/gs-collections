/*
 * Copyright 2012 Goldman Sachs.
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

package com.gs.collections.impl.block.factory;

import com.gs.collections.api.block.function.primitive.BooleanFunction;
import com.gs.collections.api.block.function.primitive.ByteFunction;
import com.gs.collections.api.block.function.primitive.CharFunction;
import com.gs.collections.api.block.function.primitive.DoubleFunction;
import com.gs.collections.api.block.function.primitive.FloatFunction;
import com.gs.collections.api.block.function.primitive.IntFunction;
import com.gs.collections.api.block.function.primitive.LongFunction;
import com.gs.collections.api.block.function.primitive.ShortFunction;

public final class PrimitiveFunctions
{
    private static final IntegerIsPositive INTEGER_IS_POSITIVE = new IntegerIsPositive();
    private static final UnboxNumberToInt UNBOX_NUMBER_TO_INT = new UnboxNumberToInt();
    private static final UnboxIntegerToByte UNBOX_INTEGER_TO_BYTE = new UnboxIntegerToByte();
    private static final UnboxIntegerToChar UNBOX_INTEGER_TO_CHAR = new UnboxIntegerToChar();
    private static final UnboxIntegerToInt UNBOX_INTEGER_TO_INT = new UnboxIntegerToInt();
    private static final UnboxIntegerToFloat UNBOX_INTEGER_TO_FLOAT = new UnboxIntegerToFloat();
    private static final UnboxIntegerToLong UNBOX_INTEGER_TO_LONG = new UnboxIntegerToLong();
    private static final UnboxIntegerToShort UNBOX_INTEGER_TO_SHORT = new UnboxIntegerToShort();
    private static final UnboxIntegerToDouble UNBOX_INTEGER_TO_DOUBLE = new UnboxIntegerToDouble();
    private static final UnboxDoubleToDouble UNBOX_DOUBLE_TO_DOUBLE = new UnboxDoubleToDouble();
    private static final UnboxFloatToFloat UNBOX_FLOAT_TO_FLOAT = new UnboxFloatToFloat();
    private static final UnboxNumberToFloat UNBOX_NUMBER_TO_FLOAT = new UnboxNumberToFloat();
    private static final UnboxNumberToLong UNBOX_NUMBER_TO_LONG = new UnboxNumberToLong();
    private static final UnboxNumberToDouble UNBOX_NUMBER_TO_DOUBLE = new UnboxNumberToDouble();

    private PrimitiveFunctions()
    {
        throw new AssertionError("Suppress default constructor for noninstantiability");
    }

    public static BooleanFunction<Integer> integerIsPositive()
    {
        return INTEGER_IS_POSITIVE;
    }

    public static IntFunction<Number> unboxNumberToInt()
    {
        return UNBOX_NUMBER_TO_INT;
    }

    public static ByteFunction<Integer> unboxIntegerToByte()
    {
        return UNBOX_INTEGER_TO_BYTE;
    }

    public static CharFunction<Integer> unboxIntegerToChar()
    {
        return UNBOX_INTEGER_TO_CHAR;
    }

    public static IntFunction<Integer> unboxIntegerToInt()
    {
        return UNBOX_INTEGER_TO_INT;
    }

    public static FloatFunction<Number> unboxNumberToFloat()
    {
        return UNBOX_NUMBER_TO_FLOAT;
    }

    public static LongFunction<Number> unboxNumberToLong()
    {
        return UNBOX_NUMBER_TO_LONG;
    }

    public static DoubleFunction<Number> unboxNumberToDouble()
    {
        return UNBOX_NUMBER_TO_DOUBLE;
    }

    public static FloatFunction<Integer> unboxIntegerToFloat()
    {
        return UNBOX_INTEGER_TO_FLOAT;
    }

    public static LongFunction<Integer> unboxIntegerToLong()
    {
        return UNBOX_INTEGER_TO_LONG;
    }

    public static ShortFunction<Integer> unboxIntegerToShort()
    {
        return UNBOX_INTEGER_TO_SHORT;
    }

    public static DoubleFunction<Integer> unboxIntegerToDouble()
    {
        return UNBOX_INTEGER_TO_DOUBLE;
    }

    public static DoubleFunction<Double> unboxDoubleToDouble()
    {
        return UNBOX_DOUBLE_TO_DOUBLE;
    }

    public static FloatFunction<Float> unboxFloatToFloat()
    {
        return UNBOX_FLOAT_TO_FLOAT;
    }

    private static class IntegerIsPositive
            implements BooleanFunction<Integer>
    {
        private static final long serialVersionUID = 1L;

        public boolean booleanValueOf(Integer integer)
        {
            return integer.intValue() > 0;
        }
    }

    private static class UnboxNumberToInt
            implements IntFunction<Number>
    {
        private static final long serialVersionUID = 1L;

        public int intValueOf(Number number)
        {
            return number.intValue();
        }
    }

    private static class UnboxIntegerToByte
            implements ByteFunction<Integer>
    {
        private static final long serialVersionUID = 1L;

        public byte byteValueOf(Integer integer)
        {
            return integer.byteValue();
        }
    }

    private static class UnboxIntegerToChar
            implements CharFunction<Integer>
    {
        private static final long serialVersionUID = 1L;

        public char charValueOf(Integer integer)
        {
            return (char) integer.intValue();
        }
    }

    private static class UnboxIntegerToInt
            implements IntFunction<Integer>
    {
        private static final long serialVersionUID = 1L;

        public int intValueOf(Integer integer)
        {
            return integer;
        }
    }

    private static class UnboxIntegerToFloat
            implements FloatFunction<Integer>
    {
        private static final long serialVersionUID = 1L;

        public float floatValueOf(Integer integer)
        {
            return integer;
        }
    }

    private static class UnboxIntegerToLong
            implements LongFunction<Integer>
    {
        private static final long serialVersionUID = 1L;

        public long longValueOf(Integer integer)
        {
            return integer;
        }
    }

    private static class UnboxIntegerToShort
            implements ShortFunction<Integer>
    {
        private static final long serialVersionUID = 1L;

        public short shortValueOf(Integer integer)
        {
            return integer.shortValue();
        }
    }

    private static class UnboxIntegerToDouble
            implements DoubleFunction<Integer>
    {
        private static final long serialVersionUID = 1L;

        public double doubleValueOf(Integer integer)
        {
            return integer;
        }
    }

    private static class UnboxDoubleToDouble
            implements DoubleFunction<Double>
    {
        private static final long serialVersionUID = 1L;

        public double doubleValueOf(Double aDouble)
        {
            return aDouble;
        }
    }

    private static class UnboxFloatToFloat implements FloatFunction<Float>
    {
        private static final long serialVersionUID = 1L;

        public float floatValueOf(Float aFloat)
        {
            return aFloat;
        }
    }

    private static class UnboxNumberToFloat
            implements FloatFunction<Number>
    {
        private static final long serialVersionUID = 1L;

        public float floatValueOf(Number number)
        {
            return number.floatValue();
        }
    }

    private static class UnboxNumberToLong
            implements LongFunction<Number>
    {
        private static final long serialVersionUID = 1L;

        public long longValueOf(Number number)
        {
            return number.longValue();
        }
    }

    private static class UnboxNumberToDouble
            implements DoubleFunction<Number>
    {
        private static final long serialVersionUID = 1L;

        public double doubleValueOf(Number number)
        {
            return number.doubleValue();
        }
    }
}
