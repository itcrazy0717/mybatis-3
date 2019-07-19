package org.apache.ibatis.builder;

import java.util.HashMap;

import org.apache.ibatis.domain.blog.Author;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

/**
 * @author: dengxin.chen
 * @date: 2019-07-19 14:57
 * @description:
 */
public class SqlSourceBuilderTest {

  @Test
  public void test()
  {
    // 带有复杂 #{} 占位符的参数，接下里会解析这个占位符
    String sql = "SELECT * FROM Author WHERE username = #{username,javaType=int,jdbcType=NUMERIC}";
    SqlSourceBuilder sqlSourceBuilder = new SqlSourceBuilder(new Configuration());
    SqlSource sqlSource = sqlSourceBuilder.parse(sql, Author.class, new HashMap<>());
    BoundSql boundSql = sqlSource.getBoundSql(new Author());

    System.out.println(String.format("SQL: %s\n", boundSql.getSql()));
    System.out.println(String.format("ParameterMappings: %s", boundSql.getParameterMappings()));
  }
}
