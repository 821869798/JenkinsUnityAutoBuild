// 执行命令并且获取返回值
def CallCmd(cmdline) {
  def isWindows = !isUnix()
  if (isWindows) {
    def result = bat(returnStatus: true, script: cmdline)
    return result
    }else {
    def result = sh(returnStatus: true, script: cmdline)
    return result
  }
}

// 判断是否为SVN地址（根据URL判断版本控制类型）
def IsSvnUrl(String scmUrl) {
  // 提取实际URL（去掉分支信息）
  def urlPart = scmUrl.split('\\|')[0]
  return urlPart.startsWith('svn://')
}

// 获取Unity的exe文件目录
def GetUnityExePath() {
  // 优先使用jenkins环境变量中的Unity路径，其次才是代码中定义的默认值
  def unityExePath = ''
  if (env.Unity2022) {
    unityExePath = env.Unity2022
  } else {
    if (isUnix()) {
      unityExePath = env.Unity2022_DefaultPath_Unix
    } else {
      unityExePath = env.Unity2022_DefaultPath
    }
  }
  return unityExePath
}

// 安装 p12 证书并获取 codeSignIdentity
// 参数: signingParam - XcodeSigningParam 对象，包含 p12FilePath, p12Password
// 返回: codeSignIdentity 字符串（从 p12 中自动提取）
def InstallP12AndGetCodeSignIdentity(signingParam) {
  def p12FilePath = signingParam.p12FilePath
  def p12Password = signingParam.p12Password
  def filePrefix = signingParam.filePrefix ?: "default_"
  
  // 给shell写入变量值的文件
  def tempP12Properties = "${env.WORKSPACE}/${env.TEMP_PROPERTIES_DIR}/${filePrefix}p12.properties"
  
  // 安装 p12 证书并获取 codeSignIdentity
  def installP12Shell = """
  # 导入 p12 证书到 keychain (使用 -A 允许所有应用访问，避免弹窗)
  security import "${p12FilePath}" -k "${env.HOME}/Library/Keychains/login.keychain-db" -P "${p12Password}" -T /usr/bin/codesign -T /usr/bin/security -A 2>/dev/null || true
  
  # 从 p12 中提取证书的 Common Name 作为 codeSignIdentity
  # 方法1: 使用 openssl 解析 p12 文件（标准模式）
  P12CodeSignIdentity=\$(openssl pkcs12 -in "${p12FilePath}" -clcerts -nokeys -passin pass:"${p12Password}" 2>/dev/null | openssl x509 -noout -subject -nameopt RFC2253 2>/dev/null | sed -n 's/^.*CN=\\([^,]*\\).*\$/\\1/p')
  
  # 方法2: 如果失败，尝试 openssl legacy 模式（openssl 3.x 需要）
  if [ -z "\${P12CodeSignIdentity}" ]; then
    echo "Trying openssl with -legacy flag..."
    P12CodeSignIdentity=\$(openssl pkcs12 -in "${p12FilePath}" -clcerts -nokeys -passin pass:"${p12Password}" -legacy 2>/dev/null | openssl x509 -noout -subject -nameopt RFC2253 2>/dev/null | sed -n 's/^.*CN=\\([^,]*\\).*\$/\\1/p')
  fi
  
  if [ -z "\${P12CodeSignIdentity}" ]; then
    echo "Error: Failed to extract codeSignIdentity from p12 file!"
    exit 1
  fi
  
  echo "Extracted codeSignIdentity: \${P12CodeSignIdentity}"
  echo P12CodeSignIdentity=\${P12CodeSignIdentity} > ${tempP12Properties} || exit 1
  """
  def exitCode = CallCmd(installP12Shell)
  if (exitCode != 0) {
    error('install p12 certificate failed!')
  }
  
  // 读取从 p12 中提取的 codeSignIdentity
  def p12Props = readProperties file: tempP12Properties
  def codeSignIdentity = p12Props.P12CodeSignIdentity
  echo "Using codeSignIdentity: ${codeSignIdentity}"
  
  return codeSignIdentity
}

