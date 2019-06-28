/**
 * Copyright 2009-2019 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 对象包装器接口，基于MetaClass工具类，可以说ObjectWrapper是对MetaClass的指定类的具体化
 * @author Clinton Begin
 */
public interface ObjectWrapper {

  /**
   * 获得值
   * @param prop PropertyTokenizer对象，相当于键
   * @return
   */
  Object get(PropertyTokenizer prop);

  /**
   * 设置值
   * @param prop PropertyTokenizer对象，相当于键
   * @param value 值
   */
  void set(PropertyTokenizer prop, Object value);

  /**
   * {@link org.apache.ibatis.reflection.MetaClass#findProperty(String, boolean)}
   * @param name
   * @param useCamelCaseMapping
   * @return
   */
  String findProperty(String name, boolean useCamelCaseMapping);

  /**
   * {@link MetaClass#getGetterNames()}
   * @return
   */
  String[] getGetterNames();

  /**
   * {@link MetaClass#getSetterNames()}
   * @return
   */
  String[] getSetterNames();

  /**
   * {@link org.apache.ibatis.reflection.MetaClass#getSetterType(String)}
   * @param name
   * @return
   */
  Class<?> getSetterType(String name);

  /**
   * {@link org.apache.ibatis.reflection.MetaClass#getGetterType(String)}
   * @param name
   * @return
   */
  Class<?> getGetterType(String name);

  /**
   * {@link org.apache.ibatis.reflection.MetaClass#hasSetter(String)}
   * @param name
   * @return
   */
  boolean hasSetter(String name);

  /**
   * {@link org.apache.ibatis.reflection.MetaClass#hasGetter(String)}
   * @param name
   * @return
   */
  boolean hasGetter(String name);

  /**
   * {@link MetaObject#forObject(Object, ObjectFactory, ObjectWrapperFactory, ReflectorFactory)}
   * @param name
   * @param prop
   * @param objectFactory
   * @return
   */
  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

  /**
   * 是否为集合
   * @return
   */
  boolean isCollection();

  /**
   * 添加元素到集合
   * @param element
   */
  void add(Object element);

  /**
   * 添加多个元素到集合
   * @param element
   * @param <E>
   */
  <E> void addAll(List<E> element);

}
