/*******************************************************************************
 * Copyright (c) 2008, 2012 SAP AG and IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    SAP AG - initial API and implementation
 *    Andrew Johnson - matching of overloaded methods
 *******************************************************************************/
package org.eclipse.mat.parser.internal.oql.compiler;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.parser.internal.Messages;
import org.eclipse.mat.parser.internal.oql.compiler.CompilerImpl.ConstantExpression;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.util.IProgressListener.OperationCanceledException;
import org.eclipse.mat.util.MessageUtil;
import org.eclipse.mat.util.PatternUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Pattern;

class MethodCallExpression extends Expression {
    String name;
    List<Expression> parameters;

    public MethodCallExpression(String name, List<Expression> parameters) {
        this.name = name;
        this.parameters = parameters;
    }

    @Override
    public Object compute(EvaluationContext ctx) throws SnapshotException, OperationCanceledException {
        Object subject = ctx.getSubject();
        if (subject == null)
            return null;

        if (subject.getClass().isArray())
            subject = PathExpression.asList(subject);

        // compute arguments
        Object[] arguments = new Object[parameters.size()];
        for (int ii = 0; ii < arguments.length; ii++)
            arguments[ii] = parameters.get(ii).compute(ctx);

        // special handling for #toString() and IObjects
        if (subject instanceof IObject && "toString".equals(this.name) && parameters.isEmpty()) {
            String name = ((IObject) subject).getClassSpecificName();
            return name != null ? name : ((IObject) subject).getTechnicalName();
        }

        /*
         * Finding the right method is tricky as the arguments have already been boxed.
         * E.g. consider overloaded methods 
         * remove(int)
         * remove(Object)
         * with argument Integer(1).
         */
        List<Method> extraMethods = new ArrayList<Method>();
        final Class<? extends Object> subjectClass = subject.getClass();
        Method[] methods;
        methods = subjectClass.getMethods();
        if (!Modifier.isPublic(subjectClass.getModifiers())) {
            // Non-public class public methods are only accessible via
            // interfaces. For example java.util.Arrays$ArrayList.get()
            for (Class<?> superClass = subjectClass; superClass != null; superClass = superClass.getSuperclass()) {
                for (Class<?> c : superClass.getInterfaces()) {
                    firstChoiceMethods(extraMethods, c, arguments);
                }
            }
            firstChoiceMethods(extraMethods, subjectClass, arguments);
            for (Class<?> superClass = subjectClass; superClass != null; superClass = superClass.getSuperclass()) {
                for (Class<?> c : superClass.getInterfaces()) {
                    extraMethods.addAll(Arrays.asList(c.getMethods()));
                }
            }
        } else {
            firstChoiceMethods(extraMethods, subjectClass, arguments);
        }
        if (extraMethods.size() > 0) {
            // Then add the original methods
            extraMethods.addAll(Arrays.asList(methods));
            // Remove duplicates
            extraMethods = new ArrayList<Method>(new LinkedHashSet<Method>(extraMethods));
            methods = extraMethods.toArray(new Method[extraMethods.size()]);
        }
        nextMethod:
        for (int ii = 0; ii < methods.length; ii++) {
            if (methods[ii].getName().equals(this.name)) {
                Class<?>[] parameterTypes = methods[ii].getParameterTypes();
                if (parameterTypes.length == arguments.length) {
                    Object savedArgs[] = null;
                    for (int jj = 0; jj < arguments.length; jj++) {
                        if (arguments[jj] == ConstantExpression.NULL) {
                            arguments[jj] = null;
                        }
                        if (arguments[jj] != null && !isConvertible(parameterTypes[jj], arguments[jj])) {
                            // we do some special magic here...
                            if (parameterTypes[jj].isAssignableFrom(Pattern.class)) {
                                if (savedArgs == null)
                                    savedArgs = new Object[arguments.length];
                                savedArgs[jj] = arguments[jj];
                                arguments[jj] = Pattern.compile(PatternUtil.smartFix(String.valueOf(arguments[jj]),
                                        false));
                            } else {
                                if (savedArgs != null) {
                                    // Restore arguments
                                    for (int ia = 0; ia < savedArgs.length; ++ia) {
                                        if (savedArgs[ia] != null)
                                            arguments[ia] = savedArgs[ia];
                                    }
                                }
                                continue nextMethod;
                            }
                        }
                    }

                    try {
                        return methods[ii].invoke(subject, arguments);
                    } catch (IllegalArgumentException e) {
                        throw new SnapshotException(Arrays.toString(arguments), e);
                    } catch (IllegalAccessException e) {
                        throw new SnapshotException(methods[ii].toString(), e);
                    } catch (InvocationTargetException e) {
                        throw new SnapshotException(e);
                    }
                }
            }
        }

        StringBuilder argTypes = new StringBuilder();
        for (Object arg : arguments) {
            if (argTypes.length() > 0)
                argTypes.append(", ");
            argTypes.append(arg != null ? unboxedType(arg.getClass()).getName() : null);
        }
        throw new SnapshotException(MessageUtil.format(Messages.MethodCallExpression_Error_MethodNotFound,
                new Object[]{this.name, argTypes, subject, subject != null ? subject.getClass().getName() : null}));
    }

