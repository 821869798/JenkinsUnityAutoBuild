pipeline {
    agent any
    parameters {
        string(name: 'dsl_pipelineName', defaultValue: 'trunk', description: '流水线名称（用于文件夹和Job命名）')
        string(name: 'dsl_scmUrl', defaultValue: 'https://github.com/821869798/unifantasy.git|main', description: '项目SCM地址与分支(git格式url|branch svn格式url)')
        string(name: 'dsl_defaultWorkPath', defaultValue: '~/Documents/JenkinsProject', description: '默认工作目录（项目存放路径）')
        string(name: 'dsl_defaultOutputPath', defaultValue: '~/Documents/JenkinsOutput', description: '默认输出目录（打包输出路径）')
        string(name: 'dsl_credentialsId', defaultValue: '', description: 'Jenkins凭据ID（用于Git/SVN认证）')
        string(name: 'dsl_keychainCredentialsId', defaultValue: '', description: 'Mac Keychain凭据ID（默认gt, 为空不解锁）')
    }
    stages {
        stage('使用dsl创建jobs') {
            steps {
                script {
                    // 获取当前 job 所在的文件夹路径
                    def currentFolder = ''
                    def jobName = env.JOB_NAME
                    if (jobName.contains('/')) {
                        currentFolder = jobName.substring(0, jobName.lastIndexOf('/'))
                    }
                    
                    // scm 是一个全局变量，userRemoteConfigs 是 GitSCM 的属性
                    // 安全获取 SCM 配置，防止 null 或空列表
                    def remoteConfig = (scm?.userRemoteConfigs?.size() > 0) ? scm.userRemoteConfigs[0] : null
                    def jenkinsScmUrl = env.GIT_URL
                    def credId = remoteConfig?.credentialsId

                    // 获取当前分支（去掉 origin/ 前缀）
                    def jenkinsScmBranch = env.GIT_BRANCH?.replaceFirst(/^origin\//, '') ?: 'main'
                    println("pipeline credentialsId: ${credId}")
                    println("pipeline git url: ${jenkinsScmUrl}")
                    println("pipeline git branch: ${jenkinsScmBranch}")
                    
                    def dslParams = [
                        dsl_pipelineName: params.dsl_pipelineName,
                        dsl_scmUrl: params.dsl_scmUrl,
                        dsl_parentFolder: currentFolder,
                        dsl_defaultWorkPath: params.dsl_defaultWorkPath,
                        dsl_defaultOutputPath: params.dsl_defaultOutputPath,
                        dsl_credentialsId: params.dsl_credentialsId,
                        dsl_jenkinsScmUrl: jenkinsScmUrl,
                        dsl_jenkinsScmBranch: jenkinsScmBranch,
                        dsl_jenkinsScmCredentialsId: credId,
                        dsl_keychainCredentialsId: params.dsl_keychainCredentialsId
                    ]
                    jobDsl sandbox: true, targets: 'scripts/create_jobs_dsl.groovy', additionalParameters: dslParams
                }
            }
        }
    }
}