// 从 mobileprovision 文件中获取签名方法
// 参数: mobileprovisionFilePath - mobileprovision 文件路径
// 返回: signingMethod 字符串（app-store, development, enterprise, ad-hoc）
def GetSigningMethodFromMobileprovision(mobileprovisionFilePath) {
  def tempSigningMethodProperties = "${env.WORKSPACE}/${env.TEMP_PROPERTIES_DIR}/signingMethod.properties"
  
  def getSigningMethodShell = """
  # 解析 mobileprovision 文件获取签名方法（隐藏 plist 内容输出）
  set +x
  PLIST_CONTENT=\$(security cms -D -i "${mobileprovisionFilePath}" 2>/dev/null)
  set -x
  
  # 检查是否为企业证书 (ProvisionsAllDevices = true)
  PROVISIONS_ALL_DEVICES=\$(/usr/libexec/PlistBuddy -c 'Print ProvisionsAllDevices' /dev/stdin <<< "\${PLIST_CONTENT}" 2>/dev/null || echo "false")
  
  # 检查是否有设备列表 (ProvisionedDevices) - 只检测命令是否成功
  if /usr/libexec/PlistBuddy -c 'Print ProvisionedDevices' /dev/stdin <<< "\${PLIST_CONTENT}" >/dev/null 2>&1; then
    HAS_DEVICES="true"
  else
    HAS_DEVICES="false"
  fi
  
  # 检查 get-task-allow (development 模式)
  GET_TASK_ALLOW=\$(/usr/libexec/PlistBuddy -c 'Print :Entitlements:get-task-allow' /dev/stdin <<< "\${PLIST_CONTENT}" 2>/dev/null || echo "false")
  
  echo "PROVISIONS_ALL_DEVICES=\${PROVISIONS_ALL_DEVICES}, HAS_DEVICES=\${HAS_DEVICES}, GET_TASK_ALLOW=\${GET_TASK_ALLOW}"
  
  # 判断签名方法
  if [ "\${PROVISIONS_ALL_DEVICES}" = "true" ]; then
    SIGNING_METHOD="enterprise"
  elif [ "\${HAS_DEVICES}" = "true" ]; then
    if [ "\${GET_TASK_ALLOW}" = "true" ]; then
      SIGNING_METHOD="development"
    else
      SIGNING_METHOD="ad-hoc"
    fi
  else
    SIGNING_METHOD="app-store"
  fi
  
  echo "Detected signingMethod: \${SIGNING_METHOD}"
  echo SigningMethod=\${SIGNING_METHOD} > ${tempSigningMethodProperties} || exit 1
  """
  
  def exitCode = CallCmd(getSigningMethodShell)
  if (exitCode != 0) {
    error('get signing method from mobileprovision failed!')
  }
  
  // 读取签名方法
  def signingMethodProps = readProperties file: tempSigningMethodProperties
  def signingMethod = signingMethodProps.SigningMethod
  echo "Using signingMethod: ${signingMethod}"
  
  return signingMethod
}

// XCode构建ipa的参数
class XcodeBuildProject {
    String xcodeProjectName
    String xcodeProjectPath
    String archivePath
    String ipaOutputPath
}

// XCode证书信息
class XcodeSigningParam {
    // 这个签名的包的前缀，和别的签名不要重复
    String filePrefix
    // p12 证书文件路径
    String p12FilePath
    // p12 证书密码
    String p12Password
    String mobileprovisionFilePath
}



