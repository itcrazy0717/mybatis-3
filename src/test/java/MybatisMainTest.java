import java.io.Reader;

import org.apache.ibatis.BaseDataTest;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.submitted.complex_column.Person;
import org.apache.ibatis.submitted.complex_column.PersonMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * @author: dengxin.chen
 * @date: 2019-07-22 17:03
 * @description:
 */
public class MybatisMainTest {

  private static SqlSessionFactory sqlSessionFactory;

  @BeforeAll
  static void initDatabase() throws Exception {
    try (Reader reader = Resources.getResourceAsReader("org/apache/ibatis/submitted/complex_column/ibatisConfig.xml")) {
      sqlSessionFactory = new SqlSessionFactoryBuilder().build(reader);
    }

    BaseDataTest.runScript(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource(),
                           "org/apache/ibatis/submitted/complex_column/CreateDB.sql");
  }

  @Test
  public void mainTest() {
    try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
      PersonMapper personMapper = sqlSession.getMapper(PersonMapper.class);
      Person person = personMapper.getWithoutComplex(2L);
      Assertions.assertNotNull(person, "person must not be null");
      Assertions.assertEquals("Christian", person.getFirstName());
      Assertions.assertEquals("Poitras", person.getLastName());
      Person parent = person.getParent();
      Assertions.assertNotNull(parent, "parent must not be null");
      Assertions.assertEquals("John", parent.getFirstName());
      Assertions.assertEquals("Smith", parent.getLastName());
    }
  }

}
