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
package org.apache.ibatis.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;

/**
 * 负责将sql语句中的#{}替换成相应的？占位符，并获取该?占位符的ParameterMapping对象<br/>
 * @author Clinton Begin
 */
public class SqlSourceBuilder extends BaseBuilder {

  private static final String PARAMETER_PROPERTIES = "javaType,jdbcType,mode,numericScale,resultMap,typeHandler,jdbcTypeName";

  public SqlSourceBuilder(Configuration configuration) {
    super(configuration);
  }

  /**
   * 解析原始sql，创建SqlSource对象
   * @param originalSql
   * @param parameterType
   * @param additionalParameters
   * @return
   */
  public SqlSource parse(String originalSql, Class<?> parameterType, Map<String, Object> additionalParameters) {
    // 创建ParameterMappingTokenHandler对象 #{}占位符处理器
    ParameterMappingTokenHandler handler = new ParameterMappingTokenHandler(configuration, parameterType, additionalParameters);
    // 创建GenericTokenParser对象 #{}占位符解析器
    GenericTokenParser parser = new GenericTokenParser("#{", "}", handler);
    // 执行解析
    String sql = parser.parse(originalSql);
    // 创建StaticSqlSource对象
    return new StaticSqlSource(configuration, sql, handler.getParameterMappings());
  }

  private static class ParameterMappingTokenHandler extends BaseBuilder implements TokenHandler {

    /**
     * ParameterMapping数组
     */
    private List<ParameterMapping> parameterMappings = new ArrayList<>();

    /**
     * 参数类型
     */
    private Class<?> parameterType;

    /**
     * additionalParameters参数对应的MetaObject对象
     */
    private MetaObject metaParameters;

    public ParameterMappingTokenHandler(Configuration configuration, Class<?> parameterType, Map<String, Object> additionalParameters) {
      super(configuration);
      this.parameterType = parameterType;
      // 创建additionalParameters参数对应的MetaObject对象
      this.metaParameters = configuration.newMetaObject(additionalParameters);
    }

    public List<ParameterMapping> getParameterMappings() {
      return parameterMappings;
    }

    @Override
    public String handleToken(String content) {
      // 构建ParameterMapping对象，并添加到parameterMappings中
      parameterMappings.add(buildParameterMapping(content));
      // 返回"?"占位符
      return "?";
    }

    private ParameterMapping buildParameterMapping(String content) {
      /*
       * 将 #{xxx} 占位符中的内容解析成 Map。大家可能很好奇一个普通的字符串是怎么解析成 Map 的，
       * 举例说明一下。如下：
       *
       *    #{age,javaType=int,jdbcType=NUMERIC,typeHandler=MyTypeHandler}
       *
       * 上面占位符中的内容最终会被解析成如下的结果：
       *
       *  {
       *      "property": "age",
       *      "typeHandler": "MyTypeHandler",
       *      "jdbcType": "NUMERIC",
       *      "javaType": "int"
       *  }
       *
       * parseParameterMapping 内部依赖 ParameterExpression 对字符串进行解析，ParameterExpression 的
       * 逻辑不是很复杂，这里就不分析了。大家若有兴趣，可自行分析
       */
      // 解析成Map集合
      Map<String, String> propertiesMap = parseParameterMapping(content);
      // 获得属性的名字
      String property = propertiesMap.get("property");
      // 类型
      Class<?> propertyType;
      if (metaParameters.hasGetter(property)) { // issue #448 get type from additional params
        propertyType = metaParameters.getGetterType(property);
      } else if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
        propertyType = parameterType;
      } else if (JdbcType.CURSOR.name().equals(propertiesMap.get("jdbcType"))) {
        propertyType = java.sql.ResultSet.class;
      } else if (property == null || Map.class.isAssignableFrom(parameterType)) {
        // 如果property为空，或parameterType是Map类型，则将property设置为Object.class
        propertyType = Object.class;
      } else {
        /*
         * 代码逻辑走到此分支中，表明 parameterType 是一个自定义的类，
         * 比如 Article，此时为该类创建一个元信息对象
         */
        MetaClass metaClass = MetaClass.forClass(parameterType, configuration.getReflectorFactory());
        // 检测参数对象有没有与 property 想对应的 getter 方法
        if (metaClass.hasGetter(property)) {
          // 获取成员变量的类型
          propertyType = metaClass.getGetterType(property);
        } else {
          propertyType = Object.class;
        }
      }


      // 创建ParameterMapping.Builder对象
      ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, propertyType);
      // 将 propertyType 赋值给 javaType
      Class<?> javaType = propertyType;
      String typeHandlerAlias = null;
      // 初始化ParameterMapping.Builder对象的属性
      for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
        String name = entry.getKey();
        String value = entry.getValue();
        if ("javaType".equals(name)) {
          // 如果用户明确配置了 javaType，则以用户的配置为准
          javaType = resolveClass(value);
          builder.javaType(javaType);
        } else if ("jdbcType".equals(name)) {
          // 解析 jdbcType
          builder.jdbcType(resolveJdbcType(value));
        } else if ("mode".equals(name)) {
          builder.mode(resolveParameterMode(value));
        } else if ("numericScale".equals(name)) {
          builder.numericScale(Integer.valueOf(value));
        } else if ("resultMap".equals(name)) {
          builder.resultMapId(value);
        } else if ("typeHandler".equals(name)) {
          typeHandlerAlias = value;
        } else if ("jdbcTypeName".equals(name)) {
          builder.jdbcTypeName(value);
        } else if ("property".equals(name)) {
          // Do Nothing
        } else if ("expression".equals(name)) {
          throw new BuilderException("Expression based parameters are not supported yet");
        } else {
          throw new BuilderException("An invalid property '" + name + "' was found in mapping #{" + content + "}.  Valid properties are " + PARAMETER_PROPERTIES);
        }
      }
      // 如果typeHandlerAlias非空，则获取对应的TypeHandler对象，并设置到ParameterMapping.Builder对象中
      if (typeHandlerAlias != null) {
        builder.typeHandler(resolveTypeHandler(javaType, typeHandlerAlias));
      }
      // 创建ParameterMapping对象
      return builder.build();
    }

    private Map<String, String> parseParameterMapping(String content) {
      try {
        return new ParameterExpression(content);
      } catch (BuilderException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new BuilderException("Parsing error was found in mapping #{" + content + "}.  Check syntax #{property|(expression), var1=value1, var2=value2, ...} ", ex);
      }
    }
  }

}
