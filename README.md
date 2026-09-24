# 苍穹外卖学习项目

基于黑马程序员课程完成的学习项目，收录当前 Java 后端代码及课程提供的管理端页面部署包。前端目录是已打包的静态页面和 Nginx 配置，不包含可重新构建的 Vue 源码。

## 项目结构

```text
sky-common/             公共常量、工具与配置属性
sky-pojo/               DTO、实体和 VO
sky-server/             接口、业务逻辑、数据访问和配置
frontend/nginx/conf/    Nginx 配置
frontend/nginx/html/    管理端静态页面
pom.xml                Maven 多模块入口
```

后端采用 Spring Boot 2.7、MyBatis、MySQL、Redis、JWT、WebSocket 和 Apache POI 等技术，包含员工、分类、菜品、套餐、购物车、订单和统计报表相关实现。

## 环境与数据库

- JDK 17、Maven 3.9。
- MySQL 与 Redis。
- Nginx；前端配置来自随课程页面提供的 Nginx 1.20.2 部署包。
- 使用图片上传、微信业务或地图服务时，需配置对应服务。

本次提供的文件中没有数据库建表和初始化 SQL。启动前需另外准备课程配套数据库，并确认表结构与 `sky-server/src/main/resources/mapper/` 一致。

## 后端配置

默认激活 `dev` 配置。上传版配置从环境变量读取连接信息和凭据，不包含开发者本机密钥。

在启动 Java 的终端或 IDE 运行配置中设置：

| 环境变量 | 用途 |
| --- | --- |
| `SKY_DB_USER`、`SKY_DB_PASSWORD` | MySQL 用户和密码 |
| `SKY_DB_HOST`、`SKY_DB_PORT`、`SKY_DB_NAME` | 默认 `localhost`、`3306`、`sky_take_out` |
| `SKY_REDIS_HOST`、`SKY_REDIS_PORT`、`SKY_REDIS_PASSWORD`、`SKY_REDIS_DATABASE` | 默认 `localhost`、`6379`、空密码、数据库 `0` |
| `SKY_JWT_ADMIN_SECRET`、`SKY_JWT_USER_SECRET` | 管理端及用户端 JWT 签名密钥，请自行设置 |
| `SKY_OSS_ENDPOINT`、`SKY_OSS_ACCESS_KEY_ID`、`SKY_OSS_ACCESS_KEY_SECRET`、`SKY_OSS_BUCKET` | 阿里云 OSS 配置，当前应用配置会读取这些变量 |
| `SKY_BAIDU_AK` | 地图服务配置，当前应用配置会读取此变量 |
| `SKY_WECHAT_APP_ID`、`SKY_WECHAT_SECRET` | 微信小程序配置 |
| `SKY_WECHAT_MCH_ID`、`SKY_WECHAT_MCH_SERIAL_NO`、`SKY_WECHAT_API_V3_KEY` | 微信支付商户配置 |
| `SKY_WECHAT_NOTIFY_URL`、`SKY_WECHAT_REFUND_NOTIFY_URL` | 支付与退款回调地址 |

没有使用微信支付时，可保留对应空值；若需要支付功能，还需根据 `WeChatProperties` 与 `WeChatPayUtil` 配置证书路径等必要参数。空配置不代表相关业务已经可以正常运行。

Spring Boot 不会自动读取任意 `.env` 文件，请通过真实环境变量或 IDE 运行参数传入。

## 编译与启动

在仓库根目录执行：

```bash
mvn test
mvn package -DskipTests
java -jar sky-server/target/sky-server-1.0-SNAPSHOT.jar
```

也可通过 IDE 启动 `com.sky.SkyApplication`。默认后端端口为 `8080`。当前收录版本未包含自动化测试用例，`mvn test` 成功不等于已经验证真实数据库和外部服务链路。

## 管理端页面

将 `frontend/nginx/conf/` 和 `frontend/nginx/html/` 中的内容放入自己的 Nginx 安装目录对应位置。先检查配置，再启动 Nginx：

```powershell
.\nginx.exe -t
.\nginx.exe
```

默认通过 `http://localhost` 访问管理端，接口转发为：

- `/api/` 转发至 `http://localhost:8080/admin/`。
- `/user/` 转发至后端 `/user/`。
- `/ws/` 转发至后端 WebSocket 接口。

该前端是课程提供的已有页面，不运行 `npm install` 或 `npm run build`。仓库没有包含 Nginx 可执行文件、运行日志和缓存目录。

## 来源

课程与前端页面来自黑马程序员。此仓库用于记录学习和练习实现，不将课程提供的前端页面声明为个人原创；第三方文件保留随附声明。
