
class PipelineProject {
    String name
    String buildPlatform
    String description
}

// 创建三个平台的构建流水线
def projects = [
    new PipelineProject(name: "${dsl_pipelineName}-Android",      buildPlatform: '1',     description: 'build Android'),
    new PipelineProject(name: "${dsl_pipelineName}-iOS",          buildPlatform: '2',     description: 'build iOS'),
    new PipelineProject(name: "${dsl_pipelineName}-Windows",      buildPlatform: '0',     description: 'build Windows')
]

//传递过来的参数，不需访问additionalParameters，已在根环境中，直接访问
//def dsl_pipelineName = additionalParameters.dsl_pipelineName
//def dsl_scmUrl = additionalParameters.dsl_scmUrl
// 从传入参数获取默认工作目录和输出目录
def defaultWorkPath = dsl_defaultWorkPath
def defaultOutputPath = dsl_defaultOutputPath
def credentialsId = dsl_credentialsId

// 从 Pipeline 传递过来的 Jenkins 项目 SCM 信息
def jenkinsScmUrl = dsl_jenkinsScmUrl ?: 'https://github.com/821869798/JenkinsUnityAutoBuild.git'
def jenkinsScmBranch = dsl_jenkinsScmBranch ?: 'main'

def jenkinsScmCredentialsId = dsl_jenkinsScmCredentialsId

// 创建文件夹（基于父文件夹路径）
def parentFolder = dsl_parentFolder ?: ''  // 从参数获取父文件夹路径
def folderName = parentFolder ? "${parentFolder}/${dsl_pipelineName}" : dsl_pipelineName
folder(folderName) {
    description('构建流水线文件夹')
}

projects.each { project ->

    def scmUrl = dsl_scmUrl
    // job 名称包含文件夹路径
    def jobFullName = "${folderName}/${project.name}"

    def buildPlatformScript = 'return ["0":"Windows64","1":"Android","2":"iOS"]'.replaceAll(/"${project.buildPlatform}":"(.+?)"/, /"${project.buildPlatform}":"$1:selected"/)
    
    pipelineJob(jobFullName) {
        // Define job properties
        description("${project.description}")
        
        //job parameters
        parameters {
            stringParam('credentialsId', "${credentialsId}", 'Jenkins凭据ID(用于Git/SVN认证)')
            stringParam('projectPath', "${defaultWorkPath}/${project.name}", '打包项目所在的目录，不存在通过url拉取')
            stringParam('outputPath', "${defaultOutputPath}", '打包的输出目录')
            
            // buildPlatform - Extended Choice 参数
            choiceParameter {
                name('buildPlatform')
                filterable(false)
                description('选择打包平台')
                choiceType('PT_SINGLE_SELECT')
                script {
                    groovyScript {
                        script {
                            script(buildPlatformScript)
                            sandbox(true)
                        }
                        fallbackScript {
                            script("return [\"${project.buildPlatform}\"]")
                            sandbox(true)
                        }
                    }
                    randomName('')
                    filterLength(0)
                }
            }
            
            // buildMode - Extended Choice 参数
            choiceParameter {
                name('buildMode')
                filterable(false)
                description('选择打包模式')
                choiceType('PT_SINGLE_SELECT')
                script {
                    groovyScript {
                        script {
                            script('return ["0":"全量打包","1":"直接Build App","2":"打空包","3":"打热更资源版本"]')
                            sandbox(true)
                        }
                        fallbackScript {
                            script('return ["0"]')
                            sandbox(true)
                        }
                    }
                    randomName('')
                    filterLength(0)
                }
            }
            
            booleanParam('enableUnityDevelopment',false,'开启unity的developmentbuild')
            booleanParam('enableGameDevelopment',false,'Game的开发者模式,指代码的逻辑是开发者模式')
            

            
            stringParam('scmUrl', "${scmUrl}", '项目url(git|svn),直接填url或者执行git填url|branch')
            booleanParam('enableProjectUpdate',true,'使用Git或者SVN更新项目')
            booleanParam('enableBuildExcel',true,'是否导表')
            booleanParam('enableIncrement',true,'是否是增量打包')
            
            // androidBuildOption - Extended Choice 参数
            choiceParameter {
                name('androidBuildOption')
                filterable(false)
                description('打包特殊选项')
                choiceType('PT_SINGLE_SELECT')
                script {
                    groovyScript {
                        script {
                            script('return ["0":"Mono","1":"Il2cppArmFull","2":"AABModeArmFull","3":"Il2cppArmFullAndX86","4":"Il2cpp32","5":"AABModeArmFullAndX86","6":"Il2cppArm64AndX86:selected"]')
                            sandbox(true)
                        }
                        fallbackScript {
                            script('return ["6"]')
                            sandbox(true)
                        }
                    }
                    randomName('')
                    filterLength(0)
                }
            }
            
            stringParam('versionNumber', '1.0.0.0', '打包版本(前三位为app版本,最后一位资源)')
            stringParam('iOSBundleVersion', '0', 'iOS构建版本号(数字)')
            
            // iOSSigningType - Extended Choice 参数（多选）
            choiceParameter {
                name('iOSSigningType')
                filterable(false)
                description('iOS出包证书签名类型，可以多选')
                choiceType('PT_CHECKBOX')
                script {
                    groovyScript {
                        script {
                            script('return ["1":"appstore发布包:selected","2":"development开发者包:selected","3":"企业证书包"]')
                            sandbox(true)
                        }
                        fallbackScript {
                            script('return ["1:selected","2:selected"]')
                            sandbox(true)
                        }
                    }
                    randomName('')
                    filterLength(0)
                }
            }
            
            booleanParam('iOSIpaResign', true, 'iOS打包多个证书包时，后面的包使用重签名的方式加速')
            booleanParam('SkipUnityBuild', false, '跳过Unity打包,例如只测试Xcode打包')
        }

        definition {
            cpsScm {
                scm {
                    git {
                        remote {
                            url(jenkinsScmUrl)
                            credentials(jenkinsScmCredentialsId)
                        }
                        branch(jenkinsScmBranch)
                    }
                }
                scriptPath('scripts/unity_pipeline.groovy')
            }
        }
    }
}

/*
// 预先保存变量（避免在闭包内访问外部变量导致沙箱限制）
def viewName = "${dsl_pipelineName}-view"
def jobRegex = "${dsl_pipelineName}-.+"

listView(viewName) {
    //description('All unstable jobs for project A')
    filterBuildQueue()
    filterExecutors()
    jobs {
        //name('trunk-Pipeline')
        regex(jobRegex)
    }
    columns {
        status()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}
*/
