/**
 * Copyright 2009-2017 the original author or authors.
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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * 属性分词器<br/>
 * 对复杂属性进行分词 复杂属性如一个类的属性为另一个类，在进行取值的时候写成形如Bean.xx.xx的形式<br/>
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {

  /**
   * 当前字符串
   */
  private String name;

  /**
   * 索引的{@link #name}，因为{@link #name}如果存在，{@link #index}会被更改
   */
  private final String indexedName;

  /**
   * 编号
   * 对于数组name[0]，则index=0
   * 对于Map map[key]，则index=key
   */
  private String index;

  /**
   * 剩余字符串
   */
  private final String children;

  public PropertyTokenizer(String fullname) {
    // 初始化name、children字符串，使用.作为分隔符
    // 属性A.B的形式，这种形式为复杂形式，需进行分词
    int delim = fullname.indexOf('.');
    if (delim > -1) {
      /*
       * 以点位为界，进行分割。比如：
       *    fullname = www.coolblog.xyz
       *
       * 以第一个点为分界符：
       *    name = www
       *    children = coolblog.xyz
       */
      name = fullname.substring(0, delim);
      children = fullname.substring(delim + 1);
    } else {
      name = fullname;
      children = null;
    }
    // 记录当前的name
    indexedName = name;
    // 若存在[，则获得index，并修改name
    delim = name.indexOf('[');
    if (delim > -1) {
      /*
       * 获取中括号里的内容，比如：
       *   1. 对于数组或List集合：[] 中的内容为数组下标，
       *      比如 fullname = articles[1]，index = 1
       *   2. 对于Map：[] 中的内容为键，
       *      比如 fullname = xxxMap[keyName]，index = keyName
       */
      index = name.substring(delim + 1, name.length() - 1);
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  @Override
  public boolean hasNext() {
    return children != null;
  }

  @Override
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }
}
