/**
 *    Copyright 2009-2018 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
public class MetaClass {

  private final ReflectorFactory reflectorFactory;
  private final Reflector reflector;

  // 注意：MetaClass类的构造方法为private的
  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    // 根据类型创建Reflector
    this.reflector = reflectorFactory.findForClass(type);
  }

  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  public MetaClass metaClassForProperty(String name) {
    // 获得属性的类
    Class<?> propType = reflector.getGetterType(name);
    // 创建MetaClass对象
    return MetaClass.forClass(propType, reflectorFactory);
  }

  public String findProperty(String name) {
    // 构建属性
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  public String findProperty(String name, boolean useCamelCaseMapping) {
    // 下划线转驼峰
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    // 获得属性
    return findProperty(name);
  }

  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      return reflector.getSetterType(prop.getName());
    }
  }

  public Class<?> getGetterType(String name) {
    // 创建PropertyTokenizer对象，对name进行分词
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 有子表达式
    if (prop.hasNext()) {
      // 创建MetaClass对象
      MetaClass metaProp = metaClassForProperty(prop);
      // 递归判断子表达式children，获得返回值的类型
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    // 直接获得返回值得类型
    return getGetterType(prop);
  }

  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    // 获得getting方法返回的类型
    Class<?> propType = getGetterType(prop);
    // 创建MetaClass对象
    return MetaClass.forClass(propType, reflectorFactory);
  }

  private Class<?> getGetterType(PropertyTokenizer prop) {
    // 获得返回类型
    Class<?> type = reflector.getGetterType(prop.getName());
    // 如果类型为泛型
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      // 获得返回的类型
      Type returnType = getGenericGetterType(prop.getName());
      // 如果是泛型，则解析其真正的类型
      if (returnType instanceof ParameterizedType) {
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        // 因为Collection是Collection<E>，存在一个泛型的参数
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }

  private Type getGenericGetterType(String propertyName) {
    try {
      // 获得Invoker对象
      Invoker invoker = reflector.getGetInvoker(propertyName);
      // 如果MethodInvoker对象，则说明是getting方法，解析方法返回类型
      if (invoker instanceof MethodInvoker) {
        Field _method = MethodInvoker.class.getDeclaredField("method");
        _method.setAccessible(true);
        Method method = (Method) _method.get(invoker);
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
      // 如果GetFieldInvoker对象，则说明是Field，直接访问
      } else if (invoker instanceof GetFieldInvoker) {
        Field _field = GetFieldInvoker.class.getDeclaredField("field");
        _field.setAccessible(true);
        Field field = (Field) _field.get(invoker);
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
    }
    return null;
  }

  public boolean hasSetter(String name) {
    // 属性分词器，用于解析属性名
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasSetter(prop.getName())) {
        // 为属性创建MetaClass
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasSetter(prop.getName());
    }
  }

  public boolean hasGetter(String name) {
    // 创建PropertyTokenizer对象，对name进行分词
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 有子表达式
    if (prop.hasNext()) {
      // 判断是否有该属性的getting方法
      if (reflector.hasGetter(prop.getName())) {
        // 创建MetaClass对象
        MetaClass metaProp = metaClassForProperty(prop);
        // 递归判断子表达式，是否有getting方法
        return metaProp.hasGetter(prop.getChildren());
      } else {
        return false;
      }
    // 无子表达式
    } else {
      // 判断是否有该属性的getting方法
      return reflector.hasGetter(prop.getName());
    }
  }

  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  private StringBuilder buildProperty(String name, StringBuilder builder) {
    // 创建PropertyToken对象，对name进行分词
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 有子表达式
    if (prop.hasNext()) {
      // 获得属性名，并添加到builder中
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        // 拼接属性到builder中
        builder.append(propertyName);
        builder.append(".");
        // 创建MetaClass对象
        MetaClass metaProp = metaClassForProperty(propertyName);
        // 递归解析子表达式children，并将结果添加到builder中
        metaProp.buildProperty(prop.getChildren(), builder);
      }
      // 无表达式
    } else {
      // 获得属性名，并添加到builder中
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
