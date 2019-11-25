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
package com.learning.test.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.learning.test.domain.Person;

public interface PersonMapper {

  /**
   * 通过#符拼接sql 测试$符和#的区别
   *
   * @param id
   * @return
   */
  Person getPersonByIdFromPound(Long id);

  /**
   * /* 通过$符拼接sql 测试$符和#的区别
   *
   * @param id
   * @return
   */
  Person getPersonByIdFromDollar(Long id);

  /**
   * 分页插件测试
   *
   * @return
   */
  List<Person> listPerson();

  /**
   * 测试多参数解析
   *
   * @param id
   * @param firstName
   * @return
   */
  Person getPerson(@Param("id") Long id, @Param("firstName") String firstName);

  void updateById(Person person);

  void insert(Person person);
}
