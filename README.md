# mirai-console-teamspeak3-plugin
监听 TeamSpeak3 服务器的人员进出事件，并通过 Mirai 播报

## 开始使用
将插件 jar 包放入 Mirai 的 `plugins` 文件夹下，然后重启 Mirai 即可。  

当插件成功启用后，会在config/org.evaz.mirai-teamspeak3 目录下生成配置文件 `config.yaml`。 修改配置文件后即可开始使用。

若不需要自定义播报模板，可以直接开始使用，插件会使用默认模板进行播报。

若需要自定义播报模板，可以使用插件指令进行管理。
1. 在QQ中使用 `/tsb bind <UID>` 绑定QQ号和TeamSpeak3 UID
2. 在QQ中使用 `/tst add <ID> <TYPE> <TEMPLATE>` 添加模板
现在插件会使用自定义模板进行播报。当存在多个模板时，插件会随机选择一个模板进行播报。

## 配置文件
```yaml
hostName: localhost # TeamSpeak3 服务器地址
queryPort: 10011 # TeamSpeak3 服务器查询端口
userName: serveradmin # TeamSpeak3 服务器查询用户名
password: password # TeamSpeak3 服务器查询密码
virtualServerId: 1 # TeamSpeak3 服务器虚拟服务器 ID
targetGroupIds: # 播报的目标群组列表
  - qqGroup1
targetUserIds: # 播报的目标用户列表
  - qqUser1
excludedUIDs: # 排除的用户列表(填写需要排除的用户的 UID)
  - ServerQuery # 排除ServerQuery
  - Unknown # 排除Unknown(Unknown是在代码中产生的，并不存在在TeamSpeak3服务器中)
channelCacheRefreshInterval: 600 # 频道缓存刷新间隔(秒)
heartbeatInterval: 60 # 心跳间隔(秒)
listenLoopDelay: 1 # 监听循环延迟(秒)
defaultTemplates: # 默认模板
  join: '用户 {nickname} (UID: {uid}) 加入了服务器'
  leave: '用户 {nickname} (UID: {uid}) 离开了服务器'
```

## 插件指令
#### 绑定
- `tsb bind <UID>` 绑定QQ号和TeamSpeak3 UID
- `tsb unbind <UID>` 解绑QQ号和TeamSpeak3 UID
#### 播报模板管理
- `tst add <ID> <TYPE> <TEMPLATE>` 添加模板
- - `ID` 模板 ID，用于区分不同模板，可以是任意字符串
- - `TYPE` 模板类型，`join` 或 `leave`，分别对应用户加入和离开服务器的事件
- - `TEMPLATE` 模板内容，支持占位符(模板中不能出现空格)
- `tst remove <ID>` 删除模板
- `tst list` 列出所有模板

## 模板占位符
- `{nickname}` 用户昵称
- `{uid}` 用户 UID
- `{channelName}` 频道名称
- `{channelId}` 频道 ID

## 开发与发布

### 自动发布
本项目配置了 GitHub Actions 自动发布流程。当推送符合 `v*.*.*` 格式的标签时（例如 `v1.1.0`），会自动：
1. 构建项目并生成 JAR 文件
2. 生成更新日志（基于 Git 提交记录）
3. 创建 GitHub Release 并上传构建产物

#### 发布步骤
```bash
# 1. 更新 build.gradle.kts 中的版本号
# 2. 提交并推送更改
git add .
git commit -m "chore: bump version to 1.2.0"
git push

# 3. 创建并推送标签
git tag v1.2.0
git push origin v1.2.0
```

发布完成后，可以在 [Releases 页面](../../releases) 查看和下载构建产物。
