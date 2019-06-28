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
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 */
public class Reflector {

  /**
   * 对应的类
   */
  private final Class<?> type;

  /**
   * 可读属性集合
   */
  private final String[] readablePropertyNames;

  /**
   * 可写属性集合
   */
  private final String[] writablePropertyNames;

  /**
   * 属性对应的setting方法的映射
   * key 属性名称
   * value 为Invoker对象
   */
  private final Map<String, Invoker> setMethods = new HashMap<>();

  /**
   * 属性对应的getting方法的映射
   * key 属性名称
   * value 为Invoker对象
   */
  private final Map<String, Invoker> getMethods = new HashMap<>();

  /**
   * 属性对应的setting方法的参数类型的映射 {@link #setMethods}
   * key 为属性名称
   * value 为方法参数类型
   */
  private final Map<String, Class<?>> setTypes = new HashMap<>();

  /**
   * 属性对应的getting方法的参数类型的映射 {@link #getMethods}
   * key 为属性名称
   * value 为方法参数类型
   */
  private final Map<String, Class<?>> getTypes = new HashMap<>();

  /**
   * 默认构造方法
   */
  private Constructor<?> defaultConstructor;

  /**
   * 不区分大小写的属性集合
   */
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  public Reflector(Class<?> clazz) {
    // 设置对应类
    type = clazz;
    // 初始化defaultConstructor
    addDefaultConstructor(clazz);
    // 初始化getMethods和getTypes，通过遍历getting方法
    addGetMethods(clazz);
    // 初始化setMethods和setTypes，通过遍历setting方法
    addSetMethods(clazz);
    // 初始化getMethods和getTypes、setMethods和setTypes，通过遍历fields属性
    addFields(clazz);
    // 初始化readablePropertyNames、writablePropertyNames、caseInsensitivePropertyMap属性
    readablePropertyNames = getMethods.keySet().toArray(new String[getMethods.keySet().size()]);
    writablePropertyNames = setMethods.keySet().toArray(new String[setMethods.keySet().size()]);
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  private void addDefaultConstructor(Class<?> clazz) {
    // 获得所有构造方法
    Constructor<?>[] consts = clazz.getDeclaredConstructors();
    // 遍历所有构造方法，查找无参的构造方法
    for (Constructor<?> constructor : consts) {
      // 构造方法无参数，则为默认构造方法
      if (constructor.getParameterTypes().length == 0) {
        // 赋值给defaultConstructor
        this.defaultConstructor = constructor;
      }
    }
  }

  private void addGetMethods(Class<?> cls) {
    // 属性与其getting方法的映射
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    // 获得所有方法
    Method[] methods = getClassMethods(cls);
    // 遍历所有方法
    for (Method method : methods) {
      // 如果参数大于0，则说明不是getting方法，忽略
      if (method.getParameterTypes().length > 0) {
        continue;
      }
      // 以get和is方法名开头，说明是getting方法
      String name = method.getName();
      if ((name.startsWith("get") && name.length() > 3)
          || (name.startsWith("is") && name.length() > 2)) {
        // 获得属性
        name = PropertyNamer.methodToProperty(name);
        // 添加到conflictingGetters集合中
        addMethodConflict(conflictingGetters, name, method);
      }
    }
    // 解决getting冲突方法
    resolveGetterConflicts(conflictingGetters);
  }

  // 解决getting冲突方法，最终一个属性只保留一个对应的方法
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    // 遍历每个属性，查找其最匹配的方法，因为子类可以覆写父类的方法，所以一个属性可能对应多个getting方法
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      // 最匹配的方法
      Method winner = null;
      String propName = entry.getKey();
      for (Method candidate : entry.getValue()) {
        // 最开始winner为空，先将其赋值为candidate，然后继续循环
        if (winner == null) {
          winner = candidate;
          continue;
        }
        // 基于返回类型比较
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        /**
         * 当两个方法的返回值类型一致，若两个方法返回值类型均为boolean，则选取isXXX方法为winner
         * 否则无法决定选择哪个方法更为合适，只能抛出异常
         */
        if (candidateType.equals(winnerType)) {
          if (!boolean.class.equals(candidateType)) {
            throw new ReflectionException(
              "Illegal overloaded getter method with ambiguous type for property "
              + propName + " in class " + winner.getDeclaringClass()
              + ". This breaks the JavaBeans specification and can cause unpredictable results.");
            // 选择boolean类型的is方法
          } else if (candidate.getName().startsWith("is")) {
            winner = candidate;
          }
          // 如果winnerType是candidateType的子类，则winnerType不变，因为子类可能更为具体
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
        // 如果candidateType是winner子类，则winner更新为candidateType
        } else if (winnerType.isAssignableFrom(candidateType)) {
          winner = candidate;
        // 返回类型冲突，抛出异常
        } else {
          throw new ReflectionException(
            "Illegal overloaded getter method with ambiguous type for property "
            + propName + " in class " + winner.getDeclaringClass()
            + ". This breaks the JavaBeans specification and can cause unpredictable results.");
        }
      }
      // 添加到getMethods和getTypes中
      addGetMethod(propName, winner);
    }
  }

  private void addGetMethod(String name, Method method) {
    // 判断是否为合理的属性名
    if (isValidPropertyName(name)) {
      // 添加到getMethods中
      getMethods.put(name, new MethodInvoker(method));
      // 添加到
      Type returnType = TypeParameterResolver.resolveReturnType(method, type);
      getTypes.put(name, typeToClass(returnType));
    }
  }

  private void addSetMethods(Class<?> cls) {
    // 属性与其setting方法的映射
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    // 获得所有方法
    Method[] methods = getClassMethods(cls);
    // 遍历所有方法
    for (Method method : methods) {
      String name = method.getName();
      // 方法名为set开头
      if (name.startsWith("set") && name.length() > 3) {
        if (method.getParameterTypes().length == 1) {
          // 获得属性
          name = PropertyNamer.methodToProperty(name);
          // 添加到conflictingSetters集合中
          addMethodConflict(conflictingSetters, name, method);
        }
      }
    }
    // 解决setting冲突方法
    resolveSetterConflicts(conflictingSetters);
  }

  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
    list.add(method);
  }

  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    // 遍历每个属性，查找最匹配的方法，因为子类可以覆写父类的方法，所以每一个属性，可能对应多个setting方法
    for (String propName : conflictingSetters.keySet()) {
      List<Method> setters = conflictingSetters.get(propName);
      /**
       * 获取getter方法的返回值类型，通过返回值类型反推哪个setter更合适
       */
      Class<?> getterType = getTypes.get(propName);
      Method match = null;
      ReflectionException exception = null;
      // 遍历属性对应的setting方法
      for (Method setter : setters) {
        // 参数类型
        Class<?> paramType = setter.getParameterTypes()[0];
        // 参数类型和返回类型一致，则认为是最好的选择，并结束循环
        if (paramType.equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        if (exception == null) {
          try {
            // 选择一个更加匹配的方法
            match = pickBetterSetter(match, setter, propName);
          } catch (ReflectionException e) {
            // there could still be the 'best match'
            match = null;
            exception = e;
          }
        }
      }
      if (match == null) {
        throw exception;
      } else {
        // 添加到setMethods和setTypes中
        addSetMethod(propName, match);
      }
    }
  }

  private Method pickBetterSetter(Method setter1, Method setter2, String property) {

    if (setter1 == null) {
      return setter2;
    }
    // 获取参数1和参数2
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    // 如果参数2可以赋值给参数1，即参数2是参数1的子类，则认为参数2对应的setter方法更合适
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    // 如果参数1可以赋值给参数2，即参数1是参数2的子类，则认为参数1对应的setter方法更合适
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    // 两种参数类型不相关，则抛出异常
    throw new ReflectionException("Ambiguous setters defined for property '" + property + "' in class '"
                                  + setter2.getDeclaringClass() + "' with types '" + paramType1.getName() + "' and '"
                                  + paramType2.getName() + "'.");
  }

  private void addSetMethod(String name, Method method) {
    if (isValidPropertyName(name)) {
      // 添加到setMethods中
      setMethods.put(name, new MethodInvoker(method));
      Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
      // 添加到setTypes中
      setTypes.put(name, typeToClass(paramTypes[0]));
    }
  }

  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    // 普通类型，直接使用类
    if (src instanceof Class) {
      result = (Class<?>) src;
    // 泛型类型，使用泛型
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    // 泛型数组，获得具体类
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      // 普通类型
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        // 递归该方法，返回类
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    // 都不符合，则 使用Object类
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  private void addFields(Class<?> clazz) {
    // 获取所有的field
    Field[] fields = clazz.getDeclaredFields();
    // 进行遍历
    for (Field field : fields) {
       // 添加到setMethods和setTypes中
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        int modifiers = field.getModifiers();
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          addSetField(field);
        }
      }
      // 添加到getMethods和getTypes中
      if (!getMethods.containsKey(field.getName())) {
        addGetField(field);
      }
    }
    // 递归处理父类
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  private void addSetField(Field field) {
    // 判断field属性命名的合理性
    if (isValidPropertyName(field.getName())) {
      // 添加到setMethods集合中
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      // 添加到setTypes集合中
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    // 判断field属性命名的合理性
    if (isValidPropertyName(field.getName())) {
      // 添加到getMethods集合中
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      // 添加到getTypes集合中
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler <code>Class.getMethods()</code>,
   * because we want to look for private methods as well.
   *
   * @param cls The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> cls) {
    // 每个方法签名与该方法的映射
    Map<String, Method> uniqueMethods = new HashMap<>();
    // 循环类，类的父类，父类的父类，直到父类为Object
    Class<?> currentClass = cls;
    while (currentClass != null && currentClass != Object.class) {
      // 记录当前类定义的方法
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      // 记录接口中定义的方法
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }
      // 获得父类
      currentClass = currentClass.getSuperclass();
    }

    // 转换成Method数组返回
    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[methods.size()]);
  }

  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      // bridge方法 参见https://www.zhihu.com/question/54895701/answer/141623158
      if (!currentMethod.isBridge()) {
        // 获得方法签名
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        // 当前uniqueMethods不存在时，进行添加
        if (!uniqueMethods.containsKey(signature)) {
          // 添加到uniqueMethods中
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    // 返回类型
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    // 方法名
    sb.append(method.getName());
    // 方法参数
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      if (i == 0) {
        sb.append(':');
      } else {
        sb.append(',');
      }
      sb.append(parameters[i].getName());
    }
    // 返回格式：returnType#方法名：参数名1，参数名2，参数名3
    // void#checkPackageAccess:java.lang.ClassLoader,boolean
    return sb.toString();
  }

  /**
   * 判断是否可以修改可访问性<br/>
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.keySet().contains(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.keySet().contains(propertyName);
  }

  /**
   * 不区分大小写的属性集合
   * @param name
   * @return
   */
  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
