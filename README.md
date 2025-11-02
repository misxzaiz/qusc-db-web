# DB Admin - 数据库管理工具

一个基于 Spring Boot 3 + JDK 17 + Vue 3 的数据库管理界面，支持 MySQL 数据库的基础连接管理和 SQL 操作。

## 功能特性

- 数据库连接管理（增删改查）
- SQL 查询编辑器
- 数据表格展示
- 暗色主题（类似 VS Code）
- 快捷键支持（F5 执行查询）

## 技术栈

### 后端
- Spring Boot 3.2.0
- JDK 17
- Spring Data JPA
- MySQL Connector

### 前端
- Vue 3
- Vite
- Axios

## 快速开始

### 环境要求
- JDK 17+
- Node.js 16+
- MySQL 8.0+

### 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端服务将在 http://localhost:8080 启动

### 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端服务将在 http://localhost:3000 启动

## 使用说明

1. **添加数据库连接**
   - 访问"连接管理"页面
   - 点击"新建连接"
   - 填写数据库连接信息
   - 点击"测试并保存"

2. **执行 SQL**
   - 访问"SQL编辑器"页面
   - 选择数据库连接
   - 输入 SQL 语句
   - 按 F5 或点击"执行查询"

## 项目结构

```
├── backend
│   ├── src
│   │   ├── main
│   │   │   ├── java/com/dbadmin
│   │   │   │   ├── config
│   │   │   │   ├── controller
│   │   │   │   ├── exception
│   │   │   │   ├── model
│   │   │   │   ├── repository
│   │   │   │   └── service
│   │   │   └── resources
│   │   └── test
│   └── pom.xml
├── frontend
│   ├── src
│   │   ├── components
│   │   ├── services
│   │   ├── utils
│   │   ├── views
│   │   ├── App.vue
│   │   └── main.js
│   ├── index.html
│   ├── package.json
│   └── vite.config.js
```

