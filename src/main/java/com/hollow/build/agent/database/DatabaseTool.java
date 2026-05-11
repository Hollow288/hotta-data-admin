package com.hollow.build.agent.database;

import com.hollow.build.agent.core.Tool;

/**
 * 标记接口：表示这是一个供 DatabaseAgent 使用的工具。
 *
 * <p>有了它，Spring 注入 {@code List<DatabaseTool>} 时只会拿到属于数据库 agent 的工具，
 * 不会和其他 agent（比如 AliasAgent）的工具混在一起。
 */
public interface DatabaseTool extends Tool {
}