pipeline {
  agent any

  environment {
    Unity2022_DefaultPath = 'C:/Program Files/Unity/Hub/Editor/2022.3.62f1/Editor/Unity.exe'
    Unity2022_DefaultPath_Unix = '/Applications/Unity/Hub/Editor/2022.3.62f1/Unity.app/Contents/MacOS/Unity'
    // 打包服务器基础URL，替换成实际的HTTP File Server
    PACKAGE_SERVER_BASE_URL = 'https://xxx'
    // 版本目录路径
    PACKAGE_VERSION_PATH = 'version'
    // 飞书通知机器人ID，需要先在Manage Jenkins中的Lark Notice中配置好
    LARK_ROBOT_ID = ''
    // 临时 properties 文件目录
    TEMP_PROPERTIES_DIR = 'temp_properties'
  }

  stages {
    stage('处理路径参数') {
      steps {
        script {
          // 加载飞书通知模块
          larkNotify = load "${env.WORKSPACE}/scripts/utility/lark_notification.groovy"
          
          // 发送构建开始通知
          larkNotify.sendBuildStart(env.LARK_ROBOT_ID)
          
          // 处理 ~ 开头的路径，替换为绝对路径
          // Windows路径使用反斜杠，统一转换为正斜杠
          def userHome = isUnix() ? env.HOME : env.USERPROFILE.replace('\\', '/')
          
          env.projectPath = params.projectPath
          if (env.projectPath.startsWith('~/')) {
            env.projectPath = env.projectPath.replaceFirst('~', userHome)
          }
          
          env.outputPath = params.outputPath
          if (env.outputPath.startsWith('~/')) {
            env.outputPath = env.outputPath.replaceFirst('~', userHome)
          }
          
	  // 如果Unity工程不是git或者svn根目录中，要设置一下这个
          // env.unityProjectPath = env.projectPath + "/GameClient/UnityProject"
	  env.unityProjectPath = env.projectPath
          echo "Resolved projectPath: ${env.projectPath}"
          echo "Resolved unityProjectPath: ${env.unityProjectPath}"
          echo "Resolved outputPath: ${env.outputPath}"
          
          // 创建临时 properties 目录
          def tempPropertiesDir = "${env.WORKSPACE}/${env.TEMP_PROPERTIES_DIR}"
          if (isUnix()) {
            sh "mkdir -p '${tempPropertiesDir}'"
          } else {
            bat "if not exist \"${tempPropertiesDir}\" mkdir \"${tempPropertiesDir}\""
          }
        }
      }
    }
    stage('检测本地工程') {
      steps {
        script {
          def projectPath = env.projectPath
          def projectExist = fileExists(projectPath)
          if (!projectExist) {
            // 不存在工程，需要拉取一下,|符号需要转义
            def scmUrlArray = params.scmUrl.split("\\|")
            def scmUrlRaw = ''
            def scmBranch = ''
            if (scmUrlArray.size() == 1) {
              scmUrlRaw = scmUrlArray[0]
            } else if (scmUrlArray.size() == 2) {
              scmUrlRaw = scmUrlArray[0]
              scmBranch = scmUrlArray[1]
            } else {
              error("check local project: scm url format error! ${params.scmUrl}")
            }
            
            // 使用凭据管理来处理认证
            def hasCredentials = params.credentialsId?.trim()
            def isSvn = IsSvnUrl(scmUrlRaw)
            
            if (!isSvn) {
              // Git 克隆逻辑
              def gitCloneAction = { String scmUrl ->
                def cmdArg = ''
                if (scmBranch != '') {
                  cmdArg = """
                  git clone -b ${scmBranch} ${scmUrl} "${projectPath}" || exit 1
                  """
                } else {
                  cmdArg = """
                  git clone ${scmUrl} "${projectPath}" || exit 1
                  """
                }
                echo 'Start checkout or clone project...'
                def exitCode = CallCmd(cmdArg)
                if (exitCode != 0) {
                  error('checkout or clone project failed!')
                }
              }
              
              if (hasCredentials) {
                // 使用凭据
                withCredentials([usernamePassword(credentialsId: params.credentialsId, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
                  def scmUrlWithCreds = scmUrlRaw.replaceFirst('://', "://${GIT_USERNAME}:${GIT_PASSWORD}@")
                  gitCloneAction(scmUrlWithCreds)
                }
              } else {
                // 不使用凭据，直接克隆（适用于公开仓库或 SSH 认证）
                gitCloneAction(scmUrlRaw)
              }
            }
            else {
              // SVN 检出逻辑
              def svnCheckoutAction = { String usernameArg, String passwordArg ->
                def cmdArg = ""
                if (usernameArg && passwordArg) {
                  cmdArg = """
                    svn checkout ${scmUrlRaw} "${projectPath}" --username ${usernameArg} --password ${passwordArg} --non-interactive --trust-server-cert || exit 1
                  """
                } else {
                  cmdArg = """
                    svn checkout ${scmUrlRaw} "${projectPath}" --non-interactive --trust-server-cert || exit 1
                  """
                }
                echo 'Start checkout or clone project...'
                def exitCode = CallCmd(cmdArg)
                if (exitCode != 0) {
                  error('checkout or clone project failed!')
                }
              }
              
              if (hasCredentials) {
                // 使用凭据
                withCredentials([usernamePassword(credentialsId: params.credentialsId, usernameVariable: 'SVN_USERNAME', passwordVariable: 'SVN_PASSWORD')]) {
                  svnCheckoutAction(SVN_USERNAME, SVN_PASSWORD)
                }
              } else {
                // 不使用凭据
                svnCheckoutAction(null, null)
              }
            }
          }
          
        }
      }
    }
    stage('更新工程') {
      steps {
        script {
          def projectPath = env.projectPath
          def unityProjectPath = env.unityProjectPath
          // 调用工程更新
          // 特别注意，加了params.的jenkins参数booleanParam才是有类型的bool，否则是string。extendedChoice得到的参数都是string
          if (params.enableProjectUpdate) {
            def hasCredentials = params.credentialsId?.trim()
            def isSvn = IsSvnUrl(params.scmUrl)
            
            if (!isSvn) {
              // Git 更新逻辑
              def gitUpdateAction = {
                def cmdArg = """
                  git -C "${projectPath}" restore -s HEAD -- "${unityProjectPath}/Assets" || exit 1
                  git -C "${projectPath}" restore -s HEAD -- "${unityProjectPath}/ProjectSettings" || exit 1
                  git -C "${projectPath}" pull || exit 1
                """
                echo 'Start update project...'
                def exitCode = CallCmd(cmdArg)
                if (exitCode != 0) {
                  error('update project failed!')
                }
              }
              
              if (hasCredentials) {
                // 使用凭据
                withCredentials([usernamePassword(credentialsId: params.credentialsId, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
                  gitUpdateAction()
                }
              } else {
                // 不使用凭据
                gitUpdateAction()
              }
              return
            }
            else {
              // SVN 更新逻辑
              def svnUpdateAction = { String usernameArg, String passwordArg ->
                def cmdArg = ""
                if (usernameArg && passwordArg) {
                  cmdArg = """
                    svn revert -R "${unityProjectPath}/Assets" --username ${usernameArg} --password ${passwordArg} --non-interactive || exit 1
                    svn revert -R "${unityProjectPath}/ProjectSettings" --username ${usernameArg} --password ${passwordArg} --non-interactive || exit 1
                    svn update "${projectPath}" --username ${usernameArg} --password ${passwordArg} --non-interactive --trust-server-cert || exit 1
                  """
                } else {
                  cmdArg = """
                    svn revert -R "${unityProjectPath}/Assets" --non-interactive || exit 1
                    svn revert -R "${unityProjectPath}/ProjectSettings" --non-interactive || exit 1
                    svn update "${projectPath}" --non-interactive --trust-server-cert || exit 1
                  """
                }
                echo 'Start update project...'
                def exitCode = CallCmd(cmdArg)
                if (exitCode != 0) {
                  error('update project failed!')
                }
              }
              
              if (hasCredentials) {
                // 使用凭据
                withCredentials([usernamePassword(credentialsId: params.credentialsId, usernameVariable: 'SVN_USERNAME', passwordVariable: 'SVN_PASSWORD')]) {
                  svnUpdateAction(SVN_USERNAME, SVN_PASSWORD)
                }
              } else {
                // 不使用凭据
                svnUpdateAction(null, null)
              }
              return
            }
          }

          echo 'Skip Update Project!'
        }
      }
    }

    stage('准备构建参数') {
      steps {
        script {

       //获取一些参数来自定义的构建名
          def buildDisplayName = currentBuild.displayName
          buildDisplayName = buildDisplayName.startsWith('#') ? buildDisplayName.substring(1) : buildDisplayName
          def formattedDate = new Date().format('yyyy-MM-dd')
          def jobShortName = JOB_NAME.tokenize('/').last()
          def buildVersionName = "${jobShortName}_${formattedDate}_${buildDisplayName}"
          echo "buildVersionName:${buildVersionName}"

          def projectPath = env.projectPath
          def unityProjectPath = env.unityProjectPath
          def outputPath = env.outputPath
          
          // 判断打包平台,用于控制启动Unity执行的打包方法
          def buildMethod = 'AutoBuild.AutoBuildEntry.'
          // 启动Unity直接指定的目标平台 
          // Standalone Win Win64 OSXUniversal Linux64iOS Android WebGL WindowsStoreApps tvOS
          def buildTarget = ''
          // 打包输出目录
          def finalOutputPath = outputPath

          switch (params.buildPlatform) {
            case '0':
              //windows
              buildMethod += 'BuildWindows'
              buildTarget = 'Standalone'
              // 打包下载的相对路径（用于生成完整URL）
              env.packageRelativePath = "Windows/" + buildVersionName
              
              // Windows 的特殊目录（类似 iOS 处理方式）
              // 先打包到 projectPath 同级的临时目录
              env.windowsTempBuildPath = projectPath + "_winbuild"
              // 最终输出目录（保持原来的目录结构）
              env.windowsFinalOutputPath = finalOutputPath + "/Windows"
              env.windowsBuildVersionName = buildVersionName
              // Unity 输出到临时目录
              finalOutputPath = env.windowsTempBuildPath
              break
            case '1':
              //Android
              buildMethod += 'BuildAndroid'
              finalOutputPath += '/Android'
              buildTarget = 'Android'
              // 打包下载的相对路径（用于生成完整URL）
              env.packageRelativePath = "Android/" + buildVersionName
              break
            case '2':
              //iOS
              buildMethod += 'BuildiOS'
              finalOutputPath += '/iOS'
              buildTarget = 'iOS'

              // iOS的特殊目录
              env.iOSIpaOutputPath = finalOutputPath + "/" + buildVersionName;
              // 打包下载的相对路径（用于生成完整URL）
              env.packageRelativePath = "iOS/" + buildVersionName;
              // xcode project path
              finalOutputPath = projectPath + "_xcodeprj";
              env.iOSArchivePath = projectPath + "_xcode_archive";
              env.iOSXcodeProjectPath = finalOutputPath;

              // 如果没有选证书类型，失败
              List signingList = params.iOSSigningType.tokenize(',')
              if (signingList.size() <= 0) {
                error("iOS build,but no signing type to select!")
              }

              break
            default :
              error('Build Platform not support!')
              break
          }

          //调用unity的命令行参数
          // Windows路径需要双引号，Unix不需要
          def q = isUnix() ? '' : '"'
          env.unity_execute_arg = "-quit -batchmode -nographics -projectPath ${q}${unityProjectPath}${q} -executeMethod ${buildMethod} -buildTarget ${buildTarget} " +
          "buildPlatform=${buildPlatform} ${q}outputPath=${finalOutputPath}${q} buildVersionName=${buildVersionName} buildMode=${buildMode} " +
          "versionNumber=${versionNumber} enableIncrement=${enableIncrement} androidBuildOption=${androidBuildOption} enableBuildExcel=${enableBuildExcel} " +
          "enableUnityDevelopment=${enableUnityDevelopment} enableGameDevelopment=${enableGameDevelopment}"
        }
      }
    }

    stage('Unity构建') {
      when {
        expression {
          return params.SkipUnityBuild != true
        }
      }
      steps {
        script {
          def unityExePath = GetUnityExePath()
          // Windows需要双引号包裹exe路径，Unix不需要
          def cmdArg = isUnix() ? "${unityExePath} ${env.unity_execute_arg}" : "\"${unityExePath}\" ${env.unity_execute_arg}"
          def exitCode = CallCmd(cmdArg)
          if (exitCode != 0) {
            error('unity build target failed!')
          }
        }
      }
    }
    stage("XCode打包ipa") {
      when {
        expression {
          return isUnix() && params.buildPlatform == '2'
        }
      }
      steps {
        script {

          // 尝试解锁 keychain (解决 CodeSign errSecInternalComponent 错误)
          def keychainCredId = params.keychainCredentialsId
          if (keychainCredId?.trim()) {
              // 定义统一的解锁命令，使用环境变变量 KEYCHAIN_PWD
              def unlockCmd = """
                # 1. 解锁 Keychain
                security unlock-keychain -p "\${KEYCHAIN_PWD}" "${env.HOME}/Library/Keychains/login.keychain-db"
              """
              try {
                  // 1. 优先尝试 Username with Password (兼容旧配置/用户习惯)
                  withCredentials([usernamePassword(credentialsId: keychainCredId, usernameVariable: 'KC_USER', passwordVariable: 'KEYCHAIN_PWD')]) {
                      CallCmd(unlockCmd)
                  }
              } catch (Exception e_user) {
                  // 2. 如果失败(通常是凭据类型不匹配)，则尝试 Secret Text (纯文本密码)
                  echo "Try 'Username with password' failed (${e_user.message}), retry with 'Secret text'..."
                  try {
                      withCredentials([string(credentialsId: keychainCredId, variable: 'KEYCHAIN_PWD')]) {
                          CallCmd(unlockCmd)
                      }
                  } catch (Exception e_string) {
                       // 两个都失败了(可能是ID不存在，或者网络问题等)
                       echo "unlock keychain warning: ${e_string.message}"
                  }
              }
          } else {
              echo "Skip unlock keychain (keychainCredentialsId is empty)"
          }

          // 写入ipa信息
          def tempIpaInfoProperties = "${env.WORKSPACE}/${env.TEMP_PROPERTIES_DIR}/xcode_ipa.properties"

          // Xcode ipa构建
          def XcodeBuildIpaFunction = { XcodeBuildProject xcodeProject, XcodeSigningParam signingParam ->
            def mobileprovisionFilePath = signingParam.mobileprovisionFilePath;
            
            // 安装 p12 并获取 codeSignIdentity
            def codeSignIdentity = InstallP12AndGetCodeSignIdentity(signingParam)
            
            // 自动从 mobileprovision 获取签名方法
            def signingMethod = GetSigningMethodFromMobileprovision(mobileprovisionFilePath)
            
            // 给shell写入变量值的文件
            def tempXcodeProperties = "${env.WORKSPACE}/${env.TEMP_PROPERTIES_DIR}/xcode.properties"
            def shellScripts = """
            # 获取mobileprovision的uuid和包名
            CurrentUUID=`/usr/libexec/PlistBuddy -c 'Print UUID' /dev/stdin <<< \$(security cms -D -i ${mobileprovisionFilePath})` || exit 1
            CurrentBundleId=`/usr/libexec/PlistBuddy -c 'Print :Entitlements:application-identifier' /dev/stdin <<< \$(security cms -D -i ${mobileprovisionFilePath}) | cut -d '.' -f2-` || exit 1
            CurrentDevelopmentTeam=`/usr/libexec/PlistBuddy -c 'Print TeamIdentifier:0' /dev/stdin <<< \$(security cms -D -i ${mobileprovisionFilePath})` || exit 1
            echo CurrentUUID=\${CurrentUUID} > ${tempXcodeProperties} || exit 1
            echo CurrentBundleId=\${CurrentBundleId} >> ${tempXcodeProperties} || exit 1
            echo CurrentDevelopmentTeam=\${CurrentDevelopmentTeam} >> ${tempXcodeProperties} || exit 1
            # 安装mobileprovision文件
            mkdir -p "${env.HOME}/Library/MobileDevice/Provisioning Profiles/" || exit 1
            cp "${mobileprovisionFilePath}" "${env.HOME}/Library/MobileDevice/Provisioning Profiles/\${CurrentUUID}.mobileprovision" || exit 1
            """
            def exitCode = CallCmd(shellScripts)
            if (exitCode != 0) {
              error('get mobileprovision info failed!')
            }
            // 读取之前shell获取的变量值
            def xcodeProps = readProperties file: tempXcodeProperties
            // 生成exportOptionsPlist.plist文件，打ipa需要
            def exportOptionsContent = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>provisioningProfiles</key>
    <dict>
        <key>${xcodeProps.CurrentBundleId}</key>
        <string>${xcodeProps.CurrentUUID}</string>
    </dict>
    <key>method</key>
    <string>${signingMethod}</string>
</dict>
</plist>"""
            println(exportOptionsContent)
            writeFile file: "${env.WORKSPACE}/${signingParam.filePrefix}exportOptionsPlist.plist", text: exportOptionsContent

            // 开始调用xcode build
            def xcodeShell = """
            # replace bundle id 
            sed -E -i '' '/PRODUCT_BUNDLE_IDENTIFIER = "?com\\.unity3d\\..*;/!s/PRODUCT_BUNDLE_IDENTIFIER = .*;/PRODUCT_BUNDLE_IDENTIFIER = ${xcodeProps.CurrentBundleId};/g' "${xcodeProject.xcodeProjectPath}/${xcodeProject.xcodeProjectName}.xcodeproj/project.pbxproj" || exit 1

            # xcode build
            xcodebuild archive -project "${xcodeProject.xcodeProjectPath}/${xcodeProject.xcodeProjectName}.xcodeproj" \\
              -scheme ${xcodeProject.xcodeProjectName} -sdk iphoneos -configuration Release \\
              -archivePath "${xcodeProject.archivePath}/${signingParam.filePrefix}${xcodeProject.xcodeProjectName}.xcarchive" \\
              CODE_SIGN_IDENTITY="${codeSignIdentity}" PROVISIONING_PROFILE_APP="${xcodeProps.CurrentUUID}" \\
              PRODUCT_BUNDLE_IDENTIFIER_APP="${xcodeProps.CurrentBundleId}" DEVELOPMENT_TEAM="${xcodeProps.CurrentDevelopmentTeam}" CODE_SIGN_STYLE=Manual || exit 1

            # export ipa
            xcodebuild -exportArchive -archivePath "${xcodeProject.archivePath}/${signingParam.filePrefix}${xcodeProject.xcodeProjectName}.xcarchive" \\
              -exportOptionsPlist "${env.WORKSPACE}/${signingParam.filePrefix}exportOptionsPlist.plist" \\
              -exportPath ${xcodeProject.ipaOutputPath} || exit 1  

            # mv
            iOSProductName=\$(basename "\$(find '${xcodeProject.archivePath}/${signingParam.filePrefix}${xcodeProject.xcodeProjectName}.xcarchive/Products/Applications' -name '*.app' | head -1 )" .app)
            iOSIpaName="${signingParam.filePrefix}\${iOSProductName}"
            mv "${xcodeProject.ipaOutputPath}/\${iOSProductName}.ipa" "${xcodeProject.ipaOutputPath}/\${iOSIpaName}.ipa" || exit 1
            
            # 生成 itms-services 安装用的 manifest plist 文件（从模板复制）
            cp "${env.WORKSPACE}/tools/manifest_template.plist" "${xcodeProject.ipaOutputPath}/\${iOSIpaName}.plist" || exit 1
            # 替换plist中的占位符
            sed -i '' "s|__BUNDLE_ID__|${xcodeProps.CurrentBundleId}|g" "${xcodeProject.ipaOutputPath}/\${iOSIpaName}.plist"
            sed -i '' "s|__TITLE__|\${iOSProductName}|g" "${xcodeProject.ipaOutputPath}/\${iOSIpaName}.plist"
            # 替换IPA下载地址（完整URL）
            sed -i '' "s|__IPA_URL__|${env.PACKAGE_SERVER_BASE_URL}/${env.PACKAGE_VERSION_PATH}/${env.packageRelativePath}/\${iOSIpaName}.ipa|g" "${xcodeProject.ipaOutputPath}/\${iOSIpaName}.plist"
            
            echo iOSProductName=\${iOSProductName} > ${tempIpaInfoProperties} || exit 1
            echo iOSIpaName=\${iOSIpaName} >> ${tempIpaInfoProperties} || exit 1
            """
            exitCode = CallCmd(xcodeShell)
            if (exitCode != 0) {
              error('xcodebuild failed!')
            }

          }

          // 创建一个重签名ipa文件
          def ResignIpaFunction = { XcodeBuildProject xcodeProject,XcodeSigningParam signingParam -> 
            // 安装 p12 并获取 codeSignIdentity
            def codeSignIdentity = InstallP12AndGetCodeSignIdentity(signingParam)
            
            def resignShell = """
              echo "Start resign ipa......"
              source ${tempIpaInfoProperties}
              
              # 获取重签名使用的mobileprovision的UUID
              ResignUUID=`/usr/libexec/PlistBuddy -c 'Print UUID' /dev/stdin <<< \$(security cms -D -i ${signingParam.mobileprovisionFilePath})` || exit 1
              
              # 安装mobileprovision文件
              mkdir -p "${env.HOME}/Library/MobileDevice/Provisioning Profiles/" || exit 1
              cp "${signingParam.mobileprovisionFilePath}" "${env.HOME}/Library/MobileDevice/Provisioning Profiles/\${ResignUUID}.mobileprovision" || exit 1

              # 获取重签名使用的mobileprovision的bundle-id
              ResignBundleId=`/usr/libexec/PlistBuddy -c 'Print :Entitlements:application-identifier' /dev/stdin <<< \$(security cms -D -i ${signingParam.mobileprovisionFilePath}) | cut -d '.' -f2-` || exit 1
              
    	        /bin/bash ${env.WORKSPACE}/tools/resign.sh \\
                -s "${xcodeProject.ipaOutputPath}/\${iOSIpaName}.ipa" -c "${codeSignIdentity}" \\
                -p ${signingParam.mobileprovisionFilePath} || exit 1
              ipaResignOutput="${xcodeProject.ipaOutputPath}/${signingParam.filePrefix}\${iOSProductName}.ipa"
              mv "${xcodeProject.ipaOutputPath}/\${iOSProductName}-resign.ipa" "\${ipaResignOutput}" || exit 1
              
              # 生成 itms-services 安装用的 manifest plist 文件（从模板复制）
              ipaResignPlist="${xcodeProject.ipaOutputPath}/${signingParam.filePrefix}\${iOSProductName}.plist"
              cp "${env.WORKSPACE}/tools/manifest_template.plist" "\${ipaResignPlist}" || exit 1
              # 替换plist中的占位符
              sed -i '' "s|__BUNDLE_ID__|\${ResignBundleId}|g" "\${ipaResignPlist}"
              sed -i '' "s|__TITLE__|\${iOSProductName}|g" "\${ipaResignPlist}"
              # 替换IPA下载地址（完整URL）
              sed -i '' "s|__IPA_URL__|${env.PACKAGE_SERVER_BASE_URL}/${env.PACKAGE_VERSION_PATH}/${env.packageRelativePath}/${signingParam.filePrefix}\${iOSProductName}.ipa|g" "\${ipaResignPlist}"
            """
            exitCode = CallCmd(resignShell)
            if (exitCode != 0) {
              error("ipa resgin failed: ${signingParam.filePrefix} !")
            }
          }

          // 读取证书的toml配置
          def iOSSigningConfig = readTOML file: "${env.WORKSPACE}/iOSSigningConfig.toml"
          def signingRootPath = "${env.WORKSPACE}"

          // 可用的证书map集合
          def xcodeSigningMap = [:]
          iOSSigningConfig.signings.each { key, value ->
            value.mobileprovisionFilePath = "${signingRootPath}/${value.mobileprovisionFilePath}"
            value.p12FilePath = "${signingRootPath}/${value.p12FilePath}"
            xcodeSigningMap[key] = value
          }

          def xcodeProject = new XcodeBuildProject(
            xcodeProjectName: "Unity-iPhone",
            xcodeProjectPath: env.iOSXcodeProjectPath,
            archivePath : env.iOSArchivePath,
            ipaOutputPath: env.iOSIpaOutputPath,
          )

          def cleanShellScripts = """
            rm -rf ${xcodeProject.archivePath}
            mkdir -p ${xcodeProject.archivePath} || exit 1
            mkdir -p ${xcodeProject.ipaOutputPath} || exit 1
    
            #修改CFBundleVersion
            /usr/libexec/PlistBuddy -c "Set :CFBundleVersion ${params.iOSBundleVersion}" "${xcodeProject.xcodeProjectPath}/Info.plist" || exit 1
            # clean project
            xcodebuild clean -project "${xcodeProject.xcodeProjectPath}/${xcodeProject.xcodeProjectName}.xcodeproj"  -configuration Release -alltargets || exit 1
          """
        
          def exitCode = CallCmd(cleanShellScripts)
          if (exitCode != 0) {
            error('xcode project clean failed!')
          }

          // 根据证书打包
          List signingList = params.iOSSigningType.tokenize(',')
          def count = 0
          signingList.each { String signingType ->
            def signingParam = xcodeSigningMap.get(signingType)
            if (signingParam == null) {
              error("iOS Signing Type not found:${signingType}")
            }
            if (params.iOSIpaResign && count > 0) {
              // 使用重签名ipa
              ResignIpaFunction(xcodeProject,signingParam)
            } else {
              XcodeBuildIpaFunction(xcodeProject,signingParam)
            }
            count = count + 1
          }

        }
      }
    }

    // Windows 打包压缩 stage
    stage("Windows打包压缩") {
      when {
        expression {
          // 仅在 Windows 平台且 Unity 构建未跳过时执行
          return !isUnix() && params.buildPlatform == '0' && params.SkipUnityBuild != true
        }
      }
      steps {
        script {
          def tempBuildPath = env.windowsTempBuildPath
          def finalOutputPath = env.windowsFinalOutputPath
          def buildVersionName = env.windowsBuildVersionName
          
          echo "Compressing Windows build..."
          echo "Source: ${tempBuildPath}"
          echo "Target: ${finalOutputPath}"
          
          // 使用多种方式进行 ZIP 压缩（优先级：7z > tar > PowerShell）
          // 7z 支持多线程压缩，性能最好
          def compressScript = """
            @echo off
            setlocal enabledelayedexpansion
            
            set "FINAL_PATH=${finalOutputPath}"
            set "TEMP_PATH=${tempBuildPath}"
            set "ZIP_FILENAME=${buildVersionName}.zip"
            set "ZIP_FILEPATH=%FINAL_PATH%\\%ZIP_FILENAME%"
            
            REM 确保目标目录存在
            if not exist "%FINAL_PATH%" (
              mkdir "%FINAL_PATH%"
            )
            
            REM 删除已存在的压缩文件
            if exist "%ZIP_FILEPATH%" (
              del /f /q "%ZIP_FILEPATH%"
            )
            
            REM 获取 CPU 核心数用于并行压缩
            echo Using %NUMBER_OF_PROCESSORS% CPU cores for compression...
            
            REM 优先检查 7z 命令（支持多线程压缩）
            where 7z >nul 2>&1
            if %ERRORLEVEL% equ 0 (
              echo Using 7z for multi-threaded ZIP compression...
              pushd "%TEMP_PATH%"
              7z a -tzip -mx=5 -mmt=on "%ZIP_FILEPATH%" *
              set EXIT_CODE=!ERRORLEVEL!
              popd
              if !EXIT_CODE! neq 0 (
                echo ERROR: 7z compression failed!
                exit /b 1
              )
              echo Compression completed: %ZIP_FILEPATH%
              goto :end
            )
            
            REM 检查 tar 命令（Windows 10+ 自带）
            where tar >nul 2>&1
            if %ERRORLEVEL% equ 0 (
              echo 7z not found, using tar for ZIP compression...
              pushd "%TEMP_PATH%"
              tar -a -cf "%ZIP_FILEPATH%" *
              set EXIT_CODE=!ERRORLEVEL!
              popd
              if !EXIT_CODE! neq 0 (
                echo ERROR: tar compression failed!
                exit /b 1
              )
              echo Compression completed: %ZIP_FILEPATH%
              goto :end
            )
            
            REM 最后回退到 PowerShell Compress-Archive
            echo tar not found, using PowerShell Compress-Archive...
            powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path '%TEMP_PATH%\\*' -DestinationPath '%ZIP_FILEPATH%' -CompressionLevel Optimal -Force"
            if !ERRORLEVEL! neq 0 (
              echo ERROR: PowerShell compression failed!
              exit /b 1
            )
            echo Compression completed: %ZIP_FILEPATH%
            
            :end
            REM 清理临时构建目录
            rd /s /q "%TEMP_PATH%"
            
            endlocal
          """
          
          def exitCode = bat(returnStatus: true, script: compressScript)
          if (exitCode != 0) {
            error('Windows build compression failed!')
          }
          
          echo "Windows build compressed successfully to: ${finalOutputPath}/${buildVersionName}.zip"
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
    unstable {
      script {
        // 构建不稳定时发送飞书通知
        larkNotify.sendBuildEnd(env.LARK_ROBOT_ID, 'UNSTABLE')
      }
    }
  }
}
