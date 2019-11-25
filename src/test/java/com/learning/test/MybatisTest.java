package com.learning.test;

import java.io.Reader;
import java.util.List;

import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.learning.test.domain.Person;
import com.learning.test.mapper.PersonMapper;

/**
 * @author: dengxin.chen
 * @date: 2019-07-22 17:03
 * @description:
 */
public class MybatisTest {

  private static SqlSessionFactory sqlSessionFactory;

  @BeforeAll
  static void initDatabase() throws Exception {
    try (Reader reader = Resources.getResourceAsReader("com/learning/test/config/mybatis-config.xml")) {
      sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
    }

    BaseDataTest.runScript(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(),
                           "com/learning/test/db/CreateDB.sql");
  }


  /**
   * 基础测试
   */
  @Test
  public void baseTest() {
    SqlSession sqlSession = sqlSessionFactory.openSession();
    try {
      PersonMapper personMapper = sqlSession.getMapper(PersonMapper.class);
      Person person1 = personMapper.getPersonByIdFromPound(2L);
      Person person2 = personMapper.getPersonByIdFromDollar(2L);
      System.out.println(person1.toString());
      System.out.println(person2.toString());
    } finally {
      sqlSession.close();
    }
  }

  /**
   * 一级缓存测试 注意需要关闭二级缓存
   */
  @Test
  public void firstCacheTest() {
    SqlSession sqlSession = sqlSessionFactory.openSession();
    try {
      PersonMapper personMapper = sqlSession.getMapper(PersonMapper.class);
      Person person1 = personMapper.getPersonByIdFromDollar(2L);
      System.out.println(person1.toString());
      System.out.println("第二次会话相同，获取到了缓存吗？如果未打印sql则获取到了缓存");
      personMapper.getPersonByIdFromDollar(2L);
    } finally {
      sqlSession.close();
    }
  }

  /**
   * 测试二级缓存 注意需在mapper.xml文件中进行二级缓存配置
   */
  @Test
  public void secondCacheTest() {
    SqlSession sqlSession1 = sqlSessionFactory.openSession();
    SqlSession sqlSession2 = sqlSessionFactory.openSession();
    try {
      PersonMapper personMapper1 = sqlSession1.getMapper(PersonMapper.class);
      PersonMapper personMapper2 = sqlSession2.getMapper(PersonMapper.class);

      Person person1 = personMapper1.getPersonByIdFromDollar(2L);
      System.out.println("会话1的查询结果: " + person1.toString());
      // 由于二级缓存是事务性的，所以必须commit才能将缓存进行更新
      sqlSession1.commit();

      Person person2 = personMapper2.getPersonByIdFromDollar(2L);
      System.out.println("会话2的查询结果：" + person2.toString());

    } finally {
      sqlSession1.close();
      sqlSession2.close();
    }
  }

  /**
   * 测试一级缓存失效
   */
  @Test
  public void testCacheInvalid() {
    SqlSession sqlSession = sqlSessionFactory.openSession();
    try {
      PersonMapper personMapper = sqlSession.getMapper(PersonMapper.class);
      Person person1 = personMapper.getPersonByIdFromDollar(2L);
      System.out.println(person1.toString());
      // 插入数据
      Person person = new Person();
      person.setId(4L);
      person.setFirstName("tom");
      person.setLastName("chen");
      personMapper.insert(person);

      System.out.println("执行相同会话，缓存会失效吗");
      Person person2 = personMapper.getPersonByIdFromDollar(2L);
      System.out.println(person2);
    } finally {
      sqlSession.close();
    }
  }

  /**
   * 测试一级缓存跨会话出现脏读的情况，注意必须关闭二级缓存
   */
  @Test
  public void testDirtyRead() {
    SqlSession sqlSession1 = sqlSessionFactory.openSession();
    SqlSession sqlSession2 = sqlSessionFactory.openSession();
    try {
      PersonMapper personMapper1 = sqlSession1.getMapper(PersonMapper.class);
      PersonMapper personMapper2 = sqlSession2.getMapper(PersonMapper.class);

      Person person1 = personMapper1.getPersonByIdFromDollar(2L);
      System.out.println(person1.toString());

      // 会话2更新了一级缓存
      Person person = new Person();
      person.setId(2L);
      person.setFirstName("tom");
      person.setLastName("chen");
      personMapper2.updateById(person);

      Person person2 = personMapper2.getPersonByIdFromDollar(2L);
      System.out.println("会话2更新后的数据：" + person2.toString());

      System.out.println("会话1会查询到最新的数据吗");
      Person person3 = personMapper1.getPersonByIdFromDollar(2L);
      System.out.println(person3);
    } finally {
      sqlSession1.close();
      sqlSession2.close();
    }
  }

  /**
   * 分页插件测试 针对不同数据库方言会产生不同的分页sql语句
   */
  @Test
  public void pageHelperTest() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      PageHelper.startPage(2, 5);
      PersonMapper personMapper = sqlSession.getMapper(PersonMapper.class);
      List<Person> list = personMapper.listPerson();
      PageInfo pageInfo = new PageInfo(list);
      System.out.println("结果：");
      pageInfo.getList().forEach(e -> System.out.println(e.toString()));
    }
  }

}
