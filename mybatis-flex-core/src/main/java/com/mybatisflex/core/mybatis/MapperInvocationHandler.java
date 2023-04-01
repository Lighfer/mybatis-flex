/**
 * Copyright (c) 2022-2023, Mybatis-Flex (fuhai999@gmail.com).
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
package com.mybatisflex.core.mybatis;

import com.mybatisflex.annotation.UseDataSource;
import com.mybatisflex.core.FlexGlobalConfig;
import com.mybatisflex.core.datasource.DataSourceKey;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.dialect.DbType;
import com.mybatisflex.core.dialect.DialectFactory;
import com.mybatisflex.core.row.RowMapper;
import com.mybatisflex.core.table.TableInfo;
import com.mybatisflex.core.table.TableInfoFactory;
import com.mybatisflex.core.util.StringUtil;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.util.MapUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MapperInvocationHandler implements InvocationHandler {
    private static final String NONE_KEY = "!NONE";
    private static final Map<Method, String> methodDataSourceKeyMap = new ConcurrentHashMap<>();

    private final Object mapper;
    private final FlexDataSource dataSource;

    public MapperInvocationHandler(Object mapper, Configuration configuration) {
        this.mapper = mapper;
        this.dataSource = (FlexDataSource) configuration.getEnvironment().getDataSource();
    }


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        boolean clearDsKey = false;
        boolean clearDbType = false;
        try {
            //获取用户动态指定，由用户指定数据源，则应该有用户清除
            String dataSourceKey = DataSourceKey.get();

            if (StringUtil.isBlank(dataSourceKey)) {
                String methodKey = getMethodDataSource(method, proxy);
                if (!NONE_KEY.equals(methodKey)) {
                    dataSourceKey = methodKey;
                    DataSourceKey.use(dataSourceKey);
                    clearDsKey = true;
                }
            }

            //优先获取用户自己配置的 dbType
            DbType dbType = DialectFactory.getHintDbType();
            if (dbType == null) {
                if (dataSourceKey != null) {
                    dbType = dataSource.getDbType(dataSourceKey);
                }
                if (dbType == null) {
                    dbType = FlexGlobalConfig.getDefaultConfig().getDbType();
                }
                DialectFactory.setHintDbType(dbType);
                clearDbType = true;
            }
            return method.invoke(mapper, args);
        } finally {
            if (clearDbType) {
                DialectFactory.clearHintDbType();
            }
            if (clearDsKey) {
                DataSourceKey.clear();
            }
        }
    }


    private static String getMethodDataSource(Method method, Object proxy) {
        return MapUtil.computeIfAbsent(methodDataSourceKeyMap, method, method1 -> {
            UseDataSource useDataSource = method1.getAnnotation(UseDataSource.class);
            if (useDataSource != null && StringUtil.isNotBlank(useDataSource.value())) {
                return useDataSource.value();
            }

            Class<?>[] interfaces = proxy.getClass().getInterfaces();
            if (interfaces[0] != RowMapper.class) {
                TableInfo tableInfo = TableInfoFactory.ofMapperClass(interfaces[0]);
                if (tableInfo != null) {
                    String dataSourceKey = tableInfo.getDataSource();
                    if (StringUtil.isNotBlank(dataSourceKey)) {
                        return dataSourceKey;
                    }
                }
            }
            return NONE_KEY;
        });
    }


}