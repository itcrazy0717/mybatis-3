/**
 *    Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.cache.decorators;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;

/**
 * 基于最少使用的淘汰机制<br/>
 * Lru (least recently used) cache decorator.
 *
 * @author Clinton Begin
 */
public class LruCache implements Cache {

  /**
   * 装饰的Cache对象
   */
  private final Cache delegate;

  /**
   * 基于LinkedHashMap实现淘汰机制
   */
  private Map<Object, Object> keyMap;

  /**
   * 最老的键，即要被淘汰的
   */
  private Object eldestKey;

  public LruCache(Cache delegate) {
    this.delegate = delegate;
    // 初始化keyMap对象
    setSize(1024);
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  public void setSize(final int size) {
    // 在LinkedHashMpa的构造函数中，当accessOrder为true时，会按照访问顺序排序，最近访问的放在最前面，最早访问的放在后面
    keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
      private static final long serialVersionUID = 4267176411845948333L;

      // LinkedHashMap自带的判断是否删除最老的元素，默认返回false，即不删除老元素
      // 因此重写此方法，当满足一定条件的时候删除老数据
      @Override
      protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
        boolean tooBig = size() > size;
        if (tooBig) {
          eldestKey = eldest.getKey();
        }
        return tooBig;
      }
    };
  }

  @Override
  public void putObject(Object key, Object value) {
    delegate.putObject(key, value);
    cycleKeyList(key);
  }

  @Override
  public Object getObject(Object key) {
    // 刷新keyMap的访问顺序
    keyMap.get(key); //touch
    return delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
    keyMap.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  private void cycleKeyList(Object key) {
    // 添加到keyMap中
    keyMap.put(key, key);
    // 如果超过上限，则移除最少使用的那个
    if (eldestKey != null) {
      delegate.removeObject(eldestKey);
      eldestKey = null;// 置空
    }
  }

}