    /**
     * Try for a good match on the method.
     * Also try unboxed arguments.
     */
    private void firstChoiceMethods(List<Method> extraMethods, final Class<? extends Object> subjectClass,
                                    Object[] arguments) {
        // find appropriate method
        Class<?>[] argumentTypes1 = new Class<?>[arguments.length];
        Class<?>[] argumentTypes2 = new Class<?>[arguments.length];
        int i = 0;
        boolean unbox = false;
        for (Object args : arguments) {
            if (args != null)
                argumentTypes1[i] = args.getClass();
            argumentTypes2[i] = unboxedType(argumentTypes1[i]);
            if (argumentTypes2 != argumentTypes1)
                unbox = true;
            i++;
        }
        try {
            Method m1 = subjectClass.getMethod(name, argumentTypes1);
            extraMethods.add(m1);
        } catch (SecurityException e1) {
        } catch (NoSuchMethodException e1) {
        }
        if (unbox) try {
            Method m1 = subjectClass.getMethod(name, argumentTypes2);
            extraMethods.add(m1);
        } catch (SecurityException e1) {
        } catch (NoSuchMethodException e1) {
        }
    }

    private Class<?> unboxedType(Class<?> arg) {
        if (arg == Boolean.class) {
            arg = boolean.class;
        }
        if (arg == Byte.class) {
            arg = byte.class;
        } else if (arg == Short.class) {
            arg = short.class;
        } else if (arg == Character.class) {
            arg = char.class;
        } else if (arg == Integer.class) {
            arg = int.class;
        } else if (arg == Long.class) {
            arg = long.class;
        } else if (arg == Float.class) {
            arg = float.class;
        } else if (arg == Double.class) {
            arg = double.class;
        }
        return arg;
    }

    /**
     * Can method invocation convert the argument via unboxing/widening conversion?
     */
    private boolean isConvertible(Class<?> parameterType, Object argument) {
        Class<?> argumentType = argument.getClass();
        if (parameterType.isAssignableFrom(argumentType))
            return true;
        if (argumentType == Boolean.class && (
                parameterType == boolean.class || parameterType == Boolean.class))
            return true;
        if (argumentType == Byte.class && (
                parameterType == byte.class || parameterType == Byte.class
                        || parameterType == short.class || parameterType == Short.class
                        || parameterType == int.class || parameterType == Integer.class
                        || parameterType == long.class || parameterType == Long.class
                        || parameterType == float.class || parameterType == Float.class
                        || parameterType == double.class || parameterType == Double.class))
            return true;
        if (argumentType == Short.class && (
                parameterType == short.class || parameterType == Short.class
                        || parameterType == int.class || parameterType == Integer.class
                        || parameterType == long.class || parameterType == Long.class
                        || parameterType == float.class || parameterType == Float.class
                        || parameterType == double.class || parameterType == Double.class))
            return true;
        if (argumentType == Character.class && (
                parameterType == char.class || parameterType == Character.class
                        || parameterType == int.class || parameterType == Integer.class
                        || parameterType == long.class || parameterType == Long.class
                        || parameterType == float.class || parameterType == Float.class
                        || parameterType == double.class || parameterType == Double.class))
            return true;
        if (argumentType == Integer.class && (
                parameterType == int.class || parameterType == Integer.class
                        || parameterType == long.class || parameterType == Long.class
                        || parameterType == float.class || parameterType == Float.class
                        || parameterType == double.class || parameterType == Double.class))
            return true;
        if (argumentType == Long.class && (
                parameterType == long.class || parameterType == Long.class
                        || parameterType == float.class || parameterType == Float.class
                        || parameterType == double.class || parameterType == Double.class))
            return true;
        if (argumentType == Float.class && (
                parameterType == float.class || parameterType == Float.class
                        || parameterType == double.class || parameterType == Double.class))
            return true;
        if (argumentType == Double.class && (
                parameterType == double.class || parameterType == Double.class))
            return true;
        return false;
    }

    @Override
    public boolean isContextDependent(EvaluationContext ctx) {
        for (Expression element : this.parameters) {
            boolean isContextDependent = element.isContextDependent(ctx);

            if (isContextDependent)
                return true;
        }

        return false;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(256);

        buf.append(name);
        buf.append("(");

        for (Iterator<Expression> iter = this.parameters.iterator(); iter.hasNext(); ) {
            Expression element = iter.next();
            buf.append(element);

            if (iter.hasNext())
                buf.append(",");
        }

        buf.append(")");

        return buf.toString();
    }

}
