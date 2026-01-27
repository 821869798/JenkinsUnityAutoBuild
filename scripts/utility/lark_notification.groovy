// 飞书通知封装模块
// 使用方式: def larkNotify = load 'scripts/lark_notification.groovy'
//          larkNotify.sendBuildStart(env.LARK_ROBOT_ID)
//          larkNotify.sendBuildEnd(env.LARK_ROBOT_ID, 'SUCCESS')

// 发送飞书卡片通知（构建结束）
// 参数: robotId - 飞书机器人ID
//       buildResult - 构建结果 (SUCCESS/FAILURE/ABORTED 等)
//       extraInfo - 额外信息 (可选，Map类型，如 [key: value])
def sendBuildEnd(String robotId, String buildResult, Map extraInfo = [:]) {
  if (!robotId?.trim()) {
    echo "Lark robot ID is not configured, skip notification."
    return
  }
  
  // 根据构建结果设置状态显示
  def statusText = ''
  def statusColor = 'grey'
  switch (buildResult) {
    case 'SUCCESS':
      statusText = '成功'
      statusColor = 'green'
      break
    case 'FAILURE':
      statusText = '失败'
      statusColor = 'red'
      break
    case 'ABORTED':
      statusText = '已中止'
      statusColor = 'grey'
      break
    case 'UNSTABLE':
      statusText = '不稳定'
      statusColor = 'red'
      break
    default:
      statusText = buildResult ?: '未知'
      statusColor = 'grey'
  }
  
  // 构建平台名称
  def platformName = getPlatformName()
  
  // 构建基础消息内容
  def textContent = [
    "📋 **任务名称**：[${env.JOB_NAME}](${env.JOB_URL})",
    "🔢 **构建编号**：[${currentBuild.displayName}](${env.BUILD_URL})",
    "🎯 **构建平台**：${platformName}",
    "🌟 **构建状态**：<font color='${statusColor}'>${statusText}</font>",
    "🕐 **构建用时**：${currentBuild.durationString?.replace(' and counting', '') ?: 'N/A'}"
  ]
  
  // 添加额外信息
  extraInfo.each { key, value ->
    if (value?.trim()) {
      textContent.add("${key}：${value}")
    }
  }
  
  // 添加 @所有人（仅失败时）
  if (buildResult == 'FAILURE') {
    textContent.add("<at id=all></at>")
  }
  
  // 构建按钮 - 下载地址和控制台日志
  def buttons = []
  if (env.packageRelativePath) {
    def encodedPath = "/${env.PACKAGE_VERSION_PATH}/${env.packageRelativePath}".replace('/', '%2F')
    buttons.add([
      title: "下载地址",
      url: "${env.PACKAGE_SERVER_BASE_URL}/FileServer/?path=${encodedPath}"
    ])
  }
  buttons.add([
    title: "控制台日志",
    type: "danger",
    url: "${env.BUILD_URL}console"
  ])
  
  // 发送飞书卡片消息
  lark(
    robot: robotId,
    type: "CARD",
    title: "📢 Unity 构建通知 - ${platformName}",
    text: textContent,
    buttons: buttons
  )
}

// 发送飞书卡片通知（构建开始）
// 参数: robotId - 飞书机器人ID
def sendBuildStart(String robotId) {
  if (!robotId?.trim()) {
    echo "Lark robot ID is not configured, skip notification."
    return
  }
  
  // 构建平台名称
  def platformName = getPlatformName()
  
  // 构建模式名称
  def buildModeName = getBuildModeName()
  
  // 构建基础消息内容
  def textContent = [
    "📋 **任务名称**：[${env.JOB_NAME}](${env.JOB_URL})",
    "🔢 **构建编号**：[${currentBuild.displayName}](${env.BUILD_URL})",
    "🎯 **构建平台**：${platformName}",
    "🌟 **构建状态**：<font color='blue'>开始构建</font>",
    "📦 **打包模式**：${buildModeName}",
    "🏷️ **版本号**：${params.versionNumber}"
  ]
  
  // 添加增量打包信息
  if (params.enableIncrement) {
    textContent.add("⚡ **增量打包**：是")
  }
  
  // 添加开发模式信息
  textContent.add("🔧 **Unity开发模式**：${params.enableUnityDevelopment ? '是' : '否'}")
  textContent.add("🎮 **游戏开发模式**：${params.enableGameDevelopment ? '是' : '否'}")
  
  // 构建按钮 - 只有控制台日志
  def buttons = [
    [
      title: "控制台日志",
      type: "danger",
      url: "${env.BUILD_URL}console"
    ]
  ]
  
  // 发送飞书卡片消息
  lark(
    robot: robotId,
    type: "CARD",
    title: "🚀 Unity 构建开始 - ${platformName}",
    text: textContent,
    buttons: buttons
  )
}

// 获取构建平台名称
def getPlatformName() {
  def platformName = '未知'
  switch (params.buildPlatform) {
    case '0': platformName = 'Windows'; break
    case '1': platformName = 'Android'; break
    case '2': platformName = 'iOS'; break
  }
  return platformName
}

// 获取构建模式名称
def getBuildModeName() {
  def buildModeName = '未知'
  switch (params.buildMode) {
    case '0': buildModeName = '全量打包'; break
    case '1': buildModeName = '直接Build App'; break
    case '2': buildModeName = '打空包'; break
    case '3': buildModeName = '打热更资源版本'; break
  }
  return buildModeName
}

// 返回当前对象，以便在 load() 后可以调用方法
return this
