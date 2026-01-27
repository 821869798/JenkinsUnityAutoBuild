// 测试飞书通知的 Pipeline

pipeline {
  agent any

  environment {
    // 打包服务器基础URL
    PACKAGE_SERVER_BASE_URL = 'https://gt-package-qt.zhuziplay.com'
    // 版本目录路径
    PACKAGE_VERSION_PATH = 'version'
    // 飞书通知机器人ID
    LARK_ROBOT_ID = 'gt-notice-bot'
  }

  parameters {
    string(name: 'testPlatform', defaultValue: 'Test', description: '测试平台名称')
    string(name: 'buildPlatform', defaultValue: '1', description: '构建平台 (0=Windows, 1=Android, 2=iOS)')
    string(name: 'buildMode', defaultValue: '0', description: '打包模式 (0=全量, 1=Build App, 2=空包, 3=热更)')
    string(name: 'versionNumber', defaultValue: '0.1.0.0', description: '版本号')
    booleanParam(name: 'enableIncrement', defaultValue: true, description: '增量打包')
    booleanParam(name: 'enableUnityDevelopment', defaultValue: false, description: 'Unity开发模式')
    booleanParam(name: 'enableGameDevelopment', defaultValue: false, description: '游戏开发模式')
    booleanParam(name: 'simulateFailure', defaultValue: false, description: '模拟构建失败')
    booleanParam(name: 'testDownloadUrl', defaultValue: true, description: '测试下载链接')
  }

  stages {
    stage('测试准备') {
      steps {
        script {
          // 加载飞书通知模块
          larkNotify = load "${env.WORKSPACE}/scripts/utility/lark_notification.groovy"
          
          echo "开始测试飞书通知..."
          echo "机器人ID: ${env.LARK_ROBOT_ID}"
          
          // 设置测试用的打包路径
          if (params.testDownloadUrl) {
            env.packageRelativePath = "${params.testPlatform}/test_build_001"
          }
          
          // 发送构建开始通知
          larkNotify.sendBuildStart(env.LARK_ROBOT_ID)
        }
      }
    }

    stage('模拟构建') {
      steps {
        script {
          echo "模拟构建中..."
          sleep(time: 2, unit: 'SECONDS')
          
          if (params.simulateFailure) {
            error("模拟构建失败！")
          }
          
          echo "模拟构建完成！"
        }
      }
    }
  }

  // 构建完成后的通知处理
  post {
    success {
      script {
        // 构建成功时发送飞书通知
        larkNotify.sendBuildEnd(env.LARK_ROBOT_ID, 'SUCCESS')
      }
    }
    failure {
      script {
        // 构建失败时发送飞书通知
        larkNotify.sendBuildEnd(env.LARK_ROBOT_ID, 'FAILURE')
      }
    }
    aborted {
      script {
        // 构建中止时发送飞书通知
        larkNotify.sendBuildEnd(env.LARK_ROBOT_ID, 'ABORTED')
      }
    }
  }
}
