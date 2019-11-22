using System;
using System.Collections.Generic;

namespace AutoBuild
{
    public class AutoBuildArgs
    {
        //打包输出的目录
        public string outputPath;
        //最终的打包路径
        public string outputFinalPath;
        //打包的版本号,如1.0.12
        public string versionNumber;
        //版本名字，一般是作为文件名标记
        public string buildVersionName;
        //是否开启unity的开发模式
        public bool enableUnityDevelopment;
        //是否使用导出额外的工程，例如iOS
        public bool enableExportProject;
        //Game的开发者模式
        public bool enableGameDevelopment;
        //build方式
        public enum BuildMode
        {
            AllBuild = 0,           //全量打包
            NoAssetBundle = 1,      //不打包AssetBundle，直接Build
            CSharpOnly = 2,         //只重新编译C#代码（不包括lua），不打AssetBundle和场景
        }
        public BuildMode buildMode = BuildMode.AllBuild;
    }
}
