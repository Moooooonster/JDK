/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
package jdk.vm.ci.meta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Provides access to the metadata of a class typically provided in a class file.
 */
public interface MetaAccessProvider {

    /**
     * Returns the resolved Java type representing a given Java class.
     *
     * @param clazz the Java class object
     * @return the resolved Java type object
     */
    ResolvedJavaType lookupJavaType(Class<?> clazz);

    /**
     * Returns the resolved Java types representing some given Java classes.
     *
     * @param classes the Java class objects
     * @return the resolved Java type objects
     */
    default ResolvedJavaType[] lookupJavaTypes(Class<?>[] classes) {
        ResolvedJavaType[] result = new ResolvedJavaType[classes.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = lookupJavaType(classes[i]);
        }
        return result;
    }

    /**
     * Provides the {@link ResolvedJavaMethod} for a {@link Method} or {@link Constructor} obtained
     * via reflection.
     */
    ResolvedJavaMethod lookupJavaMethod(Executable reflectionMethod);

    /**
     * Provides the {@link ResolvedJavaField} for a {@link Field} obtained via reflection.
     */
    ResolvedJavaField lookupJavaField(Field reflectionField);

    /**
     * Returns the resolved Java type of the given {@link JavaConstant} object.
     *
     * @return {@code null} if {@code constant.isNull() || !constant.kind.isObject()}
     */
    ResolvedJavaType lookupJavaType(JavaConstant constant);

    /**
     * Returns the number of bytes occupied by this constant value or constant object.
     *
     * @param constant the constant whose bytes should be measured
     * @return the number of bytes occupied by this constant
     */
    long getMemorySize(JavaConstant constant);

    /**
     * Parses a
     * <a href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.3">method
     * descriptor</a> into a {@link Signature}. The behavior of this method is undefined if the
     * method descriptor is not well formed.
     */
    Signature parseMethodDescriptor(String methodDescriptor);

    /**
     * Encodes a deoptimization action and a deoptimization reason in an integer value.
     *
     * @param debugId an integer that can be used to track the origin of a deoptimization at
     *            runtime. There is no guarantee that the runtime will use this value. The runtime
     *            may even keep fewer than 32 bits.
     *
     * @return the encoded value as an integer
     */
    JavaConstant encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason, int debugId);

    DeoptimizationReason decodeDeoptReason(JavaConstant constant);

    DeoptimizationAction decodeDeoptAction(JavaConstant constant);

    int decodeDebugId(JavaConstant constant);
}